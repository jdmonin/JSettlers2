/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2024 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
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
package soc.client;

import soc.game.GameAction;
import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOptionSet;
import soc.game.SOCInventoryItem;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.game.SOCRoutePiece;
import soc.game.SOCScenario;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCVillage;
import soc.message.SOCSimpleRequest;  // to request simple things from the server without defining a lot of methods
import soc.util.SOCStringManager;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Insets;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.Timer;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

/**
 * This is a component that can display a Settlers of Catan Board.
 * It can be used in an applet or an application.
 *
 *<H3>Graphics:</H3>
 * This panel loads its hex texture images and dice result numbers from a directory named {@code images}
 * in the same directory as this class. Everything else is drawn as lines, circles, polygons, and text.
 * If the user's preferred set of hex images changes after creating a {@code SOCBoardPanel},
 * call {@link #reloadBoardGraphics(Component)} and {@link #flushBoardLayoutAndRepaint()}.
 *<P>
 * The main drawing methods are {@link #drawBoardEmpty(Graphics)} for hexes and ports,
 * and {@link #drawBoard(Graphics)} for placed pieces like settlements and the robber.
 * The board background color is set in {@link SOCPlayerInterface}.
 * Since all areas outside the board boundaries are filled with
 * water hex tiles, this color is only a fallback; it's briefly visible
 * at window creation while the hexes are loading and rendering.
 *
 *<H3>Updating game data:</H3>
 * This panel draws the board and pieces currently in the game data.
 * Call {@link #setPlayer(SOCPlayer)} when the client sits down as a player.
 * When game data changes, call methods like {@link #repaint()} or {@link #flushBoardLayoutAndRepaint()}.
 * Call other methods like {@link #updateMode()} or {@link #setLatestPiecePlacement(SOCPlayingPiece)} as needed
 * to help the panel understand specific game events.
 *
 *<H3>Interaction:</H3>
 * When the mouse is over the game board, a tooltip shows information
 * such as a hex's resource, a piece's owner, a port's trading ratio, or the
 * number under the robber. See {@link #hoverTip}.
 *<P>
 * During game play, moving the mouse over the board shows ghosted roads,
 * settlements, cities, and ships at locations the player can build.
 * See: {@link #hilight}, {@link SOCBoardPanel.BoardToolTip#hoverRoadID}.
 * Right-click to build, or use the {@link SOCBuildingPanel}'s buttons.
 *<P>
 * Before the game begins, the boardpanel is full of water hexes.
 * You can show a short message text of 1 or 2 lines by calling
 * {@link #setSuperimposedText(String, String)}.
 *<P>
 * During game play, you can show a short 1-line message text in the
 * top-center part of the panel by calling {@link #setSuperimposedTopText(String)}.
 *
 *<H3>Scaling and rotation:</H3>
 * Based on board size in hexes, this panel's minimum width and height in pixels is {@link #getMinimumSize()}.
 * To set its size, call {@link #setSize(int, int)} or {@link #setBounds(int, int, int, int)};
 * these methods will set a flag to rescale board graphics if needed.
 * If the game has 6 players but not {@link SOCGame#hasSeaBoard}, the board is also
 * rotated 90 degrees clockwise.
 *<P>
 * Pixel coordinates can be transformed between actual (scaled/rotated) and
 * unscaled/un-rotated "internal" pixel coordinates with
 * {@link #scaleFromActual(int)}, {@link #scaleToActual(int)},
 * {@link #scaleFromActual(int[])}, {@link #scaleToActual(int[])}.
 *<P>
 * The panel can in some cases be stretched wider than the board requires, with a built-in x-margin:
 * {@link SOCPlayerInterface}'s custom LayoutManager checks for the necessary conditions.
 *
 *<H3>Sequence for loading, rendering, and drawing images:</H3>
 *<UL>
 *  <LI> Constructor calls {@link #loadImages(Component, boolean)} and {@link #rescaleCoordinateArrays()}
 *  <LI> Layout manager calls {@code setSize(..)} or {@code setBounds(..)},
 *       which calls {@link #rescaleBoard(int, int)}
 *  <LI> {@link #rescaleBoard(int, int)} scales hex images, calls
 *       {@link #renderBorderedHex(Image, Color)} and {@link #renderPortImages()}
 *       into image buffers to use for redrawing the board
 *  <LI> {@link #paintComponent(Graphics)} calls {@link #drawBoard(Graphics)}
 *  <LI> First call to {@code drawBoard(..)} calls {@link #drawBoardEmpty(Graphics)} which renders into a buffer image
 *  <LI> {@code drawBoard(..)} draws the placed pieces over the buffered board image from {@code drawBoardEmpty(..)}
 *</UL>
 *
 *<H3>Scenario-specific board items:</H3>
 * Some scenarios have special pieces, like {@code SC_WOND}'s {@link SOCFortress}
 * (tooltip text from keyed strings {@code board.sc_piri.*}) or {@code SC_CLVI}'s Villages.
 *<BR>
 * Some scenarios draw colored diamonds at specific nodes or edges, to indicate things that are part of the scenarios.
 * Hovering over a diamond should show a tooltip for its purpose:
 *<UL>
 * <LI> {@link SOCScenario#K_SC_CLVI SC_CLVI}: Yellow {@link SOCVillage}s, each with a remaining-cloth count number.
 *   Tooltip text from {@code board.sc_clvi.village}.
 * <LI> {@link SOCScenario#K_SC_FTRI SC_FTRI}: Yellow Dev Card "gift" edges, green SVP "gift" edges.
 *   Tooltip text from {@code board.edge.devcard}, {@code board.edge.svp}.
 * <LI> {@link SOCScenario#K_SC_WOND SC_WOND}: Node Sets {@code N1} - {@code N3} where player cannot initially place,
 *   or where a building is required to start building a certain Wonder. Various colors.
 *   Tooltip text from {@code board.nodelist._SC_WOND.N1} - {@code .N3}.
 *</UL>
 */
@SuppressWarnings("serial")
/*package*/ class SOCBoardPanel extends JComponent implements MouseListener, MouseMotionListener
{
    /** i18n text strings */
    private static final SOCStringManager strings = SOCStringManager.getClientManager();

    /**
     * Hex and port graphics sets are in subdirectories of this directory
     * (see {@link #HEX_GRAPHICS_SET_SUBDIRS}). Within each set,
     * the rotated versions for the 6-player non-sea board are in <tt><i>DIRNAME</i>/rotat</tt>.
     * The images are loaded into the {@link #hexes}, {@link #rotatHexes},
     * {@link #scaledHexes}, and {@link #scaledPorts} arrays.
     */
    private static String IMAGEDIR = "/resources/hexes";

    /**
     * List of subdirectories for each hex and port graphics set, within {@link #IMAGEDIR}.
     * Indexes are the known values of {@link SOCPlayerClient#PREF_HEX_GRAPHICS_SET}:
     * 0 = pastel, 1 = classic. The currently-used index is {@link #hexesGraphicsSetIndex}.
     *<P>
     * Each set includes hexes for all land types and water, a subdir <tt>rotat</tt> for rotated hexes,
     * and related graphics like <tt>miscPort.gif</tt>.
     *<P>
     * Colors for water border and other items vary by Graphics Set;
     * see {@link #HEX_GRAPHICS_SET_BORDER_WATER_COLORS}, {@link #HEX_GRAPHICS_SET_SC_PIRI_PATH_COLORS}.
     *
     * @since 2.0.00
     */
    private static String[] HEX_GRAPHICS_SET_SUBDIRS = { "pastel", "classic" };

    /**
     * For {@link #isScaled}, minimum acceptable scaling factor.
     * Will use unscaled graphics if panel width or height isn't at least
     * this ratio * {@link #minSize}.
     * @since 2.0.00
     */
    private static float SCALE_FACTOR_MIN = 1.08f;

    /**
     * When {@link #isLargeBoard}, the minimum visual {@link SOCBoard#getBoardWidth()}
     * for good-looking aspect ratio, and enough width for {@link SOCBuildingPanel} contents below:
     * 18 half-hex units.
     * @since 2.0.00
     */
    private static final int BOARDWIDTH_VISUAL_MIN = 18;

    /**
     * When {@link #isLargeBoard}, the minimum visual {@link SOCBoard#getBoardHeight()}
     * for enough height for {@link SOCHandPanel}s to left and right:
     * 17 half-hex units.
     * @since 2.0.00
     */
    private static final int BOARDHEIGHT_VISUAL_MIN = 17;

    /**
     * How many unscaled pixels to move down for each row of hexes.
     * @see #HEXHEIGHT
     * @since 1.1.00
     */
    private static final int deltaY = 46;

    /**
     * How many unscaled pixels to move over for a new hex.
     * @see #HEXWIDTH
     * @since 1.1.00
     */
    private static final int deltaX = 54;

    /**
     * Width of half a hex in unscaled pixels;
     * each row moves a half hex over horizontally,
     * compared to the row above/below it.
     * @see #deltaX
     * @see #halfdeltaY
     * @since 1.1.00
     */
    private static final int halfdeltaX = 27;

    /**
     * Height of half a hex in unscaled pixels.
     * Used in layout calculations when {@link #isLargeBoard}.
     * @since 2.0.00
     * @see #deltaY
     * @see #halfdeltaX
     * @see #HALF_HEXHEIGHT
     * @see #HEXY_OFF_SLOPE_HEIGHT
     */
    private static final int halfdeltaY = 23;

    /**
     * x-offset to move over 1 hex, for each port facing direction (1-6).
     * Unscaled. 0 is unused.
     *<P>
     * Facing is the direction to the land hex touching the port.
     * Facing 1 is NE, 2 is E, 3 is SE, 4 is SW, etc: see {@link SOCBoard#FACING_E} etc.
     * @see #DELTAY_FACING
     * @since 1.1.08
     */
    private static final int[] DELTAX_FACING =
    {
        0, halfdeltaX, deltaX, halfdeltaX, -halfdeltaX, -deltaX, -halfdeltaX
    };

    /**
     * y-offset to move over 1 hex, for each port facing direction (1-6).
     * Unscaled. 0 is unused.
     * @see #DELTAX_FACING
     * @since 1.1.08
     */
    private static final int[] DELTAY_FACING =
    {
        0, -deltaY, 0, deltaY, deltaY, 0, -deltaY
    };

    /**
     * hex coordinates for drawing the classic 4-player board.
     * Upper-left corner of each hex, left-to-right in each row.
     * These were called {@link #hexX}, {@link #hexY} before 1.1.08.
     * @see #hexX_6pl
     */
    private static final int[] hexX_st =
    {
        deltaX + halfdeltaX, 2 * deltaX + halfdeltaX, 3 * deltaX + halfdeltaX, 4 * deltaX + halfdeltaX,  // row 1 4 hexes
        deltaX, 2 * deltaX, 3 * deltaX, 4 * deltaX, 5 * deltaX,                                          // row 2 5 hexes
        halfdeltaX, deltaX + halfdeltaX, 2 * deltaX + halfdeltaX, 3 * deltaX + halfdeltaX, 4 * deltaX + halfdeltaX, 5 * deltaX + halfdeltaX,  // row 3 6 hexes
        0, deltaX, 2 * deltaX, 3 * deltaX, 4 * deltaX, 5 * deltaX, 6 * deltaX,                           // row 4 7 hexes
        halfdeltaX, deltaX + halfdeltaX, 2 * deltaX + halfdeltaX, 3 * deltaX + halfdeltaX, 4 * deltaX + halfdeltaX, 5 * deltaX + halfdeltaX,  // row 5 6 hexes
        deltaX, 2 * deltaX, 3 * deltaX, 4 * deltaX, 5 * deltaX,                                          // row 6 5 hexes
        deltaX + halfdeltaX, 2 * deltaX + halfdeltaX, 3 * deltaX + halfdeltaX, 4 * deltaX + halfdeltaX   // row 7 4 hexes
    };
    private static final int[] hexY_st =
    {
        0, 0, 0, 0,
        deltaY, deltaY, deltaY, deltaY, deltaY,
        2 * deltaY, 2 * deltaY, 2 * deltaY, 2 * deltaY, 2 * deltaY, 2 * deltaY,
        3 * deltaY, 3 * deltaY, 3 * deltaY, 3 * deltaY, 3 * deltaY, 3 * deltaY, 3 * deltaY,
        4 * deltaY, 4 * deltaY, 4 * deltaY, 4 * deltaY, 4 * deltaY, 4 * deltaY,
        5 * deltaY, 5 * deltaY, 5 * deltaY, 5 * deltaY, 5 * deltaY,
        6 * deltaY, 6 * deltaY, 6 * deltaY, 6 * deltaY
    };

    /**
     * hex coordinates for drawing the 6-player board, or null.
     * Initialized by constructor of the first 6-player boardpanel.
     * To make room for the ring of ports/water which isn't in the
     * coordinate system, the offset from {@link #hexX_st} is {@link #HEXX_OFF_6PL},
     * and the offset from {@link #hexY_st} is {@link #HEXY_OFF_6PL}.
     * @see #hexX
     * @since 1.1.08
     */
    private static int[] hexX_6pl, hexY_6pl;

    /**
     * In 6-player mode, the offset of {@link #hexX_6pl}, {@link #hexY_6pl}
     * from {@link #hexX_st}, {@link #hexY_st} in unscaled board coordinates.
     * Remember that x and y are swapped on-screen, because the board is rotated.
     * @since 1.1.08
     */
    private static final int HEXX_OFF_6PL = deltaX, HEXY_OFF_6PL = deltaY;

    /**
     * In 6-player mode, subtract this instead of {@link #HEXY_OFF_6PL}
     * in {@link #findEdge(int, int, boolean)}, {@link #findHex(int, int)}, {@link #findNode(int, int)}.
     * @since 1.1.08
     */
    private static final int HEXY_OFF_6PL_FIND = 7;

    /**
     * The vertical offset for A-nodes vs Y-nodes along a road;
     * the height of the sloped top/bottom hex edges.
     * @since 2.0.00
     * @see #HEXHEIGHT
     * @see #hexCornersY
     * @see #halfdeltaY
     */
    private static final int HEXY_OFF_SLOPE_HEIGHT = 16;

    /**
     * Diameter for rendering the hex port graphics' clear middle circle in {@link #renderPortImages()}.
     * The border drawn around the clear circle is also based on this.
     * @see #portArrowsX
     * @since 1.1.20
     */
    private static final int HEX_PORT_CIRCLE_DIA = 38;

    /**
     * Line stroke width when drawing {@link HilightStyle#MOST_RECENT_PLACEMENT} pieces.
     * @since 2.7.00
     */
    private static final int MOST_RECENT_PLACEMENT_STROKE_WIDTH_OUTER = 5,
        MOST_RECENT_PLACEMENT_STROKE_WIDTH_INNER = 3;

    /**
     * coordinates for drawing the playing pieces
     */
    /***  road looks like "|" along left edge of hex ***/
    private static final int[] vertRoadX = { -2,  3,  3, -2, -2 };  // center is (x=0.5, y=32 == HALF_HEXHEIGHT)
    private static final int[] vertRoadY = { 17, 17, 47, 47, 17 };

    /***  road looks like "/" along upper-left edge of hex ***/
    private static final int[] upRoadX = { -1, 26, 29, 2, -1 };
    private static final int[] upRoadY = { 15, -2, 2, 19, 15 };

    /***  road looks like "\" along lower-left edge of hex ***/
    private static final int[] downRoadX = { -1, 2, 29, 26, -1 };
    private static final int[] downRoadY = { 49, 45, 62, 66, 49 };

    /***  settlement  ***/
    private static final int[] settlementX = { -7, 0, 7, 7, -7, -7, 7 };
    private static final int[] settlementY = { -7, -14, -7, 5, 5, -7, -7 };

    /***  city  ***/
    private static final int[] cityX =
    {
        -10, -4, 2, 2, 10, 10, -10, -10, 0, 0, 10, 5, -10
    };
    private static final int[] cityY =
    {
        -8, -14, -8, -4, -4, 6, 6, -8, -8, -4, -4, -8, -8
    };

    /**
     * Ship.
     * Center is (x=0.5, y=32 == {@link #HALF_HEXHEIGHT}).
     * @see #warshipX
     * @since 2.0.00
     */
    private static final int[] shipX =           // center is (x=0.5, y=32)
        { -2,  3,  6,  6,  4, 13, 11, -11, -11, -1, -1,  0,  0, -2 },
                               shipY =
        { 22, 24, 28, 33, 36, 36, 43,  43,  36, 36, 34, 32, 27, 22 };

    /**
     * Warship for scenario <tt>SC_PIRI</tt>.
     * Center is (x=0.5, y=32 == {@link #HALF_HEXHEIGHT}).
     * Design is based on the normal ship ({@link #shipX}, {@link #shipY})
     * with two sails (offset x-5 and +5 from single-sail model).
     * @since 2.0.00
     */
    private static final int[] warshipX =        // center is (x=0.5, y=32)
        { -7, -2,  1,  1, -1,    4,  4,  5,  5, 3,   8, 11, 11,  9,    13, 11, -11, -11, -6, -6, -5, -5, -7 },
                               warshipY =
        { 22, 24, 28, 33, 36,   36, 34, 32, 27, 22, 24, 28, 33, 36,    36, 43,  43,  36, 36, 34, 32, 27, 22 };

    /**
     * Fortress polygon for scenario <tt>SC_PIRI</tt>.
     * X is -14 to +14; Y is -11 to +11.
     * @since 2.0.00
     */
    private static final int[] fortressX =
        //  left side        //  crenellations
        // right side        // bottom of towers and of main mass
        { -14,-14, -7, -7,   -5, -5, -1, -1, 1,  1,  5, 5,
          7,  7, 14, 14,      7, 7, -7, -7 },
                               fortressY =
        {  11,-11,-11, -7,   -7, -9, -9, -7,-7, -9, -9,-7,
         -7,-11,-11, 11,     11, 9,  9, 11 };

    /**
     * marker diamond/"village" polygon. X is -14 to +14; Y is -10 to +10.
     * Used in {@link #drawVillage(Graphics, SOCVillage)}, and as a
     * generic marker in {@link #drawMarker(Graphics, int, int, Color, int)}.
     *<P>
     * Before v2.3.00 these fields were called villageX, villageY.
     * @since 2.0.00
     */
    private static final int[] diamondX = {  0,  14,  0, -14,   0 },
                               diamondY = { -10,  0, 10,   0, -10 };

    /** robber polygon. X is -4 to +4; Y is -8 to +8. */
    private static final int[] robberX =
    {
        -2, -4, -4, -2, 2, 4, 4, 2, 4, 4, -4, -4, -2, 2
    };
    private static final int[] robberY =
    {
        -2, -4, -6, -8, -8, -6, -4, -2, 0, 8, 8, 0, -2, -2
    };

    // The pirate ship uses shipX, shipY like any other ship.

    /**
     * For port hexes, the triangular arrowheads towards port settlement nodes:
     * Array of shapes' X coordinates. For Y see {@link #portArrowsY}.
     *<P>
     * First index is the shape number, second index is coordinates within the shape, clockwise from tip.
     * Shape numbers are 0-5, for the 6 facing directions numbered the same way as
     * {@link SOCBoard#getAdjacentNodeToHex(int)}: clockwise from top (northern point of hex),
     * 0 is north, 1 is northeast, etc, 5 is northwest.
     * @see #HEX_PORT_CIRCLE_DIA
     * @since 1.1.20
     */
    private static final int[][] portArrowsX =
    {
        { 27, 31, 23 },
        { 51, 49, 45 },
        { 51, 45, 49 },
        { 27, 23, 31 },
        { 3, 5, 9 },
        { 3, 9, 5 },
    };

    /**
     * For port hexes, the triangular arrowheads towards port settlement nodes:
     * Array of shapes' Y coordinates. For X and details see {@link #portArrowsX}.
     * @since 1.1.20
     */
    private static final int[][] portArrowsY =
    {
        { 4, 8, 8 },
        { 18, 24, 16 },
        { 45, 47, 39 },
        { 59, 55, 55 },
        { 45, 39, 47 },
        { 18, 16, 24 },
    };

    /**
     * Current-player arrow, left-pointing.
     * First point is top of arrow-tip's bevel, last point is bottom of tip.
     * arrowXL[4] is rightmost X coordinate.
     * (These points are important for adjustment when scaling in {@link #rescaleCoordinateArrays()})
     * @see #arrowY
     * @see #ARROW_SZ
     * @since 1.1.00
     */
    private static final int[] arrowXL =
    {
        0,  17, 18, 18, 36, 36, 18, 18, 17,  0
    };

    /**
     * Current-player arrow, right-pointing.
     * Calculated when needed by flipping {@link #arrowXL} in {@link #rescaleCoordinateArrays()}.
     * @since 1.1.00
     */
    private static int[] arrowXR = null;

    /**
     * Current-player arrow, y-coordinates: same whether pointing left or right.
     * First point is top of arrow-tip's bevel, last point is bottom of tip.
     * @since 1.1.00
     */
    private static final int[] arrowY =
    {
        17,  0,  0,  6,  6, 30, 30, 36, 36, 19
    };

    /**
     * Current-player arrow fits in a 37 x 37 square.
     * @see #arrowXL
     * @since 1.1.00
     */
    private static final int ARROW_SZ = 37;

    /**
     * Current-player arrow color: Light green tint: #E0FFE0.
     *<P>
     * Before v2.0.00 this was cyan #6AB7B7 (r=106, g=183, b=183).
     *
     * @see #ARROW_COLOR_PLACING
     * @since 1.1.00
     */
    private static final Color ARROW_COLOR = new Color(224, 255, 224);

    /**
     * Player arrow color when game is over,
     * and during {@link SOCGame#SPECIAL_BUILDING} phase of the 6-player game.
     *<P>
     * The game-over color was added in 1.1.09.  Previously, {@link #ARROW_COLOR} was used.
     * @since 1.1.08
     */
    private static final Color ARROW_COLOR_PLACING = new Color(255, 255, 60);

    /**
     * Border colors for hex rendering.
     * Same indexes as {@link #hexes}.
     * Used in {@link #rescaleBoard(int, int)}.
     *<P>
     * Water hex border color varies by hex Graphics Set;
     * use {@link #HEX_GRAPHICS_SET_BORDER_WATER_COLORS}
     * instead of index 0 here.
     *
     * @see #ROTAT_HEX_BORDER_COLORS
     * @since 1.1.20
     */
    private static final Color[] HEX_BORDER_COLORS =
    {
        null,  // water
        new Color(78,16,0), new Color(58,59,57), new Color(20,113,0),  // clay, ore, sheep
        new Color(142,109,0), new Color(9,54,13), new Color(203,180,73),  // wheat, wood, desert
        null, new Color(188,188,188)  // gold (no border), fog
    };

    /**
     * Border colors for hex rendering when {@link #isRotated}.
     * Same indexes as {@link #rotatHexes}.
     * Used in {@link #rescaleBoard(int, int)}.
     *<P>
     * Water hex border color varies by hex Graphics Set;
     * use {@link #HEX_GRAPHICS_SET_BORDER_WATER_COLORS}
     * instead of index 0 here.
     *
     * @see #HEX_BORDER_COLORS
     * @since 1.1.20
     */
    private static final Color[] ROTAT_HEX_BORDER_COLORS =
    {
        null,  // water
        new Color(120,36,0), HEX_BORDER_COLORS[2], HEX_BORDER_COLORS[3],  // clay, ore, sheep
        HEX_BORDER_COLORS[4], new Color(9,54,11), HEX_BORDER_COLORS[6]  // wheat, wood, desert
    };

    /**
     * Water hex border color, based on {@code waterHex.gif} average color, which varies by hex Graphics Set.
     * Currently-used index is {@link #hexesGraphicsSetIndex}.
     * Same indexes as {@link #HEX_GRAPHICS_SET_SUBDIRS}; see that javadoc for details.
     *<P>
     * For non-water borders, instead use {@link #HEX_BORDER_COLORS} or {@link #ROTAT_HEX_BORDER_COLORS}.
     * @see #HEX_GRAPHICS_SET_SC_PIRI_PATH_COLORS
     * @since 2.0.00
     */
    private static final Color[] HEX_GRAPHICS_SET_BORDER_WATER_COLORS =
    {
        new Color(80, 138, 181),  // pastel.  waterHex.gif HSV 205, 48, 87: Border HSV 205, 56, 71
        new Color(38,  60, 113),  // classic. waterHex.gif HSV 222, 60, 55: Border HSV 222, 66, 44
    };

    /**
     * Pirate Path dotted-line color, which varies by hex Graphics Set, for
     * scenario gameopt {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.
     * Currently-used index is {@link #hexesGraphicsSetIndex}.
     * Same indexes as {@link #HEX_GRAPHICS_SET_SUBDIRS}; see that javadoc for details.
     * @see #HEX_GRAPHICS_SET_BORDER_WATER_COLORS
     * @since 2.0.00
     */
    private static final Color[] HEX_GRAPHICS_SET_SC_PIRI_PATH_COLORS =
    {
        ColorSquare.WATER.darker(),    // pastel
        ColorSquare.WATER.brighter(),  // classic
    };

    /**
     * For repaint when retrying a failed rescale-image,
     * the 3-second delay (in millis) before which {@link DelayedRepaint} will call repaint().
     *<P>
     * This constant was introduced in v1.1.20, previously the value was hardcoded.
     * @see #scaledMissedImage
     * @see #RESCALE_MAX_RETRY_MS
     * @since 1.1.20
     */
    private static final int RESCALE_RETRY_DELAY_MS = 3000;

    /**
     * For repaint when retrying a failed rescale-image,
     * the 7-second maximum time (in millis) after which no more retries will be done.
     *<P>
     * This constant was introduced in v1.1.20, previously the value was hardcoded.
     * @see #scaledMissedImage
     * @see #RESCALE_RETRY_DELAY_MS
     * @since 1.1.20
     */
    private static final int RESCALE_MAX_RETRY_MS = 7000;

    /**
     * BoardPanel's {@link #mode}s. NONE is normal gameplay, or not the client's turn.
     * For correlation to game state, see {@link #updateMode()}.
     *<P>
     * If a mode is added, please also update {@link #clearModeAndHilight(int)}.
     * If a piece or item hovers in this mode with the mouse cursor, update
     * {@link SOCBoardPanel.BoardToolTip#handleHover(int, int, int, int)}.
     * If the player clicks to place or interact in the new mode, update {@link #mouseClicked(MouseEvent)}.
     * See {@link #hilight} and {@link SOCBoardPanel.BoardToolTip}.
     */
    private final static int NONE = 0;

    /**
     * Place a road, or place a free road when not {@link #isLargeBoard}.
     * @see #PLACE_FREE_ROAD_OR_SHIP
     */
    private final static int PLACE_ROAD = 1;

    private final static int PLACE_SETTLEMENT = 2;

    private final static int PLACE_CITY = 3;

    /**
     * Place the robber, or just hover at a hex.
     * @see #PLACE_PIRATE
     */
    private final static int PLACE_ROBBER = 4;

    /**
     * Place an initial settlement, or just hover at a port.
     * @see SOCBoardPanel.BoardToolTip#hoverIsPort
     */
    private final static int PLACE_INIT_SETTLEMENT = 5;

    /** Place an initial road or ship. */
    private final static int PLACE_INIT_ROAD = 6;

    /**
     * Hover while picking a {@code :consider-move} at robot {@link #otherPlayer}'s
     * {@link SOCPlayer#canPlaceSettlement(int) canPlaceSettlement(node)} locations.
     */
    public final static int CONSIDER_LM_SETTLEMENT = 7;

    /** Hover while picking a {@code :consider-move} at robot {@link #otherPlayer}'s potential roads. */
    public final static int CONSIDER_LM_ROAD = 8;

    /**
     * Hover while picking a {@code :consider-move} at robot {@link #otherPlayer}'s potential ships.
     * @since 2.0.00
     */
    public final static int CONSIDER_LM_SHIP = 13;

    /** Hover while picking a {@code :consider-move} at robot {@link #otherPlayer}'s potential cities. */
    public final static int CONSIDER_LM_CITY = 9;

    /**
     * Hover while picking a {@code :consider-target} at robot {@link #otherPlayer}'s
     * {@link SOCPlayer#canPlaceSettlement(int) canPlaceSettlement(node)} locations.
     */
    public final static int CONSIDER_LT_SETTLEMENT = 10;

    /** Hover while picking a {@code :consider-target} at robot {@link #otherPlayer}'s potential roads. */
    public final static int CONSIDER_LT_ROAD = 11;

    /**
     * Hover while picking a {@code :consider-target} at robot {@link #otherPlayer}'s potential ships.
     * @since 2.0.00
     */
    public final static int CONSIDER_LT_SHIP = 14;

    /** Hover while picking a {@code :consider-target} at robot {@link #otherPlayer}'s potential cities. */
    public final static int CONSIDER_LT_CITY = 12;

    // see above for mode#s 13, 14

    /** Place a ship on the large sea board.
     *  @since 2.0.00 */
    private final static int PLACE_SHIP = 15;

    /**
     * Place a free road or ship on the large sea board.
     * If not {@link #isLargeBoard}, use {@link #PLACE_ROAD} instead.
     * @since 2.0.00
     */
    private final static int PLACE_FREE_ROAD_OR_SHIP = 16;

    /**
     * Move a previously-placed ship on the large sea board.
     * Also set {@link #moveShip_fromEdge} and {@link #moveShip_isWarship}.
     * @since 2.0.00
     */
    private final static int MOVE_SHIP = 17;

    /**
     * Move the pirate ship.
     * @since 2.0.00
     * @see #PLACE_ROBBER
     */
    private final static int PLACE_PIRATE = 18;

    /**
     * In scenario option {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI} game state {@link SOCGame#PLACING_INV_ITEM},
     * boardpanel mode to place a port next to player's coastal settlement/city.
     * @since 2.0.00
     */
    private final static int SC_FTRI_PLACE_PORT = 19;

    // future boardpanel state#s can go here

    /**
     * Player's turn is starting; waiting for current player to take action.
     * (State {@link SOCGame#ROLL_OR_CARD})
     * @since 1.1.03
     */
    private final static int TURN_STARTING = 97;

    /**
     * Game is still forming, players are sitting down.
     * (State {@link SOCGame#READY} or {@link SOCGame#NEW})
     * @since 1.1.00
     */
    private final static int GAME_FORMING = 98;

    /**
     * Game has ended.
     * @since 1.1.00
     */
    private final static int GAME_OVER = 99;

    /**
     * During initial-piece placement, the tooltip is moved this far over to make room.
     * @since 1.1.00
     */
    public final static int HOVER_OFFSET_X_FOR_INIT_PLACE = 9;

    /**
     * During robber placement, the tooltip is moved this far over to make room.
     * @since 1.1.00
     */
    public final static int HOVER_OFFSET_X_FOR_ROBBER = 15;

    /**
     * For popup-menu build request, network send maximum delay (seconds)
     * @since 1.1.00
     */
    protected static int BUILD_REQUEST_MAX_DELAY_SEC = 5;

    /**
     * For popup-menu build request, length of time after popup to ignore further
     * mouse-clicks.  Avoids Windows accidental build by popup-click during game's
     * initial piece placement. (150 ms)
     * @since 1.1.00
     */
    protected static int POPUP_MENU_IGNORE_MS = 150;

    /**
     * Pixel spacing around {@link #superText1}, {@link #superText2}, {@link #superTextTop}
     * @since 1.1.07
     */
    private static final int SUPERTEXT_INSET = 3, SUPERTEXT_PADDING_HORIZ = 2 * SUPERTEXT_INSET + 2;

    /**
     * hex size, in unscaled internal-pixels: 55 wide, 64 tall.
     * The road polygon coordinate-arrays ({@link #downRoadX}, etc)
     * are plotted against a hex of this size.
     * @see #deltaX
     * @see #deltaY
     * @see #HALF_HEXHEIGHT
     * @see #HEXY_OFF_SLOPE_HEIGHT
     */
    private static final int HEXWIDTH = 55, HEXHEIGHT = 64;

    /**
     * Half of {@link #HEXHEIGHT}, in unscaled internal pixels, for use with various board graphics.
     * Also == {@link #halfdeltaY} + 9.
     * @see #HEXY_OFF_SLOPE_HEIGHT
     * @since 2.0.00
     */
    private static final int HALF_HEXHEIGHT = 32;

    /**
     * When using classic 4-player board, the width padding (unscaled internal-pixels) in {@link #PANELX}
     * beyond {@link SOCBoard#WIDTH_VISUAL_ORIGINAL} * {@link #halfdeltaX}.
     * @see #PANELPAD_LBOARD_RT
     * @since 2.0.00
     */
    private static final int PANELPAD_CBOARD4_WIDTH = 1 + halfdeltaX;

    /**
     * When using classic 4-player board, the height padding (unscaled internal-pixels) in {@link #PANELY}
     * beyond {@link SOCBoard#HEIGHT_VISUAL_ORIGINAL} * {@link #halfdeltaY} + {@link #HEXY_OFF_SLOPE_HEIGHT}.
     * @see #PANELPAD_LBOARD_BTM
     * @since 2.0.00
     */
    private static final int PANELPAD_CBOARD4_HEIGHT = 2;

    /**
     * When {@link #isLargeBoard}, padding on right-hand side so pieces there are visible,
     * in internal coordinates (like {@link #panelMinBW}).
     * @see #PANELPAD_CBOARD4_WIDTH
     * @since 2.0.00
     */
    private static final int PANELPAD_LBOARD_RT = HALF_HEXHEIGHT;

    /**
     * When {@link #isLargeBoard}, padding on bottom side so pieces there are visible,
     * in internal coordinates (like {@link #panelMinBH}).
     * @see #PANELPAD_CBOARD4_HEIGHT
     * @since 2.0.00
     */
    private static final int PANELPAD_LBOARD_BTM = HEXWIDTH / 4;

    /**
     * Default size of the whole panel, in unscaled internal pixels.
     * This constant may not reflect the current game board's minimum size:
     * In board-internal coordinates, use {@link #panelMinBW} and {@link #panelMinBH} instead.
     * For minimum acceptable size in on-screen pixels,
     * call {@link #getMinimumSize()} instead of using PANELX and PANELY directly.
     * For actual current size in screen pixels, see
     * {@link #scaledPanelW} {@link #scaledPanelH};
     * If {@link #isRotated()}, the minimum size swaps {@link #PANELX} and {@link #PANELY}.
     * If 6-player board or Large/Sea Board, the minimum size is larger.
     *<P>
     * Left/top margins for {@link #isLargeBoard} are 0 for x, {@link #halfdeltaY} for y.
     */
    public static final int
        PANELX = SOCBoard.WIDTH_VISUAL_ORIGINAL * halfdeltaX + PANELPAD_CBOARD4_WIDTH,  // 379
        PANELY = SOCBoard.HEIGHT_VISUAL_ORIGINAL * halfdeltaY + HEXY_OFF_SLOPE_HEIGHT + PANELPAD_CBOARD4_HEIGHT;  // 340

    /**
     * Diameter and font size (unscaled internal-pixels) for dice number circles on hexes.
     * @since 1.1.08
     */
    private static final int DICE_NUMBER_CIRCLE_DIAMETER = 20, DICE_NUMBER_FONTPOINTS = 13;

    /**
     * Dice number circle background colors on hexes. <PRE>
     * Index:  Dice:  Color:
     *   0     2, 12  yellow
     *   1     3, 11
     *   2     4, 10  orange
     *   3     5,  9
     *   4     6,  8  red </PRE>
     *
     * @since 1.1.08
     */
    private static final Color[] DICE_NUMBER_CIRCLE_COLORS =
        { Color.YELLOW,
          new Color(255, 189, 0),
          new Color(255, 125, 0),
          new Color(255,  84, 0),
          Color.RED
        };

    /**
     * Minimum required unscaled width and height, as determined by options and {@link #isRotated}.
     * Set in constructor based on board's {@link SOCBoardLarge#getBoardWidth()} and height
     * plus any positive Visual Shift margin ({@link #panelShiftBX}, {@link #panelShiftBY}),
     * or {@link #PANELX} and {@link #PANELY}.
     * Used by {@link #getMinimumSize()}.
     * @since 1.1.08
     * @see #panelMinBW
     * @see #unscaledBoardW
     * @see #rescaleBoard(int, int)
     */
    private Dimension minSize;

    /**
     * Ensure that super.setSize is called at least once.
     * @since 1.1.08
     */
    private boolean hasCalledSetSize;

    /**
     * The board is configured for classic 6-player layout (and is {@link #isRotated});
     * set in constructor by checking {@link SOCBoard#getBoardEncodingFormat()}
     * &lt;= {@link SOCBoard#BOARD_ENCODING_6PLAYER} and {@link SOCGame#maxPlayers} &gt; 4.
     *<P>
     * The entire coordinate system is land, except the rightmost hexes are unused
     * (7D-DD-D7 row).
     * The 6-player mode uses {@link #hexX_6pl} instead of {@link #hexX_st} for coordinates.
     *<P>
     * When {@link #isLargeBoard}, this field is false even if the game has 5 or 6 players.
     * The <tt>is6player</tt> flag is about the hex layout on the board, not the number of players.
     * @see #inactiveHexNums
     * @see #isLargeBoard
     * @since 1.1.08
     */
    protected boolean is6player;

    /**
     * The board is configured Large-Board Format (up to 127x127, oriented like classic 4-player);
     * set in constructor by checking {@link SOCBoard#getBoardEncodingFormat()}.
     * The coordinate system is an arbitrary land/water mixture.
     *<P>
     * When <tt>isLargeBoard</tt>, {@link #isRotated()} is false even if the game has 5 or 6 players.
     *
     * @see #is6player
     * @see #portHexCoords
     * @since 2.0.00
     */
    protected final boolean isLargeBoard;

    /**
     * The board is visually rotated 90 degrees clockwise (classic 6-player: game opt PL > 4)
     * compared to its internal pixel coordinates. "Top" in board coords is "at right" in screen coords.
     *<P>
     * Use this for rotation:
     *<UL>
     * <LI> From internal to screen (cw):  R(x, y) = ({@link #panelMinBH} - y, x)
     * <LI> From screen to internal (ccw): R(x, y) = (y, {@link #panelMinBW} - x)
     *</UL>
     * When the board is also {@link #isScaled scaled}, go in this order:
     *<UL>
     * <LI> Rotate clockwise, scale up, then add ({@link #panelMarginX}, {@link #panelMarginY}).
     * <LI> Subtract ({@link #panelMarginX}, {@link #panelMarginY}), scale down, then rotate counterclockwise.
     *</UL>
     * When calculating position at which to draw an image or polygon,
     * remember that rotation changes which corner is considered (0,0),
     * and the image is offset from that corner.  (For example, {@link #drawHex(Graphics, int)}
     * subtracts HEXHEIGHT from x, after rotation but before scaling.)
     *<P>
     * When {@link #isLargeBoard}, <tt>isRotated</tt> is false even if the game has 5 or 6 players.
     *
     * @see #isScaledOrRotated
     * @since 1.1.08
     */
    protected boolean isRotated;

    /**
     * Convenience flag - The board is {@link #isRotated rotated} and/or {@link #isScaled scaled up}.
     * Check both of those flags when transforming between screen coordinates and internal pixel coordinates.
     * See {@link #isRotated} for order of rotation/scaling/margin translation.
     * @since 1.1.08
     */
    protected boolean isScaledOrRotated;

    /**
     * actual size on-screen, not internal-pixels size
     * ({@link #panelMinBW}, {@link #panelMinBH}).
     * Includes any positive {@link #panelMarginX}/{@link #panelMarginY}
     * from {@link #panelShiftBX}, {@link #panelShiftBY}, and {@link SOCPlayerInterface#layoutVS}.
     *<P>
     * See {@link #scaledBoardW} for board width within {@code scaledPanelW}, containing board hexes from game data
     * but not including margins. See {@link #unscaledBoardW} for unscaled (internal pixel) board width.
     *<P>
     * Before v2.0.00 these fields were {@code scaledPanelX, scaledPanelY}.
     * @since 1.1.00
     */
    private int scaledPanelW, scaledPanelH;

    /**
     * The width in pixels within {@link #scaledPanelW} containing the board hexes from game data.
     * If {@code scaledPanelW > scaledBoardW}, that margin will be filled by water hexes.
     *<P>
     * Used for {@link #scaleToActual(int)} and {@link #scaleFromActual(int)}:
     * Scaling ratio is {@code scaledBoardW} / {@link #unscaledBoardW}.
     *
     * @since 2.0.00
     */
    private int scaledBoardW;

    /**
     * <tt>panelMinBW</tt> and <tt>panelMinBH</tt> are the minimum width and height,
     * in board-internal pixel coordinates (not rotated or scaled). Based on board's
     * {@link SOCBoard#getBoardWidth()} and {@link SOCBoard#getBoardHeight()},
     * plus any top or left margin from the Visual Shift layout part "VS"
     * passed into our constructor: {@link #panelShiftBX}, {@link #panelShiftBY}.
     *<P>
     * Differs from static {@link #PANELX}, {@link #PANELY} for {@link #is6player 6-player board}
     * and the {@link #isLargeBoard large board}.
     *<P>
     * Differs from {@link #minSize} because minSize takes {@link #isRotated} into account.
     *<P>
     * Rescaling formulas use {@link #scaledBoardW} and {@link #unscaledBoardW} instead of these fields,
     * to avoid distortion from rotation or board size aspect ratio changes.
     * @since 1.1.08
     */
    protected int panelMinBW, panelMinBH;

    /**
     * Margin size, in board-internal pixels (not rotated or scaled),
     * for the Visual Shift layout part ("VS") if any passed into our constructor.
     * For actual-pixels size see {@link #panelMarginX}, {@link #panelMarginY}.
     *<P>
     * For more info on "VS" see the "Added Layout Parts" section of
     * {@link SOCBoardLarge#getAddedLayoutPart(String) BL.getAddedLayoutPart("VS")}'s javadoc.
     * @since 2.0.00
     */
    protected int panelShiftBX, panelShiftBY;

    /**
     * Scaled (actual pixels) panel x-margin on left and y-margin on top, to center board visually
     * if board's unscaled width is less than {@link #panelMinBW}. Includes any Visual Shift
     * (Added Layout Part "VS") from {@link #panelShiftBX}, {@link #panelShiftBY}.
     *<P>
     * Methods like {@link #drawBoard(Graphics)} and {@link #drawBoardEmpty(Graphics)} will
     * translate by {@code panelMarginX} before calling playing-piece methods like
     * {@link #drawSettlement(Graphics, int, int, HilightStyle, boolean)}, so those
     * pieces' methods can ignore the {@code panelMarginX} value.
     *<P>
     * Updated in {@link #rescaleBoard(int, int)}.
     * Won't be negative if {@link #isRotated}, which is used only with classic 6-player board.
     * @since 2.0.00
     */
    protected int panelMarginX, panelMarginY;

    /**
     * Unscaled (internal pixel, but rotated if needed) panel width for
     * {@link #scaleToActual(int)} and {@link #scaleFromActual(int)}
     * based on board size; does not include margins like {@link #scaledPanelW} does.
     * See {@link #scaledBoardW} or {@link #scaledPanelW} for scaled (actual screen pixel) width.
     * Unlike {@link #panelMinBW}, is same axis as {@code scaledPanelW} when {@link #isRotated}.
     * @see #isScaled
     * @see #minSize
     * @since 2.0.00
     */
    private int unscaledBoardW;

    /**
     * If true, some images in {@link #hexes}[] are a larger or nonuniform size and so
     * their images must always be rescaled for {@link #drawBoardEmpty(Graphics)},
     * even when boardpanel dimensions are standard size (! {@link #isScaled}).
     * {@link #scaledHexes} contains those images scaled for the current panel size.
     *<P>
     * Is set in constructor from {@link #hexesMustAlwaysScale}, or {@link #rotatHexesMustAlwaysScale}
     * if {@link #isRotated}. See those static fields for more info.
     *<P>
     * This flag refers to the static hex image sizes, not the overall panel size/geometry
     * tracked by {@link #isScaled}. It may be true even when {@code isScaled} is false,
     * and doesn't change when the panel is resized.
     *
     * @since 2.0.00
     */
    private boolean isHexesAlwaysScaled;

    /**
     * The board is currently scaled up, larger than
     * {@link #panelMinBW} x {@link #panelMinBH} pixels.
     * Use {@link #scaleToActual(int)}, {@link #scaleFromActual(int)},
     * etc to convert between internal and actual screen pixel coordinates.
     *<P>
     * When the board is also {@link #isRotated rotated}, go in this order:
     * Rotate clockwise, then scale up; Scale down, then rotate counterclockwise.
     *<P>
     * When this flag is set true, also sets {@link #scaledAt}. A copy of the static board graphic images
     * will be rescaled to match the scaled-up size and stored in {@link #scaledHexes},
     * {@link #scaledPorts}, etc.
     *
     * @see #isScaledOrRotated
     * @see #scaledAt
     * @see #rescaleBoard(int, int)
     * @see #isHexesAlwaysScaled
     * @since 1.1.00
     */
    protected boolean isScaled;

    /**
     * Time of last request to resize and repaint, as returned by {@link System#currentTimeMillis()}.
     * Used with {@link #scaledMissedImage}.
     * @see #drawnEmptyAt
     * @since 1.1.00
     */
    protected long scaledAt;

    /**
     * Flag used while drawing a scaled board ({@link #isScaled}
     * or {@link #isHexesAlwaysScaled}). If board size
     * was recently changed, could be waiting for an image to resize.
     * If it still hasn't appeared after 7 seconds, we'll give
     * up and create a new one.  (This can happen due to AWT bugs.)
     * Set in {@link #drawHex(Graphics, int)}, checked in {@link #drawBoard(Graphics)}.
     * @see #scaledHexFail
     * @see #scaledAt
     * @see #drawnEmptyAt
     * @since 1.1.00
     */
    protected boolean scaledMissedImage;

    /**
     * Time of start of board repaint, as returned by {@link System#currentTimeMillis()}.
     * Used in {@link #drawBoardEmpty(Graphics)} with {@link #scaledMissedImage}.
     * @see #scaledAt
     * @since 1.1.08
     */
    private long drawnEmptyAt;

    /**
     * Debugging flag where board item tooltip includes the item's coordinates.
     * When set, the coordinates become part of the hoverText string.
     * Set or cleared with {@link #setDebugShowCoordsFlag(boolean)}, from
     * SOCPlayerInterface debug command {@code =*= showcoords} or {@code =*= hidecoords}.
     * @see BoardToolTip#setHoverText(String, int)
     * @see #debugShowPotentials
     * @since 2.0.00
     */
    private boolean debugShowCoordsTooltip = false;

    /**
     * For debugging, flags to show board and player potential/legal coordinate sets for
     * client {@link #player}. Sets flagged as shown are drawn in {@link #drawBoardEmpty(Graphics)}.
     * Currently implemented only for the sea board layout ({@link SOCBoardLarge}).
     *<P>
     * Stored in same order as piece types:
     *<UL>
     *<LI> 0: Legal roads - yellow parallel lines
     *<LI> 1: Legal settlements - yellow squares
     *<LI> 2: Board boundaries - yellow rectangle (Legal cities has no set)
     *<LI> 3: Legal ships - yellow diamonds
     *<LI> 4: Potential roads - green parallel lines
     *<LI> 5: Potential settlements - green squares
     *<LI> 6: Potential cities - green larger squares
     *<LI> 7: Potential ships - green diamonds
     *<LI> 8: Land hexes - red round rects
     *<LI> 9: Nodes on land - red round rects
     *</UL>
     *<P>
     * Changed via {@link #setDebugShowPotentialsFlag(int, boolean, boolean)} with
     * SOCPlayerInterface debug command {@code =*= show: n} or {@code =*= hide: n},
     * where {@code n} is an index shown above or {@code all}.
     *<P>
     * If {@link #player} is null, shows for player 0.
     *<P>
     * Has package-level visibility, for use by {@link SOCPlayerInterface#updateAtPutPiece(int, int, int, boolean, int)}.
     * @see #debugShowCoordsTooltip
     * @since 2.0.00
     */
    boolean[] debugShowPotentials;

    /**
     * Font of dice-number circles appearing on hexes, dice numbers on cloth villages,
     * and strength on fortresses.
     * @since 1.1.08
     */
    private Font diceNumberCircleFont;

    /**
     * FontMetrics of {@link #diceNumberCircleFont}.
     * Used in hex and village dice numbers.
     * @since 1.1.08
     */
    private FontMetrics diceNumberCircleFM;

    /**
     * Translate hex ID to hex number to get coords.
     * Invalid (non-hex coordinate) IDs are 0.
     * Null when {@link #isLargeBoard}.
     */
    private int[] hexIDtoNum;

    /**
     * Hex numbers which aren't drawn, or null.
     * For {@link #is6player 6-player board},
     * the rightmost line of hexes (7D-DD-D7) are skipped.
     * Indicate this to {@link #drawBoard(Graphics)}.
     * @since 1.1.08
     */
    private boolean[] inactiveHexNums;

    /**
     * On the Large Board, the sea hex coordinates which have a port graphic
     * drawn on them. Used for giving the black pirate ship a high-contrast border
     * when placed at such a hex, otherwise it disappears into some port types'
     * dark or busy graphics. Necessary because SOCBoardLarge has ports' edge
     * coordinates but not these calculated hex coordinates.
     *<P>
     * Null unless {@link #isLargeBoard}.
     * @see #drawPorts_LargeBoard(Graphics)
     * @since 2.0.00
     */
    private Set<Integer> portHexCoords;

    /**
     * hex coordinates for drawing pieces on the board.
     * Upper-left corner of each hex, left-to-right in each row.
     * Points to {@link #hexX_st} or {@link #hexX_6pl},
     * and {@link #hexY_st} or {@link #hexY_6pl},
     * depending on {@link #is6player} flag.
     *<P>
     * Null when {@link #isLargeBoard}, because of its simpler
     * coordinate encoding.  In that case, calculate (x,y) with:
     *<UL>
     *<LI> y = halfdeltaY * (r+1);
     *<LI> x = halfdeltaX * c;
     *</UL>
     *
     * @since 1.1.08
     */
    private int[] hexX, hexY;

    /**
     * Hex images - shared unscaled original-resolution from {@link #IMAGEDIR}'s GIF files.
     * Image references from here are copied to {@link #scaledHexes},
     * then image objects are copied and scaled up if {@link #isScaled}.
     * Also contains {@code miscPort.gif} for drawing 3:1 ports' base image.
     *<P>
     * {@link #hexesMustAlwaysScale} was set true by {@code loadHexesAndImages}
     * if any of these are a larger or nonuniform size.
     *<P>
     * For indexes, see {@link #loadHexesAndImages(BufferedImage[], String, Class, boolean)}.
     *<P>
     * {@link #scaledPorts} stores the 6 per-facing port overlays from {@link #renderPortImages()}.
     *
     * @see #scaledHexes
     * @see #rotatHexes
     * @see #hexesGraphicsSetIndex
     */
    private static BufferedImage[] hexes;

    /**
     * If true, some images in {@link #hexes} are a larger or nonuniform size
     * (not all {@link #HEXWIDTH} x {@link #HEXHEIGHT} pixels) and so
     * their images must always be rescaled for {@link #drawBoardEmpty(Graphics)},
     * even when boardpanel dimensions are standard size (! {@link #isScaled}):
     * If ! {@link #isRotated}, a boardpanel instance's {@link #scaledHexes}
     * contains those images scaled for the current panel size.
     *<P>
     * Is used to set {@link #isHexesAlwaysScaled} in constructor.
     * Is set only by {@link #loadImages(Component, boolean)} called earlier in constructor.
     *
     * @see #rotatHexesMustAlwaysScale
     * @since 2.0.00
     */
    private static boolean hexesMustAlwaysScale;

    /**
     * From user pref {@link SOCPlayerClient#PREF_HEX_GRAPHICS_SET},
     * the index within {@link #HEX_GRAPHICS_SET_SUBDIRS} from which
     * hex graphics were loaded by {@link #loadImages(Component, boolean)}
     * into {@link #hexes} and {@link #rotatHexes}.
     * @see #scaledHexesGraphicsSetIndex
     * @since 2.0.00
     */
    private static int hexesGraphicsSetIndex;

    /**
     * Hex images - rotated board; from <tt><i>{@link #IMAGEDIR}</i>/rotat</tt>'s GIF files.
     * Images from here are copied to {@link #scaledHexes},
     * then image objects are copied and scaled up if {@link #isScaled}.
     *<P>
     * {@link #rotatHexesMustAlwaysScale} was set true by {@code loadHexesAndImages}
     * if any of these are a larger or nonuniform size.
     *<P>
     * For indexes, see {@link #loadHexesAndImages(BufferedImage[], String, Class, boolean)}.
     *
     * @see #hexes
     * @see #hexesGraphicsSetIndex
     * @since 1.1.08
     */
    private static BufferedImage[] rotatHexes;

    /**
     * If true, some images in {@link #rotatHexes} are a larger or nonuniform size
     * (not all {@link #HEXHEIGHT} x {@link #HEXWIDTH} pixels) and so
     * their images must always be rescaled for {@link #drawBoardEmpty(Graphics)},
     * even when boardpanel dimensions are standard size (! {@link #isScaled}):
     * If {@link #isRotated}, a boardpanel instance's {@link #scaledHexes}
     * contains those images scaled for the current panel size.
     *<P>
     * Is used to set {@link #isHexesAlwaysScaled} in constructor.
     * Is set only by {@link #loadImages(Component, boolean)} called earlier in constructor.
     *
     * @see #hexesMustAlwaysScale
     * @since 2.0.00
     */
    private static boolean rotatHexesMustAlwaysScale;

    /**
     * Hex images - private scaled copy, if {@link #isScaled} or {@link #isHexesAlwaysScaled}.
     * Otherwise points to static copies: Either {@link #hexes} or {@link #rotatHexes}.
     *<P>
     * For port overlay images, see {@link #scaledPorts}.
     *
     * @see #scaledHexFail
     * @since 1.1.00
     */
    private BufferedImage[] scaledHexes;

    /**
     * Port images - private copy, rotated and/or scaled if necessary.
     * Contains the 6 per-facing port overlays built in {@link #renderPortImages()}.
     * {@code miscPort.gif} is in {@link #hexes} along with the land hex images used for 2:1 ports.
     *<P>
     * For the rest of the hex images, see {@link #scaledHexes}.
     *
     * @see #scaledPortFail
     * @since 1.1.00
     */
    private BufferedImage[] scaledPorts;

    /**
     * When {@link #scaledHexes} was created for this instance, the value of {@link #hexesGraphicsSetIndex}.
     * See that field for more details.
     *<P>
     * Checked at {@link #flushBoardLayoutAndRepaint()} in case
     * {@link #reloadBoardGraphics(Component)} has recently been called.
     * @since 2.0.00
     */
    private int scaledHexesGraphicsSetIndex;

    /**
     * Hex/port images - Per-image flag to check if rescaling failed, if {@link #isScaled}
     * or {@link #isHexesAlwaysScaled}.
     *
     * @see #scaledHexes
     * @see #drawHex(Graphics, int)
     * @since 1.1.00
     */
    private boolean[] scaledHexFail, scaledPortFail;

    /**
     * Arrow dice number bounding-box size in pixels; 24 x 24 square fits in the arrow.
     * @see #drawArrow(Graphics, int, int)
     * @since 1.1.00
     */
    private static final int DICE_SZ = 24;

    /**
     * Coordinate arrays for drawing the playing pieces.
     * Local copy if isScaled, otherwise points to static arrays.
     * @since 1.1.00
     */
    private int[] scaledVertRoadX, scaledVertRoadY;

    /**
     * scaled road shape looks like "/"
     * @since 1.1.00
     */
    private int[] scaledUpRoadX, scaledUpRoadY;

    /**
     * scaled road shape looks like "\"
     * @since 1.1.00
     */
    private int[] scaledDownRoadX, scaledDownRoadY;

    /**
     * scaled settlement
     * @since 1.1.00
     */
    private int[] scaledSettlementX, scaledSettlementY;

    /**
     * scaled city
     * @since 1.1.00
     */
    private int[] scaledCityX, scaledCityY;

    /**
     * scaled ship
     * @since 2.0.00
     */
    private int[] scaledShipX, scaledShipY;

    /**
     * scaled fortress (scenario _SC_PIRI)
     * @since 2.0.00
     */
    private int[] scaledFortressX, scaledFortressY;

    /**
     * scaled diamond marker (village in scenario _SC_CLVI, etc).
     * See {@link #diamondX} for details.
     *<P>
     * Before v2.3.00 these fields were called scaledVillageX, scaledVillageY.
     * @since 2.0.00
     */
    private int[] scaledDiamondX, scaledDiamondY;

    /**
     * scaled warship (scenario _SC_PIRI)
     * @since 2.0.00
     */
    private int[] scaledWarshipX, scaledWarshipY;

    /**
     * scaled robber
     * @since 1.1.00
     */
    private int[] scaledRobberX, scaledRobberY;

    // The pirate ship uses scaledShipX, scaledShipY like any other ship.

    /**
     * For port hexes, the triangular arrowheads towards port settlement nodes.
     * @see #portArrowsX
     * @see #rescaleCoordinateArrays()
     * @since 1.1.20
     */
    private int[][] scaledPortArrowsX, scaledPortArrowsY;

    /**
     * Current-player arrow, left-pointing and right-pointing.
     * @see #rescaleCoordinateArrays()
     * @since 1.1.00
     */
    private int[] scaledArrowXL, scaledArrowXR, scaledArrowY;

    /**
     * Font for dice number in arrow. Is set in and cached for {@link #drawArrow(Graphics, int, int)}
     * along with {@link #arrowDiceHeight}.
     * @since 2.0.00
     */
    private Font arrowDiceFont;

    /**
     * Pixel height of text digits in the current {@link #arrowDiceFont}.
     * Usually smaller than {@link FontMetrics#getAscent()}.
     * Is set in and cached for {@link #drawArrow(Graphics, int, int)}.
     * @since 2.0.00
     */
    private int arrowDiceHeight;

    /**
     * Hex polygon's corner coordinates, clockwise from top-center,
     * as located in hex graphics like waterHex.gif:
     * (27,0) (54,16) (54,46) (27,62) (0,46) (0,16).
     *  If rotated 90deg clockwise, clockwise from center-right, would be:
     * (62,27) (46,54) (16,54) (0,27) (16,0) (46,0);
     *  swap x and y from these arrays.
     *<P>
     * Last element repeats first one so that {@link Graphics#drawPolyline(int[], int[], int)}
     * will close the shape by drawing all 6 sides.
     * @see #hexCornersY
     * @since 1.1.07
     */
    private static final int[] hexCornersX =
    {
        27, 54, 54, 27, 0, 0, 27
    };

    /** hex polygon's corner coordinates, clockwise from top-center.
     * @see #hexCornersX
     * @see #HEXY_OFF_SLOPE_HEIGHT
     * @since 1.1.07
     */
    private static final int[] hexCornersY =
    {
        0, 16, 46, 62, 46, 16, 0
    };

    /**
     * hex polygon corner coordinates, as scaled to current board size.
     * @see #hexCornersX
     * @see #rescaleCoordinateArrays()
     * @since 1.1.07
     */
    private int[] scaledHexCornersX, scaledHexCornersY;

    /**
     * Previous pointer coordinates for interface; the mouse was at this location when
     * {@link #hilight} was last determined in {@link #mouseMoved(MouseEvent)}.
     */
    private int ptrOldX, ptrOldY;

    /**
     * (tooltip) Hover text for info on pieces/parts of the board. Its mode uses boardpanel mode constants.
     * Also contains "hovering" road/settlement/city near mouse pointer.
     * @see #hilight
     * @since 1.1.00
     */
    private BoardToolTip hoverTip;

    /**
     * Context menu for build/cancel-build
     * @see #popupMenuSystime
     * @see #buildReqTimerTask
     * @see #doBoardMenuPopup(int, int)
     * @since 1.1.00
     */
    private BoardPopupMenu popupMenu;

    /**
     * Tracks last menu-popup time for {@link #popupMenu}. Avoids misinterpretation
     * of popup-click with placement-click during initial placement: On Windows,
     * popup-click must be caught in mouseReleased, but mousePressed is called
     * immediately afterwards.
     * @since 1.1.00
     */
    private long popupMenuSystime;

    /**
     * For right-click build {@link #popupMenu}; used for fallback part of
     * client-server-client communication of a build request. Created whenever
     * right-click build request is sent to server.
     *<P>
     * Is fallback for this usual sequence of calls:
     *<OL>
     * <LI> {@link SOCBoardPanel#popupExpectingBuildRequest()}
     * <LI> {@link SOCPlayerInterface#updateAtGameState()}
     * <LI> {@link SOCBoardPanel#popupFireBuildingRequest()}
     *</OL>
     * @since 1.1.00
     */
    protected BoardPanelSendBuildTask buildReqTimerTask;

    /**
     * Text to be displayed as 2 lines superimposed over center
     * of the board graphic (during game setup).
     * Either supertext2, or both, can be null to display nothing.
     * @see #setSuperimposedText(String, String)
     * @since 1.1.07
     */
    private String superText1, superText2;

    /**
     * Width, height of {@link #superText1} and {@link #superText2} if known, or 0.
     * Calculated in {@link #drawSuperText(Graphics)}.
     * @since 1.1.07
     */
    private int superText1_w, superText_h, superText_des, superText2_w,
        superTextBox_x, superTextBox_y, superTextBox_w, superTextBox_h;

    /**
     * Text to be displayed as 1 line superimposed over the top-center
     * of the board graphic (during game play).
     * Can be null to display nothing.
     * @see #setSuperimposedTopText(String)
     * @since 1.1.08
     */
    private String superTextTop;

    /**
     * Width, height of {@link #superTextTop} if known, or 0.
     * Calculated in {@link #drawSuperTextTop(Graphics)}.
     * Y-position of top of this textbox is {@link #SUPERTEXT_INSET}.
     * @since 1.1.08
     */
    private int superTextTop_w, superTextTop_h, superTextTopBox_x, superTextTopBox_w, superTextTopBox_h;

    /**
     * The current player's most recent piece placement this turn, or {@code null}.
     * See {@link #setLatestPiecePlacement(SOCPlayingPiece)} for details.
     * @since 2.7.00
     */
    private SOCPlayingPiece latestPiecePlacement;

    /**
     * Edge or node being pointed to. When placing a road/settlement/city,
     * used for coordinate of "ghost" piece under the mouse pointer.
     * 0 when nothing is hilighted. -1 for a road at edge 0x00.
     *<P>
     * During {@link #PLACE_INIT_ROAD}, this can be either a road or a ship.
     * Along coastal edges it could be either one.
     * Default to road:
     * Check {@link #player}.{@link SOCPlayer#isPotentialRoad(int) isPotentialRoad(hilight)}
     * first, then {@link SOCPlayer#isPotentialShip(int) .isPotentialShip}.
     * Player can right-click to build an initial ship along a coastal edge.
     *<P>
     * Hilight is drawn in {@link #drawBoard(Graphics)}. Value updated in {@link #mouseMoved(MouseEvent)}.
     * Cleared in {@link #clearModeAndHilight(int)}.
     *
     * @see #hoverTip
     */
    private int hilight;

    /**
     * If hilighting an edge, is the {@link #hilight} a ship and not a road?
     * @since 2.0.00
     */
    private boolean hilightIsShip;

    /**
     * During {@link #MOVE_SHIP} mode, the edge coordinate
     * from which we're moving the ship.  0 otherwise.
     * The hovering "move-to" location under the mouse pointer is {@link #hilight}
     * and then (at left-click to select destination, or right-click to show the menu)
     * is {@link #moveShip_toEdge}.
     * @see #moveShip_isWarship
     * @since 2.0.00
     */
    private int moveShip_fromEdge;

    /**
     * During {@link #MOVE_SHIP} mode, the edge coordinate to which we're
     * moving the ship, 0 otherwise.  While choosing a location to move to,
     * the hovering ship under the mouse pointer is {@link #hilight}, but
     * when the menu appears (on Windows at least) hilight becomes 0.
     * @see #tryMoveShipToEdge()
     * @see #moveShip_fromEdge
     * @since 2.0.00
     */
    private int moveShip_toEdge;

    /**
     * During {@link #MOVE_SHIP} mode, true if the ship being moved
     * is a warship in scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.
     * @see #moveShip_fromEdge
     * @since 2.0.00
     */
    private boolean moveShip_isWarship;

    /**
     * Map grid sectors (from unscaled on-screen coordinates) to hex edges.
     * Invalid edges are 0.
     * The grid has 15 columns (each being 1/2 of a hex wide) and 23 rows
     * (each 1/3 hex tall).
     * This maps graphical coordinates to the board coordinate system.
     *<P>
     * The edge coordinate number at grid (x,y) is in <tt>edgeMap</tt>[x + (y * 15)].
     * If <tt>edgeMap</tt>[x,y] == 0, it's not a valid edge coordinate.
     *<P>
     * On the 4-player board, row 0 is the top of the topmost row of water
     * hexes.  Here are the grid (x,y) values for the edges of the top-left
     * <b>land</b> hex on the 4-player board, and to the right and down; its
     * top is in y=3 of this grid:<PRE>
     *             ^               ^
     *       (4,3)   (5,3)   (6,3)   (7,3)
     *     |               |
     *   (4,4)           (6,4)
     *   (4,5)           (6,5)
     *     |               |
     *(3,6)  (4,6)   (5,6)   (6,6)   (7,6)
     *             v               v
     *             |               |
     *           (5,7)           (7,7)
     *           (5,8)           (7,8) </PRE>
     *<P>
     * The 6-player board is similar to the 4-player layout, but its
     * top-left land hex edges start at (3,0) instead of (4,3), and it is
     * visually rotated 90 degrees on screen, but not rotated in this
     * coordinate system, versus the 4-player board.
     *<P>
     * <b>Note:</b> For the 6-player board, edge 0x00 is a valid edge that
     * can be built on.  It is marked here as -1, since a value of 0 marks an
     * invalid edge in this map.
     *<P>
     * In {@link #is6player 6-player mode}, there is an extra ring of water/port hexes
     * on the outside, which isn't within the coordinate system.  So this grid is
     * shifted +1 column, +3 rows.
     *<P>
     * Not used when {@link #isLargeBoard}.
     *
     * @see #findEdge(int, int, boolean)
     * @see #initEdgeMapAux(int, int, int, int, int)
     */
    private int[] edgeMap;

    /**
     * Map grid sectors (from unscaled on-screen coordinates) to hex nodes.
     * Invalid nodes are 0.
     * The grid has 15 columns and 23 rows.
     * This maps graphical coordinates to the board coordinate system.
     * Each row of hexes touches 3 columns and 5 rows here. For instance,
     * hex 0x35 has its top-center point (node) in row y=6, and its bottom-center
     * point in row y=10, of this grid.  Its left edge is column x=3, and right is column x=5.
     *<P>
     * The node number at grid (x,y) is nodeMap[x + (y * 15)].
     *<P>
     * In {@link #is6player 6-player mode}, there is an extra ring of water/port hexes
     * on the outside, which isn't within the coordinate system.  So this grid appears
     * shifted +1 column, +3 rows on screen, to account for the outside ring.
     *<P>
     * In 4-player mode, here are the nodeMap coordinates (x,y) for the left end of the topmost
     * row of water hexes:
     *<PRE>
     *       (4,0)       (6,0)
     *     /       \   /      \
     * (3,1)       (5,1)
     * (3,2)       (5,2)
     * (3,3)       (5,3)
     * /   \       /   \      /
     *       (3,4)       (6,4)
     *</PRE>
     * Not used when {@link #isLargeBoard}.
     *
     * @see #findNode(int, int)
     * @see #initNodeMapAux(int, int, int, int, int)
     */
    private int[] nodeMap;

    /**
     * Map grid sectors (from unscaled on-screen coordinates) to hexes.
     * Invalid hexes are 0.
     * The grid has 15 columns (each being 1/2 of a hex wide) and 23 rows
     * (each 1/3 hex tall).
     * This maps graphical coordinates to the board coordinate system.
     * Not used when {@link #isLargeBoard}.
     * @see #findHex(int, int)
     */
    private int[] hexMap;

    /**
     * The game which this board is a part of
     */
    private SOCGame game;

    /**
     * The board in the game
     */
    private SOCBoard board;

    /**
     * The player that is using this interface;
     * initially {@code null} when first joining or observing a game.
     * Set by {@link #setPlayer(SOCPlayer)}.
     * @see #playerNumber
     */
    private SOCPlayer player;

    /**
     * player number of our {@link #player} if in a game, or -1
     * when first joining or observing a game. Set by {@link #setPlayer(SOCPlayer)}.
     * @since 1.1.00
     */
    private int playerNumber;

    /**
     * When in "consider" mode, this is the robot player we'll send the command to;
     * see {@link #setOtherPlayer(SOCPlayer)} for details.
     */
    private SOCPlayer otherPlayer;

    /**
     * offscreen buffer of everything (board, pieces, hovering pieces, tooltip), to prevent flicker.
     * @see #emptyBoardBuffer
     */
    private VolatileImage buffer;

    /**
     * offscreen buffer of board without any pieces placed, to prevent flicker.
     * If the board layout changes (at start of game, for example),
     * call {@link #flushBoardLayoutAndRepaint()} to clear the buffered copy.
     * @see #buffer
     * @since 1.1.08
     */
    private VolatileImage emptyBoardBuffer;

    /**
     * Modes of interaction; for correlation to game state, see {@link #updateMode()}.
     * For tooltip's mode, see {@link SOCBoardPanel.BoardToolTip#hoverMode}.
     */
    private int mode;

    /**
     * Number of times that hint message has been shown which
     * prompts the player to right-click (not left- or double-click)
     * and show the build menu in order to build.
     * Show at most twice to avoid annoying new users.
     * @see #mouseClicked(MouseEvent)
     * @since 1.1.20
     */
    private int hintShownCount_RightClickToBuild;

    /**
     * During initial placement, the node coordinate of
     * the most recent settlement placed by the player.
     *<P>
     * Before v2.0.00 this field was {@code initstlmt}.
     */
    private int initSettlementNode;

    /**
     * the player interface that this board is a part of
     */
    private final SOCPlayerInterface playerInterface;

    /** Cached colors, for use for robber's "ghost"
     *  (previous position) when moving the robber.
     *  Values are determined the first time the
     *  robber is ghosted on that type of tile.
     *
     *  Index ranges from 0 to {@link SOCBoard#max_robber_hextype}.
     *
     *  @see soc.client.ColorSquare
     *  @see #drawRobber(Graphics, int, boolean, boolean)
     *  @since 1.1.00
     */
    protected Color[] robberGhostFill, robberGhostOutline;

    /**
     * create a new board panel in a game interface.
     * The minimum size needed on-screen is based on the game options and {@code layoutVS}.
     * After construction, call {@link #getMinimumSize()} to read it.
     *
     * @param pi  the player interface that spawned us; not null
     * @param layoutVS  Optional board layout "visual shift and trim" (Added Layout Part "VS")
     *     to use when setting minimum size of this {@code SOCBoardPanel}, or {@code null}
     */
    public SOCBoardPanel(final SOCPlayerInterface pi, final int[] layoutVS)
    {
        super();
        setOpaque(true);

        game = pi.getGame();
        playerInterface = pi;
        player = null;
        playerNumber = -1;
        board = game.getBoard();
        isScaled = false;
        scaledMissedImage = false;
        final int bef = board.getBoardEncodingFormat();
        if (bef == SOCBoard.BOARD_ENCODING_LARGE)
        {
            is6player = false;
            isLargeBoard = true;
            isRotated = isScaledOrRotated = false;
        } else {
            is6player = (bef == SOCBoard.BOARD_ENCODING_6PLAYER)
                || (game.maxPlayers > 4);
            isLargeBoard = false;
            isRotated = isScaledOrRotated = is6player;
        }

        // Calc Board Size: scaledPanelW, scaledPanelH, panelMinBW, panelMinBH, etc.
        // If these calcs change, also update getExtraSizeFromBoard()

        if (isRotated)
        {
            // scaledPanelW, scaledPanelH are on-screen minimum size.
            // panelMinBW, panelMinBH are board-coordinates, so not rotated.
            // Thus, x <-> y between these two pairs of variables.
            scaledPanelW = PANELY + (2 * deltaY);  // wider, for is6player board's height
            scaledPanelH = PANELX + halfdeltaY;
            panelMinBW = scaledPanelH;
            panelMinBH = scaledPanelW;
        } else {
            if (isLargeBoard)
            {
                // TODO isLargeBoard: what if we need a scrollbar?
                int bh = board.getBoardHeight(), bw = board.getBoardWidth();
                if (bh < BOARDHEIGHT_VISUAL_MIN)
                    bh = BOARDHEIGHT_VISUAL_MIN;
                if (bw < BOARDWIDTH_VISUAL_MIN)
                    bw = BOARDWIDTH_VISUAL_MIN;
                scaledPanelW = halfdeltaX * bw + PANELPAD_LBOARD_RT;
                scaledPanelH = halfdeltaY * bh + PANELPAD_LBOARD_BTM + HEXY_OFF_SLOPE_HEIGHT;

                if (layoutVS != null)
                {
                    final int shiftBX, shiftBY;
                    shiftBY = (layoutVS[0] * halfdeltaY) / 2;
                    shiftBX = (layoutVS[1] * halfdeltaX) / 2;

                    if (shiftBX != 0)
                    {
                        panelMarginX = panelShiftBX = shiftBX;
                        if (shiftBX > 0)
                            scaledPanelW += shiftBX;
                    }
                    if (shiftBY != 0)
                    {
                        panelMarginY = panelShiftBY = shiftBY;
                        if (shiftBY > 0)
                            scaledPanelH += shiftBY;
                    }

                    if (layoutVS.length >= 4)
                    {
                        final int trimBX, trimBY;
                        trimBY = (layoutVS[2] * halfdeltaY) / 2;
                        trimBX = (layoutVS[3] * halfdeltaX) / 2;
                        if (trimBX > 0)
                            scaledPanelW -= trimBX;
                        if (trimBY > 0)
                            scaledPanelH -= trimBY;
                    }
                }
            } else {
                scaledPanelW = PANELX;
                scaledPanelH = PANELY;
                panelShiftBX = -halfdeltaX / 2;  // center the classic 4-player board
                panelMarginX = panelShiftBX;
            }

            panelMinBW = scaledPanelW;
            panelMinBH = scaledPanelH;
        }

        minSize = new Dimension(scaledPanelW, scaledPanelH);
        unscaledBoardW = scaledPanelW;  // also == panelMinBW (panelMinBH if rotated) at this point
        scaledBoardW = scaledPanelW;
        hasCalledSetSize = false;
        debugShowPotentials = new boolean[10];

        int i;

        // init coord holders
        ptrOldX = 0;
        ptrOldY = 0;

        hilight = 0;
        moveShip_fromEdge = 0;
        hilightIsShip = false;

        if (isLargeBoard)
        {
            // Because of the straightforward coordinate system used for isLargeBoard,
            // there's no need for these (x,y) -> board-coordinate maps.
            // Calculate (x,y) with:
            //   y = halfdeltaY * r;
            //   x = halfdeltaX * c;

            edgeMap = null;
            nodeMap = null;
            hexMap = null;
            hexIDtoNum = null;
            hexX = null;
            hexY = null;
            inactiveHexNums = null;

        } else {

            initCoordMappings();

        }  // if (isLargeBoard)

        // set mode of interaction
        mode = NONE;

        // Set up mouse listeners
        this.addMouseListener(this);
        this.addMouseMotionListener(this);

        // Cached colors to be determined later
        robberGhostFill = new Color [1 + board.max_robber_hextype];
        robberGhostOutline = new Color [1 + board.max_robber_hextype];

        // Set up hover tooltip info
        hoverTip = new BoardToolTip(this);

        // Set up popup menu
        popupMenu = new BoardPopupMenu(this);
        add (popupMenu);
        popupMenuSystime = System.currentTimeMillis();  // Set to a reasonable value

        // Overlay text
        superText1 = null;
        superText2 = null;

        // load the static images
        loadImages(this, isRotated);

        // Point to static images, unless we're later resized.
        // Now that we've called loadImages, update isHexesAlwaysScaled.
        BufferedImage[] h;
        if (isRotated)
        {
            h = rotatHexes;
            isHexesAlwaysScaled = rotatHexesMustAlwaysScale;
        } else {
            h = hexes;
            isHexesAlwaysScaled = hexesMustAlwaysScale;
        }

        scaledHexes = new BufferedImage[h.length];
        scaledPorts = new BufferedImage[6];
        for (i = h.length - 1; i >= 0; --i)
            scaledHexes[i] = h[i];
        scaledHexesGraphicsSetIndex = hexesGraphicsSetIndex;
        scaledHexFail = new boolean[h.length];
        scaledPortFail = new boolean[scaledPorts.length];

        // point to static coordinate arrays, unless we're later resized.
        // If this is the first instance, calculate arrowXR.
        rescaleCoordinateArrays();
    }

    /**
     * During the constructor, initialize the board-coordinate to screen-coordinate mappings:
     * {@link #edgeMap}, {@link #nodeMap}, {@link #hexMap},
     * {@link #hexIDtoNum}, {@link #hexX}, {@link #hexY},
     * {@link #inactiveHexNums}.
     *<P>
     * Not used when {@link #isLargeBoard}.
     * @since 2.0.00
     */
    private void initCoordMappings()
    {
        int i;
        // init edge map
        edgeMap = new int[345];
        Arrays.fill(edgeMap, 0);

        if (is6player)
        {
            // since 0x00 is a valid edge for 6player, it's
            // marked in the map as -1 (0 means invalid in the map).
            initEdgeMapAux(3, 0, 9, 3, 0x17);    // Top row: 0x17 is first land hex of this row
            initEdgeMapAux(2, 3, 10, 6, 0x15);
            initEdgeMapAux(1, 6, 11, 9, 0x13);
            initEdgeMapAux(0, 9, 12, 12, 0x11);  // Middle row: 0x11 is leftmost land hex
            initEdgeMapAux(1, 12, 11, 15, 0x31);
            initEdgeMapAux(2, 15, 10, 18, 0x51);
            initEdgeMapAux(3, 18, 9, 21, 0x71);  // Bottom row: 0x71 is first land hex of this row
        } else {
            initEdgeMapAux(4, 3, 10, 6, 0x37);    // Top row: 0x37 is first land hex of this row
            initEdgeMapAux(3, 6, 11, 9, 0x35);
            initEdgeMapAux(2, 9, 12, 12, 0x33);  // Middle row: 0x33 is leftmost land hex
            initEdgeMapAux(3, 12, 11, 15, 0x53);
            initEdgeMapAux(4, 15, 10, 18, 0x73);  // Bottom row: 0x73 is first land hex of this row
        }

        // init node map
        nodeMap = new int[345];
        Arrays.fill(nodeMap, 0);

        if (is6player)
        {
            initNodeMapAux(3,  0,  9,  4, 0x17);  // Very top row: 3 across
            initNodeMapAux(2,  3, 10,  7, 0x15);
            initNodeMapAux(1,  6, 11, 10, 0x13);
            initNodeMapAux(0,  9, 12, 13, 0x11);  // Middle row: 6 across, 0x11 is leftmost land hex
            initNodeMapAux(1, 12, 11, 16, 0x31);
            initNodeMapAux(2, 15, 10, 19, 0x51);
            initNodeMapAux(3, 18,  9, 22, 0x71);  // Very bottom row: 3 across
        } else {
            initNodeMapAux(4,  3, 10,  7, 0x37);  // Top row: 0x37 is first land hex of this row
            initNodeMapAux(3,  6, 11, 10, 0x35);
            initNodeMapAux(2,  9, 12, 13, 0x33);  // Middle row: 0x33 is leftmost land hex
            initNodeMapAux(3, 12, 11, 16, 0x53);
            initNodeMapAux(4, 15, 10, 19, 0x73);  // Bottom row: 0x73 is first land hex of this row
        }

        // init hex map
        hexMap = new int[345];
        Arrays.fill(hexMap, 0);

        if (is6player)
        {
            initHexMapAux(3, 1, 8, 2, 0x17);    // Top row: 0x17 is first land hex
            initHexMapAux(2, 4, 9, 5, 0x15);
            initHexMapAux(1, 7, 10, 8, 0x13);
            initHexMapAux(0, 10, 11, 11, 0x11);
            initHexMapAux(1, 13, 10, 14, 0x31);
            initHexMapAux(2, 16, 9, 17, 0x51);
            initHexMapAux(3, 19, 8, 20, 0x71);  // Bottom row: 0x71 is first land hex
        } else {
            initHexMapAux(4, 4, 9, 5, 0x37);    // Top row: 0x37 is first land hex
            initHexMapAux(3, 7, 10, 8, 0x35);
            initHexMapAux(2, 10, 11, 11, 0x33);
            initHexMapAux(3, 13, 10, 14, 0x53);
            initHexMapAux(4, 16, 9, 17, 0x73);  // Bottom row: 0x73 is first land hex
        }

        hexIDtoNum = new int[0xDE];
        Arrays.fill(hexIDtoNum, 0);

        initHexIDtoNumAux(0x17, 0x7D, 0);
        initHexIDtoNumAux(0x15, 0x9D, 4);
        initHexIDtoNumAux(0x13, 0xBD, 9);
        initHexIDtoNumAux(0x11, 0xDD, 15);
        initHexIDtoNumAux(0x31, 0xDB, 22);
        initHexIDtoNumAux(0x51, 0xD9, 28);
        initHexIDtoNumAux(0x71, 0xD7, 33);

        if (is6player)
        {
            if (hexX_6pl == null)
            {
                final int L = hexX_st.length;
                hexX_6pl = new int[L];
                hexY_6pl = new int[L];
                for (i = 0; i < L; ++i)
                    hexX_6pl[i] = hexX_st[i] + HEXX_OFF_6PL;
                for (i = 0; i < L; ++i)
                    hexY_6pl[i] = hexY_st[i] + HEXY_OFF_6PL;
            }
            hexX = hexX_6pl;
            hexY = hexY_6pl;

            // Hex numbers (in range 0-36) to skip: (coords 7D-DD-D7).
            inactiveHexNums = new boolean[hexX_6pl.length];
            int[] inacIdx = {3, 8, 14, 21, 27, 32, 36};
            for (i = 0; i < inacIdx.length; ++i)
                inactiveHexNums[inacIdx[i]] = true;
        } else {
            hexX = hexX_st;
            hexY = hexY_st;
            inactiveHexNums = null;
        }
    }

    /**
     * Initialize {@link #edgeMap}'s valid edges across 1 row of hexes,
     * for use by {@link #findEdge(int, int, boolean)}.
     *<P>
     * For details of {@link #edgeMap}'s layout and usage, see its javadoc.
     * For more details of the initialization algorithm used in this
     * method, see comments within
     * {@link #initNodeMapAux(int, int, int, int, int)}.
     *<P>
     * Not applicable when {@link #isLargeBoard}.
     *
     * @param x1  Leftmost x-value to init within edgeMap; the x-value
     *    of the row's leftmost vertical edge
     * @param y1  Topmost y-value to init within edgeMap; the y-value of
     *    the upper angled edges of the hex (angled "/" and "\")
     * @param x2  Rightmost x-value to init; the x-value of the
     *    row's rightmost vertical edge
     * @param y2  Bottommost y-value to init; the y-value of
     *    the lower angled edges of the hex (angled "\" and "/"),
     *    should be <tt>y1</tt> + 3.
     * @param startHex  Hex coordinate of row's first valid hex;
     *   this row's {@link #edgeMap}[x,y] values will be set to
     *   edge coordinates offset from <tt>startHex</tt>.
     */
    private final void initEdgeMapAux(int x1, int y1, int x2, int y2, int startHex)
    {
        final int hexVerticalXmod2 = x1 % 2;  // to find vertical-edge (vs middle) x-coordinates within each hex
        int x;
        int y;
        int facing = 0;
        int count = 0;
        int hexNum;
        int edgeNum = 0;

        // See initNodeMapAux for comments on this algorithm.

        for (y = y1; y <= y2; y++)  // Outer loop: each y
        {
            hexNum = startHex;

            switch (count)
            {
            case 0:
                facing = 6;
                edgeNum = hexNum - 0x10;

                break;

            case 1:
                facing = 5;
                edgeNum = hexNum - 0x11;

                break;

            case 2:
                facing = 5;
                edgeNum = hexNum - 0x11;

                break;

            case 3:
                facing = 4;
                edgeNum = hexNum - 0x01;

                break;

            default:
                System.out.println("initEdgeMap error");

                return;
            }

            if (edgeNum == 0x00)
                edgeNum = -1;  // valid edge 0x00 is stored as -1 in map

            final boolean inMiddleRowsOfHex = (y > y1) && (y < y2);

            for (x = x1; x <= x2; x++)  // Inner: each x for this y
            {
                if (inMiddleRowsOfHex)
                {
                    // center of hex isn't a valid edge
                    if ((x % 2) != hexVerticalXmod2)
                        edgeNum = 0;
                }

                edgeMap[x + (y * 15)] = edgeNum;

                switch (facing)
                {
                case 1:
                    facing = 6;
                    hexNum += 0x22;
                    edgeNum = hexNum - 0x10;

                    break;

                case 2:
                    facing = 5;
                    hexNum += 0x22;
                    edgeNum = hexNum - 0x11;

                    break;

                case 3:
                    facing = 4;
                    hexNum += 0x22;
                    edgeNum = hexNum - 0x01;

                    break;

                case 4:
                    facing = 3;
                    edgeNum = hexNum + 0x10;

                    break;

                case 5:
                    facing = 2;
                    edgeNum = hexNum + 0x11;

                    break;

                case 6:
                    facing = 1;
                    edgeNum = hexNum + 0x01;

                    break;

                default:
                    System.out.println("initEdgeMap error");

                    return;
                }
            }

            count++;
        }
    }

    private final void initHexMapAux(int x1, int y1, int x2, int y2, int startHex)
    {
        int x;
        int y;
        int hexNum;
        int count = 0;

        for (y = y1; y <= y2; y++)
        {
            hexNum = startHex;

            for (x = x1; x <= x2; x++)
            {
                hexMap[x + (y * 15)] = hexNum;

                if ((count % 2) != 0)
                {
                    hexNum += 0x22;
                }

                count++;
            }
        }
    }

    /**
     * Within {@link #nodeMap}, set the node coordinates within a rectangular section
     * from (x1,y1) to (x2,y2) covering all nodes of one horizontal row of hexes.
     *<P>
     * The grid maps graphical coordinates to the board coordinate system.
     * Each row of hexes covers 5 rows here. For instance, hex 0x35 has its top-center
     * point (node) in row 6, and its bottom-center point in row 10.
     * All 6 nodes of each hex in range will be initialized within {@link #nodeMap}.
     *<P>
     * For node coordinates, see RST dissertation figure A2. For hex coordinates, see figure A1.
     *<P>
     * {@link #initEdgeMapAux(int, int, int, int, int)} uses a similar structure.
     *<P>
     * Not applicable when {@link #isLargeBoard}.
     *
     * @param x1 Starting x-coordinate within {@link #nodeMap}'s index;
     *           should correspond to left edge of <tt>startHex</tt>
     * @param y1 Starting y-coordinate within {@link #nodeMap}'s index;
     *           should correspond to top point of <tt>startHex</tt>
     * @param x2 Ending x-coordinate; should correspond to right edge of the last hex in the
     *           row of hexes being initialized.  Each hex is 2 units wide in the grid.
     * @param y2 Ending y-coordinate; should correspond to bottom point of <tt>startHex</tt>,
     *           and thus should be y1 + 4.
     * @param startHex  Starting hex ID (0x-coordinate of first hex in this row), to use with nodeMap[x1, y1].
     */
    private final void initNodeMapAux(int x1, int y1, int x2, int y2, int startHex)
    {
        int rowState = 0;  // current state; related to row# and logic for node coords from hex coords
        int row = 0;  // 0 for first row (y==y1), 1 for second, etc.
        int hexNum;  // starts with startHex, incr by 0x22 to move across a horizontal row of board coords
                     // during rowStates 01, 12, 32, 41.
        int nodeNum = 0;  // node number

        /**
         * Brief Illustration of row, rowState, nodeNum:
         *   As seen for startHex = 0x37.  Node numbers below are in hex.
         *         x-- 4     5     6     7     8        4     5     6     7     8
         * row   y
         *  |    |     nodeNums: (0 where blank)       rowState at top of x-loop:
         *  |    |
         *  0    3     0     38          5A            00    01    00    01    00
         *                /      \    /      \    /       /      \    /      \    /
         *  1    4     27          49          6B      10    11    12    11    12
         *             |           |           |       |           |           |
         *  2    5     0           0           0       20    20    20    20    20
         *             |           |           |       |           |           |
         *  3    6     36          58          7A      30    31    32    31    32
         *           /    \      /    \      /    \       \      /    \      /    \
         *  4    7     0     47          69            40    41    40    41    40
         */

        for (int y = y1; y <= y2; y++, row++)
        {
            hexNum = startHex;

            switch (row)
            {
            case 0:
                rowState = 00;
                nodeNum = 0;

                break;

            case 1:
                rowState = 10;
                nodeNum = hexNum - 0x10;

                break;

            case 2:
                rowState = 20;
                nodeNum = 0;

                break;

            case 3:
                rowState = 30;
                nodeNum = hexNum - 0x01;

                break;

            case 4:
                rowState = 40;
                nodeNum = 0;

                break;

            default:
                System.out.println("initNodeMap error");

                return;
            }

            for (int x = x1; x <= x2; x++)
            {
                nodeMap[x + (y * 15)] = nodeNum;

                switch (rowState)
                {
                // Used in top row (row==0) //
                case 01:
                    rowState = 00;
                    hexNum += 0x22;
                    nodeNum = 0;

                    break;

                case 00:
                    rowState = 01;
                    nodeNum = hexNum + 0x01;

                    break;

                // Used in row 1 (row==1) //
                case 12:
                    rowState = 11;
                    hexNum += 0x22;
                    nodeNum = 0;

                    break;

                case 11:
                    rowState = 12;
                    nodeNum = hexNum + 0x12;

                    break;

                case 10:
                    rowState = 11;
                    nodeNum = 0;

                    break;

                // Used in middle row (row==2) //
                case 20:
                    nodeNum = 0;

                    break;

                // Used in row 3 //
                case 30:
                    rowState = 31;
                    nodeNum = 0;

                    break;

                case 32:
                    rowState = 31;
                    hexNum += 0x22;
                    nodeNum = 0;

                    break;

                case 31:
                    rowState = 32;
                    nodeNum = hexNum + 0x21;

                    break;

                // Used in bottom row (row==4) //
                case 41:
                    rowState = 40;
                    hexNum += 0x22;
                    nodeNum = 0;

                    break;

                case 40:
                    rowState = 41;
                    nodeNum = hexNum + 0x10;

                    break;

                default:
                    System.out.println("initNodeMap error");

                    return;
                }
            }  // for (x)
        }  // for (y)
    }

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
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(scaledPanelW, scaledPanelH);
    }

    /**
     * Minimum required width and height, as determined by options and {@link #isRotated()}.
     * Ignores {@link SOCPlayerInterface#displayScale}.
     *<P>
     * Minimum size is set in the constructor.
     * On the classic 4-player and 6-player boards, the size is based on {@link #PANELX} and {@link #PANELY}.
     * When {@link SOCGame#hasSeaBoard}, the size is based on {@link SOCBoard#getBoardWidth()}
     * and {@link SOCBoard#getBoardHeight() .getBoardHeight()} plus any positive optional
     * {@link SOCBoardLarge#getAddedLayoutPart(String) SOCBoardLarge.getAddedLayoutPart("VS")}.
     *
     * @return minimum size
     * @see #getExtraSizeFromBoard(boolean)
     */
    @Override
    public Dimension getMinimumSize()
    {
        return minSize;
    }

    /**
     * Get the amount of our current width and height that comes from the board being larger than
     * the standard 4-player board size. Compares {@link SOCBoard#getBoardWidth()}, {@link SOCBoard#getBoardHeight()}
     * against {@link SOCBoardPanel#BOARDWIDTH_VISUAL_MIN}, {@link SOCBoardPanel#BOARDHEIGHT_VISUAL_MIN}.
     * Adjusts for SOCBoardPanel padding constants and any space added by a positive optional
     * {@link SOCBoardLarge#getAddedLayoutPart(String) SOCBoardLarge.getAddedLayoutPart("VS")}.
     *
     * @param doScale  If true, the returned extra width & height are scaled screen pixels, not board-internal;
     *      calls {@link #scaleToActual(int)}
     * @return extra size in pixels, or (0, 0)
     * @since 2.0.00
     */
    public Dimension getExtraSizeFromBoard(final boolean doScale)
    {
        // Based on constructor's size calcs for panelMinBW, panelMinBH

        if (! isLargeBoard)
        {
            if (isRotated)
            {
                // standard 6pl
                int retW = 2 * deltaY, retH = halfdeltaY;
                if (doScale)
                {
                    retW = scaleToActual(retW);
                    retH = scaleToActual(retH);
                }
                return new Dimension(retW, retH);
            } else {
                // standard 4pl
                return new Dimension(0, 0);
            }
        }

        int bh = board.getBoardHeight(), bw = board.getBoardWidth();
        if (bh < BOARDHEIGHT_VISUAL_MIN)
            bh = BOARDHEIGHT_VISUAL_MIN;
        if (bw < BOARDWIDTH_VISUAL_MIN)
            bw = BOARDWIDTH_VISUAL_MIN;

        bh -= SOCBoard.HEIGHT_VISUAL_ORIGINAL;
        bw -= SOCBoard.WIDTH_VISUAL_ORIGINAL;

        int w = bw * halfdeltaX, h = bh * halfdeltaY;
        if (panelShiftBX > 0)
            w += panelShiftBX;
        if (panelShiftBY > 0)
            h += panelShiftBY;

        int retW = w - PANELPAD_CBOARD4_WIDTH + PANELPAD_LBOARD_RT,
            retH = h - PANELPAD_CBOARD4_HEIGHT + PANELPAD_LBOARD_BTM;
                // ignore HEXY_OFF_SLOPE_HEIGHT: it adds same amount to classic & sea board height
        if (doScale)
        {
            retW = scaleToActual(retW);
            retH = scaleToActual(retH);
        }

        return new Dimension(retW, retH);
    }

    /**
     * Set the board to a new size, rescale graphics and repaint if needed.
     *
     * @param newW  New width in pixels, no less than {@link #getMinimumSize()}.width
     * @param newH  New height in pixels, no less than {@link #getMinimumSize()}.height
     * @throws IllegalArgumentException if newW or newH is too small but not 0.
     *   During initial layout, the layoutmanager may make calls to setSize(0,0);
     *   such a call is passed to super without scaling graphics.
     *   To not throw this exception, call {@link #setSize(int, int, boolean)} instead.
     * @since 1.1.00
     */
    @Override
    public void setSize(int newW, int newH)
        throws IllegalArgumentException
    {
        setSize(newW, newH, false);
    }

    /**
     * Set the board to a new size, rescale graphics and repaint if needed.
     *
     * @param newW  New width in pixels, no less than {@link #getMinimumSize()}.width
     * @param newH  New height in pixels, no less than {@link #getMinimumSize()}.height
     * @param noException  If true, don't throw an exception, increase newW or newH instead.
     *     If false, throw if newW or newH too small; see {@link #setSize(int, int)} for details.
     * @throws IllegalArgumentException if newW or newH is too small but not 0:
     *     See {@link #setSize(int, int)} for details.
     * @since 2.0.00
     */
    public void setSize(int newW, int newH, final boolean noException)
        throws IllegalArgumentException
    {
        if ((newW == scaledPanelW) && (newH == scaledPanelH) && hasCalledSetSize)
            return;  // Already sized.

        if (noException)
        {
            if ((newW != 0) && (newW < minSize.width))
                newW = minSize.width;
            if ((newH != 0) && (newH < minSize.height))
                newH = minSize.height;
        }

        // If below min-size, rescaleBoard throws
        // IllegalArgumentException. Pass to our caller.
        rescaleBoard(newW, newH);

        // Resize
        super.setSize(newW, newH);
        if ((newW > 0) && (newH > 0))
            hasCalledSetSize = true;
        repaint();
    }

    /**
     * Set the board to a new size, rescale graphics and repaint if needed.
     *
     * @param sz New size in pixels, no less than {@link #panelMinBW} wide by {@link #panelMinBH} tall
     * @throws IllegalArgumentException if sz is too small but not 0.
     *   During initial layout, the layoutmanager may make calls to setSize(0,0);
     *   such a call is passed to super without scaling graphics.
     * @since 1.1.00
     */
    @Override
    public void setSize(Dimension sz)
        throws IllegalArgumentException
    {
        setSize (sz.width, sz.height);
    }

    /**
     * Set the board to a new location and size, rescale graphics and repaint if needed.
     * Called from {@link SOCPlayerInterface}'s custom layout manager.
     *
     * @param x  New location's x-coordinate
     * @param y  new location's y-coordinate
     * @param w  New width in pixels, no less than {@link #getMinimumSize()}.width
     * @param h  New height in pixels, no less than {@link #getMinimumSize()}.height
     * @throws IllegalArgumentException if w or h is too small but not 0.
     *   During initial layout, the layoutmanager may make calls to setBounds(0,0,0,0);
     *   such a call is passed to super without scaling graphics.
     * @since 1.1.00
     */
    @Override
    public void setBounds(int x, int y, int w, int h)
        throws IllegalArgumentException
    {
        if ((w != scaledPanelW) || (h != scaledPanelH))
            rescaleBoard(w, h);

        super.setBounds(x, y, w, h);
    }

    /**
     * Set or clear the most recent piece placement.
     * Usually by the current player.
     * Is also cleared by {@ink #updateMode()} when current player changes.
     * Doesn't trigger a repaint; assumes caller will do so soon.
     *<P>
     * Some drawing code assumes that when not null, {@code piece} is
     * one of the references passed to {@link SOCGame#putPiece(SOCPlayingPiece)}
     * (not a different object whose fields have the same values).
     *
     * @param piece  Latest piece placement, or {@code null} to clear
     * @since 2.7.00
     */
    public void setLatestPiecePlacement(final SOCPlayingPiece piece)
    {
        latestPiecePlacement = piece;
    }

    /**
     * A playing piece's value was updated:
     * {@code _SC_CLVI} village cloth count, or
     * {@code _SC_PIRI} pirate fortress strength.
     * Repaint that piece on the board if necessary.
     * @param piece  Piece that was updated, includes its new value
     * @since 2.0.00
     */
    public void pieceValueUpdated(final SOCPlayingPiece piece)
    {
        if (piece instanceof SOCFortress)
        {
            final SOCFortress fort = (SOCFortress) piece;
            final int strength = fort.getStrength();

            if (0 == strength)
            {
                if (0 == ((SOCBoardLarge) board).getPirateHex())
                {
                    // All players have recaptured their fortresses: The pirate fleet & path is removed.
                    flushBoardLayoutAndRepaint();
                }
                // else
                //  Server is about to send a game message to put a settlement where fortress was.
                //  Don't paint now; that message will cause another repaint.

                return;  // <--- Early return: fortress removed; repaint or another piece coming soon ---
            }

            final int pn = piece.getPlayerNumber();

            // repaint this piece in the AWT thread
            java.awt.EventQueue.invokeLater(new Runnable()
            {
                public void run()
                {
                    final boolean xlat = (panelMarginX != 0) || (panelMarginY != 0);

                    final Image ibuf = buffer;  // Local var in case field becomes null in other thread during paint
                    if (ibuf != null)
                        drawFortress(ibuf.getGraphics(), fort, pn, false, xlat);

                    Graphics bgr = getGraphics();
                    if (bgr != null)
                        drawFortress(bgr, fort, pn, false, xlat);
                    else
                        repaint();
                }
            });
        }
        else if (piece instanceof SOCVillage)
        {
            // Villages are drawn once in drawBoardEmpty, then kept in an image buffer.
            // If village cloth count becomes 0, redraw it in gray.
            // Otherwise no update needed, village cloth count isn't shown in the drawn piece
            // (shown in tooltip hover instead).
            if (((SOCVillage) piece).getCloth() == 0)
                flushBoardLayoutAndRepaint();
        }
        else
        {
            // generic catch-all for future piece types: just repaint the board.
            flushBoardLayoutAndRepaint();
        }
    }

    /**
     * Clear the board layout (as rendered in the empty-board buffer) and trigger a repaint.
     *
     * @since 1.1.08
     * @see SOCPlayerInterface#updateAtNewBoard()
     */
    public void flushBoardLayoutAndRepaint()
    {
        if (emptyBoardBuffer != null)
        {
            emptyBoardBuffer.flush();
            emptyBoardBuffer = null;
        }
        if (isScaled || isHexesAlwaysScaled)
        {
            scaledAt = System.currentTimeMillis();  // reset the image-scaling timeout
            scaledMissedImage = false;
        }

        if ((scaledHexes != null) && (scaledHexesGraphicsSetIndex != hexesGraphicsSetIndex))
        {
            // force overwrite of hexes having previous graphics set
            try
            {
                rescaleBoard(scaledPanelW, scaledPanelH);
            } catch (IllegalArgumentException e) {}
        }

        repaint();
    }

    /**
     * Clear the board layout (as rendered in the
     * empty-board buffer) and trigger a repaint,
     * only if we're showing potential/legal
     * settlements/roads/cities for debug purposes.
     * @since 2.0.00
     */
    public void flushBoardLayoutAndRepaintIfDebugShowPotentials()
    {
        boolean foundAny = false;
        for (int i = debugShowPotentials.length - 1; i >= 0; --i)
        {
            if (debugShowPotentials[i])
            {
                foundAny = true;
                break;
            }
        }

        if (! foundAny)
            return;

        flushBoardLayoutAndRepaint();
    }

    /**
     * Set the board fields to a new size, and rescale graphics if needed.
     * Does not call repaint or setSize.
     * Updates {@link #isScaledOrRotated}, {@link #scaledPanelW}, {@link #panelMarginX}, and other fields.
     * Calls {@link #rescaleCoordinateArrays()}, {@link #renderBorderedHex(Image, Color)},
     * and {@link #renderPortImages()}.
     *
     * @param newW  New width in pixels, no less than {@link #minSize}.width
     * @param newH  New height in pixels, no less than {@link #minSize}.height
     * @throws IllegalArgumentException if newW or newH is below {@link #minSize} but not 0.
     *   During initial layout, the layoutmanager may cause calls to rescaleBoard(0,0);
     *   such a call is ignored, no rescaling of graphics is done.
     * @since 1.1.00
     */
    private void rescaleBoard(int newW, int newH)
        throws IllegalArgumentException
    {
        if ((newW == 0) || (newH == 0))
            return;
        if ((newW < minSize.width) || (newH < minSize.height))
            throw new IllegalArgumentException("Below minimum size");

        /**
         * Set vars
         */
        scaledPanelW = newW;
        scaledPanelH = newH;

        scaledBoardW = newW;  // for use in next scaleToActual call
        isScaled = true;      // also needed for that call
        scaledHexesGraphicsSetIndex = hexesGraphicsSetIndex;
        if (scaleToActual(minSize.height) > newH)
        {
            // Using scaledPanelW:unscaledBoardW as a scaling ratio, newH wouldn't fit contents of board.
            // So, calc ratio based on newH:minSize.height instead
            float ratio = newH / (float) minSize.height;
            scaledBoardW = (int) (ratio * minSize.width);
        }

        if ((scaledPanelW != minSize.width) || (scaledPanelH != minSize.height))
        {
            isScaled = ((scaledPanelW / (float) minSize.width) >= SCALE_FACTOR_MIN)
                && ((scaledPanelH / (float) minSize.height) >= SCALE_FACTOR_MIN);
            if (! isScaled)
            {
                scaledBoardW = minSize.width;
            }
        }
        scaledAt = System.currentTimeMillis();
        isScaledOrRotated = (isScaled || isRotated);

        /**
         * Margins and centering board within wider or taller panel
         */
        if (isRotated)
        {
            // Because rotated, scaled on-screen width checks board height

            final int hexesWidth = halfdeltaY * (board.getBoardHeight() + 2)  + HALF_HEXHEIGHT + HEXY_OFF_SLOPE_HEIGHT;
                // getBoardHeight needs +2 to cover all of portsRing
            panelMarginX = scaleToActual(panelMinBH - hexesWidth) / 2;  // take half, to center

            final int hexesHeight = halfdeltaX * (board.getBoardWidth() - 1),  // width - 1 from portsRing's extra margin
                      scaledBoardH = scaleToActual(hexesHeight);
            panelMarginY = (scaledPanelH - scaledBoardH) / 2;
        } else {
            final int hexesWidth = halfdeltaX * board.getBoardWidth();
            panelMarginX = scaleToActual(panelMinBW - hexesWidth) / 2;  // take half, to center
            panelMarginY = (scaledPanelH - scaleToActual(panelMinBH)) / 2;
        }

        if (panelMarginX < (halfdeltaX / 4))  // and also if negative (larger than panelMinBW)
            panelMarginX = 0;
        if (scaledBoardW < scaledPanelW)
            panelMarginX += (scaledPanelW - scaledBoardW) / 2;

        if (panelMarginY < (halfdeltaY / 4))  // and also if negative (larger than panelMinBW)
            panelMarginY = 0;

        panelMarginX += scaleToActual(panelShiftBX);
        panelMarginY += scaleToActual(panelShiftBY);

        /**
         * Off-screen buffer is now the wrong size.
         * paintComponent() will create a new one.
         */
        if (buffer != null)
        {
            buffer.flush();
            buffer = null;
        }
        if (emptyBoardBuffer != null)
        {
            emptyBoardBuffer.flush();
            emptyBoardBuffer = null;
        }
        diceNumberCircleFont = null;
        diceNumberCircleFM = null;

        /**
         * Scale coordinate arrays for drawing pieces,
         * or (if not isScaled) point to static arrays.
         */
        rescaleCoordinateArrays();

        /**
         * Scale and render images, or point to static arrays.
         * For water hex border color, loops assume SOCBoard.WATER_HEX == index 0 in BC.
         */
        final BufferedImage[] staticHex;  // hex type images
        final Color[] BC;  // border colors
        final Color waterBC;  // water border color
        if (isRotated)
        {
            staticHex = rotatHexes;
            BC = ROTAT_HEX_BORDER_COLORS;
        } else {
            staticHex = hexes;
            BC = HEX_BORDER_COLORS;
        }
        waterBC = HEX_GRAPHICS_SET_BORDER_WATER_COLORS[hexesGraphicsSetIndex];

        if (! (isScaled || isHexesAlwaysScaled))
        {
            for (int i = scaledHexes.length - 1; i >= 0; --i)
                if (i < BC.length)
                    scaledHexes[i] = renderBorderedHex(staticHex[i], (i != 0) ? BC[i] : waterBC);
                else
                    scaledHexes[i] = staticHex[i];
        }
        else
        {
            final int w = scaleToActual((isRotated) ? HEXHEIGHT : HEXWIDTH),
                      h = scaleToActual((isRotated) ? HEXWIDTH : HEXHEIGHT);

            for (int i = scaledHexes.length - 1; i >= 0; --i)
            {
                if (staticHex[i] != null)
                {
                    BufferedImage hi = getScaledImageUp(staticHex[i], w, h);
                    if (i < BC.length)
                        hi = renderBorderedHex(hi, (i != 0) ? BC[i] : waterBC);

                    scaledHexes[i] = hi;
                    scaledHexFail[i] = false;
                } else {
                    scaledHexes[i] = null;
                    scaledHexFail[i] = true;
                }
            }

            for (int i = scaledPorts.length - 1; i >= 0; --i)
                scaledPortFail[i] = false;
        }

        // Once the port arrowhead arrays and images are scaled, we can draw the 6 port images.
        renderPortImages();

        if ((superText1 != null) && (superTextBox_w > 0))
        {
            superTextBox_x = (newW - superTextBox_w) / 2;
            superTextBox_y = (newH - superTextBox_h) / 2;
        }
    }

    /**
     * Render a border around the edge of this hex, returning a new image.
     * @param hex  Un-bordered hex image
     * @param borderColor  Color to paint the rendered border,
     *     from {@link #HEX_BORDER_COLORS} or {@link #ROTAT_HEX_BORDER_COLORS},
     *     or {@code null} to not render a border
     * @return a new Image for the bordered hex, or the original {@code hex} if {@code borderColor} was null
     * @since 1.1.20
     */
    private BufferedImage renderBorderedHex(final BufferedImage hex, final Color borderColor)
    {
        if (borderColor == null)
            return hex;

        final int w = hex.getWidth(null), h = hex.getHeight(null);

        final BufferedImage bHex = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = bHex.createGraphics();

        g.drawImage(hex, 0, 0, w, h, null);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(scaleToActual(13) / 10f));  // border line width 1.3px
        g.setColor(borderColor);
        if (isRotated)
            g.translate(scaleToActual(1), 0);  // overlap pixel border properly, especially on right-hand side
        g.drawPolyline(scaledHexCornersX, scaledHexCornersY, 7);

        g.dispose();
        return bHex;
    }

    /**
     * Based on the waterHex image, render the 6 port images (1 per "facing" rotation), each with arrows in its
     * 2 settlement directions. Fills {@link #scaledPorts}, starting from {@code waterHex.gif} previously loaded
     * into {@link #scaledHexes}[0].
     *<P>
     * Before calling this method, call {@link #rescaleCoordinateArrays()}.
     * @since 1.1.20
     */
    private void renderPortImages()
    {
        final Image water = scaledHexes[0];
        final int w = water.getWidth(null), h = water.getHeight(null);

        // clear circle geometry
        int diac = HEX_PORT_CIRCLE_DIA;
        int xc, yc, arrow_offx;

        // white border width
        int diab = diac + 2;

        if (isRotated)
        {
            xc = HEXHEIGHT;  yc = HEXWIDTH;
            arrow_offx = scaleToActual(HEXHEIGHT - HEXWIDTH);  // re-center on wider hex
        } else {
            xc = HEXWIDTH;  yc = HEXHEIGHT;
            arrow_offx = 0;
        }
        // center on hex, minus radius
        xc = (xc - diac) / 2;
        yc = (yc - diac) / 2;

        if (isScaled)
        {
            diab = scaleToActual(diab);
            xc = scaleToActual(xc); yc = scaleToActual(yc); diac = scaleToActual(diac);
            if (diab % 2 != 0)
            {
                ++diab;
                ++diac;
            }
            if (diac % 2 != 0)
                --diac;  // don't reduce border width
        }
        int xb = xc - (diab - diac) / 2;
        int yb = yc - (diab - diac) / 2;

        // First, clear the middle circle and draw the white border around it.
        // Then, use the resulting image as a starting point for the 6 port images with different arrowheads.

        BufferedImage portBase = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        {
            Graphics2D g = portBase.createGraphics();
            g.drawImage(water, 0, 0, w, h, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // white circular border
            g.setColor(Color.WHITE);
            g.fillOval(xb, yb, diab, diab);

            // clear circle to show port type
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
            g.fillOval(xc, yc, diac, diac);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

            g.dispose();
        }

        for (int i = 0; i < 6; ++i)
        {
            BufferedImage bufi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bufi.createGraphics();
            g.drawImage(portBase, 0, 0, w, h, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // arrows
            g.setColor(Color.WHITE);
            g.translate(arrow_offx, 0);
            g.fillPolygon(scaledPortArrowsX[i], scaledPortArrowsY[i], 3);
            int i2 = i + 1;
            if (i2 == 6)
                i2 = 0;  // wrapped around
            g.fillPolygon(scaledPortArrowsX[i2], scaledPortArrowsY[i2], 3);
            g.translate(-arrow_offx, 0);

            g.dispose();
            scaledPorts[i] = bufi;
        }
    }

    /**
     * Scale coordinate arrays for drawing pieces
     * (from internal coordinates to actual on-screen pixels),
     * or (if not isScaled) point to static arrays.
     * Called from constructor and {@link #rescaleBoard(int, int)}.
     * @since 1.1.00
     */
    private void rescaleCoordinateArrays()
    {
        if (! isScaled)
        {
            if (! isRotated)
            {
                scaledVertRoadX = vertRoadX;     scaledVertRoadY = vertRoadY;
                scaledUpRoadX   = upRoadX;       scaledUpRoadY   = upRoadY;
                scaledDownRoadX = downRoadX;     scaledDownRoadY = downRoadY;
                scaledHexCornersX = hexCornersX; scaledHexCornersY = hexCornersY;
                scaledPortArrowsX = portArrowsX; scaledPortArrowsY = portArrowsY;
            } else {
                // (cw):  P'=(width-y, x)
                scaledVertRoadX = rotateScaleCopyYToActualX(vertRoadY, HEXWIDTH, false);
                scaledVertRoadY = vertRoadX;
                scaledUpRoadX   = rotateScaleCopyYToActualX(upRoadY, HEXWIDTH, false);
                scaledUpRoadY   = upRoadX;
                scaledDownRoadX = rotateScaleCopyYToActualX(downRoadY, HEXWIDTH, false);
                scaledDownRoadY = downRoadX;
                scaledHexCornersX = hexCornersY;  // special case: coordinates already "rotated", don't subtract from HEXWIDTH
                scaledHexCornersY = hexCornersX;
                scaledPortArrowsX = new int[portArrowsX.length][];
                for (int i = 0; i < portArrowsX.length; i++)
                    scaledPortArrowsX[i] = rotateScaleCopyYToActualX(portArrowsY[i], HEXWIDTH, false);
                scaledPortArrowsY = portArrowsX;
            }
            scaledSettlementX = settlementX; scaledSettlementY = settlementY;
            scaledCityX     = cityX;         scaledCityY     = cityY;
            scaledShipX     = shipX;         scaledShipY     = shipY;
            scaledFortressX = fortressX;     scaledFortressY = fortressY;
            scaledDiamondX  = diamondX;      scaledDiamondY  = diamondY;
            scaledWarshipX  = warshipX;      scaledWarshipY  = warshipY;
            scaledRobberX   = robberX;       scaledRobberY   = robberY;
            scaledArrowXL   = arrowXL;       scaledArrowY    = arrowY;
            if (arrowXR == null)
            {
                int[] axr = new int[arrowXL.length];
                for (int i = 0; i < arrowXL.length; ++i)
                    axr[i] = (ARROW_SZ - 1) - arrowXL[i];
                arrowXR = axr;

                // Assigned to static field only when complete,
                // so another thread won't see a partially
                // calculated arrowXR.
            }
            scaledArrowXR = arrowXR;
        }
        else
        {
            scaledPortArrowsX = new int[portArrowsX.length][];
            scaledPortArrowsY = new int[portArrowsY.length][];

            if (! isRotated)
            {
                scaledVertRoadX = scaleCopyToActual(vertRoadX);
                scaledVertRoadY = scaleCopyToActual(vertRoadY);
                scaledUpRoadX   = scaleCopyToActual(upRoadX);
                scaledUpRoadY   = scaleCopyToActual(upRoadY);
                scaledDownRoadX = scaleCopyToActual(downRoadX);
                scaledDownRoadY = scaleCopyToActual(downRoadY);
                scaledHexCornersX = scaleCopyToActual(hexCornersX);
                scaledHexCornersY = scaleCopyToActual(hexCornersY);
                for (int i = 0; i < portArrowsX.length; ++i)
                {
                    scaledPortArrowsX[i] = scaleCopyToActual(portArrowsX[i]);
                    scaledPortArrowsY[i] = scaleCopyToActual(portArrowsY[i]);
                }
            } else {
                // (cw):  P'=(width-y, x)
                scaledVertRoadX = rotateScaleCopyYToActualX(vertRoadY, HEXWIDTH, true);
                scaledVertRoadY = scaleCopyToActual(vertRoadX);
                scaledUpRoadX   = rotateScaleCopyYToActualX(upRoadY, HEXWIDTH, true);
                scaledUpRoadY   = scaleCopyToActual(upRoadX);
                scaledDownRoadX = rotateScaleCopyYToActualX(downRoadY, HEXWIDTH, true);
                scaledDownRoadY = scaleCopyToActual(downRoadX);
                scaledHexCornersX = scaleCopyToActual(hexCornersY);  // special case: don't subtract from HEXWIDTH
                scaledHexCornersY = scaleCopyToActual(hexCornersX);
                for (int i = 0; i < portArrowsX.length; ++i)
                {
                    scaledPortArrowsX[i] = rotateScaleCopyYToActualX(portArrowsY[i], HEXWIDTH, true);
                    scaledPortArrowsY[i] = scaleCopyToActual(portArrowsX[i]);
                }
            }
            scaledSettlementX = scaleCopyToActual(settlementX);
            scaledSettlementY = scaleCopyToActual(settlementY);
            scaledCityX     = scaleCopyToActual(cityX);
            scaledCityY     = scaleCopyToActual(cityY);
            scaledShipX = scaleCopyToActual(shipX);
            scaledShipY = scaleCopyToActual(shipY);
            scaledFortressX = scaleCopyToActual(fortressX);
            scaledFortressY = scaleCopyToActual(fortressY);
            scaledDiamondX  = scaleCopyToActual(diamondX);
            scaledDiamondY  = scaleCopyToActual(diamondY);
            scaledWarshipX = scaleCopyToActual(warshipX);
            scaledWarshipY = scaleCopyToActual(warshipY);
            scaledRobberX   = scaleCopyToActual(robberX);
            scaledRobberY   = scaleCopyToActual(robberY);
            scaledArrowXL   = scaleCopyToActual(arrowXL);
            scaledArrowY    = scaleCopyToActual(arrowY);

            // Ensure arrow-tip sides are 45 degrees.
            int p = Math.abs(scaledArrowXL[0] - scaledArrowXL[1]);
            if (p != Math.abs(scaledArrowY[0] - scaledArrowY[1]))
            {
                scaledArrowY[0] = scaledArrowY[1] + p;
            }
            int L = scaledArrowXL.length - 1;
            p = Math.abs(scaledArrowXL[L] - scaledArrowXL[L-1]);
            if (p != Math.abs(scaledArrowY[L] - scaledArrowY[L-1]))
            {
                scaledArrowY[L] = scaledArrowY[L-1] - p;
            }

            // Now, flip for scaledArrowXR
            scaledArrowXR = new int[scaledArrowXL.length];
            int xmax = scaledArrowXL[4];  // Element defined as having max coord in arrowXL
            for (int i = 0; i < scaledArrowXR.length; ++i)
                scaledArrowXR[i] = xmax - scaledArrowXL[i];
        }
    }

    /**
     * Rescale to actual screen coordinates - Create a copy
     * of array, and scale up the copy's elements as X or Y coordinates.
     *<P>
     * Before v2.0.00 this method was {@code scaleCopyToActualX(x[])} and {@link scaleCopyToActualY(y[])}.
     *
     * @param orig Int array to be scaled up; each member is an x-coordinate or y-coordinate.
     * @return Scaled copy of orig
     *
     * @see #scaleToActual(int[])
     * @see #rotateScaleCopyYToActualX(int[], int, boolean)
     * @since 1.1.00
     */
    public int[] scaleCopyToActual(int[] orig)
    {
        int[] xs = new int[orig.length];
        for (int i = orig.length - 1; i >= 0; --i)
            xs[i] = (int) ((orig[i] * (long) scaledBoardW) / unscaledBoardW);
        return xs;
    }

    /**
     * Copy and rotate this array of y-coordinates, optionally also rescaling.
     * Rotates internal to actual (clockwise):  P'=(width-y, x)
     * @param yorig Array to copy and rotate, not null
     * @param width Width to rotate against
     * @param rescale Should we also rescale, same formula as {@link #scaleCopyToActual(int[])}?
     * @return Rotated copy of <tt>yorig</tt> for use as x-coordinates
     * @since 1.1.08
     */
    public int[] rotateScaleCopyYToActualX(final int[] yorig, final int width, final boolean rescale)
    {
        int[] xr = new int[yorig.length];
        for (int i = yorig.length - 1; i >= 0; --i)
            xr[i] = width - yorig[i];
        if (rescale)
            for (int i = yorig.length - 1; i >= 0; --i)
                xr[i] = (int) ((xr[i] * (long) scaledBoardW) / unscaledBoardW);

        return xr;
    }

    /**
     * Scale up an image with decent quality.
     * Convenience method to call instead of obsolete {@link Image#getScaledInstance(int, int, int)}.
     * Calls <code>
     * {@link Graphics2D#drawImage(Image, int, int, int, int, java.awt.image.ImageObserver) Graphics2D.drawImage}
     * (src, 0, 0, w, h, null)</code> using {@link RenderingHints#VALUE_INTERPOLATION_BICUBIC}.
     *<P>
     * For more info see the Java2D team blog 2007 post "The Perils of Image.getScaledInstance()" by ChrisAdamson.
     *
     * @param src  Source image to scale up; assumes is transparent, not opaque.
     * @param w  Scale up to this width
     * @param h  Scale up to this height
     * @return  the scaled image
     * @since 1.1.20
     */
    public final BufferedImage getScaledImageUp(final Image src, final int w, final int h)
    {
        BufferedImage bufi = playerInterface.getGraphicsConfiguration().createCompatibleImage
            (w, h, Transparency.BITMASK);
            // use PI instead of this.getGraphicsConfiguration(),
            // which is null during early call by PI constructor through initUIElements/rescaleBoard
        Graphics2D g = bufi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        return bufi;
    }

    /**
     * Set or clear the debug flag where the board item tooltip includes the item's coordinates.
     * Takes effect the next time the mouse moves.
     * @see BoardToolTip#setHoverText(String, int)
     * @param setOn  If true, set the flag; if false, clear it
     * @since 2.0.00
     */
    void setDebugShowCoordsFlag(final boolean setOn)
    {
        if (setOn == debugShowCoordsTooltip)
            return;

        debugShowCoordsTooltip = setOn;

        // no immediate repaint, because we don't know the coordinate (on)
        // and the coordinate string is part of the text (off).
    }

    /**
     * Set or clear a debug flag to show player 0's potential/legal coordinate sets.
     * Currently implemented only for the sea board layout ({@link SOCBoardLarge}).
     * When turning on pieceType 2 (or all), prints some geometry info to {@link System#err};
     * that's printed even if not using a sea board layout.
     *
     * @param pieceType  Piece type; 0=road, 1=settle, 2=city, 3=ship;
     *         Use 8 for land hexes, 9 for nodes on board.  Or, -1 for all.
     *         See {@link #debugShowPotentials} javadoc for all values.
     * @param setPotential  If true, show/hide the potential set, not the legal set
     * @param setOn  If true, set the flag; if false, clear it
     * @since 2.0.00
     */
    void setDebugShowPotentialsFlag
        (int pieceType, final boolean setPotential, final boolean setOn)
    {
        if (pieceType == -1)
        {
            Arrays.fill(debugShowPotentials, setOn);  // all flags
        } else {
            if (setPotential && (pieceType < 4))
                pieceType += 4;
            if (setOn == debugShowPotentials[pieceType])
                return;  // nothing to do

            debugShowPotentials[pieceType] = setOn;
        }

        scaledMissedImage = true;  // force redraw of the empty board
        repaint();  // to call drawBoard, drawBoardEmpty

        if (setOn && ((pieceType == 2) || (pieceType == -1)))
        {
            int[] vs = (board instanceof SOCBoardLarge)
                ? ((SOCBoardLarge) board).getAddedLayoutPart("VS") : null;
            if (vs == null)
                vs = new int[]{0, 0};
            System.err.println
                ("debugShowPotentials: Board size (height, width) = 0x"
                 + Integer.toHexString(board.getBoardWidth()) + ",0x" + Integer.toHexString(board.getBoardHeight())
                 + ", VS (down, right) = " + vs[0] + "," + vs[1]);
            System.err.println
                ("  Panel size (width, height): unscaled = ("
                 + panelMinBW + "," + panelMinBH + ')' + ((isRotated) ? ", rotated" : "")
                 + ", current = (" + scaledBoardW + " of " + scaledPanelW + "," + scaledPanelH
                 + "), margin (left, top) = (" + panelMarginX + "," + panelMarginY
                 + "), unscaled shift (right, down) = (" + panelShiftBX + "," + panelShiftBY
                 + ')' );
            int w = playerInterface.getWidth(), h = playerInterface.getHeight();
            Insets ins = playerInterface.getInsets();
            System.err.println
                ("  PI window size (width, height) = " + w + "," + h
                 + ", inner = " + (w - ins.left - ins.right) + "," + (h - ins.top - ins.bottom)
                 + ", insets (left, right, top, bottom) = "
                 + ins.left + "," + ins.right + "," + ins.top + "," + ins.bottom);
        }
    }

    /**
     * Redraw the board using double buffering. Don't call this directly, use
     * {@link Component#repaint()} instead.
     *<P>
     * Calls {@link #drawBoard(Graphics)} to do the actual work.
     *<P>
     * To protect against bugs, paint contains a try-catch that will
     * print stack traces to the player chat print area.
     */
    @Override
    public void paintComponent(Graphics g)
    {
        VolatileImage ibuf = buffer;  // Local var in case field becomes null in other thread during paint
        try
        {
            final GraphicsConfiguration gconf = getGraphicsConfiguration();
            if (gconf == null)
            {
                repaint();  // try again soon
                return;
            }

            do
            {
                final int ibufValidStatus = (ibuf == null) ? -1 : ibuf.validate(gconf);
                if ((ibuf == null) || (ibufValidStatus == VolatileImage.IMAGE_INCOMPATIBLE))
                {
                    ibuf = gconf.createCompatibleVolatileImage
                        (scaledPanelW, scaledPanelH, Transparency.OPAQUE);
                }
            } while (ibuf.contentsLost());
            buffer = ibuf;

            // Try-catch: Because of message timing during placement,
            // watch for the board's lists of roads, settlements, ships, etc
            // being modified as we're drawing them.
            // Happens with foreach loop iteration; wasn't an issue
            // in v1.x.xx with older-java piece enumerations.
            for (int tries = 1; tries <= 3; ++tries)
            {
                final Graphics2D graf2D = ibuf.createGraphics();
                    // done within loop to have a known starting state (no transforms, etc)
                try
                {
                    drawBoard(graf2D);  // Do the actual drawing
                    break;
                } catch (ConcurrentModificationException cme) {
                    if (tries == 3)
                        repaint();  // try again soon
                          // Meanwhile we'll paint whatever succeeded, to avoid an entire-window blank flash
                } finally {
                    graf2D.dispose();
                }
            }

            if (hoverTip.isVisible())
                hoverTip.paint(ibuf.getGraphics());
            g.drawImage(ibuf, 0, 0, this);

        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }

    /**
     * Overriden so the peer isn't painted, which clears background. Don't call
     * this directly, use {@link Component#repaint()} instead.
     */
    @Override
    public void update(Graphics g)
    {
        paint(g);
    }

    /**
     * Draw a board tile by its hex number (v1 or v2 board encoding).
     * Hex type and port info (if any) are looked up from {@link SOCBoard#getHexLayout()} and {@code hexNum}.
     *<P>
     * Not used if {@link #isLargeBoard}.
     * When {@link #isLargeBoard}, call {@link #drawHex(Graphics, int, int, int, int, int)} instead.
     *
     * @param g       graphics
     * @param hexNum  hex location number (0-36)
     */
    private final void drawHex(Graphics g, int hexNum)
    {
        final int portFacing;
        int hexType = board.getHexLayout()[hexNum];

        if (hexType < SOCBoard.MISC_PORT_HEX)
        {
            portFacing = -1;
        }
        else if (hexType < 0x10)
        {
            portFacing = hexType - 6;  // 7-12 -> 1-6
            hexType = SOCBoard.MISC_PORT;
        }
        else
        {
            // Decode port bit mask:
            // (port facing, 1-6)        (kind of port)
            //      \--> [0 0 0][0 0 0 0] <--/
            portFacing = hexType >> 4;
            hexType = hexType & 0x0F;
        }

        drawHex(g, hexX[hexNum], hexY[hexNum], hexType, portFacing, hexNum);
    }

    /**
     * Draw a land, water, or port hex tile at a given location.
     * Use <tt>hexType</tt> to determine the hex graphic,
     * and <tt>portFacing</tt> to determine a port overlay (if any).
     * When <tt>hexNum</tt> is -1, no dice number is drawn.
     * Otherwise, also draw the dice number for this hex (if any).
     *
     * @param g      graphics
     * @param x      board-graphics x-coordinate to draw at; upper-left corner of hex
     * @param y      board-graphics y-coordinate to draw at; upper-left corner of hex
     * @param hexType hex type, as in {@link SOCBoard#getHexTypeFromCoord(int)};
     *                also the index into {@link #hexes}.
     *                If drawing a port (<tt>portFacing</tt> != -1),
     *                <tt>hexType</tt> is {@link SOCBoard#getPortTypeFromNodeCoord(int)},
     *                such as {@link SOCBoard#SHEEP_PORT} or {@link SOCBoard#MISC_PORT}.
     * @param portFacing  port facing (1-6), or -1 for no port: the edge of the port's hex
     *                touching land, which contains 2 nodes where player can build a
     *                port settlement/city.  Valid range is
     *                {@link SOCBoard#FACING_NE} to {@link SOCBoard#FACING_NW}.
     * @param hexNum  hex location number (0-36) to look up its dice number,
     *                   or -1 if this isn't a valid hex number or if the dice number shouldn't be drawn.
     *                   When {@link #isLargeBoard}, pass in the hex coordinate as hexNum.
     * @since 1.1.08
     */
    private final void drawHex
        (Graphics g, int x, int y, final int hexType, final int portFacing, final int hexNum)
    {
        if (hexType < 0)
        {
            playerInterface.chatPrintDebug("* bad hex type " + hexType + " at x,y(" + x + "," + y + ")");
            return;
        }

        // Set up dice number font fields early: they're also used
        // for fortresses, village markers, etc, which might get
        // drawn before a hex having a dice number
        if (diceNumberCircleFont == null)
        {
            int fsize = DICE_NUMBER_FONTPOINTS;
            if (isScaled)
                fsize = scaleToActual(fsize);
            diceNumberCircleFont = new Font("Dialog", Font.BOLD, fsize);
        }
        if ((diceNumberCircleFM == null) && (diceNumberCircleFont != null))
        {
            diceNumberCircleFM = getFontMetrics(diceNumberCircleFont);
        }

        if (isScaledOrRotated)
        {
            if (isRotated)
            {
                // (cw):  P'=(panelMinBH-y, x)
                int y1 = x;
                x = panelMinBH - y - HEXHEIGHT;  // move 1 hex over, since corner of image has rotated
                y = y1;
            }
            if (isScaled)
            {
                x = scaleToActual(x);
                y = scaleToActual(y);
            }
        }

        /**
         * If previous scaling has failed, fallback image will be smaller
         * compared to rest of the board graphics.  This is rare but
         * must be visually handled.  Center the smaller graphic in
         * the larger hex space, so it won't overlap other hexes.
         */
        boolean recenterPrevMiss = false;
        int xm=0, ym=0;  // offset for re-centering miss

        /**
         * If board is scaled, could be waiting for an image to resize.
         * If it still hasn't appeared after 7 seconds, give up and
         * create a new one.  (This can happen due to AWT bugs.)
         * drawBoard will then repaint with the new image.
         * If the new image also fails, a "fallback" will occur; a reference
         * to the static original-resolution image will be used.
         */
        boolean missedDraw = false;

        // Draw the hex image. For ports, the overlay (circle with arrows) will be drawn on top of this hex.
        final int htypeIdx;
        {
            final BufferedImage[] hexis = (isRotated ? rotatHexes : hexes);  // Fall back to original, or to rotated?

            // For the 3:1 port, don't use hexType for image index (hexType 0 is open water);
            // miscPort.gif is at end of hex images array. hexType index works for 2:1 port types.
            htypeIdx = ((portFacing == -1) || (hexType != SOCBoard.MISC_PORT))
                ? hexType : (hexis.length - 1);

            if (isScaled && (scaledHexes[htypeIdx] == hexis[htypeIdx]))
            {
                recenterPrevMiss = true;
                int w = hexis[htypeIdx].getWidth(null);
                int h = hexis[htypeIdx].getHeight(null);
                xm = (scaleToActual(w) - w) / 2;
                ym = (scaleToActual(h) - h) / 2;
                x += xm;
                y += ym;
            }

            /**
             * Draw the hex graphic
             */
            if (! g.drawImage(scaledHexes[htypeIdx], x, y, this))
            {
                // for now, draw the placeholder; try to rescale and redraw soon if we can

                g.translate(x, y);
                g.setColor(hexColor(hexType));
                g.fillPolygon(scaledHexCornersX, scaledHexCornersY, 6);
                g.setColor(Color.BLACK);
                g.drawPolyline(scaledHexCornersX, scaledHexCornersY, 7);
                g.translate(-x, -y);

                missedDraw = true;
                if ((isScaled || isHexesAlwaysScaled)
                    && (RESCALE_MAX_RETRY_MS < (drawnEmptyAt - scaledAt)))
                {
                    // rescale the image, or if that failed already, give up and use fallback

                    if (scaledHexFail[htypeIdx])
                    {
                        scaledHexes[htypeIdx] = hexis[htypeIdx];  // fallback
                    }
                    else
                    {
                        scaledHexFail[htypeIdx] = true;
                        final int w = scaleToActual((isRotated) ? HEXHEIGHT : HEXWIDTH),
                                  h = scaleToActual((isRotated) ? HEXWIDTH : HEXHEIGHT);
                        scaledHexes[htypeIdx] = getScaledImageUp(hexis[htypeIdx], w, h);
                    }
                }
            }

            if (recenterPrevMiss)
            {
                // Don't "center" further drawing
                x -= xm;
                y -= ym;
                recenterPrevMiss = false;
            }
        }

        /**
         * Draw the port overlay image
         */
        if (portFacing != -1)
        {
            // fallback will be non-scaled hexes[htypeIdx]

            final int ptypeIdx = portFacing - 1;  // index 0-5 == facing 1-6

            if (isScaled && (scaledPorts[ptypeIdx] == hexes[htypeIdx]))
            {
                recenterPrevMiss = true;
                int w = hexes[htypeIdx].getWidth(null);
                int h = hexes[htypeIdx].getHeight(null);
                xm = (scaleToActual(w) - w) / 2;
                ym = (scaleToActual(h) - h) / 2;
                x += xm;
                y += ym;
            }

            if (! g.drawImage(scaledPorts[ptypeIdx], x, y, this))
            {
                g.drawImage(hexes[htypeIdx], x, y, null);  // show smaller unscaled hex graphic, instead of a blank space
                missedDraw = true;
                if ((isScaled || isHexesAlwaysScaled)
                    && (RESCALE_MAX_RETRY_MS < (drawnEmptyAt - scaledAt)))
                {
                    if (scaledPortFail[ptypeIdx])
                    {
                        scaledPorts[ptypeIdx] = hexes[htypeIdx];  // fallback
                    }
                    else
                    {
                        scaledPortFail[ptypeIdx] = true;

                        // TODO try to re-render this particular port type
                        /*
                        int w = scaleToActual(portis[1].getWidth(null));
                        int h = scaleToActual(portis[1].getHeight(null));
                        scaledPorts[ptypeIdx] = getScaledImageUp(portis[ptypeIdx], w, h);
                         */

                        // Instead of rendering, for now immediately fall back:
                        scaledPorts[ptypeIdx] = hexes[htypeIdx];  // fallback
                    }
                }
            }

            // Don't adj x,y to un-"center" further drawing:
            // If scaled, x,y will be recalculated anyway.
            // If not scaled, recenterPrevMiss is false.
        }

        if (hexNum == -1)
        {
            if (missedDraw)
            {
                // drawBoard will check this field after all hexes are drawn.
                scaledMissedImage = true;
            }
            return;  // <---- Early return: This hex isn't within hexLayout/numberLayout ----
        }

        /**
         * Draw the number
         */
        final int hnl = board.getNumberOnHexFromNumber(hexNum);
        if (hnl > 0)
        {
            if ((diceNumberCircleFM != null) && (diceNumberCircleFont != null))
            {
                final int dx, dy;  // Offset of number graphic from upper-left corner of hex
                if (isRotated)
                {
                    dx = 22;  dy = 17;
                } else {
                    dx = 17;  dy = 22;
                }

                if (! isScaled)
                {
                    x += dx;
                    y += dy;
                }
                else
                {
                    x += scaleToActual(dx);
                    y += scaleToActual(dy);
                }

                // Draw the circle and dice number:
                int dia = DICE_NUMBER_CIRCLE_DIAMETER;
                if (isScaled)
                    dia = scaleToActual(dia);
                ++dia;

                // Get color from rarity, fill dice circle, outline with darker shade
                {
                    int colorIdx;
                    if (hnl < 7)
                        colorIdx = hnl - 2;
                    else
                        colorIdx = 12 - hnl;
                    Color cc = DICE_NUMBER_CIRCLE_COLORS[colorIdx];

                    g.setColor(cc);
                    g.fillOval(x, y, dia, dia);
                    g.setColor(cc.darker().darker());
                    g.drawOval(x, y, dia, dia);
                }

                final String numstr = Integer.toString(hnl);
                x += (dia - (diceNumberCircleFM.stringWidth(numstr) - scaleToActual(1))) / 2;
                    // -1 removes unwanted advance spacing after digit string;
                    // if more precison needed, see java.awt.font.TextLayout.getBounds
                y += (dia + diceNumberCircleFM.getAscent() - diceNumberCircleFM.getDescent()) / 2;
                g.setFont(diceNumberCircleFont);
                g.setColor(Color.BLACK);
                g.drawString(numstr, x, y);

            } else {
                missedDraw = true;
            }
        }  // if (hnl > 0)

        if (missedDraw)
        {
            // drawBoard will check this field after all hexes are drawn.
            scaledMissedImage = true;
        }
    }

    /**
     * Draw the robber.
     *<P>
     * The pirate ship (if any) is drawn via
     * {@link #drawRoadOrShip(Graphics, int, int, HilightStyle, boolean, boolean)}.
     *
     * @param g       Graphics context
     * @param hexID   Board hex encoded position
     * @param fullNotGhost  Draw with normal colors, not faded-out "ghost"
     *                (as during PLACE_ROBBER movement)
     * @param fillNotOutline  Fill the robber, not just outline
     *                (as for previous robber position)
     */
    private final void drawRobber
        (Graphics g, final int hexID, final boolean fullNotGhost, final boolean fillNotOutline)
    {
        int hx, hy;
        if (isLargeBoard)
        {
            hx = halfdeltaX * (hexID & 0xFF);
            hy = halfdeltaY * (hexID >> 8) + HALF_HEXHEIGHT;  // HALF_HEXHEIGHT == halfdeltaY + 9
        } else {
            int hexNum = hexIDtoNum[hexID];
            hx = hexX[hexNum] + halfdeltaX;
            hy = hexY[hexNum] + HALF_HEXHEIGHT;
        }

        if (isRotated)
        {
            // (cw):  P'=(panelMinBH-y, x)
            int hy1 = hx;
            hx = panelMinBH - hy;
            hy = hy1;
        }
        if (isScaled)
        {
            hx = scaleToActual(hx);
            hy = scaleToActual(hy);
        }

        Color rFill, rOutline;
        if (fullNotGhost && fillNotOutline)
        {
            rFill = Color.lightGray;
            rOutline = Color.black;
        } else {
            // Determine "ghost" color, we're moving the robber
            int hexType = board.getHexTypeFromCoord(hexID);
            if ((hexType >= robberGhostFill.length) || (hexType < 0))
            {
                // should not happen
                rFill = Color.lightGray;
                rOutline = Color.black;
            } else if (robberGhostFill[hexType] != null)
            {
                // was cached from previous calculation
                rFill = robberGhostFill[hexType];
                rOutline = robberGhostOutline[hexType];

                if (! fillNotOutline)
                {
                    final int dnum = board.getNumberOnHexFromCoord(hexID);
                    if ((hexType == SOCBoard.DESERT_HEX)
                        || (dnum <= 3) || (dnum >= 11))
                    {
                        // outline-only against a light background.
                        rFill = Color.BLACK;
                    }
                }
            } else {
                // find basic color, "ghost" it
                rOutline = hexColor(hexType);
                if (rOutline == ColorSquare.WATER)
                {
                    // Should not happen
                    rOutline = Color.lightGray;
                }

                // If hex is light, robber fill color should be dark. (average with gray)
                // If hex is dark or midtone, it should be light. (average with white)
                rFill = SOCPlayerInterface.makeGhostColor(rOutline);
                rOutline = rOutline.darker();  // Always darken the outline

                // Remember for next time
                robberGhostFill[hexType] = rFill;
                robberGhostOutline[hexType] = rOutline;

            }  // cached ghost color?
        }  // normal or ghost?

        g.translate(hx, hy);
        if (fillNotOutline)
        {
            g.setColor(rFill);
            g.fillPolygon(scaledRobberX, scaledRobberY, 13);
        } else {
            rOutline = rFill;  // stands out better against hex color
        }
        g.setColor(rOutline);
        g.drawPolygon(scaledRobberX, scaledRobberY, 14);
        g.translate(-hx, -hy);
    }

    /**
     * draw a road or ship along an edge.
     * Or, draw the pirate ship in the center of a hex.
     * @param g  graphics
     * @param edgeNum  Edge number of this road or ship; accepts -1 for edgeNum 0x00.
     *           For the pirate ship in the middle of a hex, <tt>edgeNum</tt>
     *           can be a hex coordinate, and <tt>pn</tt> must be -2 or -3.
     * @param pn   Player number, or -1 for a white outline or fill color (depending on <tt>isHilight</tt>),
     *             or -2 for the black pirate ship, -3 for the previous-pirate outline.
     *             If the pirate ship (style -2) is being drawn on a hex which also shows a port,
     *             that black ship will be drawn with a white outline for visibility against the
     *             sometimes-dark sometimes-busy port graphics.
     * @param hilightStyle  Style of hilight to draw (showing a potential placement etc),
     *           or {@code null} for the usual piece style without hilight
     * @param isRoadNotShip  True to draw a road; false to draw a ship if {@link #isLargeBoard}
     * @param isWarship   True to draw a war ship (not normal ship) if {@link #isLargeBoard}, for scenario _SC_PIRI
     */
    private final void drawRoadOrShip
        (Graphics g, int edgeNum, final int pn, HilightStyle hilightStyle,
         final boolean isRoadNotShip, final boolean isWarship)
    {
        // Draw a road or ship
        int roadX[], roadY[];
        int hx, hy;
        if (edgeNum == -1)
            edgeNum = 0x00;

        if (! isLargeBoard)
        {
            final int hexNum;
            int dy = 0;  // y-offset, if edge's hex would draw it off the map

            if ((((edgeNum & 0x0F) + (edgeNum >> 4)) % 2) == 0)
            { // If first and second digit
              // are even, then it is '|'.
                hexNum = hexIDtoNum[edgeNum + 0x11];
                roadX = scaledVertRoadX;
                roadY = scaledVertRoadY;
            }
            else if (((edgeNum >> 4) % 2) == 0)
            { // If first digit is even,
              // then it is '/'.
                if ((edgeNum >= 0x81) && (0 == ((edgeNum - 0x81) % 0x22)))
                {
                    // hex is off the south edge of the board.
                    // move 2 hexes north and offset y.
                    hexNum = hexIDtoNum[edgeNum - 0x10 + 0x02];
                    dy = 2 * deltaY;
                } else {
                    hexNum = hexIDtoNum[edgeNum + 0x10];
                }
                roadX = scaledUpRoadX;
                roadY = scaledUpRoadY;
            }
            else
            { // Otherwise it is '\'.
                if ((edgeNum >= 0x18) && (0 == ((edgeNum - 0x18) % 0x22)))
                {
                    // hex is off the north edge of the board.
                    // move 2 hexes south and offset y.
                    hexNum = hexIDtoNum[edgeNum + 0x20 - 0x01];
                    dy = -2 * deltaY;
                } else {
                    hexNum = hexIDtoNum[edgeNum + 0x01];
                }
                roadX = scaledDownRoadX;
                roadY = scaledDownRoadY;
            }

            hx = hexX[hexNum];
            hy = hexY[hexNum] + dy;

        } else {

            // isLargeBoard:
            // Determining (r,c) edge direction: | / \
            //   "|" if r is odd
            //   Otherwise: s = r/2
            //   "/" if (s,c) is even,odd or odd,even
            //   "\" if (s,c) is odd,odd or even,even
            // Remember the vertical margin of halfdeltaY (or, r+1).

            final int r = (edgeNum >> 8),
                      c = (edgeNum & 0xFF);

            if (isWarship) {
                roadX = scaledWarshipX;
                roadY = scaledWarshipY;
            } else if (! isRoadNotShip) {
                roadX = scaledShipX;
                roadY = scaledShipY;
            } else {
                // roadX,roadY contents vary by edge direction
                roadX = null;  // always set below; null here
                roadY = null;  // to satisfy compiler
            }

            if ((pn <= -2) || ((r % 2) == 1))  // -2 or -3 is pirate ship, at a hex coordinate
            {
                // "|"
                hx = halfdeltaX * c;
                hy = halfdeltaY * r;  // offset: scaledVertRoadY is center of hex, not upper corner
                if (isRoadNotShip)
                {
                    roadX = scaledVertRoadX;
                    roadY = scaledVertRoadY;
                }
            } else {
                if ((c % 2) != ((r/2) % 2))
                {
                    // "/"
                    hx = halfdeltaX * c;
                    hy = halfdeltaY * (r+1);
                    if (isRoadNotShip)
                    {
                        roadX = scaledUpRoadX;
                        roadY = scaledUpRoadY;
                    } else {
                        hx += (halfdeltaX / 2);
                        hy -= halfdeltaY;
                    }
                } else {
                    // "\"
                    hx = halfdeltaX * c;
                    hy = halfdeltaY * (r-1);  // offset: scaledDownRoadY is bottom of hex, not upper corner
                    if (isRoadNotShip)
                    {
                        roadX = scaledDownRoadX;
                        roadY = scaledDownRoadY;
                    } else {
                        hx += (halfdeltaX / 2);
                        hy += halfdeltaY;
                    }
                }
            }

        }  // if (! isLargeBoard)

        if (isRotated)
        {
            // (cw):  P'=(panelMinBH-y, x)
            int hy1 = hx;
            hx = panelMinBH - hy - deltaX;  // -deltaX is because road poly coords are against hex width/height,
                                        // and the hex image gets similar translation in drawHex.
            hy = hy1;
        }
        if (isScaled)
        {
            hx = scaleToActual(hx);
            hy = scaleToActual(hy);
        }

        g.translate(hx, hy);

        if (hilightStyle == HilightStyle.MOST_RECENT_PLACEMENT)
        {
            if (g instanceof Graphics2D)
                drawPieceMostRecentPlacement((Graphics2D) g, roadX, roadY);
            else
                // fallback; unlikely to need this
                hilightStyle = HilightStyle.HOVER;
        }

        // Fill
        if (pn != -3)
        {
            if (pn == -1)
                g.setColor(Color.WHITE);
            else if (pn == -2)  // pirate ship
            {
                if (hilightStyle == HilightStyle.HOVER)
                    g.setColor(Color.LIGHT_GRAY);
                else
                    g.setColor(Color.BLACK);
            }
            else if (hilightStyle == HilightStyle.HOVER)
                g.setColor(playerInterface.getPlayerColor(pn, true));
            else
                g.setColor(playerInterface.getPlayerColor(pn));

            g.fillPolygon(roadX, roadY, roadX.length);
        }

        // Outline
        if (! ((pn == -1) && (hilightStyle != null)))
        {
            if (hilightStyle == HilightStyle.MOST_RECENT_PLACEMENT)
                g.setColor(Color.white);
            else if (pn == -2)
                if ((portHexCoords != null) && portHexCoords.contains(Integer.valueOf(edgeNum)))
                    g.setColor(Color.white);  // pirate is on a port
                else
                    g.setColor(Color.darkGray);
            else if (pn == -3)
                g.setColor(Color.lightGray);
            else if (hilightStyle == HilightStyle.HOVER)
                g.setColor(playerInterface.getPlayerColor(pn, false));
            else
                g.setColor(Color.black);
        }
        g.drawPolygon(roadX, roadY, roadX.length);

        g.translate(-hx, -hy);
    }

    /**
     * draw a settlement
     */
    private final void drawSettlement
        (Graphics g, int nodeNum, int pn, final HilightStyle hilightStyle, final boolean outlineOnly)
    {
        drawSettlementOrCity(g, nodeNum, pn, hilightStyle, outlineOnly, false);
    }

    /**
     * draw a city
     */
    private final void drawCity
        (Graphics g, int nodeNum, int pn, final HilightStyle hilightStyle)
    {
        drawSettlementOrCity(g, nodeNum, pn, hilightStyle, false, true);
    }

    /**
     * draw a settlement or city; they have the same logic for determining (x,y) from nodeNum.
     * @param hilightStyle  Style of hilight to draw (showing a potential placement etc),
     *           or {@code null} for the usual piece style without hilight
     * @param outlineOnly  If set for settlement, draw only the outline, not the filled polygon.  Ignored when {@code isCity}.
     * @since 1.1.08
     */
    private final void drawSettlementOrCity
        (Graphics g, final int nodeNum, final int pn,
         HilightStyle hilightStyle, final boolean outlineOnly, final boolean isCity)
    {
        final int hx, hy;
        {
            final int[] nodexy = nodeToXY(nodeNum);
            hx = nodexy[0];  hy = nodexy[1];
        }

        // System.out.println("NODEID = "+Integer.toHexString(nodeNum)+" | HEXNUM = "+hexNum);

        g.translate(hx, hy);

        if (isCity)
        {
            if (hilightStyle == HilightStyle.MOST_RECENT_PLACEMENT)
            {
                if (g instanceof Graphics2D)
                    drawPieceMostRecentPlacement((Graphics2D) g, scaledCityX, scaledCityY);
                else
                    // fallback; unlikely to need this
                    hilightStyle = HilightStyle.HOVER;
            }
            else if (hilightStyle != null)
            {
                g.setColor(playerInterface.getPlayerColor(pn, true));
                g.drawPolygon(scaledCityX, scaledCityY, 8);

                // Draw again, slightly offset, for "ghost", since we can't fill and
                // cover up the underlying settlement.
                g.translate(1,1);
                g.drawPolygon(scaledCityX, scaledCityY, 8);
                g.translate(-(hx+1), -(hy+1));

                return;  // <--- Early return: hilight outline only ---
            }

            g.setColor(playerInterface.getPlayerColor(pn));
            g.fillPolygon(scaledCityX, scaledCityY, 8);
            g.setColor((hilightStyle != null) ? Color.white : Color.black);
            g.drawPolygon(scaledCityX, scaledCityY, 8);
        } else {
            // settlement

            final boolean isHilightMostRecent = (hilightStyle == HilightStyle.MOST_RECENT_PLACEMENT);

            if (isHilightMostRecent)
            {
                if (g instanceof Graphics2D)
                    drawPieceMostRecentPlacement((Graphics2D) g, scaledSettlementX, scaledSettlementY);
                else
                    // fallback; unlikely to need this
                    hilightStyle = HilightStyle.HOVER;
            }

            if (! outlineOnly)
            {
                if (hilightStyle == HilightStyle.HOVER)
                    g.setColor(playerInterface.getPlayerColor(pn, true));
                else
                    g.setColor(playerInterface.getPlayerColor(pn));

                g.fillPolygon(scaledSettlementX, scaledSettlementY, 6);
            }

            if (isHilightMostRecent)
                g.setColor(Color.white);
            else if (((hilightStyle != null)) || outlineOnly)
                g.setColor(playerInterface.getPlayerColor(pn, false));
            else
                g.setColor(Color.black);
            g.drawPolygon(scaledSettlementX, scaledSettlementY, 7);
        }

        g.translate(-hx, -hy);
    }

    /**
     * Draw a piece with thick dark border/shadow, for {@link HilightStyle#MOST_RECENT_PLACEMENT}.
     * @param g  Graphics to draw on
     * @param polyX  Piece shape's x-coordinates, scaled to screen
     * @param polyY  Piece shape's y-coordinates, scaled to screen
     * @since 2.7.00
     */
    private void drawPieceMostRecentPlacement(final Graphics2D g, final int[] polyX, final int[] polyY)
    {
        final Stroke prevStroke = g.getStroke();

        g.setStroke
            (new BasicStroke
                ((MOST_RECENT_PLACEMENT_STROKE_WIDTH_OUTER * scaledBoardW) / panelMinBW,
                 BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(Color.DARK_GRAY);
        g.drawPolygon(polyX, polyY, polyX.length);

        g.setStroke
            (new BasicStroke
                ((MOST_RECENT_PLACEMENT_STROKE_WIDTH_INNER * scaledBoardW) / panelMinBW,
                 BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(Color.BLACK);
        g.drawPolygon(polyX, polyY, polyX.length);

        g.setStroke(prevStroke);
    }

    /**
     * Draw a dotted line with some thickness at each of these sea edge coordinates.
     * Unless a color is specified, client must have an active {@link #player}.
     * Calls {@link #drawSeaEdgeLine(Graphics, int)}.
     *<P>
     * Special case: Player 0 is blue. If {@code co == null}, and using the pastel (not classic)
     * hex graphics, player 0's player color is too close to the water hex blue/cyan:
     * Will use dark blue from {@link #HEX_GRAPHICS_SET_SC_PIRI_PATH_COLORS} instead.
     *
     * @param g  Graphics
     * @param co  Color for lines, or {@code null} to use {@link SOCPlayerInterface#getPlayerColor(int)};
     *              if {@code null}, client must have an active {@link #playerNumber}.
     * @param lse  Set of edge coordinates, or null
     * @since 2.0.00
     */
    private void drawSeaEdgeLines(Graphics g, Color co, final Collection<Integer> lse)
    {
        if ((lse == null) || lse.isEmpty())
            return;

        if ((co == null) && (playerNumber == 0) && (hexesGraphicsSetIndex == 0))
            co = HEX_GRAPHICS_SET_SC_PIRI_PATH_COLORS[hexesGraphicsSetIndex];  // avoid cyan-on-light-blue

        final Stroke prevStroke;
        if (g instanceof Graphics2D)
        {
            // Draw as a dotted line with some thickness
            prevStroke = ((Graphics2D) g).getStroke();
            final int hexPartWidth = scaleToActual(halfdeltaX);
            final float[] dash = { hexPartWidth * 0.15f, hexPartWidth * 0.12f };  // length of dash/break
            ((Graphics2D) g).setStroke
                (new BasicStroke
                    ((3 * scaledBoardW) / panelMinBW, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                     1.5f, dash, hexPartWidth * 0.1f));
            if (co == null)
                co = playerInterface.getPlayerColor(playerNumber);
        } else {
            prevStroke = null;
            if (co == null)
                co = playerInterface.getPlayerColor(playerNumber, true);
        }

        g.setColor(co);
        for (Integer edge : lse)
            drawSeaEdgeLine(g, edge);

        if (g instanceof Graphics2D)
            ((Graphics2D) g).setStroke(prevStroke);
    }

    /**
     * For drawing the player's permitted sea edges for ships, draw
     * a line covering the middle 60% of this edge on the board (leaves out 20% on each end).
     * For efficiency, the player color and line stroke must be set before calling this method.
     * @param edge  Edge coordinate
     * @since 2.0.00
     */
    private final void drawSeaEdgeLine(Graphics g, final int edge)
    {
        final int[] enodes = board.getAdjacentNodesToEdge_arr(edge);
        final int[][] nodexy = { nodeToXY(enodes[0]), nodeToXY(enodes[1]) };

        // keep 60% of line length by removing 20% (1/5) from each end
        final int dx = (nodexy[1][0] - nodexy[0][0]) / 5, dy = (nodexy[1][1] - nodexy[0][1]) / 5;

        g.drawLine(nodexy[0][0] + dx, nodexy[0][1] + dy, nodexy[1][0] - dx, nodexy[1][1] - dy);
    }

    /**
     * Draw a pirate fortress, for scenario <tt>SC_PIRI</tt>.
     * If {@link SOCFortress#getStrength()} is 0, won't draw it:
     * A game message from the server will soon replace this piece
     * with a settlement.
     * @param fo  Fortress
     * @param pn  Player number, for fortress color
     * @param isHilight  Use hilight/ghosted player color?
     * @param doTranslate  If true call {@link Graphics#translate(int, int)} before and after drawing,
     *     to account for any ({@link #panelMarginX}, {@link #panelMarginY}) offset.
     *     Intended for calls from not inside the "draw all pieces" loop.
     * @since 2.0.00
     */
    private final void drawFortress
        (Graphics g, final SOCFortress fo, final int pn, final boolean isHilight, final boolean doTranslate)
    {
        final int strength = fo.getStrength();
        if (strength == 0)
            return;

        if (doTranslate)
            g.translate(panelMarginX, panelMarginY);

        g.setColor((isHilight)
            ? playerInterface.getPlayerColor(pn, true)
            : playerInterface.getPlayerColor(pn));

        final int[] nodexy = nodeToXY(fo.getCoordinates());
        g.translate(nodexy[0], nodexy[1]);

        g.fillPolygon(scaledFortressX, scaledFortressY, scaledFortressY.length);
        if (isHilight)
            g.setColor(playerInterface.getPlayerColor(pn, false));
        else
            g.setColor(Color.black);
        g.drawPolygon(scaledFortressX, scaledFortressY, scaledFortressY.length);

        // strength
        final String numstr = Integer.toString(strength);
        int x = -diceNumberCircleFM.stringWidth(numstr) / 2;
        int y = (diceNumberCircleFM.getAscent() - diceNumberCircleFM.getDescent()) * 2 / 3;  // slightly below centered
        g.setFont(diceNumberCircleFont);
        g.drawString(numstr, x, y);

        g.translate(-nodexy[0], -nodexy[1]);

        if (doTranslate)
            g.translate(-panelMarginX, -panelMarginY);
    }

    /**
     * Draw a cloth trade village (used in some scenarios in the large sea board).
     * Villages are drawn yellow unless {@link SOCVillage#getCloth() v.getCloth()}
     * is depleted to 0, those are light gray to show how close the game is to
     * an end-game condition.
     *<P>
     * Same logic for determining (x,y) from {@link SOCPlayingPiece#getCoordinates() v.getCoordinates()}
     * node as {@link #drawSettlementOrCity(Graphics, int, int, HilightStyle, boolean, boolean)}.
     * @param v  Village
     * @since 2.0.00
     */
    private void drawVillage(Graphics g, final SOCVillage v)
    {
        final Color vc = (v.getCloth() > 0) ? Color.YELLOW : Color.LIGHT_GRAY;
        final int[] nodexy = nodeToXY(v.getCoordinates());

        drawMarker(g, nodexy[0], nodexy[1], vc, v.diceNum);
   }

    /**
     * Draw a marker diamond (village/"special" symbol) centered at a final (x,y) coordinate.
     * @param x  Marker center x, must be already scaled and/or rotated
     * @param y  Marker center x, must be already scaled and/or rotated
     * @param color  Color to fill the marker
     * @param val  Value to show on the marker, or -1 for none
     * @since 2.0.00
     */
    private final void drawMarker(Graphics g, final int x, final int y, final Color color, final int val)
    {
        g.translate(x, y);

        g.setColor(color);
        g.fillPolygon(scaledDiamondX, scaledDiamondY, 4);
        g.setColor(Color.black);
        g.drawPolygon(scaledDiamondX, scaledDiamondY, 5);

        // dice # for village
        if (val >= 0)
        {
            final String numstr = Integer.toString(val);
            int sx = -diceNumberCircleFM.stringWidth(numstr) / 2;
            int sy = (diceNumberCircleFM.getAscent() - diceNumberCircleFM.getDescent()) / 2;
            g.setFont(diceNumberCircleFont);
            g.drawString(numstr, sx, sy);
        }

        g.translate(-x, -y);
    }

    /**
     * draw the arrow that shows whose turn it is.
     *
     * @param g Graphics
     * @param pnum Current player number, from {@link SOCGame#getCurrentPlayerNumber()}.
     *             Player positions are clockwise from top-left:
     *           <BR>
     *             For the classic 4-player board:<BR>
     *             0 for top-left, 1 for top-right,
     *             2 for bottom-right, 3 for bottom-left
     *           <BR>
     *             For the classic 6-player board:<BR>
     *             0 for top-left, 1 for top-right, 2 for middle-right,
     *             3 for bottom-right, 4 for bottom-left, 5 for middle-left.
     *           <P>
     *             If &lt; 0, no arrow is drawn.
     * @param diceResult Roll result to show, if rolled, from {@link SOCGame#getCurrentDice()}.
     *                   To show, {@code diceResult} must be at least 2
     *                   and gameState not {@link SOCGame#ROLL_OR_CARD}.
     */
    private final void drawArrow(Graphics g, int pnum, int diceResult)
    {
        if (pnum < 0)
            return;

        int aX, aY, diceX, diceY;  // diceY is always aY + 5
        boolean arrowLeft;

        // Player numbers are clockwise, starting at upper-left.
        // Since we have seats 0-3 in the corners already for 4-player,
        // just change pnum for 6-player.  Seats 0 and 1 need no change.
        // We'll use 4 for middle-right, and 5 for middle-left.
        // Must check game.maxPlayers and not the is6player flag,
        // in case we're on the large sea board (isLargeBoard).

        if (game.maxPlayers > 4)
        {
            switch (pnum)
            {
            case 2:  // middle-right
                pnum = 4;  break;
            case 3:  // lower-right
                pnum = 2;  break;
            case 4:  // lower-left
                pnum = 3;  break;
            }
        }

        switch (pnum)
        {
        case 0:
            // top left
            aY = scaleToActual(5);
            arrowLeft = true;
            break;

        case 1:
            // top right
            aY = scaleToActual(5);
            arrowLeft = false;
            break;

        case 2:
            // bottom right
            aY = scaledPanelH - scaleToActual(42);
            arrowLeft = false;
            break;

        default:  // 3: (Default prevents compiler var-not-init errors)
            // bottom left
            aY = scaledPanelH - scaleToActual(42);
            arrowLeft = true;
            break;

        case 4:
            // middle right
            aY = scaledPanelH / 2 - scaleToActual(12);
            arrowLeft = false;
            break;

        case 5:
            // middle left
            aY = scaledPanelH / 2 - scaleToActual(12);
            arrowLeft = true;
            break;
        }

        aX = (arrowLeft) ? scaleToActual(3) : scaledPanelW - scaleToActual(40);
        diceX = (arrowLeft) ? scaleToActual(12) : scaledPanelW - scaleToActual(39);
        diceY = aY + scaleToActual(6);

        /**
         * Draw Arrow
         */
        final int gameState = game.getGameState();
        int[] scArrowX;
        if (arrowLeft)
            scArrowX = scaledArrowXL;
        else
            scArrowX = scaledArrowXR;

        g.translate(aX, aY);

        if (! (game.isSpecialBuilding() || (gameState == SOCGame.OVER)))
            g.setColor(ARROW_COLOR);
        else
            g.setColor(ARROW_COLOR_PLACING);
        g.fillPolygon(scArrowX, scaledArrowY, scArrowX.length);
        g.setColor(Color.BLACK);
        g.drawPolygon(scArrowX, scaledArrowY, scArrowX.length);

        g.translate(-aX, -aY);

        /**
         * Draw Dice result number
         */
        if ((diceResult >= 2) && (gameState != SOCGame.ROLL_OR_CARD) && (gameState != SOCGame.SPECIAL_BUILDING))
        {
            final int boxSize = (isScaled) ? scaleToActual(DICE_SZ) : DICE_SZ;  // bounding box for dice-number digit(s)
            final int fontSize = 4 * boxSize / 5;  // 80%

            boolean needHeight = false;
            if ((arrowDiceFont == null) || (arrowDiceFont.getSize() != fontSize))
            {
                arrowDiceFont = new Font("Dialog", Font.BOLD, fontSize);
                needHeight = true;
            }
            final Font prevFont = g.getFont();
            g.setFont(arrowDiceFont);
            if (needHeight)
            {
                if (g instanceof Graphics2D)
                {
                    final TextLayout tl
                        = new TextLayout("1234567890", arrowDiceFont, ((Graphics2D) g).getFontRenderContext());
                    arrowDiceHeight = (int) tl.getBounds().getHeight();
                } else {
                    arrowDiceHeight = g.getFontMetrics().getAscent();  // usually taller than actual height of digits
                }
            }
            final FontMetrics fm = g.getFontMetrics();
            diceY += boxSize;  // text baseline at bottom of box; will move up (-y) to vertically center

            final String dstr = Integer.toString(diceResult);
            final int diceW = fm.stringWidth(dstr);
            g.drawString(dstr, diceX + (boxSize - diceW) / 2, diceY - (boxSize - arrowDiceHeight) / 2);
            g.setFont(prevFont);
        }
    }

    // TODO maybe move to socboard? otherwise refactor?
    /**
     * Hex numbers of start of each row of hexes in the board coordinates.
     * Does not apply to v3 encoding ({@link SOCBoardLarge}).
     * @since 1.1.08
     */
    private static final int[] ROW_START_HEXNUM = { 0, 4, 9, 15, 22, 28, 33 };

    /**
     * for the 6-player board (if {@link #is6player}), draw the ring of surrounding water/ports.
     * This is outside the coordinate system, and doesn't have hex numbers,
     * and so can't be drawn in the classic 4-player drawHex loop.
     * @since 1.1.08
     * @see #drawPorts_LargeBoard(Graphics)
     */
    private final void drawPortsRing(Graphics g)
    {
        int hnum, hx, hy, ptype;

        /**
         * First, draw the ring of water hexes.
         * Then we'll overlay ports on them.
         */

        // To left of each of hex numbers: 0, 4, 9, 15, 22, 28, 33.
        for (int r = 0; r <= 6; ++r)
        {
            hnum = ROW_START_HEXNUM[r];

            // Water/port to left of hex row:
            hx = hexX[hnum] - deltaX;
            hy = hexY[hnum];
            drawHex(g, hx, hy, SOCBoard.WATER_HEX, -1, -1);

            // Water/port to right of hex row:
            --hnum;  // is now rightmost hexnum of previous row
            if (hnum < 0)
                hnum = hexX.length - 1;  // wrap around
            hx = hexX[hnum];  // since the rightmost hexnum isn't within 6pl coord,
            hy = hexY[hnum];  // its (x,y) is right where we want to draw.
            drawHex(g, hx, hy, SOCBoard.WATER_HEX, -1, -1);
        }

        hx = hexX[0] - halfdeltaX;
        hy = hexY[0] - deltaY;        // Above top row
        final int hy2 = hexY[33] + deltaY;  // Below bottom row

        for (int c = 0, nodeCoord = 0x07;
             c < 4;
             ++c, nodeCoord += 0x22, hx += deltaX)
        {
            ptype = board.getPortTypeFromNodeCoord(nodeCoord);
            if (ptype == -1)
                drawHex(g, hx, hy, SOCBoard.WATER_HEX, -1, -1);

            // bottom-row coords swap the hex digits of top-row coords.
            ptype = board.getPortTypeFromNodeCoord((nodeCoord >> 4) | ((nodeCoord & 0x0F) << 4));
            if (ptype == -1)
                drawHex(g, hx, hy2, SOCBoard.WATER_HEX, -1, -1);
        }

        /**
         * Draw each port
         * Similar code to drawPorts_LargeBoard.
         */
        final int[] portsLayout = board.getPortsLayout();
        if (portsLayout == null)
            return;  // <--- Too early: board not created & sent from server ---

        final int[] portsFacing = board.getPortsFacing();
        final int[] portsEdges = board.getPortsEdges();
        for (int i = board.getPortsCount()-1; i >= 0; --i)
        {
            // The (x,y) graphic location for this port isn't in hexX/hexY, because
            // the port is just beyond the coordinate system.  Get its facing land hex
            // and base (x,y) off that.
            final int landFacing = portsFacing[i];
            final int landHexCoord = board.getAdjacentHexToEdge(portsEdges[i], landFacing);
            hnum = board.getHexNumFromCoord(landHexCoord);
            // now move 1 hex "backwards" from hnum
            hx = hexX[hnum] - DELTAX_FACING[landFacing];
            hy = hexY[hnum] - DELTAY_FACING[landFacing];

            drawHex(g, hx, hy, portsLayout[i], landFacing, -1);
        }
    }

    /**
     * Draw the ports for the {@link #isLargeBoard large board}.
     * These can occur anywhere on the board.
     * @since 2.0.00
     * @see #drawPortsRing(Graphics)
     */
    private final void drawPorts_LargeBoard(Graphics g)
    {
        int px, py;

        /**
         * Draw each port
         * Similar code to drawPortsRing.
         */
        final int[] portsLayout = board.getPortsLayout();
        if (portsLayout == null)
            return;  // <--- Too early: board not created & sent from server ---

        if (portHexCoords == null)
            portHexCoords = new HashSet<Integer>();
        else
            portHexCoords.clear();  // in case ports have changed (SC_FTRI does that)

        final int[] portsFacing = board.getPortsFacing();
        final int[] portsEdges = board.getPortsEdges();
        for (int i = board.getPortsCount()-1; i >= 0; --i)
        {
            final int edge = portsEdges[i];
            if (edge < 0)
                continue;  // SOCBoardLarge port isn't currently placed on the board: skip it

            // For each port, get its facing land hex and base (x,y) off that. Port hex
            // is drawn on the sea side, not land side, of the edge but some ports at
            // the margins of the board might have a sea side outside the coordinate system.
            // So instead we calculate ports' landHexCoord (which will always be inside the
            // system) and move 1 hex away from the facing direction.
            final int landFacing = portsFacing[i];
            final int landHexCoord = board.getAdjacentHexToEdge(edge, landFacing);
            px = halfdeltaX * ((landHexCoord & 0xFF) - 1);
            py = halfdeltaY * (landHexCoord >> 8);
            // now move 1 hex "backwards" from that hex's upper-left corner
            px -= DELTAX_FACING[landFacing];
            py -= DELTAY_FACING[landFacing];

            drawHex(g, px, py, portsLayout[i], landFacing, -1);

            // portHexCoords wants sea hex, not land hex
            int seaFacing = 3 + landFacing;
            if (seaFacing > 6)
                seaFacing -= 6;
            final int seaHex = board.getAdjacentHexToEdge(edge, seaFacing);
            if (seaHex > 0)
                portHexCoords.add(Integer.valueOf(seaHex));
        }
    }

    /**
     * Draw the whole board, including pieces and tooltip ({@link #hilight}, {@link #hoverTip}) if applicable.
     * The basic board without pieces is drawn just once, then buffered.
     * If the board layout changes (at start of game, for example),
     * call {@link #flushBoardLayoutAndRepaint()} to clear the buffered copy.
     *
     * @see #drawBoardEmpty(Graphics)
     */
    @SuppressWarnings("fallthrough")
    private void drawBoard(Graphics g)
    {
        final GraphicsConfiguration gconf = playerInterface.getGraphicsConfiguration();
            // use PI instead of this.getGraphicsConfiguration(), which is null early in setup
        VolatileImage ebb = emptyBoardBuffer;
            // Local copy, in case field becomes null in another thread
            // during drawBoardEmpty or other calls. (this has happened)

        do
        {
            final int ebbValidStatus = (ebb == null) ? -1 : ebb.validate(gconf);
            if (scaledMissedImage || (ebb == null) || (ebbValidStatus != VolatileImage.IMAGE_OK))
            {
                if ((ebb == null) || (ebbValidStatus == VolatileImage.IMAGE_INCOMPATIBLE))
                {
                    ebb = gconf.createCompatibleVolatileImage(scaledPanelW, scaledPanelH, Transparency.OPAQUE);
                    emptyBoardBuffer = ebb;
                }

                drawnEmptyAt = System.currentTimeMillis();
                scaledMissedImage = false;  // drawBoardEmpty, drawHex will set this flag if missed
                Graphics2D ebbG = ebb.createGraphics();
                drawBoardEmpty(ebbG);
                ebbG.dispose();

                if (scaledMissedImage && (scaledAt != 0)
                    && (RESCALE_MAX_RETRY_MS < (drawnEmptyAt - scaledAt)))  // eventually give up scaling it
                    scaledMissedImage = false;
            }
        } while (ebb.contentsLost());

        // draw ebb from local variable, not emptyBoardBuffer field, to avoid occasional NPE
        g.setPaintMode();
        g.drawImage(ebb, 0, 0, this);

        // ask for antialiasing if available
        if (g instanceof Graphics2D)
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        final boolean xlat = (panelMarginX != 0) || (panelMarginY != 0);
        if (xlat)
            g.translate(panelMarginX, panelMarginY);

        final int gameState = game.getGameState();

        final SOCPlayingPiece lpp = latestPiecePlacement;  // local ref, in case updated by another thread
        if (lpp != null)
        {
            // draw that early, so its shadow is "below" other drawn pieces

            final int pn = lpp.getPlayerNumber(), co = lpp.getCoordinates();
            switch (lpp.getType())
            {
            case SOCPlayingPiece.ROAD:
                drawRoadOrShip(g, co, pn, HilightStyle.MOST_RECENT_PLACEMENT, true, false);
                break;
            case SOCPlayingPiece.SHIP:
                if (! game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
                    drawRoadOrShip(g, co, pn, HilightStyle.MOST_RECENT_PLACEMENT, false, false);
                    // for SC_PIRI, is easier to draw with rest of ships in case has been upgraded to warship
                break;
            case SOCPlayingPiece.SETTLEMENT:
                drawSettlement(g, co, pn, HilightStyle.MOST_RECENT_PLACEMENT, false);
                break;
            case SOCPlayingPiece.CITY:
                drawCity(g, co, pn, HilightStyle.MOST_RECENT_PLACEMENT);
                break;
            default:
                // shouldn't happen; no code to draw other types, so ignore it
            }
        }

        if (board.getRobberHex() != -1)
        {
            drawRobber(g, board.getRobberHex(), (gameState != SOCGame.PLACING_ROBBER), true);
        }
        if (board.getPreviousRobberHex() != -1)
        {
            drawRobber(g, board.getPreviousRobberHex(), (gameState != SOCGame.PLACING_ROBBER), false);
        }

        if (isLargeBoard)
        {
            int hex = ((SOCBoardLarge) board).getPirateHex();
            if (hex > 0)
            {
                drawRoadOrShip
                    (g, hex, -2, (gameState == SOCGame.PLACING_PIRATE) ? HilightStyle.HOVER : null, false, false);
            }

            hex = ((SOCBoardLarge) board).getPreviousPirateHex();
            if (hex > 0)
            {
                drawRoadOrShip
                    (g, hex, -3, (gameState == SOCGame.PLACING_PIRATE) ? HilightStyle.HOVER : null, false, false);
            }
        }

        /**
         * draw the roads and ships
         */
        if (! game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
        {
            for (SOCRoutePiece rs : board.getRoadsAndShips())
            {
                if (rs == lpp)
                    continue;
                drawRoadOrShip(g, rs.getCoordinates(), rs.getPlayerNumber(), null, ! (rs instanceof SOCShip), false);
            }
        } else {
            for (int pn = 0; pn < game.maxPlayers; ++pn)
            {
                final SOCPlayer pl = game.getPlayer(pn);

                // count warships here, for efficiency, instead of calling SOCGame.isShipWarship for each one
                int numWarships = pl.getNumWarships();
                for (SOCRoutePiece rs : pl.getRoadsAndShips())
                {
                    final boolean isShip = (rs instanceof SOCShip);
                    if ((! isShip) && (rs == lpp))
                        continue;
                    final boolean isWarship = isShip && (numWarships > 0);
                    drawRoadOrShip
                        (g, rs.getCoordinates(), pn, (rs == lpp) ? HilightStyle.MOST_RECENT_PLACEMENT : null,
                         ! isShip, isWarship);
                        // easier to draw lpp here if isWarship
                    if (isWarship)
                        --numWarships;  // this works since warships begin with player's 1st-placed ship in getRoads()
                }

                /**
                 * draw the player's fortress, if any
                 */
                SOCFortress fo = pl.getFortress();
                if (fo != null)
                    drawFortress(g, fo, pn, false, false);
            }
        }

        /**
         * draw the settlements
         */
        for (SOCSettlement s : board.getSettlements())
        {
            if (s == lpp)
                continue;
            drawSettlement(g, s.getCoordinates(), s.getPlayerNumber(), null, false);
        }

        /**
         * draw the cities
         */
        for (SOCCity c : board.getCities())
        {
            if (c == lpp)
                continue;
            drawCity(g, c.getCoordinates(), c.getPlayerNumber(), null);
        }

        if (xlat)
            g.translate(-panelMarginX, -panelMarginY);

        /**
         * draw the current-player arrow after ("above") pieces,
         * but below any hilighted piece, in case of overlap at
         * edge of board. More likely on 6-player board for the
         * two players whose handpanels are vertically centered.
         */
        if (gameState != SOCGame.NEW)
        {
            int cpn = game.getCurrentPlayerNumber();
            if ((cpn < 0) && (gameState >= SOCGame.OVER))
            {
                SOCPlayer wp = game.getPlayerWithWin();
                if (wp != null)
                    cpn = wp.getPlayerNumber();
            }
            drawArrow(g, cpn, game.getCurrentDice());
        }

        if (player != null)
        {
        if (xlat)
            g.translate(panelMarginX, panelMarginY);

        /**
         * Draw the hilight when in interactive mode;
         * No hilight when null player (before game started).
         * The "hovering" road/settlement/city are separately painted
         * in {@link soc.client.SOCBoardPanel.BoardToolTip#paint()}.
         */
        switch (mode)
        {
        case MOVE_SHIP:
            if (moveShip_fromEdge != 0)
                drawRoadOrShip(g, moveShip_fromEdge, -1, null, false, moveShip_isWarship);
            // fall through to road modes, to draw new location (hilight)

        case PLACE_ROAD:
        case PLACE_INIT_ROAD:
        case PLACE_FREE_ROAD_OR_SHIP:

            if (hilight != 0)
            {
                drawRoadOrShip
                    (g, hilight, playerNumber, HilightStyle.HOVER,
                     ! hilightIsShip, (moveShip_isWarship && (moveShip_fromEdge != 0)));
            }
            break;

        case PLACE_SETTLEMENT:
        case PLACE_INIT_SETTLEMENT:

            if (hilight > 0)
            {
                drawSettlement(g, hilight, playerNumber, HilightStyle.HOVER, false);
            }
            break;

        case PLACE_CITY:

            if (hilight > 0)
            {
                drawCity(g, hilight, playerNumber, HilightStyle.HOVER);
            }
            break;

        case PLACE_SHIP:

            if (hilight > 0)
            {
                drawRoadOrShip(g, hilight, playerNumber, HilightStyle.HOVER, false, false);
            }
            break;

        case CONSIDER_LM_SETTLEMENT:
        case CONSIDER_LT_SETTLEMENT:

            if (hilight > 0)
            {
                drawSettlement(g, hilight, otherPlayer.getPlayerNumber(), HilightStyle.HOVER, false);
            }
            break;

        case CONSIDER_LM_ROAD:
        case CONSIDER_LT_ROAD:

            if (hilight != 0)
            {
                drawRoadOrShip(g, hilight, otherPlayer.getPlayerNumber(), null, true, false);
            }
            break;

        case CONSIDER_LM_SHIP:
        case CONSIDER_LT_SHIP:

            if (hilight != 0)
                drawRoadOrShip(g, hilight, otherPlayer.getPlayerNumber(), null, false, false);
            break;

        case CONSIDER_LM_CITY:
        case CONSIDER_LT_CITY:

            if (hilight > 0)
            {
                drawCity(g, hilight, otherPlayer.getPlayerNumber(), HilightStyle.HOVER);
            }
            break;

        case PLACE_ROBBER:

            if (hilight > 0)
            {
                drawRobber(g, hilight, true, true);
            }
            break;

        case PLACE_PIRATE:
            if (hilight > 0)
            {
                drawRoadOrShip(g, hilight, -2, null, false, false);
            }
            break;

        case SC_FTRI_PLACE_PORT:
            drawBoard_SC_FTRI_placePort(g);
            break;

        }  // switch

        if (xlat)
            g.translate(-panelMarginX, -panelMarginY);

        }  // if (player != null)

        if (superText1 != null)
        {
            drawSuperText(g);
        }
        if (superTextTop != null)
        {
            drawSuperTextTop(g);
        }
    }

    /**
     * Scenario game option {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI}: In board mode {@link #SC_FTRI_PLACE_PORT},
     * draw the possible coastal edges where the port can be placed, and if the {@link #hilight} cursor is at
     * such an edge, draw the port semi-transparently and a solid hilight line at the edge.
     * @since 2.0.00
     */
    private final void drawBoard_SC_FTRI_placePort(Graphics g)
    {
        drawSeaEdgeLines(g, Color.WHITE, player.getPortMovePotentialLocations(true));

        if (hilight == 0)
            return;

        // Draw the placing port semi-transparently if graphics support it.
        // Draw hilight line with some thickness if possible.

        int edge = hilight;
        if (edge == -1)
            edge = 0;

        final SOCInventoryItem portItem = game.getPlacingItem();
        if (portItem != null)
        {
            // draw the port; similar code to drawPorts_largeBoard

            final int landFacing = ((SOCBoardLarge) board).getPortFacingFromEdge(edge);
            final int landHexCoord = board.getAdjacentHexToEdge(edge, landFacing);
            int px = halfdeltaX * ((landHexCoord & 0xFF) - 1);
            int py = halfdeltaY * (landHexCoord >> 8);
            // now move 1 hex "backwards" from that hex's upper-left corner
            px -= DELTAX_FACING[landFacing];
            py -= DELTAY_FACING[landFacing];

            final Composite prevComposite;
            if (g instanceof Graphics2D)
            {
                prevComposite = ((Graphics2D) g).getComposite();
                ((Graphics2D) g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            } else {
                prevComposite = null;
            }
            drawHex(g, px, py, -portItem.itype, landFacing, -1);
            if (prevComposite != null)
                ((Graphics2D) g).setComposite(prevComposite);
        }

        final Stroke prevStroke;
        if (g instanceof Graphics2D)
        {
            prevStroke = ((Graphics2D) g).getStroke();
            ((Graphics2D) g).setStroke(new BasicStroke(2.5f));
        } else {
            prevStroke = null;
        }

        g.setColor(Color.WHITE);
        drawSeaEdgeLine(g, edge);

        if (prevStroke != null)
            ((Graphics2D) g).setStroke(prevStroke);
    }

    /**
     * Draw the whole board (water, hexes, ports, numbers) but no placed pieces.
     * This is drawn once, then stored.
     * If the board layout changes (at start of game or
     * {@link SOCBoardLarge#FOG_HEX fog hex} reveal, for example),
     * call {@link #flushBoardLayoutAndRepaint()} to clear the buffered copy.
     *<P>
     * For scenario option {@link SOCGameOptionSet#K_SC_CLVI _SC_CLVI},
     * <tt>drawBoardEmpty</tt> draws the board's {@link SOCVillage}s.
     *<P>
     * If {@link #panelMarginX} or {@link #panelMarginY} != 0, do not translate {@code g}
     * before calling. This method will internally translate.
     *
     * @param g Graphics, typically from {@link #emptyBoardBuffer}
     * @since 1.1.08
     * @see SOCPlayerInterface#updateAtNewBoard()
     */
    private void drawBoardEmpty(Graphics g)
    {
        if (g instanceof Graphics2D)
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Set<Integer> landHexShow;
        final int SC_6;
        if (debugShowPotentials[8] && isLargeBoard)
        {
            landHexShow = ((SOCBoardLarge) board).getLandHexCoordsSet();
            SC_6 = scaleToActual(6);
        } else {
            landHexShow = null;  // almost always null, unless debugging large board
            SC_6 = 0;            // unused unless debugging large board
        }

        g.setPaintMode();

        g.setColor(getBackground());
        g.fillRect(0, 0, scaledPanelW, scaledPanelH);

        if (scaledPorts[0] == null)
        {
            // Check if port graphics are ready.  This probably isn't needed, because
            // doLayout already called position/sizing methods which call rescaleBoard.
            renderPortImages();
        }

        final boolean xlat = (panelMarginX != 0) || (panelMarginY != 0);
        if (xlat)
            g.translate(panelMarginX, panelMarginY);

        // Draw hexes:
        // drawHex will set scaledMissedImage if missed.
        if (! isLargeBoard)
        {
            // Draw water hexes to all edges of the panel;
            // these are outside the board coordinate system.

            final int hxMin, hxMax, hyMin, hyMax;
            boolean isRowOffset;
            if (isRotated)
            {
                // Swap X, Y because hex-coords are rotated, screen-coords aren't.
                // Nonzero panelMarginY can cause blank areas visually above board
                // ("to left of" in board coordinates).
                // Nonzero panelMarginX can cause blank areas visually to left of board
                // ("below" in board coordinates).
                hxMin =
                    (panelMarginY <= 0) ? 0 : deltaX * (int) Math.floor(-scaleFromActual(panelMarginY) / (float) deltaX);
                hxMax = scaleFromActual(getHeight());
                final int nTopRows =  // at top in board coords; at right in screen coords
                    (scaledBoardW == scaledPanelW)
                    ? 1
                    : 1 + (scaleFromActual(scaledPanelW + HEXY_OFF_SLOPE_HEIGHT - scaledBoardW) / deltaY);
                hyMin = -deltaY * nTopRows;
                hyMax = panelMinBH +
                    ((panelMarginX <= 0) ? 0 : scaleFromActual(panelMarginX));
                isRowOffset = (1 == (nTopRows % 2));
            } else {
                hxMin =
                    (panelMarginX <= 0) ? 0 : deltaX * (int) Math.floor(-scaleFromActual(panelMarginX) / (float) deltaX);
                hxMax =
                    (scaledBoardW == scaledPanelW) ? panelMinBW : scaleFromActual(scaledPanelW - panelMarginX);
                final int nTopRows =
                    (panelMarginY <= 0)
                    ? 1
                    : 1 + (int) Math.ceil(panelMarginY / (float) deltaY);
                hyMin = -deltaY * nTopRows;
                hyMax = scaleFromActual(scaledPanelH);  // often same as panelMinBH
                isRowOffset = (0 == (nTopRows % 2));
            }

            for (int hy = hyMin; hy < hyMax; hy += deltaY, isRowOffset = ! isRowOffset)
            {
                int hx = hxMin;
                if (isRowOffset)
                    hx -= halfdeltaX;
                for (; hx < hxMax; hx += deltaX)
                    if ((hx < 0) || (hx >= panelMinBW) || (hy >= panelMinBH) || (0 == findHex(hx, hy)))
                        drawHex(g, hx, hy, SOCBoard.WATER_HEX, -1, -1);
            }

            // Normal board draws all 37 hexes.
            // The 6-player board skips the rightmost row (hexes 7D-DD-D7).

            if (is6player)
                drawPortsRing(g);

            for (int i = 0; i < hexX.length; i++)
                if ((inactiveHexNums == null) || ! inactiveHexNums[i])
                    drawHex(g, i);

        } else {
            // Large Board has a rectangular array of hexes.
            // (r,c) are board coordinates.
            // (x,y) are unscaled board-pixel coordinates.

            /** Panel width in board coordinates; because of panelMarginX, might be > unscaledBoardW */
            final int unscaledPanelW = scaleFromActual(scaledPanelW);

            // Top margin rows:

            final int bMarginX = scaleFromActual(panelMarginX),
                      bMarginYAndHex = scaleFromActual(panelMarginY) + HEXHEIGHT,
                      marginXNumHex = (bMarginX + deltaX - 1) / deltaX;

            // Top margin ("row -1"): Easier to draw it separately than deal with row coord -1 in main loop.
            // The initial x-coord formula aligns just enough water hexes to cover -bMarginX.
            // Then, draw more margin rows above it until none of the row would be visible.
            // Those other rows are needed only when bMarginY is +1 or more "VS" units
            // (1/4 or more of row height).
            for (int y = -halfdeltaY, xOffsetFlag = 0;
                 -y <= bMarginYAndHex;
                 y -= deltaY, xOffsetFlag = 1 - xOffsetFlag)
                for (int x = -(deltaX * marginXNumHex) - (halfdeltaX * xOffsetFlag);
                     x < unscaledPanelW;
                     x += deltaX)
                    drawHex(g, x, y, SOCBoard.WATER_HEX, -1, -1);

            // In-bounds board hexes and bottom margin:
            final int bw = board.getBoardWidth(), bh = board.getBoardHeight();
            for (int r = 1, y = halfdeltaY;
                 r < bh || y < (scaledPanelH + HEXY_OFF_SLOPE_HEIGHT);
                 r += 2, y += deltaY)
            {
                final int rshift = (r << 8);
                int c, x;
                if (((r/2) % 2) == 1)
                {
                    c = 1;  // odd hex rows start at 1
                    x = 0;
                } else {
                    c = 2;  // top row, even rows start at 2
                    x = halfdeltaX;
                }

                if ((panelMarginX != 0) || (x != 0))
                {
                    // If board is narrow or row doesn't start at left side of panel, fill margin with water.
                    // xleft drawn at >= 0 after g.translate for panelMarginX
                    for (int xleft = x; xleft > -(panelMarginX + deltaX); xleft -= deltaX)
                        drawHex(g, xleft, y, SOCBoard.WATER_HEX, -1, -1);
                }

                for (; c < bw; c += 2, x += deltaX)
                {
                    final int hexCoord = rshift | c;
                    final int hexType = (r < bh) ? board.getHexTypeFromCoord(hexCoord) : SOCBoard.WATER_HEX;
                    drawHex(g, x, y, hexType, -1, hexCoord);
                    if ((landHexShow != null) && landHexShow.contains(Integer.valueOf(hexCoord)))
                    {
                       g.setColor(Color.RED);
                       g.drawRoundRect
                           (scaleToActual(x + (halfdeltaX / 2)),
                            scaleToActual(y + ((halfdeltaY + HEXY_OFF_SLOPE_HEIGHT) / 2) + 1),
                            scaleToActual(halfdeltaX), scaleToActual(halfdeltaY + 1),
                            SC_6, SC_6);
                    }
                }

                // If board is narrower than panel, fill in with water
                int xmax = unscaledPanelW - 1;
                if (panelMarginX < 0)
                    xmax -= panelMarginX;
                while (x < xmax)
                {
                    final int hexCoord = rshift | c;
                    drawHex(g, x, y, SOCBoard.WATER_HEX, -1, hexCoord);
                    c += 2;
                    x += deltaX;
                }
            }

            // All ports
            drawPorts_LargeBoard(g);

            // For scenario _SC_PIRI, check for the Pirate Path and Lone Settlement locations.
            // Draw path only if the pirate fleet is still on the board
            // Draw our player's permitted sea edges for ships, if restricted
            {
                final int[] ppath = ((SOCBoardLarge) board).getAddedLayoutPart("PP");
                if ((ppath != null) && (0 != ((SOCBoardLarge) board).getPirateHex()))
                    drawBoardEmpty_drawPiratePath(g, ppath);

                final int[] ls = ((SOCBoardLarge) board).getAddedLayoutPart("LS");
                if (ls != null)
                {
                    for (int pn = 0; pn < ls.length; ++pn)
                        if (ls[pn] != 0)
                            drawSettlement(g, ls[pn], pn, null, true);
                }

                final HashSet<Integer> lse = (player != null) ? player.getRestrictedLegalShips() : null;
                if (lse != null)
                    drawSeaEdgeLines(g, null, lse);
            }

            // For scenario _SC_FTRI, draw markers at the SVP edges and dev card edges (added layout parts "CE", "VE")
            if (((SOCBoardLarge) board).hasSpecialEdges())
                drawBoardEmpty_specialEdges(g);

            // For scenario _SC_CLVI, draw the cloth villages
            HashMap<Integer, SOCVillage> villages = ((SOCBoardLarge) board).getVillages();
            if (villages != null)
            {
                Iterator<SOCVillage> villIter = villages.values().iterator();
                while (villIter.hasNext())
                    drawVillage(g, villIter.next());
            }

            // For scenario _SC_WOND, draw special nodes (layout parts N1, N2, N3)
            if (game.isGameOptionSet(SOCGameOptionSet.K_SC_WOND))
            {
                drawBoardEmpty_specialNodes(g, "N1", new Color(180, 90, 40));   // brown
                drawBoardEmpty_specialNodes(g, "N2", new Color(120, 40, 120));  // violet
                drawBoardEmpty_specialNodes(g, "N3", Color.RED);
            }

            // check debugShowPotentials[0 - 9]
            drawBoardEmpty_drawDebugShowPotentials(g);
        }

        if (xlat)
            g.translate(-panelMarginX, -panelMarginY);

        if (scaledMissedImage)
        {
            // With recent board resize, one or more rescaled images still hasn't
            // been completed after 7 seconds.  We've asked for a new scaled copy
            // of this image.  Repaint now, and repaint 3 seconds later.
            // (The delay gives time for the new scaling to complete.)
            scaledAt = System.currentTimeMillis();
            repaint();
            new DelayedRepaint(this).start();
        }
    }

    /**
     * For the {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI} game scenario on {@link SOCBoardLarge},
     * draw the path that the pirate fleet takes around the board.
     * @param ppath  Path of hex coordinates
     */
    private final void drawBoardEmpty_drawPiratePath(Graphics g, final int[] ppath)
    {
        int hc = ppath[ppath.length - 1];
        int r = hc >> 8, c = hc & 0xFF;
        int yprev = scaleToActual(r * halfdeltaY + HALF_HEXHEIGHT),  // HALF_HEXHEIGHT == halfdeltaY + 9
            xprev = scaleToActual(c * halfdeltaX);

        Stroke prevStroke;
        if (g instanceof Graphics2D)
        {
            // Draw as a dotted line with some thickness
            prevStroke = ((Graphics2D) g).getStroke();
            final int hexPartWidth = scaleToActual(halfdeltaX);
            final float[] dash = { hexPartWidth * 0.2f, hexPartWidth * 0.3f };  // length of dash/break
            ((Graphics2D) g).setStroke
                (new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 2.5f, dash, 0.8f));
        } else {
            prevStroke = null;
        }

        g.setColor(HEX_GRAPHICS_SET_SC_PIRI_PATH_COLORS[hexesGraphicsSetIndex]);
        for (int i = 0; i < ppath.length; ++i)
        {
            hc = ppath[i];
            r = hc >> 8; c = hc & 0xFF;
            int y = scaleToActual(r * halfdeltaY + HALF_HEXHEIGHT),
                x = scaleToActual(c * halfdeltaX);
            g.drawLine(xprev, yprev, x, y);
            xprev = x; yprev = y;
        }

        if (g instanceof Graphics2D)
            ((Graphics2D) g).setStroke(prevStroke);
    }

    /**
     * For the {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI} game scenario on {@link SOCBoardLarge},
     * draw markers at all Special Edges for the players to reach and be rewarded.
     *<P>
     * Each marker's color will be determined by its edge's type, such as {@link SOCBoardLarge#SPECIAL_EDGE_DEV_CARD}.
     * Unknown types will be drawn gray.
     * @since 2.0.00
     */
    private final void drawBoardEmpty_specialEdges(final Graphics g)
    {
        Iterator<Map.Entry<Integer, Integer>> seIter = ((SOCBoardLarge) board).getSpecialEdges();
        while (seIter.hasNext())
        {
            final Map.Entry<Integer, Integer> entry = seIter.next();
            final Color mc;  // marker color
            switch (entry.getValue())
            {
            case SOCBoardLarge.SPECIAL_EDGE_DEV_CARD:
                mc = Color.YELLOW;
                break;

            case SOCBoardLarge.SPECIAL_EDGE_SVP:
                mc = Color.GREEN;
                break;

            default:
                mc = Color.LIGHT_GRAY;
            }

            final int edge = entry.getKey();
            int x = edge & 0xFF, y = edge >> 8;  // col, row to calculate x, y
            final boolean edgeNotVertical = ((y % 2) == 0);
            x = x * halfdeltaX;
            y = y * halfdeltaY + HALF_HEXHEIGHT;

            // If the edge is along the top or bottom of a hex (not a vertical edge),
            // its center is slightly offset from the grid
            if (edgeNotVertical)
                x += (halfdeltaX / 2);

            x = scaleToActual(x);
            y = scaleToActual(y);

            drawMarker(g, x, y, mc, -1);
        }
    }

    /**
     * For a game scenario on {@link SOCBoardLarge}, draw markers at a set of Special Nodes for the players
     * to reach and be rewarded, retrieved with {@link SOCBoardLarge#getAddedLayoutPart(String)}.
     *
     * @param partKey  Key string for the added layout part, for {@link SOCBoardLarge#getAddedLayoutPart(String)}
     * @param color  Color to fill the markers
     * @since 2.0.00
     */
    private final void drawBoardEmpty_specialNodes(final Graphics g, final String partKey, final Color color)
    {
        final int[] nodes = ((SOCBoardLarge) board).getAddedLayoutPart(partKey);
        if (nodes == null)
            return;

        for (final int node : nodes)
        {
            final int[] nodexy = nodeToXY(node);
            drawMarker(g, nodexy[0], nodexy[1], color, -1);
        }
    }

    /**
     * If any bit in {@link #debugShowPotentials}[] is set, besides 8,
     * draw it on the board. Shows potential/legal placement locations for
     * client {@link #player} if playing, or player 0 if observing.
     * (<tt>debugShowPotentials[8]</tt> is drawn in the per-hex loop
     *  of {@link #drawBoardEmpty(Graphics)}).
     *<P>
     * <b>Note:</b> Currently implemented only for {@link #isLargeBoard} only for now (TODO).
     * @since 2.0.00
     * @throws IllegalStateException if ! isLargeBoard; temporary restriction
     */
    private void drawBoardEmpty_drawDebugShowPotentials(Graphics g)
        throws IllegalStateException
    {
        if (! isLargeBoard)
            throw new IllegalStateException("not supported yet");

        final SOCPlayer pl = (player != null) ? player : game.getPlayer(0);
        final int bw = board.getBoardWidth();

        if (debugShowPotentials[2])
        {
            final int bh = board.getBoardHeight();
            int w = scaleToActual(halfdeltaX * bw),
                h = scaleToActual(halfdeltaY * bh + HEXY_OFF_SLOPE_HEIGHT);
            int y = scaleToActual(halfdeltaY);
            g.setColor(Color.YELLOW);
            g.drawRect(0, y, w, h);
            g.drawRect(1, y + 1, w - 2, h - 2);
        }

        // Iterate over all nodes for:
        // 1,5: settlements: squares (Legal yellow, potential green)
        // 2,6: cities: larger squares (potential green; there is no legal set)
        // 9: nodes on land: red round rects

        final int SC_3  = scaleToActual(3),  SC_10 = scaleToActual(10), SC_12 = scaleToActual(12),
                  SC_14 = scaleToActual(14), SC_18 = scaleToActual(18);

        for (int r = 0, y = halfdeltaY + (HEXY_OFF_SLOPE_HEIGHT / 2);
             r <= board.getBoardHeight();
             ++r, y += halfdeltaY)
        {
            final int rshift = (r << 8);
            for (int c=0, x=0; c <= bw; ++c, x += halfdeltaX)
            {
                final int nodeCoord = rshift | c;
                // TODO each node, adjust y by +- HEXY_OFF_SLOPE_HEIGHT

                    // 1,5: settlements
                if (debugShowPotentials[1] && pl.isLegalSettlement(nodeCoord))
                {
                    g.setColor(Color.YELLOW);
                    g.drawRect(scaleToActual(x - 6), scaleToActual(y - 6), SC_12, SC_12);
                }
                if (debugShowPotentials[5] && pl.isPotentialSettlement(nodeCoord))
                {
                    g.setColor(Color.GREEN);
                    g.drawRect(scaleToActual(x - 7), scaleToActual(y - 7), SC_14, SC_14);
                }

                    // 6: cities (potential only)
                if (debugShowPotentials[6] && pl.isPotentialCity(nodeCoord))
                {
                    g.setColor(Color.GREEN);
                    g.drawRect(scaleToActual(x - 9), scaleToActual(y - 9), SC_18, SC_18);
                }

                    // 9: nodes on land
                if (debugShowPotentials[9] && board.isNodeOnLand(nodeCoord))
                {
                    g.setColor(Color.RED);
                    g.drawRoundRect(scaleToActual(x - 5), scaleToActual(y - 5), SC_10, SC_10, SC_3, SC_3);
                }
            }
        }

        // Iterate over all edges for:
        // 0,4: roads: parallel lines (Legal yellow, potential green)
        // 3,7: ships: diamonds (Legal yellow, potential green)

        for (int r = 0, y = halfdeltaY + (HEXY_OFF_SLOPE_HEIGHT / 2);
             r <= board.getBoardHeight();
             ++r, y += halfdeltaY)
        {
            final int rshift = (r << 8);
            final boolean edgeIsVert = ((r % 2) == 1);
            int x = (edgeIsVert) ? 0 : (halfdeltaX / 2);

            for (int c=0; c <= bw; ++c, x += halfdeltaX)
            {
                final int edgeCoord = rshift | c;

                    // 3,7: ships - diamonds
                if (debugShowPotentials[3] && pl.isLegalShip(edgeCoord))
                {
                    g.setColor(Color.YELLOW);
                    g.drawLine(scaleToActual(x-4), scaleToActual(y),   scaleToActual(x),   scaleToActual(y-4));
                    g.drawLine(scaleToActual(x),   scaleToActual(y-4), scaleToActual(x+4), scaleToActual(y));
                    g.drawLine(scaleToActual(x+4), scaleToActual(y),   scaleToActual(x),   scaleToActual(y+4));
                    g.drawLine(scaleToActual(x),   scaleToActual(y+4), scaleToActual(x-4), scaleToActual(y));
                }
                if (debugShowPotentials[7] && pl.isPotentialShip(edgeCoord))
                {
                    g.setColor(Color.GREEN);
                    g.drawLine(scaleToActual(x-6), scaleToActual(y),   scaleToActual(x),   scaleToActual(y-6));
                    g.drawLine(scaleToActual(x),   scaleToActual(y-6), scaleToActual(x+6), scaleToActual(y));
                    g.drawLine(scaleToActual(x+6), scaleToActual(y),   scaleToActual(x),   scaleToActual(y+6));
                    g.drawLine(scaleToActual(x),   scaleToActual(y+6), scaleToActual(x-6), scaleToActual(y));
                }

                    // 0,4: roads - parallel lines
                if (debugShowPotentials[0] && pl.isLegalRoad(edgeCoord))
                {
                    drawBoardEmpty_drawDebugShowPotentialRoad
                        (g, x, y, r, c, edgeIsVert, Color.YELLOW, 4);
                }
                if (debugShowPotentials[4] && pl.isPotentialRoad(edgeCoord))
                    drawBoardEmpty_drawDebugShowPotentialRoad
                        (g, x, y, r, c, edgeIsVert, Color.GREEN, 6);
            }
        }
    }

    /**
     * Draw around one potential/legal road edge,
     * for {@link #drawBoardEmpty_drawDebugShowPotentials(Graphics)}.
     * (x,y) is the center of the edge.
     *<P>
     * For large board only for now (TODO).
     * Will scale as needed, but assumes {@code g} is already
     * translated by ({@link #panelMarginX}, {@link #panelMarginY}) pixels.
     *
     * @param g  Graphics
     * @param x  Unscaled internal x-coordinate of center of this edge
     * @param y  Unscaled internal y-coordinate of center of this edge
     * @param r  Board row coordinate of this edge
     * @param c  Board column coordinate of this edge
     * @param isVert  Is this edge vertical (running north-south), not diagonal?
     * @param co  Color to draw the edge
     * @param offset  Approx unscaled internal-coordinate offset, outwards parallel to road
     */
    private final void drawBoardEmpty_drawDebugShowPotentialRoad
        (Graphics g, final int x, final int y, final int r, final int c,
         final boolean isVert, final Color co, final int offset)
    {
        g.setColor(co);

        if (isVert)
        {
            g.drawLine(scaleToActual(x - offset), scaleToActual(y - 10),
                       scaleToActual(x - offset), scaleToActual(y + 10));
            g.drawLine(scaleToActual(x + offset), scaleToActual(y - 10),
                       scaleToActual(x + offset), scaleToActual(y + 10));
            return;
        }

        // Determining SOCBoardLarge (r,c) edge direction: | / \
        //   "|" if r is odd
        //   Otherwise: s = r/2
        //   "/" if (s,c) is even,odd or odd,even
        //   "\" if (s,c) is odd,odd or even,even

        final int off2 = offset / 2;
        if ((c % 2) != ((r/2) % 2))
        {
            // road is "/"
            g.drawLine(scaleToActual(x - 10 - off2), scaleToActual(y + 6 - offset),
                       scaleToActual(x + 10 - off2), scaleToActual(y - 6 - offset));
            g.drawLine(scaleToActual(x - 10 + off2), scaleToActual(y + 6 + offset),
                       scaleToActual(x + 10 + off2), scaleToActual(y - 6 + offset));
        } else {
            // road is "\"
            g.drawLine(scaleToActual(x + 10 + off2), scaleToActual(y + 6 - offset),
                       scaleToActual(x - 10 + off2), scaleToActual(y - 6 - offset));
            g.drawLine(scaleToActual(x + 10 - off2), scaleToActual(y + 6 + offset),
                       scaleToActual(x - 10 - off2), scaleToActual(y - 6 + offset));
        }
    }

    /**
     * Draw {@link #superText1}, {@link #superText2}; if necessary, calculate {@link #superText1_w} and other fields.
     * @since 1.1.07
     */
    private void drawSuperText(Graphics g)
    {
        // Specify the font, so we know its metrics.
        // This avoids a timing bug where the wrong font's metrics are used.
        final Font bpf = new Font("Dialog", Font.PLAIN, 10 * playerInterface.displayScale);

        // Do we need to calculate the metrics?

        if ((superText1_w == 0) || ((superText2 != null) && (superText2_w == 0)))
        {
            final FontMetrics fm = g.getFontMetrics(bpf);
            if (fm == null)
            {
                repaint();
                return;  // We'll have to try again
            }

            if (superText1_w == 0)
            {
                if (superText1 == null)
                    return;  // avoid NPE from multi-threading
                superText1_w = fm.stringWidth(superText1);
                superText_h = fm.getHeight();
                superText_des = fm.getDescent();
            }
            if ((superText2 != null) && (superText2_w == 0))
            {
                superText2_w = fm.stringWidth(superText2);
            }
            // box size
            if (superText2_w > superText1_w)
                superTextBox_w = superText2_w;
            else
                superTextBox_w = superText1_w;
            if (superText2 != null)
                superTextBox_h = 2 * superText_h;
            else
                superTextBox_h = superText_h;

            superTextBox_w += 2 * SUPERTEXT_INSET + 2 * SUPERTEXT_PADDING_HORIZ;
            superTextBox_h += SUPERTEXT_INSET + 2 * fm.getDescent();

            superTextBox_x = (scaledPanelW - superTextBox_w) / 2;
            superTextBox_y = (scaledPanelH - superTextBox_h) / 2;
        }

        // adj from center
        g.setColor(Color.black);
        g.fillRoundRect(superTextBox_x, superTextBox_y, superTextBox_w, superTextBox_h, SUPERTEXT_INSET, SUPERTEXT_INSET);
        g.setColor(Color.white);
        g.fillRoundRect(superTextBox_x + SUPERTEXT_INSET, superTextBox_y + SUPERTEXT_INSET,
             superTextBox_w - 2 * SUPERTEXT_INSET, superTextBox_h - 2 * SUPERTEXT_INSET, SUPERTEXT_INSET, SUPERTEXT_INSET);
        g.setColor(Color.black);

        // draw text at center
        int tx = (scaledPanelW - superText1_w) / 2;
        int ty = superTextBox_y + SUPERTEXT_INSET + superText_h - superText_des;
        if (superText1 == null)
            return;  // avoid NPE from multi-threading

        final Font prev = g.getFont();
        g.setFont(bpf);
        g.drawString(superText1, tx, ty);
        if (superText2 != null)
        {
            tx -= (superText2_w - superText1_w) / 2;
            ty += superText_h;
            g.drawString(superText2, tx, ty);
        }
        g.setFont(prev);
    }

    /**
     * Draw {@link #superTextTop}; if necessary, calculate {@link #superTextTop_w} and other fields.
     * @since 1.1.08
     */
    private void drawSuperTextTop(Graphics g)
    {
        // Specify the font, so we know its metrics.
        // This avoids a timing bug where the wrong font's metrics are used.
        final Font bpf = new Font("Dialog", Font.PLAIN, 10 * playerInterface.displayScale);

        // Do we need to calculate the metrics?
        if (superTextTop_w == 0)
        {
            final FontMetrics fm = g.getFontMetrics(bpf);
            if (fm == null)
            {
                repaint();
                return;  // We'll have to try again
            }
            if (superTextTop_w == 0)
            {
                if (superTextTop == null)
                    return;  // avoid NPE from multi-threading
                superTextTop_w = fm.stringWidth(superTextTop);
                superTextTop_h = fm.getHeight() - fm.getDescent();
            }

            // box size
            superTextTopBox_w = superTextTop_w;
            superTextTopBox_h = superTextTop_h;

            superTextTopBox_w += 2 * SUPERTEXT_INSET + 2 * SUPERTEXT_PADDING_HORIZ;
            superTextTopBox_h += SUPERTEXT_INSET + 2 * fm.getDescent();
        }

        // draw box
        superTextTopBox_x = (scaledPanelW - superTextTopBox_w) / 2;
        g.setColor(Color.black);
        g.fillRoundRect(superTextTopBox_x, SUPERTEXT_INSET, superTextTopBox_w, superTextTopBox_h, SUPERTEXT_INSET, SUPERTEXT_INSET);
        g.setColor(Color.white);
        g.fillRoundRect(superTextTopBox_x + SUPERTEXT_INSET, 2 * SUPERTEXT_INSET,
             superTextTopBox_w - 2 * SUPERTEXT_INSET, superTextTopBox_h - 2 * SUPERTEXT_INSET, SUPERTEXT_INSET, SUPERTEXT_INSET);
        g.setColor(Color.black);

        // draw centered text
        int tx = (scaledPanelW - superTextTop_w) / 2;
        int ty = 2 * SUPERTEXT_INSET + superTextTop_h;
        g.setFont(bpf);
        if (superTextTop == null)
            return;  // avoid NPE from multi-threading
        g.drawString(superTextTop, tx, ty);

        /**
         * To debug OSX stringwidth... temp/in progress (20091129)
         *
        // green == box
        g.setColor(Color.green);
        g.drawLine(superTextTopBox_x, 0, superTextTopBox_x, 20);
        g.drawLine(superTextTopBox_x + superTextTopBox_w, 0, superTextTopBox_x + superTextTopBox_w, 20);
        // red == text
        g.setColor(Color.red);
        g.drawLine(tx, 0, tx, 20);
        g.drawLine(tx+superTextTop_w, 0, tx+superTextTop_w, 20);
        */
    }

    /**
     * Calculate the on-screen coordinates of a node.
     * @param nodeNum  Node coordinate
     * @return  Array with screen {x, y} for this node, already scaled and/or rotated
     * @since 2.0.00
     */
    private final int[] nodeToXY(final int nodeNum)
    {
        int hx, hy;

        if (! isLargeBoard)
        {
            final int hexNum;

            if (((nodeNum >> 4) % 2) == 0)
            { // If first digit is even,
              // then it is a 'Y' node
              // in the northwest corner of a hex.
                if ((nodeNum >= 0x81) && (0 == ((nodeNum - 0x81) % 0x22)))
                {
                    // this node's hex would be off the southern edge of the board.
                    // shift 1 hex north, then add to y.
                    hexNum = hexIDtoNum[nodeNum - 0x20 + 0x02 + 0x10];
                    hx = hexX[hexNum];
                    hy = hexY[hexNum] + 17 + (2 * deltaY);
                } else {
                    hexNum = hexIDtoNum[nodeNum + 0x10];
                    hx = hexX[hexNum];
                    hy = hexY[hexNum] + 17;
                }
            }
            else
            { // otherwise it is an 'A' node
              // in the northern corner of a hex.
                if ((nodeNum >= 0x70) && (0 == ((nodeNum - 0x70) % 0x22)))
                {
                    // this node's hex would be off the southern edge of the board.
                    // shift 1 hex north, then add to y.
                    hexNum = hexIDtoNum[nodeNum - 0x20 + 0x02 - 0x01];
                    hx = hexX[hexNum] + halfdeltaX;
                    hy = hexY[hexNum] + 2 + (2 * deltaY);
                }
                else if ((nodeNum & 0x0F) > 0)
                {
                    hexNum = hexIDtoNum[nodeNum - 0x01];
                    hx = hexX[hexNum] + halfdeltaX;
                    hy = hexY[hexNum] + 2;
                } else {
                    // this node's hex would be off the southwest edge of the board.
                    // shift 1 hex to the east, then subtract from x.
                    hexNum = hexIDtoNum[nodeNum + 0x22 - 0x01];
                    hx = hexX[hexNum] - halfdeltaX;
                    hy = hexY[hexNum] + 2;
                }
            }

        } else {
            // isLargeBoard

            final int r = (nodeNum >> 8),
                      c = (nodeNum & 0xFF);
            hx = halfdeltaX * c;
            hy = halfdeltaY * (r+1);

            // If the node isn't at the top center of a hex,
            // it will need to move up or down a bit vertically.
            //
            // 'Y' nodes vertical offset: move down
            final int s = r / 2;
            if ((s % 2) != (c % 2))
                hy += HEXY_OFF_SLOPE_HEIGHT;
        }

        if (isRotated)
        {
            // (cw):  P'=(panelMinBH-y, x)
            int hy1 = hx;
            hx = panelMinBH - hy;
            hy = hy1;
        }
        if (isScaled)
        {
            hx = scaleToActual(hx);
            hy = scaleToActual(hy);
        }

        final int[] xy = { hx, hy };
        return xy;
    }

    /**
     * Scale pixel-coordinate array from internal to actual screen-pixel coordinates.
     * If not isScaled, do nothing.
     *<P>
     * This method only scales; does <em>not</em> rotate if {@link #isRotated()}
     * or translate by {@link #panelMarginX} or {@link #panelMarginY}.
     *<P>
     * Before v2.0.00 this method was {@code scaleToActualX(x[])} and {@code scaleToActualY(y[])}.
     *
     * @param xa Int array to be scaled in place; each member is an x-coordinate or y-coordinate.
     *
     * @see #scaleToActual(int)
     * @see #scaleCopyToActual(int[])
     * @since 1.1.00
     */
    public void scaleToActual(int[] xa)
    {
        if (! isScaled)
            return;
        for (int i = xa.length - 1; i >= 0; --i)
            xa[i] = (int) ((xa[i] * (long) scaledBoardW) / unscaledBoardW);
    }

    /**
     * Scale an x- or y-coordinate up from internal to actual screen-pixel coordinates.
     * If not isScaled, return input.
     *<P>
     * This method only scales up; does <em>not</em> rotate if {@link #isRotated()}
     * or translate by {@link #panelMarginX} or {@link #panelMarginY}.
     *<P>
     * Before v2.0.00 this method was {@code scaleToActualX(x)} and {@code scaleToActualY(y)}.
     *
     * @param x x-coordinate or y-coordinate to be scaled
     * @see #scaleFromActual(int)
     * @see #scaleToActual(int[])
     * @since 1.1.00
     */
    public final int scaleToActual(int x)
    {
        if (! isScaled)
            return x;
        else
            return (int) ((x * (long) scaledBoardW) / unscaledBoardW);
    }

    /**
     * Scale an x- or y-coordinate down from actual-scaled to internal-scaled coordinates.
     * If not isScaled, return input.
     *<P>
     * This method only scales down; does <em>not</em> rotate if {@link #isRotated()}
     * or translate by {@link #panelMarginX} or {@link #panelMarginY}.
     * To also do those things, call {@link #rotateScaleXYFromActual(int, int)} instead.
     *<P>
     * Before v2.0.00 this method was {@code scaleFromActualX(x)} and {@code scaleFromActualY(y)}.
     *
     * @param x x-coordinate or y-coordinate to be scaled. Subtract {@link #panelMarginX}
     *     or {@link #panelMarginY} before calling.
     * @see #scaleToActual(int)
     * @since 1.1.00
     */
    public final int scaleFromActual(int x)
    {
        if (! isScaled)
            return x;
        else
            return (int) ((x * (long) unscaledBoardW) / scaledBoardW);
    }

    /**
     * Scale board-pixel internal-scaled coordinates down from actual-scaled (x, y),
     * rotating if {@link #isRotated()} and translating from {@link #panelMarginX}, {@link #panelMarginY} as needed.
     * @param x x-coordinate to be scaled
     * @param y y-coordinate to be scaled
     * @return array with {@code [xb, yb]}
     * @see #scaleFromActual(int)
     * @since 2.7.00
     */
    public final int[] rotateScaleXYFromActual(final int x, final int y)
    {
        int xb, yb;

        if (isScaled)
        {
            xb = scaleFromActual(x - panelMarginX);
            yb = scaleFromActual(y - panelMarginY);
        }
        else
        {
            xb = x - panelMarginX;
            yb = y - panelMarginY;
        }
        if (isRotated)
        {
            // (ccw): P'=(y, panelMinBW-x)
            int xb1 = yb;
            yb = panelMinBW - xb - HEXY_OFF_SLOPE_HEIGHT;  // offset for similar reasons as -HEXHEIGHT in drawHex
            xb = xb1;
        }

        return new int[]{xb, yb};
    }

    /**
     * Is the board currently scaled up, larger than
     * {@link #PANELX} x {@link #PANELY} pixels?
     * If so, use {@link #scaleToActual(int)}, {@link #scaleFromActual(int)},
     * etc to convert between internal and actual screen pixel coordinates.
     *<P>
     * When the board is also {@link #isRotated()}, see {@link #isRotated()} javadoc
     * for order of rotation/scaling/margin translation.
     *
     * @return Is the board scaled larger than default size?
     * @see #isRotated()
     * @since 1.1.00
     */
    public boolean isScaled()
    {
        return isScaled;
    }

    /**
     * Is the board currently visually rotated 90 degrees clockwise
     * compared to its internal pixel coordinates?
     * If so, the minimum size swaps {@link #PANELX} and {@link #PANELY}.
     *<P>
     * Use this for rotation:
     *<UL>
     * <LI> From internal to screen (clockwise): R(x, y) = ({@code internalHeight} - y, x)
     * <LI> From screen to internal (counterclockwise): R(x, y) = (y, {@code internalWidth} - x)
     *</UL>
     *
     * When the board is also {@link #isScaled()}, go in this order:
     *<UL>
     * <LI> Rotate clockwise, scale up, then add any margin.
     * <LI> Subtract any margin, scale down, then rotate counterclockwise.
     *</UL>
     *
     * @return Is the board rotated?
     * @see #isScaled()
     * @since 1.1.08
     */
    public boolean isRotated()
    {
        return isRotated;
    }

    /**
     * update the type of interaction mode, and trigger a repaint.
     * If current player has changed, clears {@link #setLatestPiecePlacement(SOCPlayingPiece)}.
     * Also calls {@link #updateHoverTipToMode()} and
     * (for 6-player board's Special Building Phase) updates top-center text.
     * For {@link soc.game.SOCGameOptionSet#getAllKnownOptions() Game Option "N7"},
     * updates the top-center countdown of rounds from {@link SOCGame#getRoundCount()}.
     * For the {@link SOCGame#debugFreePlacement Free Placement debug mode},
     * indicates that in the top center.
     */
    public void updateMode()
    {
        final int cpn = game.getCurrentPlayerNumber();
        String topText = null;  // assume not Special Building Phase

        if (player != null)
        {
            if (game.isDebugFreePlacement())
            {
                topText = "DEBUG: Free Placement Mode";

                if (game.getGameState() == SOCGame.PLACING_INV_ITEM)
                {
                    if (game.isGameOptionSet(SOCGameOptionSet.K_SC_FTRI))
                    {
                        mode = SC_FTRI_PLACE_PORT;
                        repaint();
                    } else {
                        mode = NONE;
                    }
                } else {
                    switch(player.getPieces().size())
                    {
                    case 1:
                    case 3:
                        mode = PLACE_INIT_ROAD;
                        break;

                    case 0:
                    case 2:
                        mode = PLACE_INIT_SETTLEMENT;
                        break;

                    default:
                        mode = NONE;
                    }
                }
            }
            else if (cpn == playerNumber)
            {
                switch (game.getGameState())
                {
                case SOCGame.START1A:
                case SOCGame.START2A:
                case SOCGame.START3A:
                    mode = PLACE_INIT_SETTLEMENT;
                    break;

                case SOCGame.START1B:
                case SOCGame.START2B:
                case SOCGame.START3B:
                    mode = PLACE_INIT_ROAD;
                    break;

                case SOCGame.PLACING_ROAD:
                    mode = PLACE_ROAD;
                    break;

                case SOCGame.PLACING_FREE_ROAD1:
                case SOCGame.PLACING_FREE_ROAD2:
                    if (isLargeBoard)
                        mode = PLACE_FREE_ROAD_OR_SHIP;
                    else
                        mode = PLACE_ROAD;
                    break;

                case SOCGame.PLACING_SETTLEMENT:
                    mode = PLACE_SETTLEMENT;
                    break;

                case SOCGame.PLACING_CITY:
                    mode = PLACE_CITY;
                    break;

                case SOCGame.PLACING_SHIP:
                    mode = PLACE_SHIP;
                    break;

                case SOCGame.PLACING_ROBBER:
                    mode = PLACE_ROBBER;
                    break;

                case SOCGame.PLACING_PIRATE:
                    mode = PLACE_PIRATE;
                    break;

                case SOCGame.PLACING_INV_ITEM:
                    if (game.isGameOptionSet(SOCGameOptionSet.K_SC_FTRI))
                    {
                        mode = SC_FTRI_PLACE_PORT;
                        repaint();
                    } else {
                        mode = NONE;
                    }
                    break;

                case SOCGame.NEW:
                case SOCGame.READY:
                    mode = GAME_FORMING;
                    break;

                case SOCGame.OVER:
                    mode = GAME_OVER;
                    break;

                case SOCGame.ROLL_OR_CARD:
                    mode = TURN_STARTING;
                    if (game.isGameOptionSet("N7"))
                    {
                        // N7: Roll no 7s during first # rounds.
                        // Show if we can roll a 7 yet.  (1.1.09)

                        final int no7roundsleft = game.getGameOptionIntValue("N7") - game.getRoundCount();
                        if (no7roundsleft == 0)
                        {
                            topText = strings.get("board.msg.n7.last.round");  // "Last round for "No 7s""
                        } else if (no7roundsleft > 0)
                        {
                            if (playerInterface.isClientCurrentPlayer()
                              && playerInterface.getClientHand().isClientAndCurrentlyCanRoll())
                                topText = strings.get("board.msg.n7.rounds.left", (1 + no7roundsleft));
                                    // "{0} rounds left for "No 7s""
                        }
                    }
                    if ((topText == null) && (! game.hasBuiltCity())
                        && playerInterface.getClientHand().isClientAndCurrentlyCanRoll()  // prevent end-of-turn flicker
                        && game.isGameOptionSet("N7C"))
                    {
                        topText = strings.get("board.msg.n7c.until_city");  // "No 7s rolled until a city is built"
                    }
                    break;

                case SOCGame.SPECIAL_BUILDING:
                    mode = NONE;
                    topText = strings.get("board.msg.special.building", player.getName());  // "Special Building: {0}"
                    break;

                default:
                    mode = NONE;
                    break;
                }
            }
            else
            {
                // Not current player
                mode = NONE;

                if (game.isSpecialBuilding())
                {
                    topText = strings.get("board.msg.special.building", game.getPlayer(cpn).getName());
                        // "Special Building: {0}"
                }
                else if (game.isGameOptionSet("N7"))
                {
                    // N7: Roll no 7s during first # rounds.
                    // Show if we're about to be able to roll a 7.  (1.1.09)
                    final int no7roundsleft = game.getGameOptionIntValue("N7") - game.getRoundCount();
                    if (no7roundsleft == 0)
                        topText = strings.get("board.msg.n7.last.round");  // "Last round for "No 7s""
                }
            }
        }
        else
        {
            mode = NONE;
        }

        moveShip_fromEdge = 0;
        if ((latestPiecePlacement != null) && (cpn != latestPiecePlacement.getPlayerNumber()))
            latestPiecePlacement = null;

        setSuperimposedTopText(topText);
            // has side effect of always calling repaint() for us
            // topText usually null
        updateHoverTipToMode();
    }

    /**
     * Update {@link #hoverTip} based on {@link #mode} when it changes;
     * called from {@link #updateMode()}. Might or might not repaint board:
     * Calls {@link BoardToolTip#setOffsetX(int)} or {@link BoardToolTip#setHoverText(String, int)}.
     * @since 1.1.00
     */
    protected void updateHoverTipToMode()
    {
        if ((mode == NONE) || (mode == TURN_STARTING) || (mode == GAME_OVER))
            hoverTip.setOffsetX(0);
        else if ((mode == PLACE_ROBBER) || (mode == PLACE_PIRATE))
            hoverTip.setOffsetX(HOVER_OFFSET_X_FOR_ROBBER);
        else if ((mode == PLACE_INIT_SETTLEMENT) || (mode == PLACE_INIT_ROAD))
        {
            hoverTip.setHoverText_modeChangedOrMouseMoved = true;
            hoverTip.setHoverText(null, 0);
            hoverTip.setOffsetX(HOVER_OFFSET_X_FOR_INIT_PLACE);
        } else {
            hoverTip.setHoverText_modeChangedOrMouseMoved = true;
            hoverTip.setHoverText(null, 0);
        }
    }

    /**
     * Set board mode to {@link #NONE}, no hilight, usually from a piece-placement mode.
     * Calls {@link #updateHoverTipToMode()} and repaints the board.
     *
     * @param ptype Piece type to clear, like {@link SOCPlayingPiece#ROAD}, or -1 for robber.
     *              Used to avoid race condition during initial placement,
     *              where server has already replied with mode for another piece type,
     *              and board has already set mode to place that piece type.
     *              If ptype doesn't match the board's current mode/piece type,
     *              board's mode is not changed to NONE.
     * @since 1.1.00
     */
    protected void clearModeAndHilight(final int ptype)
    {
        int expectedPtype;  // based on current mode

        switch (mode)
        {
        case PLACE_ROAD:
        case PLACE_INIT_ROAD:
        case PLACE_FREE_ROAD_OR_SHIP:
        case CONSIDER_LM_ROAD:
        case CONSIDER_LT_ROAD:
            expectedPtype = SOCPlayingPiece.ROAD;  // also will expect SHIP
            break;

        case PLACE_SETTLEMENT:
        case PLACE_INIT_SETTLEMENT:
        case CONSIDER_LM_SETTLEMENT:
        case CONSIDER_LT_SETTLEMENT:
            expectedPtype = SOCPlayingPiece.SETTLEMENT;
            break;

        case PLACE_CITY:
        case CONSIDER_LM_CITY:
        case CONSIDER_LT_CITY:
            expectedPtype = SOCPlayingPiece.CITY;
            break;

        case PLACE_SHIP:
        case MOVE_SHIP:
        case CONSIDER_LM_SHIP:
        case CONSIDER_LT_SHIP:
            expectedPtype = SOCPlayingPiece.SHIP;
            break;

        case PLACE_ROBBER:
        case PLACE_PIRATE:
            expectedPtype = -1;
            break;

        default:
            expectedPtype = ptype;  // Not currently placing
        }

        if ((ptype == expectedPtype)
            || (  ((mode == PLACE_INIT_ROAD) || (mode == PLACE_FREE_ROAD_OR_SHIP))
                  && (ptype == SOCPlayingPiece.SHIP)  ))
        {
            mode = NONE;
            hilight = 0;
            hilightIsShip = false;
            moveShip_fromEdge = 0;
            repaint();
        }
        updateHoverTipToMode();
    }

    /**
     * Set the player that is using this board panel to be the client's player in this game.
     * Called when observing user sits down to become a player.
     *<P>
     * Call only after {@link SOCPlayerInterface#addPlayer(String, int)} has been called for client player.
     *
     * @see #setPlayer(SOCPlayer)
     */
    public void setPlayer()
    {
        setPlayer(null);
    }

    /**
     * Set or change the player that is using this board panel.
     * Initially {@code null} when first joining or observing a game.
     * Also used for temporary change during {@link SOCGame#debugFreePlacement} mode.
     *<P>
     * If {@code pl} is null, call only after {@link SOCPlayerInterface#addPlayer(String, int)}
     * has been called for client player.
     *
     * @param pl Player to set, or null to change back to the client player
     *     using {@link SOCPlayerInterface#getClientPlayer()}
     * @see #getPlayerNumber()
     * @see #setPlayer()
     * @since 1.1.12
     */
    void setPlayer(SOCPlayer pl)
    {
        if (pl == null)
            pl = playerInterface.getClientPlayer();
        if (pl == player)
            return;

        player = pl;
        playerNumber = player.getPlayerNumber();

        // Check for per-player Legal Sea Edges (SC_PIRI)
        final HashSet<Integer> lse = player.getRestrictedLegalShips();
        if (lse != null)
        {
            flushBoardLayoutAndRepaint();
        } else {
            // check if any debugShowPotentials flags are set, which are per-player
            boolean any = false;
            for (int i = 0; i < debugShowPotentials.length; ++i)
                if (debugShowPotentials[i])
                {
                    any = true;
                    break;
                }

            if (any)
                scaledMissedImage = true;  // force redraw of empty board and its potentials
        }

        updateMode();
    }

    /**
     * Get our client player number, if not observing.
     * Almost always the client's player number.
     * During {@link SOCGame#debugFreePlacement}, the temporary
     * player set by {@link #setPlayer(SOCPlayer)}.
     * @return  Our player number, or -1 if observing the game and haven't sat down yet
     * @since 1.1.12
     */
    int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * For "consider" modes like {@link #CONSIDER_LM_SETTLEMENT} or {@link #CONSIDER_LT_ROAD},
     * set the robot player we'll send the {@code :consider-} command to.
     * Must be called before {@link #setMode(int)} into a "consider" mode.
     *
     * @param op  the robot other player; does nothing if {@code null}.
     *     This keeps the current otherPlayer in case {@code null} comes from
     *     a player name being unrecognized by {@link SOCGame#getPlayer(String)}.
     */
    public void setOtherPlayer(final SOCPlayer op)
    {
        if (op != null)
            otherPlayer = op;
    }

    /*********************************
     * Handle Events
     *********************************/
    public void mouseEntered(MouseEvent e)
    {
        ;
    }

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mousePressed(MouseEvent e)
    {
        ;  // JM: was mouseClicked (moved to avoid conflict with e.isPopupTrigger)
        mouseReleased(e);  // JM 2008-01-01 testing for MacOSX popup-trigger
    }

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mouseReleased(MouseEvent e)
    {
        try
        {
            // Needed in Windows for popup-menu handling
            if (e.isPopupTrigger())
            {
                popupMenuSystime = e.getWhen();
                e.consume();
                doBoardMenuPopup(e.getX(), e.getY());
                return;
            }
        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mouseDragged(MouseEvent e)
    {
        ;
    }

    /**
     * Mouse has left the panel; hide tooltip and any hovering piece.
     *
     * @param e MouseEvent
     */
    public void mouseExited(MouseEvent e)
    {
        boolean wantsRepaint = false;
        if (hoverTip.isVisible())
        {
            hoverTip.hideHoverAndPieces();
            wantsRepaint = true;
        }
        if ((mode != NONE) && (mode != TURN_STARTING))
        {
            hilight = 0;
            hilightIsShip = false;
            wantsRepaint = true;
        }
        if (wantsRepaint)
            repaint();
    }

    /**
     * Based on the board's current {@link #mode}, update the hovering 'hilight' piece ({@link #hilight}).
     * Trigger a {@link #repaint()} if the mouse moved or the hilight changes.
     */
    public void mouseMoved(MouseEvent e)
    {
        try
        {
            int x = e.getX();
            int y = e.getY();
            final int xb, yb;
            {
                int[] xyb = rotateScaleXYFromActual(x, y);
                xb = xyb[0];  yb = xyb[1];
            }

            int edgeNum;
            int nodeNum;
            int hexNum;

            switch (mode)
            {
            case PLACE_INIT_ROAD:

                /**** Code for finding an edge ********/
                edgeNum = 0;

                if ((ptrOldX != x) || (ptrOldY != y))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    boolean isShip = false;
                    edgeNum = findEdge(xb, yb, true);
                    if ((edgeNum < 0) && isLargeBoard)
                    {
                        edgeNum = -edgeNum;
                        if ((player != null) && game.canPlaceShip(player, edgeNum))
                            isShip = true;
                    } else {
                        // check potential roads, not ships, to keep it false if coastal edge
                        isShip = ((player != null) && ! player.isPotentialRoad(edgeNum));
                    }

                    // Figure out if this is a legal road/ship;
                    // It must be attached to the last settlement
                    if ((player == null)
                        || (! (player.isPotentialRoad(edgeNum)
                               || game.canPlaceShip(player, edgeNum) ))
                        || (! (game.isDebugFreePlacement()
                               || board.isEdgeAdjacentToNode
                                  (initSettlementNode,
                                   (edgeNum != -1) ? edgeNum : 0))))
                    {
                        edgeNum = 0;
                    }

                    if ((hilight != edgeNum) || (hilightIsShip != isShip))
                    {
                        hilight = edgeNum;
                        hilightIsShip = isShip;
                        if (debugShowCoordsTooltip)
                        {
                            String blank = (edgeNum != 0) ? "" : null;    // "" shows tip, null hides it.
                            hoverTip.setHoverText(blank, edgeNum, x, y);  // also repaints
                        } else {
                            repaint();
                        }
                    }
                }

                break;

            case PLACE_ROAD:
            case PLACE_FREE_ROAD_OR_SHIP:
            case MOVE_SHIP:

                /**** Code for finding an edge; see also PLACE_SHIP, SC_FTRI_PLACE_PORT ********/
                edgeNum = 0;

                if ((ptrOldX != x) || (ptrOldY != y))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    edgeNum = findEdge(xb, yb, true);
                    final boolean hasShips = (player != null) && (player.getNumPieces(SOCPlayingPiece.SHIP) > 0);
                    final boolean canPlaceShip =
                        hasShips && game.canPlaceShip(player, Math.abs(edgeNum));

                    if ((mode == PLACE_FREE_ROAD_OR_SHIP) && canPlaceShip
                        && (edgeNum > 0) && (player.getNumPieces(SOCPlayingPiece.ROAD) == 0))
                    {
                        // If this edge is coastal, force ship (not road) if we
                        // have no roads remaining to freely place
                        edgeNum = -edgeNum;
                    }

                    boolean isShip;
                    if ((edgeNum < 0) && isLargeBoard)
                    {
                        edgeNum = -edgeNum;
                        isShip = canPlaceShip
                            || ((mode == PLACE_FREE_ROAD_OR_SHIP) && hasShips && player.isPotentialShip(edgeNum));
                    } else {
                        isShip = false;
                    }

                    if ((edgeNum != 0) && (player != null))
                    {
                        if (mode == MOVE_SHIP)
                        {
                            isShip = true;

                            // Check player.isPotentialShipMoveTo & pirate ship location.
                            // Calling game.canMoveShip is unneeded overhead: That checks
                            // the moveShip_fromEdge location, which SOCBoardPanel did
                            // before changing its mode to MOVE_SHIP.

                            if (! player.isPotentialShipMoveTo(edgeNum, moveShip_fromEdge))
                            {
                                edgeNum = 0;
                            } else {
                                // Check edgeNum vs pirate hex:
                                final SOCBoardLarge bL = (SOCBoardLarge) board;
                                final int ph = bL.getPirateHex();
                                if ((ph != 0) && bL.isEdgeAdjacentToHex(edgeNum, ph))
                                    edgeNum = 0;
                            }
                        }
                        else {
                            if ((player.isPotentialRoad(edgeNum) && (player.getNumPieces(SOCPlayingPiece.ROAD) > 0))
                                || ((mode == PLACE_FREE_ROAD_OR_SHIP) && canPlaceShip))
                            {
                                // edgeNum is OK.

                                if (! isShip)
                                {
                                    // check potential roads, not ships, to keep it false if coastal edge
                                    isShip = (player != null) && canPlaceShip
                                        && ! player.isPotentialRoad(edgeNum);
                                }
                            } else {
                                edgeNum = 0;
                            }
                        }
                    }

                    if ((hilight != edgeNum) || (hilightIsShip != isShip))
                    {
                        hilight = edgeNum;
                        hilightIsShip = isShip;
                        if (debugShowCoordsTooltip)
                        {
                            String blank = (edgeNum != 0) ? "" : null;    // "" shows tip, null hides it.
                            hoverTip.setHoverText(blank, edgeNum, x, y);  // also repaints
                        } else {
                            repaint();
                        }
                    }
                }

                break;

            case PLACE_SETTLEMENT:
            case PLACE_INIT_SETTLEMENT:

                /**** Code for finding a node *********/
                nodeNum = 0;

                if ((ptrOldX != x) || (ptrOldY != y))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    nodeNum = findNode(xb, yb);

                    if ((player == null) || ! player.canPlaceSettlement(nodeNum))
                    {
                        nodeNum = 0;
                    }

                    if (hilight != nodeNum)
                    {
                        hilight = nodeNum;
                        hilightIsShip = false;
                        if ((mode == PLACE_INIT_SETTLEMENT) && ! debugShowCoordsTooltip)
                            hoverTip.handleHover(x, y, xb, yb);
                        else if (debugShowCoordsTooltip)
                            hoverTip.setHoverText
                                (((nodeNum != 0) ? "" : null), nodeNum, x, y);
                        else
                            repaint();
                    }
                    else if (mode == PLACE_INIT_SETTLEMENT)
                    {
                        if (debugShowCoordsTooltip && (nodeNum != 0))
                            hoverTip.setHoverText("", nodeNum, x, y);
                        else
                            hoverTip.handleHover(x, y, xb, yb);  // Will call repaint() if needed
                    }
                }

                break;

            case PLACE_CITY:

                /**** Code for finding a node *********/
                nodeNum = 0;

                if ((ptrOldX != x) || (ptrOldY != y))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    nodeNum = findNode(xb, yb);

                    if ((player == null) || ! player.isPotentialCity(nodeNum))
                    {
                        nodeNum = 0;
                    }

                    if (hilight != nodeNum)
                    {
                        hilight = nodeNum;
                        hilightIsShip = false;
                        if (debugShowCoordsTooltip)
                        {
                            String blank = (nodeNum != 0) ? "" : null;    // "" shows tip, null hides it.
                            hoverTip.setHoverText(blank, nodeNum, x, y);  // also repaints
                        } else {
                            repaint();
                        }
                    }
                }

                break;

            case PLACE_SHIP:

                /**** Code for finding an edge; see also PLACE_ROAD ********/
                edgeNum = 0;

                if ((ptrOldX != x) || (ptrOldY != y))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    edgeNum = findEdge(xb, yb, false);

                    if (edgeNum != 0)
                    {
                        if ((player == null) || (player.getNumPieces(SOCPlayingPiece.SHIP) < 1)
                            || ! game.canPlaceShip(player, edgeNum))
                            edgeNum = 0;
                    }

                    if (hilight != edgeNum)
                    {
                        hilight = edgeNum;
                        hilightIsShip = true;
                        if (debugShowCoordsTooltip)
                        {
                            String blank = (edgeNum != 0) ? "" : null;    // "" shows tip, null hides it.
                            hoverTip.setHoverText(blank, edgeNum, x, y);  // also repaints
                        } else {
                            repaint();
                        }
                    }
                }

                break;

            case PLACE_ROBBER:
            case PLACE_PIRATE:

                /**** Code for finding a hex *********/
                hexNum = 0;

                if ((ptrOldX != x) || (ptrOldY != y))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    hexNum = findHex(xb, yb);
                    final boolean canMove =
                        (mode == PLACE_ROBBER)
                        ? game.canMoveRobber(playerNumber, hexNum)
                        : game.canMovePirate(playerNumber, hexNum);

                    if (! canMove)
                    {
                        // Not a hex, or can't move to this hex (water, etc)
                        if (hexNum != 0)
                        {
                            if ((board instanceof SOCBoardLarge)
                                && ((SOCBoardLarge) board).isHexInLandAreas
                                    (hexNum, ((SOCBoardLarge) board).getRobberExcludedLandAreas()))
                            {
                                hoverTip.setHoverText(strings.get("board.robber.not.here"), hexNum);
                                    // "Cannot move the robber here."
                            } else {
                                hoverTip.setHoverText(null, 0);  // clear any previous
                            }

                            hexNum = 0;
                        }
                    }

                    if (hilight != hexNum)
                    {
                        hilight = hexNum;
                        hilightIsShip = false;
                        hoverTip.handleHover(x, y, xb, yb);
                        repaint();
                    }
                    else
                    {
                        hoverTip.positionToMouse(x, y); // calls repaint
                    }
                }

                break;

            case SC_FTRI_PLACE_PORT:
                /**** Code for finding an edge; see also PLACE_ROAD, PLACE_SHIP ********/
                edgeNum = 0;

                if ((ptrOldX != x) || (ptrOldY != y) || (hilight == 0))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    edgeNum = findEdge(xb, yb, false);

                    if ((edgeNum > 0) && ! game.canPlacePort(player, edgeNum))
                        edgeNum = 0;  // not valid for placement

                    final boolean changed = (hilight != edgeNum);
                    if (changed)
                        hilight = edgeNum;
                    if (debugShowCoordsTooltip)
                    {
                        String blank = (edgeNum != 0) ? "" : null;    // "" shows tip, null hides it.
                        hoverTip.setHoverText(blank, edgeNum, x, y);  // also repaints
                    } else if (changed) {
                        repaint();
                    }
                }
                break;

            case CONSIDER_LM_SETTLEMENT:
            case CONSIDER_LT_SETTLEMENT:

                /**** Code for finding a node *********/
                nodeNum = 0;

                if ((ptrOldX != x) || (ptrOldY != y))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    nodeNum = findNode(xb, yb);

                    //if (!otherPlayer.isPotentialSettlement(nodeNum))
                    //  nodeNum = 0;
                    if (hilight != nodeNum)
                    {
                        hilight = nodeNum;
                        hilightIsShip = false;
                        repaint();
                    }
                }

                break;

            case CONSIDER_LM_ROAD:
            case CONSIDER_LT_ROAD:

                /**** Code for finding an edge ********/
                edgeNum = 0;

                if ((ptrOldX != x) || (ptrOldY != y))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    edgeNum = findEdge(xb, yb, false);

                    if (! otherPlayer.isPotentialRoad(edgeNum))
                    {
                        edgeNum = 0;
                    }

                    if (hilight != edgeNum)
                    {
                        hilight = edgeNum;
                        hilightIsShip = false;
                        repaint();
                    }
                }

                break;

            case CONSIDER_LM_SHIP:
            case CONSIDER_LT_SHIP:

                /**** Code for finding an edge ********/
                edgeNum = 0;

                if ((ptrOldX != x) || (ptrOldY != y))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    edgeNum = findEdge(xb, yb, false);

                    if (! otherPlayer.isPotentialShip(edgeNum))
                    {
                        edgeNum = 0;
                    }

                    if (hilight != edgeNum)
                    {
                        hilight = edgeNum;
                        hilightIsShip = true;
                        repaint();
                    }
                }

                break;

            case CONSIDER_LM_CITY:
            case CONSIDER_LT_CITY:

                /**** Code for finding a node *********/
                nodeNum = 0;

                if ((ptrOldX != x) || (ptrOldY != y))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    nodeNum = findNode(xb, yb);

                    if (! otherPlayer.isPotentialCity(nodeNum))
                    {
                        nodeNum = 0;
                    }

                    if (hilight != nodeNum)
                    {
                        hilight = nodeNum;
                        hilightIsShip = false;
                        repaint();
                    }
                }

                break;

            case NONE:
            case TURN_STARTING:
            case GAME_OVER:
                // see hover
                if ((ptrOldX != x) || (ptrOldY != y))
                {
                    ptrOldX = x;
                    ptrOldY = y;
                    hoverTip.handleHover(x, y, xb, yb);
                }
                break;

            case GAME_FORMING:
                // No hover for forming
                break;

            }
        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param evt DOCUMENT ME!
     */
    @SuppressWarnings("fallthrough")
    public void mouseClicked(MouseEvent evt)
    {
        try
        {
            int x = evt.getX();
            int y = evt.getY();

            if (evt.isPopupTrigger())
            {
                popupMenuSystime = evt.getWhen();
                evt.consume();
                doBoardMenuPopup(x, y);
                return;  // <--- Pop up menu, nothing else to do ---
            }

            if (evt.getWhen() < (popupMenuSystime + POPUP_MENU_IGNORE_MS))
            {
                return;  // <--- Ignore click: too soon after popup click ---
            }

            boolean tempChangedMode = false;
            if ((mode == NONE) && hoverTip.isVisible())
            {
                // Normally, NONE mode ignores single clicks.
                // But in the Free Placement debug mode, these
                // can be used to place pieces.
                // Also: After initial placement, to help guide new users,
                // display a hint message popup that left-click is not used
                // to build pieces (see below).

                if (game.isDebugFreePlacement())
                {
                    if (hoverTip.hoverSettlementID != 0)
                    {
                        hilight = hoverTip.hoverSettlementID;
                        hilightIsShip = false;
                        mode = PLACE_SETTLEMENT;
                        tempChangedMode = true;
                    }
                    else if (hoverTip.hoverCityID != 0)
                    {
                        hilight = hoverTip.hoverCityID;
                        hilightIsShip = false;
                        mode = PLACE_CITY;
                        tempChangedMode = true;
                    }
                    else if (hoverTip.hoverRoadID != 0)
                    {
                        hilight = hoverTip.hoverRoadID;
                        hilightIsShip = false;
                        mode = PLACE_ROAD;
                        tempChangedMode = true;
                    }
                    else if (hoverTip.hoverShipID != 0)
                    {
                        hilight = hoverTip.hoverShipID;
                        hilightIsShip = true;
                        mode = PLACE_SHIP;
                        tempChangedMode = true;
                    }
                }
                else if (((evt.getButton() & MouseEvent.BUTTON1) != 0)
                    && (player != null) && (game.getCurrentPlayerNumber() == playerNumber)
                    && (player.getPublicVP() == 2) && (hintShownCount_RightClickToBuild < 2)
                    && (evt.getWhen() - popupMenuSystime > 5000))
                {
                    // To help during the start of the game, display a hint message
                    // reminding new users to right-click to build (OSX: control-click).
                    // Show it at most twice to avoid annoying the user.
                    // Don't show if they've just shown the right-click build menu,
                    // because they might be clicking to dismiss that.

                    ++hintShownCount_RightClickToBuild;
                    final String prompt =
                        (SOCPlayerClient.IS_PLATFORM_MAC_OSX)
                        ? "board.popup.hint_build_click.osx"
                            // "To build pieces, hold Control while clicking the build location."
                        : "board.popup.hint_build_click";  // "To build pieces, right-click the build location."
                    NotifyDialog.createAndShow
                        (playerInterface.getMainDisplay(), playerInterface,
                         "\n" + strings.get(prompt), null, true);
                        // start prompt with \n to prevent it being a lengthy popup-dialog title
                }
            }

            if ((hilight != 0) && (player != null) && (x == ptrOldX) && (y == ptrOldY))
            {
                final GameMessageSender messageSender = playerInterface.getClient().getGameMessageSender();

                switch (mode)
                {
                case NONE:
                    break;

                case TURN_STARTING:
                    break;

                case PLACE_INIT_ROAD:
                case PLACE_ROAD:
                case PLACE_FREE_ROAD_OR_SHIP:

                    if (hilight == -1)
                        hilight = 0;  // Road on edge 0x00
                    if (player.isPotentialRoad(hilight) && ! hilightIsShip)
                    {
                        messageSender.putPiece(game, new SOCRoad(player, hilight, board));

                        // Now that we've placed, clear the mode and the hilight.
                        clearModeAndHilight(SOCPlayingPiece.ROAD);
                        if (tempChangedMode)
                            hoverTip.hideHoverAndPieces();
                    }
                    else if (game.canPlaceShip(player, hilight))  // checks isPotentialShip, pirate ship
                    {
                        if (game.isGameOptionSet(SOCGameOptionSet.K_SC_FTRI)
                            && ((SOCBoardLarge) board).canRemovePort(hilight))
                        {
                            java.awt.EventQueue.invokeLater(new ConfirmPlaceShipDialog(hilight, false, -1));
                        } else {
                            messageSender.putPiece(game, new SOCShip(player, hilight, board));

                            // Now that we've placed, clear the mode and the hilight.
                            clearModeAndHilight(SOCPlayingPiece.SHIP);
                        }

                        if (tempChangedMode)
                            hoverTip.hideHoverAndPieces();
                    }

                    break;

                case MOVE_SHIP:
                    // check and move ship to hilight from fromEdge;
                    // also sets moveShip_fromEdge = 0, calls clearModeAndHilight.
                    moveShip_toEdge = hilight;
                    tryMoveShipToEdge();
                    break;

                case PLACE_INIT_SETTLEMENT:
                    if (playerNumber == playerInterface.getClientPlayerNumber())
                    {
                        initSettlementNode = hilight;
                    }
                    // no break: fall through

                case PLACE_SETTLEMENT:

                    if (player.canPlaceSettlement(hilight))
                    {
                        messageSender.putPiece(game, new SOCSettlement(player, hilight, board));
                        clearModeAndHilight(SOCPlayingPiece.SETTLEMENT);
                        if (tempChangedMode)
                            hoverTip.hideHoverAndPieces();
                    }

                    break;

                case PLACE_CITY:

                    if (player.isPotentialCity(hilight))
                    {
                        messageSender.putPiece(game, new SOCCity(player, hilight, board));
                        clearModeAndHilight(SOCPlayingPiece.CITY);
                        if (tempChangedMode)
                            hoverTip.hideHoverAndPieces();
                    }

                    break;

                case PLACE_SHIP:
                    if (game.canPlaceShip(player, hilight))  // checks isPotentialShip, pirate ship
                    {
                        if (game.isGameOptionSet(SOCGameOptionSet.K_SC_FTRI)
                            && ((SOCBoardLarge) board).canRemovePort(hilight))
                        {
                            java.awt.EventQueue.invokeLater(new ConfirmPlaceShipDialog(hilight, false, -1));
                        } else {
                            messageSender.putPiece(game, new SOCShip(player, hilight, board));
                            clearModeAndHilight(SOCPlayingPiece.SHIP);
                        }
                        if (tempChangedMode)
                            hoverTip.hideHoverAndPieces();
                    }
                    break;

                case PLACE_ROBBER:

                    if (hilight != board.getRobberHex())
                    {
                        // do we have an adjacent settlement/city?
                        // (ignore if hex doesn't have a dice number (desert))
                        boolean cliAdjacent = false;
                        if (0 != board.getNumberOnHexFromCoord(hilight))
                        {
                            for (SOCPlayer pl : game.getPlayersOnHex(hilight, null))
                            {
                                if (pl.getPlayerNumber() == playerNumber)
                                {
                                    cliAdjacent = true;
                                    break;
                                }
                            }
                        }

                        if (cliAdjacent)
                        {
                            // ask player to confirm first
                            java.awt.EventQueue.invokeLater(new MoveRobberConfirmDialog(player, hilight));
                        }
                        else
                        {
                            // ask server to move it
                            messageSender.moveRobber(game, player, hilight);
                            clearModeAndHilight(-1);
                        }
                    }

                    break;

                case PLACE_PIRATE:

                    if (hilight != ((SOCBoardLarge) board).getPirateHex())
                    {
                        // do we have an adjacent ship?
                        boolean cliAdjacent = false;
                        {
                            for (SOCPlayer pl : game.getPlayersShipsOnHex(hilight))
                            {
                                if (pl.getPlayerNumber() == playerNumber)
                                {
                                    cliAdjacent = true;
                                    break;
                                }
                            }
                        }

                        if (cliAdjacent)
                        {
                            // ask player to confirm first
                            java.awt.EventQueue.invokeLater(new MoveRobberConfirmDialog(player, -hilight));
                        }
                        else
                        {
                            // ask server to move it
                            messageSender.moveRobber(game, player, -hilight);
                            clearModeAndHilight(-1);
                        }
                    }

                    break;

                case SC_FTRI_PLACE_PORT:
                    if (hilight != 0)
                    {
                        int edge = hilight;
                        if (edge == -1)
                            edge = 0;
                        if (game.canPlacePort(player, edge))
                        {
                            // Ask server to place here.
                            messageSender.sendSimpleRequest
                                (player, SOCSimpleRequest.TRADE_PORT_PLACE, hilight, 0);
                            hilight = 0;
                        }
                    }
                    break;

                case CONSIDER_LM_SETTLEMENT:
                    if (otherPlayer.canPlaceSettlement(hilight))
                    {
                        messageSender.considerMove
                            (game, otherPlayer, new SOCSettlement(otherPlayer, hilight, board));
                        clearModeAndHilight(SOCPlayingPiece.SETTLEMENT);
                    }
                    break;

                case CONSIDER_LM_ROAD:
                    if (otherPlayer.isPotentialRoad(hilight))
                    {
                        messageSender.considerMove
                            (game, otherPlayer, new SOCRoad(otherPlayer, hilight, board));
                        clearModeAndHilight(SOCPlayingPiece.ROAD);
                    }
                    break;

                case CONSIDER_LM_SHIP:
                    if (otherPlayer.isPotentialShip(hilight))
                    {
                        messageSender.considerMove
                            (game, otherPlayer, new SOCShip(otherPlayer, hilight, board));
                        clearModeAndHilight(SOCPlayingPiece.SHIP);
                    }
                    break;

                case CONSIDER_LM_CITY:
                    if (otherPlayer.isPotentialCity(hilight))
                    {
                        messageSender.considerMove
                            (game, otherPlayer, new SOCCity(otherPlayer, hilight, board));
                        clearModeAndHilight(SOCPlayingPiece.CITY);
                    }
                    break;

                case CONSIDER_LT_SETTLEMENT:
                    if (otherPlayer.canPlaceSettlement(hilight))
                    {
                        messageSender.considerTarget
                            (game, otherPlayer, new SOCSettlement(otherPlayer, hilight, board));
                        clearModeAndHilight(SOCPlayingPiece.SETTLEMENT);
                    }
                    break;

                case CONSIDER_LT_ROAD:
                    if (otherPlayer.isPotentialRoad(hilight))
                    {
                        messageSender.considerTarget
                            (game, otherPlayer, new SOCRoad(otherPlayer, hilight, board));
                        clearModeAndHilight(SOCPlayingPiece.ROAD);
                    }
                    break;

                case CONSIDER_LT_SHIP:
                    if (otherPlayer.isPotentialShip(hilight))
                    {
                        messageSender.considerTarget
                            (game, otherPlayer, new SOCShip(otherPlayer, hilight, board));
                        clearModeAndHilight(SOCPlayingPiece.SHIP);
                    }
                    break;

                case CONSIDER_LT_CITY:
                    if (otherPlayer.isPotentialCity(hilight))
                    {
                        messageSender.considerTarget
                            (game, otherPlayer, new SOCCity(otherPlayer, hilight, board));
                        clearModeAndHilight(SOCPlayingPiece.CITY);
                    }
                    break;
                }
            }
            else if ((player != null)
                     && ((game.getCurrentPlayerNumber() == playerNumber)
                         || game.isDebugFreePlacement()))
            {
                // No hilight. But, they clicked the board, expecting something.
                // It's possible the mode is incorrect.
                // Update and wait for the next click.
                updateMode();
                ptrOldX = 0;
                ptrOldY = 0;
                mouseMoved(evt);  // mouseMoved will establish hilight using click's x,y
            }

            evt.consume();
            if (tempChangedMode)
                mode = NONE;

        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }

    /**
     * Bring up the popup menu; called from mousePressed.
     *
     * @param x x-coordinate of click, actual screen pixels (not unscaled internal)
     * @param y y-coordinate of click, actual screen pixels (not unscaled internal)
     * @since 1.1.00
     */
    protected void doBoardMenuPopup (final int x, final int y)
    {
        // Determine mode, to see if we're building or cancelling.
        switch (mode)
        {
        case PLACE_ROAD:
            popupMenu.showCancelBuild(SOCPlayingPiece.ROAD, x, y, hilight);
            break;

        case PLACE_SETTLEMENT:
            popupMenu.showCancelBuild(SOCPlayingPiece.SETTLEMENT, x, y, hilight);
            break;

        case PLACE_CITY:
            popupMenu.showCancelBuild(SOCPlayingPiece.CITY, x, y, hilight);
            break;

        case PLACE_SHIP:
        case MOVE_SHIP:
            popupMenu.showCancelBuild(SOCPlayingPiece.SHIP, x, y, hilight);
            break;

        case PLACE_INIT_ROAD:
        case PLACE_FREE_ROAD_OR_SHIP:
            // might be road or ship
            {
                final int hilightRoad =
                    ((! game.hasSeaBoard) || player.isLegalRoad(hilight)) ? hilight : 0;
                int hilightShip =
                    (game.hasSeaBoard && player.isLegalShip(hilight)) ? hilight : 0;
                popupMenu.showBuild
                    (x, y, hilightRoad, 0, 0, hilightShip);
            }
            break;

        case PLACE_INIT_SETTLEMENT:
            popupMenu.showBuild(x, y, 0, hilight, 0, 0);
            break;

        default:  // NONE, GAME_FORMING, PLACE_ROBBER, etc

            if ((hoverTip.hoverPiece != null) && (hoverTip.hoverPiece instanceof SOCFortress))
            {
                popupMenu.showAtPirateFortress(x, y, (SOCFortress) (hoverTip.hoverPiece));
                return;  // <--- early return: special case: fortress (_SC_PIRI) ---
            }

            // Along coastline, can build either road or ship
            final int hilightRoad, hilightShip;
            if (game.hasSeaBoard &&
                ((hoverTip.hoverRoadID != 0) || (hoverTip.hoverShipID != 0)))
            {
                int edge = hoverTip.hoverRoadID;
                if (edge == 0)
                    edge = hoverTip.hoverShipID;
                hilightRoad =
                    (player.isLegalRoad(edge)) ? edge : 0;
                if (hoverTip.hoverIsShipMovable)
                    hilightShip = -hoverTip.hoverShipID;
                else
                    hilightShip =
                      (player.isLegalShip(edge)) ? edge : 0;
            } else {
                hilightRoad = hoverTip.hoverRoadID;
                hilightShip = 0;
            }

            popupMenu.showBuild(x, y, hilightRoad, hoverTip.hoverSettlementID, hoverTip.hoverCityID, hilightShip);
        }
    }

    /** If the client has used the board popup menu to request building a piece,
     *  this method is used in client network-receive message treatment.
     *  @since 1.1.00
     */
    public boolean popupExpectingBuildRequest()
    {
        if (buildReqTimerTask == null)
            return false;
        return ! buildReqTimerTask.wasItSentAlready();
    }

    /**
     * Since player has chosen a piece to build from the right-click popup menu,
     * set fields to expect a placing GameState reply from server.
     * In case server doesn't reply, starts a timer to eventually send the
     * build-piece request regardless, after canceling any previous timer.
     *<P>
     * Does not send the initial build-type request; caller should do so afterwards
     * with {@link SOCBuildingPanel#clickBuildingButton(SOCGame, String, boolean)}.
     *<P>
     * To cancel this new pending build request, call {@link #popupClearBuildRequest()}.
     *<P>
     * When client receives the expected GameState from server, other client code will respond
     * by calling {@link #popupFireBuildingRequest()}.
     *
     * @param coord  Piece's edge or node coordinate
     * @param ptype  Piece type, like {@link SOCPlayingPiece#ROAD}
     * @see #popupExpectingBuildRequest()
     * @since 1.1.00
     */
    public void popupSetBuildRequest(int coord, int ptype)
    {
        if (coord == -1)
            coord = 0;  // road on edge 0x00

        Timer piTimer = playerInterface.getEventTimer();
        synchronized (piTimer)
        {
            if (buildReqTimerTask != null)
            {
                buildReqTimerTask.doNotSend();
                buildReqTimerTask.cancel();  // cancel any previous
            }
            buildReqTimerTask = new BoardPanelSendBuildTask(coord, ptype);
            // Run once, at maximum permissable delay;
            // hopefully the network is responsive and
            // we've heard back by then.
            piTimer.schedule(buildReqTimerTask, 1000 * BUILD_REQUEST_MAX_DELAY_SEC );
        }
    }

    /**
     * player decided to not build something, so cancel the {@link java.util.TimerTask}
     * that's waiting to tell the server what they wanted to build.
     * @since 1.1.00
     */
    public void popupClearBuildRequest()
    {
        Timer piTimer = playerInterface.getEventTimer();
        synchronized (piTimer)
        {
            if (buildReqTimerTask == null)
                return;
            buildReqTimerTask.doNotSend();
            buildReqTimerTask.cancel();
            buildReqTimerTask = null;
        }
    }

    /**
     * Client has received gamestate placing message; send the building request in reply.
     * @see #popupSetBuildRequest(int, int)
     * @since 1.1.00
     */
    public void popupFireBuildingRequest()
    {
        final Timer piTimer = playerInterface.getEventTimer();
        synchronized (piTimer)
        {
            if (buildReqTimerTask == null)
                return;
            buildReqTimerTask.sendOnceFromClientIfCurrentPlayer();
            buildReqTimerTask.cancel();
            buildReqTimerTask = null;
        }
        hoverTip.hideHoverAndPieces();  // Reset hover state
    }

    /**
     * Check and move ship from {@link #moveShip_fromEdge} to {@link #moveShip_toEdge}.
     * Also sets {@code moveShip_fromEdge} = 0, {@code moveShip_toEdge} = 0,
     * calls {@link #clearModeAndHilight(int) clearModeAndHilight}({@link SOCPlayingPiece#SHIP}).
     * Called from mouse click or popup menu.
     *<P>
     * Note that if {@code moveShip_toEdge} != 0, then {@link SOCGame#canMoveShip(int, int, int) SOCGame.canMoveShip}
     * ({@link #playerNumber}, {@link #moveShip_fromEdge}, {@link #moveShip_toEdge}) has probably already been called.
     *<P>
     * In scenario {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI}, checks if a gift port would be claimed by
     * placing a ship there.  If so, confirms with the user first with {@link ConfirmPlaceShipDialog}.
     * @since 2.0.00
     * @see BoardPopupMenu#tryMoveShipFromHere()
     */
    private final void tryMoveShipToEdge()
    {
        boolean clearMode = true;

        if (moveShip_fromEdge != 0)
        {
            if (game.canMoveShip(playerNumber, moveShip_fromEdge, moveShip_toEdge) != null)
            {
                if (game.isGameOptionSet
                    (SOCGameOptionSet.K_SC_FTRI) && ((SOCBoardLarge) board).canRemovePort(moveShip_toEdge))
                {
                    java.awt.EventQueue.invokeLater
                        (new ConfirmPlaceShipDialog(moveShip_toEdge, false, moveShip_fromEdge));
                    clearMode = false;
                } else {
                    playerInterface.getClient().getGameMessageSender().movePieceRequest
                        (game, playerNumber, SOCPlayingPiece.SHIP, moveShip_fromEdge, moveShip_toEdge);
                }
            }

            if (clearMode)
                moveShip_fromEdge = 0;
        }

        if (clearMode)
            clearModeAndHilight(SOCPlayingPiece.SHIP);  // exit the mode
    }

    /**
     * Text to be displayed as 2 lines superimposed over the
     * center of the board graphic (during game setup).
     * Either text2, or both, can be null to display nothing.
     * Keep the text short, because boardPanel may not be very wide ({@link #PANELX} pixels).
     * Will trigger a repaint.
     * @param text1 Line 1 (or only line) of text, or null
     * @param text2 Line 2 of text, or null; must be null if text1 is null
     * @throws IllegalArgumentException if text1 null, text2 non-null
     * @since 1.1.07
     * @see #setSuperimposedTopText(String)
     */
    public void setSuperimposedText(String text1, String text2)
        throws IllegalArgumentException
    {
        if ((superText1 == text1) && (superText2 == text2))
        {
            return;  // <--- Early return: text obviously unchanged ---

            // This quick check is an optimization.
            // Any of the 4 variables could be null.
            // It's not worth the additional complexity
            // needed to check vs null and then String.equals.
        }
        if ((superText1 == null) && (superText2 != null))
            throw new IllegalArgumentException("text2 not null, text1 null");

        superText1 = text1;
        superText2 = text2;
        superText1_w = 0;
        superText2_w = 0;
        superTextBox_w = 0;
        repaint();
    }

    /**
     * Text to be displayed as 1 lines superimposed over the
     * top center of the board graphic (during game play).
     * text can be null to display nothing.
     * Keep the text short, because boardPanel may not be very wide ({@link #PANELX} pixels).
     * Will trigger a repaint.
     * @param text Line of text, or null
     * @since 1.1.08
     * @see #setSuperimposedText(String, String)
     */
    public void setSuperimposedTopText(String text)
    {
        superTextTop = text;
        superTextTop_w = 0;
        superTextTopBox_w = 0;
        repaint();
    }

    /**
     * Find the edge coordinate, if any, of an (x, y) location from unscaled board pixels.
     *<P>
     * <b>Note:</b> For the 6-player classic board, edge 0x00 is a valid edge that
     * can be built on.  It is found here as -1, since a value of 0 marks an
     * invalid edge.
     *<P>
     * <b>Note:</b> For {@link SOCBoardLarge}, the 'sea' side of a coastal edge
     * is returned as the negative value of its edge coordinate, if {@code checkCoastal} is set.
     *
     * @param x  x coordinate, in unscaled board, not actual pixels;
     *           use {@link #scaleFromActual(int)} to convert before calling
     * @param y  y coordinate, in unscaled board, not actual pixels
     * @param checkCoastal  If true, check for coastal edges for ship placement:
     *           Mouse could be over the land half or the sea half of the edge's graphical area.
     *           Return positive edge coordinate for land, negative edge for sea.
     *           Ignored unless {@link #isLargeBoard}.
     *           Returns positive edge for non-coastal sea edges.
     * @return the coordinates of the edge, or 0 if none; -1 for the 6-player classic
     *     board's valid edge 0x00; -edge for the sea side of a coastal edge on the large board
     *     if {@code checkCoastal}.
     */
    private final int findEdge(int x, int y, final boolean checkCoastal)
    {
        // find which grid section the pointer is in
        int secX, secY;

        if (isLargeBoard)
        {
            secY = ((y - ((22*3)/2)) / (deltaY / 3));
            if ((secY % 3) == 1)
                x += 12;  // middle part of hex: adjust sector x-boundary
            secY = ((y - 22) / halfdeltaY);
            secX = ((x) / halfdeltaX);

            if ((secX < 0) || (secY < 0)
                || (secX > board.getBoardWidth())
                || (secY > board.getBoardHeight()))
                return 0;
                // TODO consider local fields for width,height

            int edge = (secY << 8) | secX;
            if (! (checkCoastal
                   && ((SOCBoardLarge) board).isEdgeCoastline(edge)))
                return edge;

            /**
             * Coastal edge.  Check for land vs sea.
             * We'll find the edge's line through this sector,
             * then determine if we're left or right of it,
             * then check that hex to see if it's land or sea.
             */
            final int edgeX, hexLeft, hexRight;

            // Determining (r,c) edge direction: | / \
            //   "|" if r is odd
            //   Otherwise: s = r/2
            //   "/" if (s,c) is even,odd or odd,even
            //   "\" if (s,c) is odd,odd or even,even
            if ((secY % 2) == 1)
            {
                // edge is "|".
                // middle is w / 2
                edgeX = halfdeltaX / 2;
                hexLeft = board.getAdjacentHexToEdge(edge, SOCBoard.FACING_W);
                hexRight = board.getAdjacentHexToEdge(edge, SOCBoard.FACING_E);
            }
            else
            {
                // y, relative to sector's upper-left corner
                final int yrel = ((y - 22) % halfdeltaY);
                if ((secX % 2) == ((secY/2) % 2))
                {
                    // edge is "\".
                    // at y=0 (relative to sector), check x=0
                    // at y=h, check x=w
                    // in middle, check x=(y/h)*w
                    //             which is (y*w) / h
                    edgeX = (yrel * halfdeltaX) / halfdeltaY;
                    hexLeft = board.getAdjacentHexToEdge(edge, SOCBoard.FACING_SW);
                    hexRight = board.getAdjacentHexToEdge(edge, SOCBoard.FACING_NE);
                } else {
                    // edge is "/".
                    // check x=((h-y)/h)*w
                    //  which is ((h-y)*w) / h
                    edgeX = ((halfdeltaY - yrel) * halfdeltaX) / halfdeltaY;
                    hexLeft = board.getAdjacentHexToEdge(edge, SOCBoard.FACING_NW);
                    hexRight = board.getAdjacentHexToEdge(edge, SOCBoard.FACING_SE);
                }
            }

            // check hex based on x, relative to sector's upper-left corner
            final int hex;
            if ((x % halfdeltaX) <= edgeX)
                hex = hexLeft;
            else
                hex = hexRight;

            if (board.isHexOnLand(hex))
                return edge;
            else
                return -edge;

        }  // if (isLargeBoard) ends

        // ( 46 is the y-distance between the centers of two hexes )
        // See edgeMap javadocs for secY, secX meanings.

        //int sector = (x / 18) + ((y / 10) * 15);
        if (is6player)
        {
            secY = (y - HEXY_OFF_6PL_FIND) / 15;
            if ((secY % 3) != 0)
                x += 8;  // middle part of hex: adjust sector boundary
            secX = (x - HEXX_OFF_6PL) / 27;
        } else {
            secY = y / 15;
            if ((secY % 3) != 0)
                x += 8;  // middle part of hex: adjust sector boundary
            secX = x / 27;
        }

        int sector = secX + (secY * 15);

        // System.out.println("SECTOR = "+sector+" | EDGE = "+edgeMap[sector]);
        if ((sector >= 0) && (sector < edgeMap.length))
            return edgeMap[sector];
        else
            return 0;
    }

    /**
     * Find the node coordinate, if any, of an (x, y) location from unscaled board pixels.
     *
     * @param x  x coordinate, in unscaled board, not actual pixels;
     *           use {@link #scaleFromActual(int)} to convert before calling
     * @param y  y coordinate, in unscaled board, not actual pixels
     * @return the coordinates of the node, or 0 if none
     */
    private final int findNode(int x, int y)
    {
        // find which grid section the pointer is in
        int secX, secY;

        if (isLargeBoard)
        {
            secX = ((x + 13) / halfdeltaX);
            secY = ((y - 20) / halfdeltaY);
            if ((secX < 0) || (secY < 0)
                || (secX > board.getBoardWidth())
                || (secY > board.getBoardHeight()))
                return 0;
            return (secY << 8) | secX;
        }

        // ( 46 is the y-distance between the centers of two hexes )
        //int sector = ((x + 9) / 18) + (((y + 5) / 10) * 15);

        if (is6player)
        {
            secX = ((x + 13 - HEXX_OFF_6PL) / 27);
            secY = ((y + 7 - HEXY_OFF_6PL_FIND) / 15);
        } else {
            secX = ((x + 13) / 27);
            secY = ((y + 7) / 15);
        }

        int sector = secX + (secY * 15);

        // System.out.println("SECTOR = "+sector+" | NODE = "+nodeMap[sector]);
        if ((sector >= 0) && (sector < nodeMap.length))
            return nodeMap[sector];
        else
            return 0;
    }

    /**
     * Find the hex coordinate, if any, of an (x, y) location from unscaled board pixels.
     *
     * @param x  x coordinate, in unscaled board, not actual pixels;
     *           use {@link #scaleFromActual(int)} to convert before calling
     * @param y  y coordinate, in unscaled board, not actual pixels
     * @return the coordinates of the hex, or 0 if none
     */
    private final int findHex(int x, int y)
    {
        // find which grid section the pointer is in
        int secX, secY;

        if (isLargeBoard)
        {
            secX = ((x + 13) / halfdeltaX);
            secY = ((y - 20) / halfdeltaY);
            final int hex = (secY << 8) | secX;
            if (-1 != ((SOCBoardLarge) board).getHexTypeFromCoord(hex))
                return hex;
            return 0;
        }

        // ( 46 is the y-distance between the centers of two hexes )
        //int sector = (x / 18) + ((y / 10) * 15);

        if (is6player)
        {
            secX = (x - HEXX_OFF_6PL) / 27;
            secY = (y - HEXY_OFF_6PL_FIND) / 15;
        } else {
            secX = x / 27;
            secY = y / 15;
        }

        int sector = secX + (secY * 15);

        // System.out.println("SECTOR = "+sector+" | HEX = "+hexMap[sector]);
        if ((sector >= 0) && (sector < hexMap.length))
            return hexMap[sector];
        else
            return 0;
    }

    /**
     * Set the interaction mode directly, for debugging purposes.
     *
     * @param m  mode, such as {@link #PLACE_ROAD}.
     *    <BR>
     *     If {@code m} is a bot-debugging {@code :consider-} mode (in range {@link #CONSIDER_LM_SETTLEMENT}
     *     - {@link #CONSIDER_LT_CITY}), but {@link #setOtherPlayer(SOCPlayer)} hasn't been called,
     *     does nothing.
     *
     * @see #updateMode()
     * @see #setModeMoveShip(int)
     * @see SOCPlayerInterface#doLocalCommand(String)
     */
    public void setMode(int m)
    {
        if ((m >= CONSIDER_LM_SETTLEMENT) && (m <= CONSIDER_LT_CITY))
        {
            if (otherPlayer == null)
                return;
        }

        mode = m;
        updateHoverTipToMode();
    }

    /**
     * Set the interaction mode to "move ship";
     * the player must now click where they want the ship to be moved.
     * Repaints the board immediately.
     * @param edge  Edge coordinate of our player's ship we're moving.
     *              Not checked for validity.
     * @see #tryMoveShipToEdge()
     * @since 2.0.00
     */
    public void setModeMoveShip(final int edge)
    {
        if (mode != NONE)
            throw new IllegalStateException();
        mode = MOVE_SHIP;
        moveShip_fromEdge = edge;
        moveShip_toEdge = 0;
        hilight = 0;
        repaint();
    }

    /**
     * Flush and reload the set of hex graphics currently selected in user preference
     * {@link SOCPlayerClient#PREF_HEX_GRAPHICS_SET}.
     *<P>
     * Note: Afterwards, you must call each current instance's {@link #flushBoardLayoutAndRepaint()}.
     *
     * @param c  Any {@link SOCBoardPanel} or other UI component packaged in the same jar,
     *     to load image resource files with getResource
     * @since 2.0.00
     */
    /* package */ static synchronized void reloadBoardGraphics(final Component c)
    {
        final boolean hadAnyRotated = (rotatHexes != null);
        if (hexes == null)
            return;  // Not loaded yet: nothing to do

        hexes = null;
        rotatHexes = null;
        loadImages(c, hadAnyRotated);
    }

    /**
     * Load the images for the board: {@link #hexes} and {@link #rotatHexes}.
     * Loads all hex types, up through {@link SOCBoardLarge#FOG_HEX},
     * because {@link #hexes} is static for all boards and all game options.
     * Checks {@link SOCPlayerClient#PREF_HEX_GRAPHICS_SET} for which graphics set to load.
     *<P>
     * May change value of {@link #hexesMustAlwaysScale} and/or {@link #rotatHexesMustAlwaysScale}:
     * If those hexes are loaded, calls {@link #checkNonstandardHexesSize(Image[], boolean)}.
     *
     * @param c  Our component, or any class packaged in the same jar,
     *     to load image resource files with getResource
     * @param wantsRotated  True for the 6-player non-sea board
     *          (v2 encoding {@link SOCBoard#BOARD_ENCODING_6PLAYER}), false otherwise.
     *          The large board (v3 encoding)'s fog-hex and gold-hex images have no rotated version,
     *          because that board layout is never rotated.
     */
    private static synchronized void loadImages(final Component c, final boolean wantsRotated)
    {
        if ((hexes != null) && ((rotatHexes != null) || ! wantsRotated))
            return;

        final Class<?> clazz = c.getClass();
        int setIdx = UserPreferences.getPref(SOCPlayerClient.PREF_HEX_GRAPHICS_SET, 0);
        if ((setIdx < 0) || (setIdx >= HEX_GRAPHICS_SET_SUBDIRS.length))
            setIdx = 0;
        final String hexSetDirBase = IMAGEDIR + "/" + HEX_GRAPHICS_SET_SUBDIRS[setIdx];

        if (hexes == null)
        {
            hexesGraphicsSetIndex = setIdx;

            hexes = new BufferedImage[10];  // water, desert, 5 resources, gold, fog, 3:1 port

            try
            {
                loadHexesAndImages(hexes, hexSetDirBase, clazz, false);
            } catch (IOException e) {
                System.out.println("Error loading board images");
            }

            hexesMustAlwaysScale = checkNonstandardHexesSize(hexes, false);
        }

        if (wantsRotated && (rotatHexes == null))
        {
            rotatHexes = new BufferedImage[8];  // only 8, not 10: large board (gold,fog) is not rotated

            try
            {
                loadHexesAndImages(rotatHexes, hexSetDirBase + "/rotat", clazz, true);
            } catch (IOException e) {
                System.out.println("Error loading rotated board images");
            }

            rotatHexesMustAlwaysScale = checkNonstandardHexesSize(rotatHexes, true);
        }
    }

    /**
     * Load hex and other related images from either normal, or rotated, resource location.
     * Calls blocking {@link ImageIO#read(java.net.URL)}.
     * Returned images are {@link BufferedImage} for performance.
     *<P>
     * Before v2.5.00, this method loaded asynchronously instead of blocking.
     *<P>
     * Before v1.1.20, this method was called {@code loadHexesPortsImages(..)}.
     *
     * @param newHexes Array to store hex images and 3:1 port image into; {@link #hexes} or {@link #rotatHexes}
     * @param imageDir Location for {@link Class#getResource(String)}: normal or rotated, under {@link #IMAGEDIR}
     *     including subdirectory from {@link #HEX_GRAPHICS_SET_SUBDIRS}
     * @param clazz  Our component, or any class packaged in the same jar,
     *     to load image resource files with getResource
     * @param wantsRotated  True for rotated, false otherwise;
     *             some hex types (goldHex, fogHex) aren't available in rotated versions,
     *             because their board layout is never rotated.
     *             This parameter isn't about whether the current board is rotated,
     *             but about whether this image directory's contents are rotated.
     * @throws IOException if an image can't be read
     * @see #renderPortImages()
     * @since 1.1.08
     */
    private static final void loadHexesAndImages
        (final BufferedImage[] newHexes, final String imageDir, final Class<?> clazz,
         final boolean wantsRotated)
        throws IOException
    {
        final int numHexImage;

        newHexes[0] = ImageIO.read(clazz.getResource(imageDir + "/waterHex.gif"));
        newHexes[1] = ImageIO.read(clazz.getResource(imageDir + "/clayHex.gif"));
        newHexes[2] = ImageIO.read(clazz.getResource(imageDir + "/oreHex.gif"));
        newHexes[3] = ImageIO.read(clazz.getResource(imageDir + "/sheepHex.gif"));
        newHexes[4] = ImageIO.read(clazz.getResource(imageDir + "/wheatHex.gif"));
        newHexes[5] = ImageIO.read(clazz.getResource(imageDir + "/woodHex.gif"));
        newHexes[6] = ImageIO.read(clazz.getResource(imageDir + "/desertHex.gif"));
        if (wantsRotated)
        {
            numHexImage = 8;
        } else {
            numHexImage = 10;
            newHexes[7] = ImageIO.read(clazz.getResource(imageDir + "/goldHex.gif"));
            newHexes[8] = ImageIO.read(clazz.getResource(imageDir + "/fogHex.gif"));
        }
        newHexes[numHexImage - 1] = ImageIO.read(clazz.getResource(imageDir + "/miscPort.gif"));
    }

    /**
     * Check whether any of the just-loaded hex images are a nonstandard size.
     * Standard size is {@link #HEXWIDTH} x {@link #HEXHEIGHT}.
     * When rotated, swap those constants for standard width x height.
     *
     * @param loaded  Hex images loaded by
     *     {@link #loadHexesAndImages(BufferedImage[], String, Class, boolean)},
     *     with same indexes as {@link #hexes} or {@link #rotatHexes}
     * @param isRotated  True if these images are rotated, and should be checked against
     *     size {@code HEXHEIGHT} x {@code HEXWIDTH}
     * @return  True if any are nonstandard, false if all hexes are standard size.
     * @since 2.0.00
     */
    private static boolean checkNonstandardHexesSize
        (final Image[] loaded, final boolean isRotated)
    {
        final int wantW = (isRotated) ? HEXHEIGHT : HEXWIDTH,
                  wantH = (isRotated) ? HEXWIDTH : HEXHEIGHT;

        for (final Image hexi : loaded)
        {
            if ((hexi.getWidth(null) != wantW) || (hexi.getHeight(null) != wantH))
                return true;
        }

        return false;
    }

    ///
    // ----- Utility methods -----
    ///

    /**
     * Hex color for a hex resource type
     * @param hexType  hexType value, as in {@link SOCBoard#DESERT_HEX}, {@link SOCBoard#WOOD_HEX},
     *                 {@link SOCBoard#WATER_HEX}.
     *                 Same value and meaning as those in {@link SOCBoard#getHexTypeFromCoord(int)}
     * @return The corresponding color from ColorSquare, or {@link ColorSquare#WATER} if hexType not recognized.
     * @since 1.1.07
     */
    public final Color hexColor(int hexType)
    {
        Color hexColor;
        switch (hexType)
        {
        case SOCBoard.DESERT_HEX:
            hexColor = ColorSquare.DESERT;
            break;
        case SOCBoard.CLAY_HEX:
            hexColor = ColorSquare.CLAY;
            break;
        case SOCBoard.ORE_HEX:
            hexColor = ColorSquare.ORE;
            break;
        case SOCBoard.SHEEP_HEX:
            hexColor = ColorSquare.SHEEP;
            break;
        case SOCBoard.WHEAT_HEX:
            hexColor = ColorSquare.WHEAT;
            break;
        case SOCBoard.WOOD_HEX:
            hexColor = ColorSquare.WOOD;
            break;
        case SOCBoardLarge.GOLD_HEX:
            if (isLargeBoard)
                hexColor = ColorSquare.GOLD;
            else
                hexColor = ColorSquare.WATER;  // for MISC_PORT_HEX
            break;
        case SOCBoardLarge.FOG_HEX:
            if (isLargeBoard)
                hexColor = ColorSquare.FOG;
            else
                hexColor = ColorSquare.WATER;  // for CLAY_PORT_HEX
            break;

        default:  // WATER_HEX
            hexColor = ColorSquare.WATER;
        }
        return hexColor;
    }

    /**
     * With a recent board resize, one or more rescaled images still hasn't
     * been completed after 7 seconds.  We've asked for a new scaled copy
     * of this image.  Wait 3 seconds and repaint the board.
     * (The delay gives time for the new scaling to complete.)
     *
     * @see SOCBoardPanel#scaledMissedImage
     * @see SOCBoardPanel#drawHex(Graphics, int)
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.00
     */
    protected static class DelayedRepaint extends Thread
    {
        /**
         * Are we already waiting in another thread?
         * Assumes since boolean is a simple var, will have atomic access.
         */
        private static boolean alreadyActive = false;
        private SOCBoardPanel bp;

        public DelayedRepaint (SOCBoardPanel bp)
        {
            setDaemon(true);
            this.bp = bp;
        }

        @Override
        public void run()
        {
            if (alreadyActive)
                return;

            alreadyActive = true;
            try
            {
                setName("delayedRepaint");
            }
            catch (Throwable th) {}

            try
            {
                Thread.sleep(RESCALE_RETRY_DELAY_MS);
            }
            catch (InterruptedException e) {}
            finally
            {
                alreadyActive = false;
                bp.repaint();
            }
        }
    }  // static class DelayedRepaint



    /**
     * (tooltip) Hover text for info on pieces/parts of the board.
     * Its mode uses boardpanel mode constants: Will be NONE, PLACE_ROAD,
     * PLACE_SETTLEMENT, PLACE_ROBBER for hex, or PLACE_INIT_SETTLEMENT for port.
     * Also contains "hovering" road/settlement/city near mouse pointer,
     * distinct from {@link SOCBoardPanel#hilight}.
     *
     * @author jdmonin
     * @since 1.1.00
     */
    protected class BoardToolTip
    {
        private final SOCBoardPanel bpanel;

        /** Text to hover-display, or null if nothing to show */
        private String hoverText;

        /** Uses board mode constants: Will be {@link SOCBoardPanel#NONE NONE},
         *  {@link SOCBoardPanel#PLACE_ROAD PLACE_ROAD}, PLACE_SHIP, PLACE_SETTLEMENT,
         *  PLACE_ROBBER for hex, or PLACE_INIT_SETTLEMENT for port.
         *  Updated in {@link #handleHover(int, int, int, int)}.
         */
        private int hoverMode;

        /** "ID" of coord as returned by {@link SOCBoardPanel#findNode(int, int) findNode}, findEdge, findHex */
        private int hoverID;

        /** Object last pointed at; null for hexes and ports */
        private SOCPlayingPiece hoverPiece;

        /** hover road ID, or 0. Readonly please from outside this inner class. Drawn in {@link #paint(Graphics)}.
         *  value is -1 for a road at edge 0x00.
         *<P>
         *  If both <tt>hoverRoadID</tt> and {@link #hoverShipID} are non-zero, they must be the same coordinate,
         *  never different non-zero values.
         */
        int hoverRoadID;

        /** hover settlement or city node ID, or 0. Readonly please from outside this inner class. Drawn in {@link #paint(Graphics)}. */
        int hoverSettlementID, hoverCityID;

        /**
         * hover ship ID, or 0. Readonly please from outside this inner class. Drawn in {@link #paint(Graphics)}.
         *<P>
         *  If both {@link #hoverRoadID} and <tt>hoverShipID</tt> are non-zero, they must be the same coordinate,
         *  never different non-zero values.
         *<P>
         * When setting this field, also set or clear {@link #hoverIsWarship}.
         * @since 2.0.00
         */
        int hoverShipID;

        /**
         * Is hover a port at coordinate hoverID?
         * @see #PLACE_INIT_SETTLEMENT
         */
        boolean hoverIsPort;

        /**
         * Is hover a ship owned by our player, movable from its current position?
         * If true, {@link #hoverShipID} is also set.
         * @since 2.0.00
         */
        private boolean hoverIsShipMovable;

        /**
         * Is hover a warship owned by our player, for scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}?
         * If true, {@link #hoverShipID} is also set.
         * @since 2.0.00
         */
        private boolean hoverIsWarship;

        /** Mouse position */
        private int mouseX, mouseY;

        /**
         * Flag to tell {@link #setHoverText(String, int)} to repaint, even if text hasn't changed.
         * @since 1.1.17
         */
        private boolean setHoverText_modeChangedOrMouseMoved;

        /** Our position (upper-left of tooltip box) */
        private int boxX, boxY;

        /** Requested X-offset from mouse pointer of tooltip box (used for robber placement) */
        private int offsetX;

        /** Our size.
         *  If boxw == 0, also indicates need fontmetrics - will be set in paint().
         */
        private int boxW, boxH;

        private final int TEXT_INSET = 3;
        private final int PADDING_HORIZ = 2 * TEXT_INSET + 2;

        BoardToolTip(SOCBoardPanel ourBoardPanel)
        {
            bpanel = ourBoardPanel;
            hoverText = null;
            hoverMode = NONE;
            hoverID = 0;
            hoverPiece = null;
            hoverRoadID = 0;
            hoverSettlementID = 0;
            hoverCityID = 0;
            hoverShipID = 0;
            hoverIsPort = false;
            hoverIsShipMovable = false;
            mouseX = 0;
            mouseY = 0;
            offsetX = 0;
            boxW = 0;
        }

        /** Currently displayed text.
         *
         * @return Tooltip text, or null if nothing.
         */
        public String getHoverText()
        {
            return hoverText;
        }

        /**
         * Is the hoverText tip non-null,
         * or is any hover ID non-zero? (hoverRoadID, etc)
         */
        public boolean isVisible()
        {
            return ((hoverText != null) || (hoverRoadID != 0)
                    || (hoverSettlementID != 0) || (hoverCityID != 0)
                    || (hoverShipID != 0));
        }

        /**
         * Show tooltip at appropriate location when mouse
         * is at (x,y) relative to the board.
         * Repaint the board.
         * @param x x-coordinate of mouse, actual screen pixels (not unscaled internal)
         * @param y y-coordinate of mouse, actual screen pixels (not unscaled internal)
         * @see #setHoverText(String, int)
         * @see #setHoverText(String, int, int, int)
         */
        public void positionToMouse(final int x, int y)
        {
            mouseX = x;
            mouseY = y;

            boxX = mouseX + offsetX;
            boxY = mouseY;
            if (offsetX < 5)
                boxY += 12;

            if (panelMinBW < ( boxX + boxW ))
            {
                // Try to float it to left of mouse pointer
                boxX = mouseX - boxW - offsetX;
                if (boxX < 0)
                {
                    // Not enough room, just place flush against right-hand side
                    boxX = panelMinBW - boxW;
                }
            }

            // if boxW == 0, we don't have the fontmetrics yet,
            // so paint() might need to change boxX or boxY
            // if we're near the bottom or right edge.

            bpanel.repaint();
            setHoverText_modeChangedOrMouseMoved = false;
            // JM TODO consider repaint(boundingbox).
        }

        /**
         * Set the hover tip (tooltip) x-position,
         * but don't repaint the board.
         * Used in robber placement.
         * @param ofsX  New offset
         */
        public void setOffsetX(int ofsX)
        {
            offsetX = ofsX;
        }

        /**
         * Set the hover text (tooltip) based on where the mouse is now,
         * and repaint the board.
         *<P>
         * Calls {@link #positionToMouse(int, int) positionToMouse(mouseX,mouseY)}.
         *
         * @param t Hover text contents, or null to clear that text (but
         *          not hovering pieces) and repaint.  Do nothing if text is
         *          already equal to <tt>t</tt>, or if both are null.
         * @param coord  Cursor's board coordinates shown when "show coordinates" debug flag is set, or -1.
         *          Ignored if {@code t} is {@code null}.  To show only the coordinate, use "" for {@code t}.
         * @see #setHoverText(String, int, int, int)
         * @see #hideHoverAndPieces()
         * @see SOCBoardPanel#setDebugShowCoordsFlag(boolean)
         */
        public void setHoverText(String t, final int coord)
        {
            if ((t != null) && (coord >= 0) && debugShowCoordsTooltip)
            {
                if (t.length() > 0)
                    t += " - 0x" + Integer.toHexString(coord);
                else
                    t = "0x" + Integer.toHexString(coord);
            }

            // If text unchanged, and mouse hasn't moved, do nothing:
            if ( (t == hoverText)  // (also covers both == null)
                 || ((t != null) && t.equals(hoverText)) )
            {
                if (! setHoverText_modeChangedOrMouseMoved)
                    return;
            }

            hoverText = t;
            if (t == null)
            {
                bpanel.repaint();
                setHoverText_modeChangedOrMouseMoved = false;
                return;
            }

            boxW = 0;  // Paint method will calculate it
            positionToMouse(mouseX, mouseY);  // Also calls repaint, clears setHoverText_modeChangedOrMouseMoved
        }

        /**
         * Set tooltip text (or hide tooltip if text is null) and show tooltip at appropriate location
         * when mouse is at (x,y) relative to the board. Repaint the board.
         *<P>
         * Convenience method, calls {@link #positionToMouse(int, int)} and {@link #setHoverText(String, int)}.
         *
         * @param t Hover text contents, or null to clear that text (but
         *          not hovering pieces) and repaint.  Do nothing if text is
         *          already equal to {@code t}, or if both are null.
         * @param coord  Cursor's board coordinates shown when "show coordinates" debug flag is set, or -1.
         *          Ignored if {@code t} is {@code null}.  To show only the coordinate, use "" for {@code t}.
         * @param x x-coordinate of mouse, actual screen pixels (not unscaled internal)
         * @param y y-coordinate of mouse, actual screen pixels (not unscaled internal)
         * @since 2.0.00
         */
        public void setHoverText(final String t, final int coord, final int x, final int y)
        {
            // TODO don't repaint twice
            positionToMouse(x, y);
            setHoverText(t, coord);
        }

        /**
         * Clear hover text, and cancel any hovering roads/settlements/cities.
         * Repaint the board.
         * The next call to {@link #handleHover(int, int, int, int)} will set up the
         * hovering pieces/text for the current mode.
         */
        public void hideHoverAndPieces()
        {
            hoverRoadID = 0;
            hoverSettlementID = 0;
            hoverCityID = 0;
            hoverShipID = 0;
            hoverIsPort = false;
            hoverIsShipMovable = false;
            hoverIsWarship = false;
            hoverText = null;
            setHoverText_modeChangedOrMouseMoved = false;
            bpanel.repaint();
        }

        /** Draw; Graphics should be the boardpanel's gc, as seen in its paint method. */
        public void paint(Graphics g)
        {
            if (playerNumber != -1)
            {
                final boolean xlat = (panelMarginX != 0) || (panelMarginY != 0);
                if (xlat)
                    g.translate(panelMarginX, panelMarginY);

                if (hoverRoadID != 0)
                {
                    if (! hoverIsShipMovable)
                        drawRoadOrShip(g, hoverRoadID, playerNumber, HilightStyle.HOVER, true, false);
                    else
                        drawRoadOrShip(g, hoverRoadID, -1, HilightStyle.HOVER, true, false);
                }
                if (hoverShipID != 0)
                {
                    drawRoadOrShip(g, hoverShipID, playerNumber, HilightStyle.HOVER, false, hoverIsWarship);
                }
                if (hoverSettlementID != 0)
                {
                    drawSettlement(g, hoverSettlementID, playerNumber, HilightStyle.HOVER, false);
                }
                if (hoverCityID != 0)
                {
                    drawCity(g, hoverCityID, playerNumber, HilightStyle.HOVER);
                }

                if (xlat)
                    g.translate(-panelMarginX, -panelMarginY);
            }

            String ht = hoverText;  // cache against last-minute change in another thread
            if (ht == null)
                return;

            if (boxW == 0)
            {
                // FontMetrics lookup, now that we have graphics info.
                // Use '-' not ' ' to get around stringWidth spacing bug.
                final Font bpf = bpanel.getFont();
                if (bpf == null)
                    return;
                final FontMetrics fm = g.getFontMetrics(bpf);
                if (fm == null)
                    return;
                boxW = fm.stringWidth(ht.replace(' ', '-')) + PADDING_HORIZ;
                boxH = fm.getHeight();

                // Check if we'd be past the bottom or right edge
                final int bpwidth = bpanel.getWidth();
                if (boxX + boxW > bpwidth)
                    boxX = bpwidth - boxW - 2;
                final int bpheight = bpanel.getHeight();
                if (boxY + boxH > bpheight)
                    boxY = bpheight - boxH - 2;
            }

            g.setColor(Color.WHITE);
            g.fillRect(boxX, boxY, boxW, boxH - 1);
            g.setColor(Color.BLACK);
            g.drawRect(boxX, boxY, boxW, boxH - 1);
            g.setFont(bpanel.getFont());
            g.drawString(ht, boxX + TEXT_INSET, boxY + boxH - TEXT_INSET);
        }

        /**
         * Mouse is hovering during normal play; look for info for tooltip text.
         * Assumes x or y has changed since last call.
         * Does not affect the "{@link SOCBoardPanel#hilight hilight}" variable used by SOCBoardPanel during
         * initial placement, and during placement from clicking "Buy" buttons.
         *<P>
         * If the board mode doesn't allow hovering pieces (ghost pieces), will clear
         * hoverRoadID, hoverSettlementID, and hoverCityID to 0.
         * Otherwise, these are set when the mouse is at a location where the
         * player can build or upgrade, and they have resources to build.
         *<P>
         * Priority when hovering over a point on the board:
         *<UL>
         *<LI> Look first for settlements or ports
         *<LI> If not over a settlement, look for a road or ship
         *<LI> If no road, look for a hex
         *<LI> If no piece currently at the point, look for potential pieces and
         *     scenario-specific items such as Villages, the Pirate Path (Added Layout Part {@code "PP"}),
         *     and members of Special Node lists ({@code "N1" - "N3"}).
         *</UL>
         *
         * @param x Cursor x, from upper-left of board: actual coordinates, not board-internal coordinates
         * @param y Cursor y, from upper-left of board: actual coordinates, not board-internal coordinates
         * @param xb  Internal board-pixel position calculated from x
         * @param yb  Internal board-pixel position calculated from y
         */
        private void handleHover(final int x, int y, final int xb, final int yb)
        {
            if ((x != mouseX) || (y != mouseY))
            {
                mouseX = x;
                mouseY = y;
                setHoverText_modeChangedOrMouseMoved = true;
            }

            // Variables set in previous call to handleHover:
            // hoverMode, hoverID, hoverText.
            // Check whether they have changed.
            // If not, just move the tooltip with positionToMouse.

            /** Coordinates on board (a node, edge, or hex) */
            int id;

            boolean modeAllowsHoverPieces = ((mode != PLACE_INIT_SETTLEMENT)
                && (mode != PLACE_INIT_ROAD) && (mode != PLACE_ROBBER) && (mode != PLACE_PIRATE)
                && (mode != TURN_STARTING) && (mode != GAME_OVER));

            final boolean debugPP = game.isDebugFreePlacement();
            final boolean playerIsCurrent =
                (player != null) && (debugPP || playerInterface.isClientCurrentPlayer());
            boolean hoverTextSet = false;  // True once text is determined

            /** If we're hovering at a node port, store its coordinate here and also set {@link #nodePortType} */
            int nodePortCoord = -1;

            /** Node port type, from board.getPortTypeFromNodeCoord, for hoverText if nothing more important nearby */
            int nodePortType = -1;

            if (! modeAllowsHoverPieces)
            {
                hoverRoadID = 0;
                hoverSettlementID = 0;
                hoverCityID = 0;
                hoverShipID = 0;
            }

            /**
             * Wrap try-catch(ConcurrentModificationException) around thread-unsafe board methods
             */

            try
            {

            // Look first for settlements/cities or ports
            id = findNode(xb, yb);
            if (id > 0)
            {
                // Are we already looking at it?
                if ((hoverMode == PLACE_SETTLEMENT) && (hoverID == id))
                {
                    positionToMouse(x, y);
                    return;  // <--- Early ret: No work needed ---
                }

                // Is anything there?
                // Check for settlements, cities, ports, fortresses:
                SOCPlayingPiece p = board.settlementAtNode(id);
                if (p == null)
                    p = game.getFortress(id);  // pirate fortress (scenario option _SC_PIRI) or null

                if (p != null)
                {
                    hoverMode = PLACE_SETTLEMENT;
                    hoverPiece = p;
                    hoverID = id;

                    StringBuffer sb = new StringBuffer();
                    String pieceExtraDesc = null;  // {2} in localized string for SC_PIRI fortress, otherwise unused
                    String portDesc = portDescAtNode(id);
                    if (portDesc != null)
                    {
                        sb.append(portDesc);  // "game.port.three", "game.port.wood"
                        if (p.getType() == SOCPlayingPiece.CITY)
                            sb.append(".city");
                        else
                            sb.append(".stlmt");  // port, not port city
                        hoverIsPort = true;
                    }
                    else
                    {
                        if (p.getType() == SOCPlayingPiece.CITY)
                            sb.append("board.city");
                        else
                            sb.append("board.stlmt");
                    }
                    String plName = p.getPlayer().getName();
                    if (plName == null)
                        plName = strings.get("board.unowned");  // "unowned"
                    if (p instanceof SOCFortress)
                    {
                        // fortress is never a port or city
                        sb.setLength(0);
                        sb.append("board.sc_piri.piratefortress");
                        if ((player != null) && (player == p.getPlayer()))
                        {
                            // Can client player attack now, or need to build ships there first?
                            if (game.canAttackPirateFortress(player, true) != null)
                            {
                                pieceExtraDesc = (SOCPlayerClient.IS_PLATFORM_MAC_OSX)
                                    ? "board.sc_piri.pf_extra.attack.osx"  // ". Control-click to attack this fortress."
                                    : "board.sc_piri.pf_extra.attack";     // ". Right-click to attack this fortress."
                            } else {
                                pieceExtraDesc = "board.sc_piri.pf_extra.build";
                                    // ". To attack this fortress, build ships to it."
                            }
                            pieceExtraDesc = strings.get(pieceExtraDesc);
                        }
                    }

                    if (pieceExtraDesc == null)
                        pieceExtraDesc = "";
                    String htext = strings.get(sb.toString(), plName, board.getPortTypeFromNodeCoord(id), pieceExtraDesc);
                    if (p == latestPiecePlacement)
                        htext += " " + strings.get("board.most_recent_placement");  // "(Most recent placement)"
                    setHoverText(htext, id);
                    hoverTextSet = true;

                    // If we're at the player's settlement, ready to upgrade to city
                    if (modeAllowsHoverPieces && playerIsCurrent
                         && (p.getPlayer() == player)
                         && (p.getType() == SOCPlayingPiece.SETTLEMENT)
                         && (player.isPotentialCity(id))
                         && (player.getNumPieces(SOCPlayingPiece.CITY) > 0)
                         && (debugPP || player.getResources().contains(SOCCity.COST)))
                    {
                        hoverCityID = id;
                    } else {
                        hoverCityID = 0;
                    }
                    hoverSettlementID = 0;
                }
                else
                {
                    // Nothing currently here.
                    // Look for potential pieces.

                    hoverSettlementID = 0;

                    // Villages for Cloth trade scenario
                    if (game.isGameOptionSet(SOCGameOptionSet.K_SC_CLVI))
                    {
                        SOCVillage vi = ((SOCBoardLarge) board).getVillageAtNode(id);
                        if (vi != null)
                        {
                            hoverMode = PLACE_ROBBER;  // const used for hovering-at-node
                            hoverID = id;
                            hoverIsPort = false;
                            hoverTextSet = true;
                            hoverCityID = 0;
                            setHoverText
                                (strings.get("board.sc_clvi.village", vi.diceNum, vi.getCloth()), id);
                                // "Village for cloth trade on {0} ({1} cloth)"
                        }
                    }

                    if (playerIsCurrent && ! hoverTextSet)
                    {
                        // Can we place here?
                        hoverCityID = 0;
                        if (modeAllowsHoverPieces
                            && (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0)
                            && (debugPP || player.getResources().contains(SOCSettlement.COST)))
                        {
                            if (player.canPlaceSettlement(id))
                            {
                                hoverSettlementID = id;
                            }
                            else if (player.isPotentialSettlement(id))
                            {
                                setHoverText(strings.get("board.settle.not.here"), id);  // "Not allowed to settle here"
                                hoverMode = PLACE_ROBBER;  // const used for hovering-at-node
                                hoverID = id;
                                hoverIsPort = false;
                                hoverTextSet = true;
                            }
                        }
                    }

                    // Initial Placement on large board: Check for
                    // a restricted starting land area.
                    // For _SC_PIRI, check for hovering at "LS" lone settlement node.
                    if (playerIsCurrent && game.hasSeaBoard && ! hoverTextSet)
                    {
                        String htext = null;

                        final int[] ls = ((SOCBoardLarge) board).getAddedLayoutPart("LS");
                        if (ls != null)
                        {
                            for (int i = ls.length - 1; i >= 0; --i)
                            {
                                if (id == ls[i])
                                {
                                    if (game.isInitialPlacement())
                                        htext = "board.sc_piri.lone.stlmt.after";  // "Lone Settlement location allowed on pirate island after initial placement"
                                    else
                                        htext = "board.sc_piri.lone.stlmt";  // "Lone Settlement location allowed on pirate island"

                                    break;
                                }
                            }
                        }

                        if ((htext == null)
                            && game.isInitialPlacement()
                            && player.isLegalSettlement(id)
                            && ! player.isPotentialSettlement(id))
                        {
                            htext = "board.initial.not.here";  // "Initial placement not allowed here"
                        }

                        if (htext != null)
                        {
                            setHoverText(strings.get(htext), id);
                            hoverMode = PLACE_ROBBER;  // const used for hovering-at-node
                            hoverID = id;
                            hoverIsPort = false;
                            hoverTextSet = true;
                        }
                    }

                    if (! hoverTextSet)
                    {
                        // Check for ports. Will show only if nothing else is nearby.

                        nodePortType = board.getPortTypeFromNodeCoord(id);
                        if (nodePortType != -1)
                        {
                            // Make note of port info, will show it only if nothing more important is
                            // found nearby. This prevents the port from "covering up" pieces on adjacent
                            // edges that the user may want to click on.

                            nodePortCoord = id;
                        }
                    }

                    // Check special nodes in sea board scenarios.
                    //     Currently hardcoded to _SC_WOND only; if other scenarios use node lists, code
                    //     here must be generalized to check for added layout part "N1" and game option "SC".
                    if ((! hoverTextSet) && game.hasSeaBoard && game.isGameOptionSet(SOCGameOptionSet.K_SC_WOND))
                    {
                        // Check node lists "N1"-"N3" for this node coordinate. If found, use node list's name string
                        int i;
                        int[] nlist;
                        for (i = 1, nlist = ((SOCBoardLarge) board).getAddedLayoutPart("N1");
                             (nlist != null) && ! hoverTextSet;
                             ++i, nlist = ((SOCBoardLarge) board).getAddedLayoutPart("N" + i))
                        {
                            for (int j = 0; j < nlist.length; ++j)
                            {
                                if (nlist[j] == id)
                                {
                                    String nlDesc = null;

                                    try {
                                        nlDesc = strings.get("board.nodelist._SC_WOND.N" + i);
                                    } catch (MissingResourceException e) {}

                                    if (nlDesc == null)
                                    {
                                        try {
                                            nlDesc = strings.get("board.nodelist.no_desc", i);
                                        } catch (MissingResourceException e) {}
                                    }

                                    if (nlDesc != null)
                                    {
                                        setHoverText(nlDesc, id);
                                        hoverMode = PLACE_ROBBER;  // const used for hovering-at-node
                                        hoverID = id;
                                        hoverIsPort = false;
                                        hoverTextSet = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                }  // end if-node-has-settlement
            }
            else
            {
                hoverSettlementID = 0;
                hoverCityID = 0;
            }

            // If not over a node (settlement), look for an edge (road or ship)
            id = findEdge(xb, yb, false);
            if (id != 0)
            {
                // Are we already looking at it?
                if ((hoverID == id) && ((hoverMode == PLACE_ROAD) || (hoverMode == PLACE_SHIP)))
                {
                    positionToMouse(x, y);
                    return;  // <--- Early ret: No work needed ---
                }

                hoverRoadID = 0;
                hoverShipID = 0;
                hoverIsShipMovable = false;
                hoverIsWarship = false;

                // Is a road or ship there?
                final SOCRoutePiece rs = board.roadOrShipAtEdge(id);
                if (rs != null)
                {
                    if (! hoverTextSet)
                    {
                        final boolean isRoad = rs.isRoadNotShip();
                        if (isRoad)
                            hoverMode = PLACE_ROAD;
                        else
                            hoverMode = PLACE_SHIP;
                        hoverPiece = rs;
                        hoverID = id;
                        String plName = rs.getPlayer().getName();
                        if (plName == null)
                            plName = strings.get("board.unowned");  // "unowned"

                        String htext;
                        if (isRoad)
                        {
                            htext = strings.get("board.road", plName);  // "Road: " + plName
                        } else {
                            // Scenario _SC_PIRI has warships; check class just in case.
                            hoverIsWarship = (rs instanceof SOCShip) && game.isShipWarship((SOCShip) rs);
                            if (hoverIsWarship)
                                htext = strings.get("board.warship", plName);  // "Warship: " + plName
                            else
                                htext = strings.get("board.ship", plName);     // "Ship: " + plName
                        }
                        if (rs == latestPiecePlacement)
                            htext += ' ' + strings.get("board.most_recent_placement");  // "(Most recent placement)"
                        setHoverText(htext, id);

                        // Can the player move their ship?
                        if (modeAllowsHoverPieces && playerIsCurrent
                             && (! isRoad)
                             && (rs.getPlayer() == player)
                             && (game.canMoveShip(playerNumber, hoverID) != null))
                        {
                            hoverIsShipMovable = true;
                            hoverShipID = id;
                        }
                    }

                    return;  // <--- Early return: Found road ---
                }
                else if (playerIsCurrent)
                {
                    // No piece there
                    if (modeAllowsHoverPieces)
                    {
                        // If this edge is coastal, do we show a road or a ship?
                        // Have findEdge determine if we're on the land or sea side.
                        final boolean canPlaceShip = game.canPlaceShip(player, id);
                        boolean isShip = false;
                        if (isLargeBoard)
                        {
                            if (player.isPotentialRoad(id)
                                && ((SOCBoardLarge) board).isEdgeCoastline(id))
                            {
                                id = findEdge(xb, yb, true);
                                if (id < 0)
                                {
                                    id = -id;
                                    isShip = canPlaceShip;
                                }
                            } else {
                                isShip = canPlaceShip;
                            }
                        }

                        if ((! isShip)
                            && player.isPotentialRoad(id)
                            && (player.getNumPieces(SOCPlayingPiece.ROAD) > 0)
                            && (debugPP || player.getResources().contains(SOCRoad.COST)))
                        {
                            hoverRoadID = id;
                        }
                        else if (canPlaceShip  // checks isPotentialShip, pirate ship
                            && (player.getNumPieces(SOCPlayingPiece.SHIP) > 0)
                            && (debugPP || player.getResources().contains(SOCShip.COST)))
                        {
                            hoverShipID = id;
                        }
                    }
                }

                // If nothing else at this edge, look for a Special Edge (usually none)
                if ((! hoverTextSet) && isLargeBoard && (hoverRoadID == 0) && (hoverShipID == 0))
                {
                    final String hoverTextKey;
                    switch (((SOCBoardLarge) board).getSpecialEdgeType(id))
                    {
                    case SOCBoardLarge.SPECIAL_EDGE_DEV_CARD:
                        hoverTextKey = "board.edge.devcard";  // "Receive Dev card for placing a ship here"
                        break;

                    case SOCBoardLarge.SPECIAL_EDGE_SVP:
                        hoverTextKey = "board.edge.svp";  // "Receive 1 SVP for placing a ship here"
                        break;

                    default:
                        // not special or not a recognized type
                        hoverTextKey = null;
                    }

                    if (hoverTextKey != null)
                    {
                        setHoverText(strings.get(hoverTextKey), id);
                        hoverTextSet = true;
                    }
                }
            }

            // By now we've set hoverRoadID, hoverShipID, hoverCityID, hoverSettlementID.
            // If debugShowCoordsTooltip their coordinates aren't shown yet with hoverText,
            // that's done below only if nothing else sets hoverText and returns.

            if (hoverTextSet)
            {
                return;  // <--- Early return: Text and hover-pieces set ---
            }

            // If nothing more important was found nearby, show port info
            if (nodePortCoord != -1)
            {
                if ((hoverMode == PLACE_INIT_SETTLEMENT) && (hoverID == nodePortCoord) && hoverIsPort)
                {
                    // Already looking at a port at this coordinate.
                    positionToMouse(x, y);
                } else {
                    String portText = strings.get(portDescAtNode(nodePortCoord), nodePortType);

                    if (isLargeBoard && game.isGameOptionSet(SOCGameOptionSet.K_SC_FTRI))
                    {
                        // Scenario _SC_FTRI: If this port can be reached and moved
                        // ("gift from the forgotten tribe"), mention that in portText.

                        final SOCBoardLarge bl = (SOCBoardLarge) board;
                        int portEdge = bl.getPortEdgeFromNode(nodePortCoord);
                        if ((portEdge != -9) && bl.canRemovePort(portEdge))
                            portText = strings.get("board.edge.ship_receive_this", portText);
                                // "Place a ship here to receive this " + portText
                    }

                    setHoverText(portText, nodePortCoord);
                    hoverMode = PLACE_INIT_SETTLEMENT;  // const used for hovering-at-port
                    hoverID = nodePortCoord;
                    hoverIsPort = true;
                }

                return;  // <--- Early return: Text and hover-pieces set ---
            }

            // If nothing found yet, look for a hex
            //  - reminder: socboard.getHexTypeFromCoord, getNumberOnHexFromCoord, socgame.getPlayersOnHex
            id = findHex(xb, yb);
            if ((id > 0) && ! (debugShowCoordsTooltip && (hoverRoadID != 0 || hoverShipID != 0) ))
            {
                // Are we already looking at it?
                if (((hoverMode == PLACE_ROBBER) || (hoverMode == PLACE_PIRATE)) && (hoverID == id))
                {
                    positionToMouse(x, y);
                    return;  // <--- Early ret: No work needed ---
                }

                if (game.getGameState() == SOCGame.PLACING_PIRATE)
                    hoverMode = PLACE_PIRATE;
                else
                    hoverMode = PLACE_ROBBER;  // const used for hovering-at-hex

                hoverPiece = null;
                hoverID = id;

                {
                    final int htype = board.getHexTypeFromCoord(id);
                    final int dicenum = board.getNumberOnHexFromCoord(id);

                    StringBuffer key = new StringBuffer("game.hex.hoverformat");
                    String hname = "";
                    String addinfo = "";
                    int hid = htype;
                    boolean showDice = false;

                    switch (htype)
                    {
                    case SOCBoard.DESERT_HEX:
                        hname = "board.hex.desert";  break;
                    case SOCBoard.CLAY_HEX:
                        hname = "resources.clay";    break;
                    case SOCBoard.ORE_HEX:
                        hname = "resources.ore";     break;
                    case SOCBoard.SHEEP_HEX:
                        hname = "resources.sheep";   break;
                    case SOCBoard.WHEAT_HEX:
                        hname = "resources.wheat";   break;
                    case SOCBoard.WOOD_HEX:
                        hname = "resources.wood";    break;
                    case SOCBoard.WATER_HEX:
                        hname = "board.hex.water";   break;

                    case SOCBoardLarge.GOLD_HEX:
                        if (isLargeBoard)
                        {
                            hname = "board.hex.gold";
                        } else {
                            // GOLD_HEX is also MISC_PORT_HEX
                            hid = SOCBoard.MISC_PORT;
                            hname = SOCBoard.getPortDescForType(hid, false);
                        }
                        break;

                    case SOCBoardLarge.FOG_HEX:
                        if (isLargeBoard)
                        {
                            if (game.isInitialPlacement() && (player != null)
                                && player.hasPotentialSettlementsInitialInFog())
                                hname = "board.hex.fog.s";  // "Fog (place ships or settlements to reveal)"
                            else
                                hname = "board.hex.fog.r";  // "Fog (place ships or roads to reveal)"
                        } else {
                            // FOG_HEX is also CLAY_PORT_HEX
                            hid = SOCBoard.CLAY_PORT;
                            hname = SOCBoard.getPortDescForType(hid, false);
                        }
                        break;

                    default:
                        {
                            // Check for a port at this hex.
                            // (May already have checked above for the node, using portDescAtNode;
                            //  only the original board layout encodes ports into the hex types.)
                            String portDesc = null;
                            if ((htype >= SOCBoard.MISC_PORT_HEX) && (htype <= SOCBoard.WOOD_PORT_HEX))
                            {
                                hid = htype - (SOCBoard.MISC_PORT_HEX - SOCBoard.MISC_PORT);
                                portDesc = SOCBoard.getPortDescForType(hid, false);
                            }
                            if (portDesc != null)
                            {
                                hname = portDesc;
                            } else {
                                hid = htype;
                                hname = "board.hex.generic";
                            }
                        }
                    }
                    if (board.getRobberHex() == id)
                    {
                        showDice = (dicenum > 0);
                        addinfo = "game.hex.addinfo.robber";
                    }
                    else if (board.getPreviousRobberHex() == id)
                    {
                        showDice = (dicenum > 0);
                        addinfo = "game.hex.addinfo.past.robber";
                    }
                    else if (isLargeBoard)
                    {
                        final SOCBoardLarge bl = (SOCBoardLarge) board;
                        if (bl.getPirateHex() == id)
                        {
                            showDice = (dicenum > 0);
                            addinfo = "game.hex.addinfo.pirate";
                        }
                        else if (bl.getPreviousPirateHex() == id)
                        {
                            showDice = (dicenum > 0);
                            addinfo = "game.hex.addinfo.past.pirate";
                        }
                        else if (bl.isHexInLandAreas(id, bl.getPlayerExcludedLandAreas()))
                        {
                            // Give the player an early warning, even if roads/ships aren't near this hex
                            addinfo = "game.hex.addinfo.cantsettle";
                        }
                    }

                    hname = strings.get(hname, hid);
                    if(showDice){
                        key.append(".dice");
                    }
                    if(addinfo.length() != 0){
                        key.append(".addi");
                        addinfo = strings.get(addinfo);
                    }
                    setHoverText(strings.get(key.toString(), hname, dicenum, addinfo), id);
                }

                return;  // <--- Early return: Found hex ---
            }

            } catch (ConcurrentModificationException e) {
                handleHover(x, y, xb, yb);  // try again now
                return;
            }

            // if we're down here, hoverText was never set, but hoverPieceIDs may be set.
            // If debugShowCoordsTooltip, show their coordinates with hoverText.
            // Don't check hoverCityID, because we have a settlement there and its tooltip
            // already shows the coordinate.

            if ((hoverSettlementID != 0) && debugShowCoordsTooltip)
            {
                setHoverText("", hoverSettlementID);
                return;
            }
            else if ((hoverRoadID != 0) || (hoverShipID != 0))
            {
                // hoverMode == PLACE_ROAD or PLACE_SHIP

                if (debugShowCoordsTooltip)
                    setHoverText("", (hoverRoadID != 0) ? hoverRoadID : hoverShipID);
                else
                    setHoverText(null, 0);

                bpanel.repaint();
                return;
            }

            // If no hex, nothing.
            if (hoverMode != NONE)
            {
                setHoverText_modeChangedOrMouseMoved = true;
                hoverMode = NONE;
            }
            setHoverText(null, 0);
        }

        /**
         * Check at this node coordinate for a port, and return its descriptive text.
         * Does not check for players' settlements or cities, only for the port.
         *
         * @param id Node coordinate ID for potential port
         *
         * @return String key with port text description for {@link SOCStringManager#get(String)},
         *    or {@code null} if no port at that node id.
         *    Text format of string key's value is "3:1 Port" or "2:1 Wood port".
         */
        public String portDescAtNode(int id)
        {
            return SOCBoard.getPortDescForType(board.getPortTypeFromNodeCoord(id), false);
        }

    }  // inner class BoardToolTip



    /**
     * This class creates a popup menu on the board,
     * to trade or build or cancel building.
     *<P>
     * {@link BoardPopupMenu#actionPerformed(ActionEvent)} usually calls
     * {@link SOCBuildingPanel#clickBuildingButton(SOCGame, String, boolean)}
     * to send messages to the server.
     * @since 1.1.00
     */
    private class BoardPopupMenu extends PopupMenu
        implements java.awt.event.ActionListener
    {
      /** our parent boardpanel */
      final SOCBoardPanel bp;

      final MenuItem buildRoadItem, buildSettleItem, upgradeCityItem;

      /**
       * Menu item to build or move a ship if {@link SOCGame#hasSeaBoard}, or null.
       * @since 2.0.00
       */
      final MenuItem buildShipItem;

      /**
       * Menu item to cancel a build as we're placing it,
       * or cancel moving a ship,
       * or undo previous placement/move in {@link SOCGame#PLAY1}.
       * Piece type to cancel/undo is {@link #cancelBuildType}.
       * The only piece that can be undone is {@link SOCBoardPanel#latestPiecePlacement}.
       * Enabled/disabled by methods like {@link #showBuild(int, int, int, int, int, int)}.
       * @see #wantsCancel
       * @see #wantsUndo
       */
      final MenuItem cancelBuildItem;

      /** determined at menu-show time, only over a useable port. Added then, and removed at next menu-show */
      SOCHandPanel.ResourceTradePopupMenu portTradeSubmenu;

      /** determined at menu-show time */
      private boolean menuPlayerIsCurrent;

      /** determined at menu-show time */
      private boolean wantsCancel, wantsUndo;

      /** If allow cancel, type of building piece ({@link SOCPlayingPiece#ROAD}, SETTLEMENT, ...) to cancel */
      private int cancelBuildType;

      /** hover road edge ID, or 0, at menu-show time; can be -1 for edge 0x00 on classic 6-player board */
      private int hoverRoadID;

      /**
       * hover settlement node ID, or 0, at menu-show time.
       * As a special case in the _SC_PIRI scenario, hoverSettlementID == -1 indicates any pirate Fortress;
       * {@link #buildSettleItem}'s text will be "Attack Fortress" instead of "Build Settlement";
       * menu item will be disabled unless it's player's own fortress and {@link SOCGame#canAttackPirateFortress()}.
       */
      private int hoverSettlementID;

      /** hover city node ID, or 0, at menu-show time */
      private int hoverCityID;

      /**
       * hover ship edge ID, or 0, at menu-show time.
       * @since 2.0.00
       */
      private int hoverShipID;

      /**
       * True if we can move a ship, at menu-show time.
       * {@link #hoverShipID} must be != 0.
       * @since 2.0.00
       */
      private boolean isShipMovable;

      /** Will this be for initial placement (send putpiece right away),
       *  or for placement during game (send build, receive gamestate, send putpiece)?
       */
      protected boolean isInitialPlacement;

      /** create a new BoardPopupMenu on this board */
      public BoardPopupMenu(SOCBoardPanel bpanel)
      {
        super ("JSettlers");
        bp = bpanel;

        buildRoadItem = new MenuItem(strings.get("board.build.road"));  // "Build Road"
        buildSettleItem = new MenuItem(strings.get("board.build.stlmt"));  // "Build Settlement"
        upgradeCityItem = new MenuItem(strings.get("board.build.upgrade.city"));  // "Upgrade to City"
        if (game.hasSeaBoard)
            buildShipItem = new MenuItem(strings.get("board.build.ship"));  // "Build Ship"
        else
            buildShipItem = null;
        cancelBuildItem = new MenuItem(strings.get("board.cancel.build"));  // "Cancel build"
        portTradeSubmenu = null;

        add(buildRoadItem);
        add(buildSettleItem);
        add(upgradeCityItem);
        if (buildShipItem != null)
            add(buildShipItem);
        addSeparator();
        add(cancelBuildItem);

        buildRoadItem.addActionListener(this);
        buildSettleItem.addActionListener(this);
        upgradeCityItem.addActionListener(this);
        if (buildShipItem != null)
            buildShipItem.addActionListener(this);
        cancelBuildItem.addActionListener(this);
      }

      /** Custom 'cancel' show method for when placing a road/settlement/city,
       *  giving the build/cancel options for that type of piece.
       *
       * @param buildType piece type (SOCPlayingPiece.ROAD, CITY, SETTLEMENT)
       * @param x   Mouse x-position
       * @param y   Mouse y-position
       * @param hilightAt Current hover/hilight coordinates of piece being cancelled/placed
       */
      public void showCancelBuild(int buildType, int x, int y, int hilightAt)
      {
          menuPlayerIsCurrent = (player != null) && playerInterface.isClientCurrentPlayer();
          wantsCancel = true;
          cancelBuildType = buildType;
          wantsUndo = false;
          hoverRoadID = 0;
          if (hoverSettlementID == -1)
          {
              // restore label after previous popup's "Attack Fortress" label for _SC_PIRI
              buildSettleItem.setLabel(strings.get("board.build.stlmt"));  // "Build Settlement"
          }
          hoverSettlementID = 0;
          hoverCityID = 0;
          hoverShipID = 0;

          buildRoadItem.setEnabled(false);
          buildSettleItem.setEnabled(false);
          upgradeCityItem.setEnabled(false);
          if (buildShipItem != null)
          {
              if (mode == MOVE_SHIP)
              {
                  final boolean enable = (hilightAt != 0) && (hilightAt != moveShip_fromEdge);
                  buildShipItem.setEnabled(enable);
                  buildShipItem.setLabel(strings.get("board.build.move.ship"));  // "Move Ship"
                  if (enable)
                      moveShip_toEdge = hilightAt;
              } else {
                  buildShipItem.setEnabled(false);
                  buildShipItem.setLabel(strings.get("board.build.ship"));  // "Build Ship"
              }
          }
          boolean enableCancel = menuPlayerIsCurrent && game.canCancelBuildPiece(buildType);
          if (enableCancel && ! game.isPractice)
          {
              final int gameState = game.getGameState(), sVersion = playerInterface.client.sVersion;
              if (((gameState == SOCGame.PLACING_FREE_ROAD1) && (sVersion < SOCGame.VERSION_FOR_CANCEL_FREE_ROAD1))
                  || ((gameState == SOCGame.PLACING_FREE_ROAD2) && (sVersion < SOCGame.VERSION_FOR_CANCEL_FREE_ROAD2)))
                  enableCancel = false;
          }
          cancelBuildItem.setEnabled(enableCancel);

          // Check for initial placement (for different cancel message)
          isInitialPlacement = game.isInitialPlacement();

          switch (buildType)
          {
          case SOCPlayingPiece.ROAD:
              cancelBuildItem.setLabel(strings.get("board.cancel.road"));  // "Cancel road"
              buildRoadItem.setEnabled(menuPlayerIsCurrent);
              hoverRoadID = hilightAt;
              break;

          case SOCPlayingPiece.SETTLEMENT:
              cancelBuildItem.setLabel(strings.get("board.cancel.stlmt"));  // "Cancel settlement"
              buildSettleItem.setEnabled(menuPlayerIsCurrent);
              hoverSettlementID = hilightAt;
              break;

          case SOCPlayingPiece.CITY:
              cancelBuildItem.setLabel(strings.get("board.cancel.city.upgrade"));  // "Cancel city upgrade"
              upgradeCityItem.setEnabled(menuPlayerIsCurrent);
              hoverCityID = hilightAt;
              break;

          case SOCPlayingPiece.SHIP:
              if (mode == MOVE_SHIP)
              {
                  cancelBuildItem.setLabel(strings.get("board.cancel.ship.move"));  // "Cancel ship move"
                  cancelBuildItem.setEnabled(true);
              } else {
                  cancelBuildItem.setLabel(strings.get("board.cancel.ship"));  // "Cancel ship"
              }
              hoverShipID = hilightAt;
              break;

          default:
              throw new IllegalArgumentException ("bad buildtype: " + buildType);
          }

          super.show(bp, x, y);
      }

      /**
       * Custom show method that finds current game status and player status.
       * Also checks for hovering-over-port for port-trade submenu,
       * and last-placed piece for undo build placement/ship movement in game state {@link SOCGame#PLAY1}.
       *
       * @param x   Mouse x-position
       * @param y   Mouse y-position
       * @param hR  Hover road ID, or 0
       * @param hSe  Hover settle ID, or 0
       * @param hC  Hover city ID, or 0
       * @param hSh  Hover ship ID, or 0; use negative if can move this currently placed ship.
       *             <tt>hSh &lt; 0</tt> is the only time this method trusts the caller's
       *             game state checks, instead of doing its own checking.
       */
      public void showBuild(int x, int y, int hR, int hSe, int hC, int hSh)
      {
          wantsCancel = false;
          wantsUndo = false;
          isInitialPlacement = false;
          isShipMovable = false;
          cancelBuildItem.setEnabled(false);
          cancelBuildItem.setLabel(strings.get("board.cancel.build"));  // "Cancel build"
          if (portTradeSubmenu != null)
          {
              // Cleanup from last time
              remove(portTradeSubmenu);
              portTradeSubmenu.destroy();
              portTradeSubmenu = null;
          }
          if (hoverSettlementID == -1)
          {
              // Restore label after previous popup's "Attack Fortress" label for _SC_PIRI
              buildSettleItem.setLabel(strings.get("board.build.stlmt"));  // "Build Settlement"
          }

          boolean didEnableDisable = true;  // don't go through both sets of menu item enable/disable statements

          menuPlayerIsCurrent = (player != null) && playerInterface.isClientCurrentPlayer();

          if (menuPlayerIsCurrent)
          {
              int gs = game.getGameState();
              if (game.isDebugFreePlacement() && game.isInitialPlacement())
              {
                  switch (player.getPieces().size())
                  {
                  case 0:
                  case 2:
                      gs = SOCGame.START1A;  // Settlement
                      break;
                  case 1:
                  case 3:
                      gs = SOCGame.START1B;  // Road
                      break;
                  default:
                      gs = SOCGame.PLAY1;  // any piece is okay
                  }
              }

              switch (gs)
              {
              case SOCGame.START1A:
              case SOCGame.START2A:
              case SOCGame.START3A:
                  isInitialPlacement = true;  // Settlement
                  buildRoadItem.setEnabled(false);
                  buildSettleItem.setEnabled(hSe != 0);
                  upgradeCityItem.setEnabled(false);
                  if (buildShipItem != null)
                      buildShipItem.setEnabled(false);
                  break;

              case SOCGame.START1B:
              case SOCGame.START2B:
              case SOCGame.START3B:
                  isInitialPlacement = true;  // Road
                  buildRoadItem.setEnabled(hR != 0);
                  buildSettleItem.setEnabled(false);
                  upgradeCityItem.setEnabled(false);
                  if (buildShipItem != null)
                      buildShipItem.setEnabled(hSh != 0);
                  if (! game.isDebugFreePlacement())
                  {
                      cancelBuildItem.setLabel(strings.get("board.cancel.stlmt"));  // "Cancel settlement" -- Initial settlement
                      cancelBuildItem.setEnabled(true);
                      cancelBuildType = SOCPlayingPiece.SETTLEMENT;
                  }
                  break;

              case SOCGame.PLACING_FREE_ROAD1:
              case SOCGame.PLACING_FREE_ROAD2:
                  if (game.isPractice
                      || ((gs == SOCGame.PLACING_FREE_ROAD1)
                          && (playerInterface.client.sVersion >= SOCGame.VERSION_FOR_CANCEL_FREE_ROAD1))
                      || ((gs == SOCGame.PLACING_FREE_ROAD2)
                          && (playerInterface.client.sVersion >= SOCGame.VERSION_FOR_CANCEL_FREE_ROAD2)))
                  {
                      cancelBuildItem.setEnabled(true);
                      cancelBuildItem.setLabel(strings.get("board.build.skip.road.ship"));  // "Skip road or ship"
                  }
                  buildRoadItem.setEnabled(hR != 0);
                  buildSettleItem.setEnabled(false);
                  upgradeCityItem.setEnabled(false);
                  if (buildShipItem != null)
                      buildShipItem.setEnabled(hSh != 0);
                  break;

              default:
                  didEnableDisable = false;  // must still check enable/disable
                  if (gs < SOCGame.PLAY1)
                      menuPlayerIsCurrent = false;  // Not in a state to place items
                  else if ((gs == SOCGame.PLAY1) || (gs == SOCGame.SPECIAL_BUILDING))
                  {
                      final SOCPlayingPiece latest = bp.latestPiecePlacement;
                      final GameAction act = game.getLastAction();
                      if ((latest != null) && (act != null))
                      {
                          // enable Undo only if game allows and currently pointing at latest piece
                          // or an adjacent coordinate

                          if ((act.actType == GameAction.ActionType.MOVE_PIECE)
                              && (latest instanceof SOCShip)
                              && game.canUndoMoveShip(playerNumber, (SOCShip) latest))
                          {
                              final int edgeCoord;
                              {
                                  int[] xyb = rotateScaleXYFromActual(x, y);
                                  edgeCoord = findEdge(xyb[0], xyb[1], false);
                              }
                              if (board.isEdgeSameOrAdjacent(edgeCoord, latest.getCoordinates()))
                              {
                                  wantsUndo = true;
                                  cancelBuildItem.setEnabled(true);
                                  cancelBuildItem.setLabel(strings.get("board.undo.move.ship"));  // "Undo move ship"
                                  cancelBuildType = SOCPlayingPiece.SHIP;
                              }
                          }
                          else if ((act.actType == GameAction.ActionType.BUILD_PIECE)
                              && game.canUndoPutPiece(playerNumber, latest))
                          {
                              int coord = 0;
                              boolean isSameCoord = false;
                              if (latest instanceof SOCRoutePiece)
                              {
                                  int[] xyb = rotateScaleXYFromActual(x, y);
                                  coord = findEdge(xyb[0], xyb[1], false);
                                  isSameCoord = board.isEdgeSameOrAdjacent(coord, latest.getCoordinates());
                              }
                              else if ((latest instanceof SOCSettlement) || (latest instanceof SOCCity))
                              {
                                  int[] xyb = rotateScaleXYFromActual(x, y);
                                  coord = findNode(xyb[0], xyb[1]);
                                  isSameCoord = board.isNodeSameOrAdjacent(coord, latest.getCoordinates());
                              }

                              if (isSameCoord)
                              {
                                  wantsUndo = true;
                                  cancelBuildItem.setEnabled(true);
                                  cancelBuildType = latest.getType();
                                  final String key;
                                  switch (cancelBuildType)
                                  {
                                  case SOCPlayingPiece.ROAD:
                                      key = "board.undo.build.road";  break;  // "Undo build Road"
                                  case SOCPlayingPiece.SETTLEMENT:
                                      key = "board.undo.build.stlmt";  break; // "Undo build Settlement"
                                  case SOCPlayingPiece.SHIP:
                                      key = "board.undo.build.ship";  break;  // "Undo build Ship"
                                  default:  // SOCPlayingPiece.CITY
                                      key = "board.undo.build.city.upgrade";  // "Undo City upgrade"
                                  }
                                  cancelBuildItem.setLabel(strings.get(key));
                              }
                          }
                      }
                  }
              }
          }

          if (! menuPlayerIsCurrent)
          {
              buildRoadItem.setEnabled(false);
              buildSettleItem.setEnabled(false);
              upgradeCityItem.setEnabled(false);
              if (buildShipItem != null)
              {
                  buildShipItem.setEnabled(false);
                  buildShipItem.setLabel(strings.get("board.build.ship"));
              }
              hoverRoadID = 0;
              if (hoverSettlementID == -1)
              {
                  // restore label after previous popup's "Attack Fortress" label for _SC_PIRI
                  buildSettleItem.setLabel(strings.get("board.build.stlmt"));
              }
              hoverSettlementID = 0;
              hoverCityID = 0;
              hoverShipID = 0;
          }
          else
          {
              final int cpn = game.getCurrentPlayerNumber();
                // note: if debugPP, cpn might not == player.playerNumber

              if (! isInitialPlacement)
              {
                  final boolean debugPP = game.isDebugFreePlacement();
                  if (debugPP || ! didEnableDisable)
                  {
                      buildRoadItem.setEnabled
                          ( player.isPotentialRoad(hR) &&
                            (debugPP ? (player.getNumPieces(SOCPlayingPiece.ROAD) > 0)
                                     : game.couldBuildRoad(cpn)) );
                      buildSettleItem.setEnabled
                          ( player.canPlaceSettlement(hSe) &&
                            (debugPP ? (player.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0)
                                     : game.couldBuildSettlement(cpn)) );
                      upgradeCityItem.setEnabled
                          ( player.isPotentialCity(hC) &&
                            (debugPP ? (player.getNumPieces(SOCPlayingPiece.CITY) > 0)
                                     : game.couldBuildCity(cpn)) );
                      if (buildShipItem != null)
                      {
                        isShipMovable = (hSh < 0);
                        if (isShipMovable)
                        {
                            hSh = -hSh;
                            buildShipItem.setLabel(strings.get("board.build.move.ship"));
                            buildShipItem.setEnabled(true);  // trust the caller's game checks
                        } else {
                            buildShipItem.setLabel(strings.get("board.build.ship"));
                            buildShipItem.setEnabled
                                ( game.canPlaceShip(player, hSh) &&
                                  (debugPP ? (player.getNumPieces(SOCPlayingPiece.SHIP) > 0)
                                           : game.couldBuildShip(cpn)) );
                        }
                      }
                  }
              }
              hoverRoadID = hR;
              hoverSettlementID = hSe;
              hoverCityID = hC;
              hoverShipID = hSh;

              // Is it a port?
              int portType = -1;
              int portId = 0;
              if (hSe != 0)
                  portId = hSe;
              else if (hC != 0)
                  portId = hC;
              else if (bp.hoverTip.hoverIsPort)
                  portId = bp.hoverTip.hoverID;

              if (portId != 0)
                  portType = board.getPortTypeFromNodeCoord(portId);

              // Menu differs based on port
              if (portType != -1)
              {
                  if (portType == SOCBoard.MISC_PORT)
                      portTradeSubmenu = new ResourceTradeAllMenu
                          (bp, playerInterface.getPlayerHandPanel(cpn));
                  else
                      portTradeSubmenu = new SOCHandPanel.ResourceTradeTypeMenu
                          (playerInterface.getPlayerHandPanel(cpn), portType, false);
                  add(portTradeSubmenu);
                  portTradeSubmenu.setEnabledIfCanTrade(true);
              }
          }

          super.show(bp, x, y);
      }

      /** Custom show method for hovering at a pirate fortress ({@link SOCFortress}),
       *  giving the options to attack if it's our player's;
       *  for scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.
       *
       * @param x   Mouse x-position
       * @param y   Mouse y-position
       * @param ft  Fortress being hovered at (our player's or otherwise), or null
       * @since 2.0.00
       */
      public void showAtPirateFortress(final int x, final int y, SOCFortress ft)
      {
          final boolean settleItemWasFortress = (hoverSettlementID == -1);
          menuPlayerIsCurrent = (player != null) && playerInterface.isClientCurrentPlayer();
          wantsCancel = false;
          cancelBuildType = 0;
          wantsUndo = false;
          hoverRoadID = 0;
          hoverSettlementID = (ft != null) ? -1 : 0;
          hoverCityID = 0;
          hoverShipID = 0;

          buildRoadItem.setEnabled(false);
          if (hoverSettlementID == -1)
          {
              buildSettleItem.setLabel(strings.get("board.build.sc_piri.attack.fortress"));  // "Attack Fortress"
              buildSettleItem.setEnabled
                  (menuPlayerIsCurrent && (ft.getPlayerNumber() == playerNumber)
                   && (game.canAttackPirateFortress() != null));
          }
          else if (settleItemWasFortress)
          {
              // Restore label after previous popup's "Attack Fortress" label for _SC_PIRI
              buildSettleItem.setLabel(strings.get("board.build.stlmt"));  // "Build Settlement"
              buildSettleItem.setEnabled(false);
          }
          upgradeCityItem.setEnabled(false);
          buildShipItem.setEnabled(false);
          cancelBuildItem.setEnabled(false);
          isInitialPlacement = false;

          super.show(bp, x, y);
      }

      /** Handling the menu items **/
      public void actionPerformed(ActionEvent e)
      {
          if (! playerInterface.isClientCurrentPlayer())
              return;
          if (! menuPlayerIsCurrent)
              return;

          Object target = e.getSource();
          if (target == buildRoadItem)
          {
              tryBuild(SOCPlayingPiece.ROAD);
          }
          else if (target == buildSettleItem)
          {
              if (hoverSettlementID != -1)
                  tryBuild(SOCPlayingPiece.SETTLEMENT);
              else
                  confirmAttackPirateFortress();
          }
          else if (target == upgradeCityItem)
          {
              tryBuild(SOCPlayingPiece.CITY);
          }
          else if ((target == buildShipItem) && (target != null))
          {
              if (mode == MOVE_SHIP)
                  tryMoveShipToEdge();
              else if (isShipMovable)
                  tryMoveShipFromHere();
              else if (game.isGameOptionSet(SOCGameOptionSet.K_SC_FTRI)
                       && ((SOCBoardLarge) board).canRemovePort(hoverShipID))
                  java.awt.EventQueue.invokeLater(new ConfirmPlaceShipDialog(hoverShipID, true, -1));
              else
                  tryBuild(SOCPlayingPiece.SHIP);
          }
          else if (target == cancelBuildItem)
          {
              if (wantsUndo)
                  tryUndo();
              else
                  tryCancel();
          }
      }

      /**
       * Send message to server to request placing this piece, if allowable.
       * If not initial placement or free placement, also sets up a reaction to send the 2nd message (putpiece)
       * when server says it's OK to build, using value of {@link #hoverSettlementID}, {@link #hoverShipID}, etc
       * when {@code tryBuild} is called.
       *<P>
       * Assumes player is current, and non-null, when called.
       *
       * @param ptype Piece type, like {@link SOCPlayingPiece#ROAD}
       */
      void tryBuild(int ptype)
      {
          final boolean debugPP = game.isDebugFreePlacement();
          final int cpn = (debugPP)
              ? playerNumber   // boardpanel's temporary player number
              : playerInterface.getClientPlayerNumber();
          int buildLoc;      // location
          boolean canBuild;  // checks resources, rules
          String btarget;    // button name on buildpanel

          // If possible, send putpiece request right now.
          // Otherwise, multi-phase send (build request, receive gamestate, putpiece request).
          final int gstate = game.getGameState();
          final boolean sendNow = isInitialPlacement || wantsCancel || debugPP
              || (gstate == SOCGame.PLACING_FREE_ROAD1) || (gstate == SOCGame.PLACING_FREE_ROAD2)
              || (((gstate == SOCGame.PLAY1) || (gstate == SOCGame.SPECIAL_BUILDING))
                  && (game.isPractice || playerInterface.client.sVersion >= 2000));
          final GameMessageSender messageSender = (sendNow)
              ? playerInterface.getClient().getGameMessageSender()
              : null;

          // Note that if we're in gameplay have clicked the "buy road" button
          // and trying to place it, game.couldBuildRoad will be false because
          // we've already spent the resources.  So, wantsCancel won't check it.

          switch (ptype)
          {
          case SOCPlayingPiece.ROAD:
              buildLoc = hoverRoadID;
              if (buildLoc == -1)
                  buildLoc = 0;
              canBuild = player.isPotentialRoad(buildLoc);
              if (! sendNow)
                  canBuild = canBuild && game.couldBuildRoad(cpn);
              if (canBuild && sendNow)
                  messageSender.putPiece(game, new SOCRoad(player, buildLoc, board));
              btarget = SOCBuildingPanel.ROAD;
              break;

          case SOCPlayingPiece.SETTLEMENT:
              buildLoc = hoverSettlementID;
              canBuild = player.canPlaceSettlement(buildLoc);
              if (! sendNow)
                  canBuild = canBuild && game.couldBuildSettlement(cpn);
              if (canBuild && sendNow)
              {
                  messageSender.putPiece(game, new SOCSettlement(player, buildLoc, board));
                  if (isInitialPlacement)
                      initSettlementNode = buildLoc;  // track for initial road mouseover hilight
              }
              btarget = SOCBuildingPanel.STLMT;
              break;

          case SOCPlayingPiece.CITY:
              buildLoc = hoverCityID;
              canBuild = player.isPotentialCity(buildLoc);
              if (! sendNow)
                  canBuild = canBuild && game.couldBuildCity(cpn);
              if (canBuild && sendNow)
                  messageSender.putPiece(game, new SOCCity(player, buildLoc, board));
              btarget = SOCBuildingPanel.CITY;
              break;

          case SOCPlayingPiece.SHIP:
              buildLoc = hoverShipID;
              canBuild = game.canPlaceShip(player, buildLoc);  // checks isPotentialShip, pirate ship
              if (! sendNow)
                  canBuild = canBuild && game.couldBuildShip(cpn);
              if (canBuild && sendNow)
                  messageSender.putPiece(game, new SOCShip(player, buildLoc, board));
              btarget = SOCBuildingPanel.SHIP;
              break;

          default:
              throw new IllegalArgumentException ("Bad build type: " + ptype);
          }

          if (! canBuild)
          {
              playerInterface.printKeyed("board.msg.cannot.build.there");  // * "Sorry, you cannot build there."
              return;
          }

          if (sendNow)
          {
              // - Easy, we've sent it right away.  Done with placing this piece.
              clearModeAndHilight(ptype);
              return;
          }

          // - During gameplay: Send, wait to receive gameState, send.

          // Set up timer to expect first-reply (and then send the second message)
          popupSetBuildRequest(buildLoc, ptype);

          // Now that we're expecting that, use buttons to send the first message
          playerInterface.getBuildingPanel().clickBuildingButton
              (game, btarget, true);
      }

      /**
       * Confirm with the user that they want to atack the pirate fortress and end their turn,
       * in scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.  If confirmed, will call
       * {@link #tryAttackPirateFortress()}.
       * @since 2.0.00
       */
      public void confirmAttackPirateFortress()
      {
          // Clear the hovering tooltip at fortress, since dialog will change our mouse focus
          hoverTip.setHoverText_modeChangedOrMouseMoved = true;
          hoverTip.setHoverText(null, 0);

          java.awt.EventQueue.invokeLater(new ConfirmAttackPirateFortressDialog());
      }

      /**
       * Send request to server to attack our player's pirate fortress,
       * in scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.
       * @since 2.0.00
       */
      public void tryAttackPirateFortress()
      {
          // Validate and make the request
          if (game.canAttackPirateFortress() != null)
              playerInterface.getClient().getGameMessageSender()
                  .sendSimpleRequest(player, SOCSimpleRequest.SC_PIRI_FORT_ATTACK);
          else
              // can't attack, cancel the request
              playerInterface.getClientListener().scen_SC_PIRI_pirateFortressAttackResult(true, 0, 0);
      }

      /**
       * Cancel placing a building piece, or cancel moving a ship.
       * Calls {@link SOCBuildingPanel#clickBuildingButton(SOCGame, String, boolean)}.
       */
      void tryCancel()
      {
          if (mode == MOVE_SHIP)
          {
              // No building-panel or server request necessary
              clearModeAndHilight(SOCPlayingPiece.SHIP);
              playerInterface.printKeyed("board.msg.canceled.move.ship");  // * "Canceled moving the ship."
              return;
          }

          String btarget = null;
          switch (cancelBuildType)
          {
          case SOCPlayingPiece.ROAD:
              btarget = SOCBuildingPanel.ROAD;
              break;
          case SOCPlayingPiece.SETTLEMENT:
              btarget = SOCBuildingPanel.STLMT;
              break;
          case SOCPlayingPiece.CITY:
              btarget = SOCBuildingPanel.CITY;
              break;
          case SOCPlayingPiece.SHIP:
              btarget = SOCBuildingPanel.SHIP;
              break;
          }
          // Use buttons to cancel the build request
          playerInterface.getBuildingPanel().clickBuildingButton
              (game, btarget, false);
      }

      /**
       * Undo placing the building piece or moving the ship
       * tracked by {@link SOCBoardPanel#latestPiecePlacement}.
       * @since 2.7.00
       */
      void tryUndo()
      {
          final GameAction act = game.getLastAction();
          if ((act == null) || (bp.latestPiecePlacement == null))
              return;
          playerInterface.getClient().getGameMessageSender().undoPutOrMovePieceRequest
              (game, latestPiecePlacement.getType(), latestPiecePlacement.getCoordinates(),
               (act.actType == GameAction.ActionType.MOVE_PIECE ? act.param2 : 0));
      }

      /**
       * Set up the board so the player can click where they want the ship moved.
       * Change mode to {@link #MOVE_SHIP} and set {@link SOCBoardPanel#moveShip_fromEdge}.
       * Assumes player is current, and the ship at {@link #hoverShipID} is movable, when called.
       * Repaints the board.
       *
       * @since 2.0.00
       * @see SOCBoardPanel#tryMoveShipToEdge()
       * @see SOCBoardPanel#setModeMoveShip(int)
       */
      private void tryMoveShipFromHere()
      {
          playerInterface.printKeyed("board.msg.click.ship.new.loc");  // * "Click the ship's new location."
          moveShip_fromEdge = hoverShipID;
          moveShip_toEdge = 0;
          moveShip_isWarship = hoverTip.hoverIsWarship;
          mode = MOVE_SHIP;
          hilight = 0;
          hoverTip.hideHoverAndPieces();  // calls repaint
      }

    }  // inner class BoardPopupMenu

    /**
     * Menu for right-click on 3-for-1 port to trade all resource types with bank/port.
     * Menu items won't necessarily say "trade 3", because the user may have a 2-for-1
     * port, or may not have a 3-for-1 port (cost 4).
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.00
     */
    /* package-access */ static class ResourceTradeAllMenu extends SOCHandPanel.ResourceTradePopupMenu
    {
        private final SOCBoardPanel bpanel;
        private final SOCHandPanel.ResourceTradeTypeMenu[] tradeFromTypes;

        /**
         * Temporary menu for board popup menu
         *
         * @throws IllegalStateException If client not current player
         */
        public ResourceTradeAllMenu(SOCBoardPanel bp, SOCHandPanel hp)
            throws IllegalStateException
        {
            super(hp, strings.get("board.trade.trade.port"));  // "Trade Port"
            bpanel = bp;
            SOCPlayerInterface pi = hp.getPlayerInterface();
            if (! pi.isClientCurrentPlayer())
                throw new IllegalStateException("Not current player");

          tradeFromTypes = new SOCHandPanel.ResourceTradeTypeMenu[5];
          for (int i = 0; i < 5; ++i)
          {
              tradeFromTypes[i] = new SOCHandPanel.ResourceTradeTypeMenu(hp, i+1, true);
              add(tradeFromTypes[i]);
          }
        }

        /**
         * Show menu at this position. Before showing, enable or
         * disable based on gamestate and player's resources.
         *
         * @param x   Mouse x-position relative to colorsquare
         * @param y   Mouse y-position relative to colorsquare
         */
        @Override
        public void show(int x, int y)
        {
            setEnabledIfCanTrade(false);
            super.show(bpanel, x, y);
        }

        /**
         * Enable or disable based on gamestate and player's resources.
         *
         * @param itemsOnly If true, enable/disable items, instead of the menu itself.
         *                  The submenus are considered items.
         *                  Items within submenus are also items.
         */
        @Override
        public void setEnabledIfCanTrade(boolean itemsOnly)
        {
            int gs = hpan.getGame().getGameState();
            for (int i = 0; i < 5; ++i)
            {
                int numNeeded = tradeFromTypes[i].getResourceCost();
                tradeFromTypes[i].setEnabledIfCanTrade(itemsOnly);
                tradeFromTypes[i].setEnabledIfCanTrade
                    ((gs == SOCGame.PLAY1)
                     && (numNeeded <= hpan.getPlayer().getResources().getAmount(i+1)));
            }
        }

        /** Cleanup, for removing this menu. */
        @Override
        public void destroy()
        {
            for (int i = 0; i < 5; ++i)
            {
                if (tradeFromTypes[i] != null)
                {
                    SOCHandPanel.ResourceTradeTypeMenu mi = tradeFromTypes[i];
                    tradeFromTypes[i] = null;
                    mi.destroy();
                }
            }
            removeAll();
            hpan = null;
        }

    }  /* static nested class ResourceTradeAllMenu */

    /**
     * Used for the delay between sending a build-request message,
     * and receiving a game-state message.
     *<P>
     * This timer will probably not be called, unless there's a large lag
     * between the server and client.  It's here just in case.
     * Ideally the server responds right away, and the client responds to that.
     *<P>
     * Not used if server and client are both v2.0.00 or newer.
     *
     * @see SOCHandPanel#autoRollSetupTimer()
     * @since 1.1.00
     */
    protected class BoardPanelSendBuildTask extends java.util.TimerTask
    {
        protected int buildLoc, pieceType;
        protected boolean wasSentAlready;

        /** Send this after maximum delay.
         *
         * @param coord Board coordinates, as used in SOCPutPiece message. Does not accept -1 for road edge 0x00.
         * @param ptype Piece type, as used in SOCPlayingPiece / SOCPutPiece
         */
        protected BoardPanelSendBuildTask (int coord, int ptype)
        {
            buildLoc = coord;
            pieceType = ptype;
            wasSentAlready = false;
        }

        /** Board coordinates, as used in SOCPutPiece message */
        public int getBuildLoc()
        {
            return buildLoc;
        }

        /** Piece type, as used in SOCPlayingPiece / SOCPutPiece */
        public int getPieceType()
        {
            return pieceType;
        }

        /**
         * This timer will probably not be called, unless there's a large lag
         * between the server and client.  It's here just in case.
         */
        @Override
        public void run()
        {
            // for debugging
            if (Thread.currentThread().getName().startsWith("Thread-"))
            {
                try {
                    Thread.currentThread().setName("timertask-boardpanel");
                }
                catch (Throwable e) {}
            }

            // Time is up.
            sendOnceFromClientIfCurrentPlayer();
        }

        public synchronized void doNotSend()
        {
            wasSentAlready = true;
        }

        public synchronized boolean wasItSentAlready()
        {
            return wasSentAlready;
        }

        /**
         * Internally synchronized around setSentAlready/wasItSentAlready.
         * Assumes player != null because of conditions leading to the call.
         */
        public void sendOnceFromClientIfCurrentPlayer()
        {
            synchronized (this)
            {
                if (wasItSentAlready())
                    return;
                doNotSend();  // Since we're about to send it.
            }

            // Should only get here once, in one thread.
            if (! playerInterface.isClientCurrentPlayer())
                return;  // Stale request, player's already changed

            final GameMessageSender messageSender = playerInterface.getClient().getGameMessageSender();

            switch (pieceType)
            {
            case SOCPlayingPiece.ROAD:
                if (player.isPotentialRoad(buildLoc))
                    messageSender.putPiece(game, new SOCRoad(player, buildLoc, board));
                break;

            case SOCPlayingPiece.SETTLEMENT:
                if (player.canPlaceSettlement(buildLoc))
                    messageSender.putPiece(game, new SOCSettlement(player, buildLoc, board));
                break;

            case SOCPlayingPiece.CITY:
                if (player.isPotentialCity(buildLoc))
                    messageSender.putPiece(game, new SOCCity(player, buildLoc, board));
                break;

            case SOCPlayingPiece.SHIP:
                if (game.canPlaceShip(player, buildLoc))  // checks isPotentialShip, pirate ship
                    messageSender.putPiece(game, new SOCShip(player, buildLoc, board));
                break;
            }

            clearModeAndHilight(pieceType);
        }

    }  // inner class BoardPanelSendBuildTask

    /**
     * Modal dialog to confirm moving the robber next to our own settlement or city.
     * Use the AWT event thread to show, so message treating can continue while the dialog is showing.
     * If the move is confirmed, call playerClient.moveRobber and clearModeAndHilight.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.11
     */
    private class MoveRobberConfirmDialog extends AskDialog implements Runnable
    {
        /** prevent serializable warning */
        private static final long serialVersionUID = 2000L;

        private final SOCPlayer pl;
        private final int robHex;

        /**
         * Creates a new MoveRobberConfirmDialog.
         * To display the dialog without tying up the client's message-handler thread,
         * call {@link java.awt.EventQueue#invokeLater(Runnable) EventQueue.invokeLater(thisDialog)}.
         *
         * @param player  Current player
         * @param newRobHex  The new robber hex, if confirmed; not validated.
         *          Use a negative value if moving the pirate.
         */
        private MoveRobberConfirmDialog(SOCPlayer player, final int newRobHex)
        {
            super(playerInterface.getMainDisplay(), playerInterface,
                strings.get((newRobHex > 0) ? "dialog.moverobber.to.hex" : "dialog.moverobber.to.hex.pirate"),
                    // "Move robber to your hex?" / "Move pirate to your hex?"
                strings.get((newRobHex > 0) ? "dialog.moverobber.are.you.sure" : "dialog.moverobber.are.you.sure.pirate"),
                    // "Are you sure you want to move the robber to your own hex?"
                    // / "Are you sure you want to move the pirate to your own hex?"
                strings.get((newRobHex > 0) ? "dialog.base.move.robber" : "dialog.base.move.pirate"),
                    // "Move Robber" / "Move Pirate"
                strings.get("dialog.moverobber.dont"),  // "Don't move there"
                null, 2);

            pl = player;
            robHex = newRobHex;
        }

        /**
         * React to the Move Robber button. (call playerClient.moveRobber)
         */
        @Override
        public void button1Chosen()
        {
            // ask server to move it
            md.getGameMessageSender().moveRobber(game, pl, robHex);
            clearModeAndHilight(-1);
        }

        /**
         * React to the Don't Move button.
         */
        @Override
        public void button2Chosen() {}

        /**
         * React to the dialog window closed by user. (Don't move the robber)
         */
        @Override
        public void windowCloseChosen() {}

    }  // nested class MoveRobberConfirmDialog

    /**
     * Modal dialog to confirm the player wants to attack the pirate fortress and end their turn.
     * Use the AWT event thread to show, so message treating can continue while the dialog is showing.
     * When the choice is made, calls {@link SOCBoardPanel.BoardPopupMenu#tryAttackPirateFortress()}.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.0.00
     */
    private class ConfirmAttackPirateFortressDialog extends AskDialog implements Runnable
    {
        private static final long serialVersionUID = 2000L;

        /**
         * Creates a new ConfirmAttackPirateFortressDialog.
         * To display the dialog without tying up the client's message-handler thread,
         * call {@link java.awt.EventQueue#invokeLater(Runnable) EventQueue.invokeLater(thisDialog)}.
         */
        protected ConfirmAttackPirateFortressDialog()
        {
            super(playerInterface.getMainDisplay(), playerInterface,
                strings.get("game.sc_piri.attfort.and.endturn"),      // "Attack and end turn?"
                strings.get("game.sc_piri.attfort.confirm.endturn"),  // "Attacking the fortress will end your turn. Are you sure?"
                strings.get("game.sc_piri.attfort.confirm"),          // "Confirm Attack"
                strings.get("base.cancel"),
                null, 2);
        }

        /**
         * React to the Confirm Attack button.
         * Call {@link SOCBoardPanel.BoardPopupMenu#tryAttackPirateFortress()}.
         */
        @Override
        public void button1Chosen()
        {
            popupMenu.tryAttackPirateFortress();
        }

        /**
         * React to the Cancel button, do nothing.
         */
        @Override
        public void button2Chosen() {}

        /**
         * React to the dialog window closed by user. (Default is Cancel)
         */
        @Override
        public void windowCloseChosen() { button2Chosen(); }

    }  // nested class ConfirmAttackPirateFortressDialog

    /**
     * For scenario {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI}, modal dialog to confirm placing a ship
     * at an edge with a "gift" trade port.  Player will need to pick up this port, and may need to
     * immediately place it elsewhere on the board.
     *<P>
     * Assumes {@link SOCGame#canPlaceShip(SOCPlayer, int)} has been called to validate.
     * Use the AWT event thread to show, so message treating can continue while the dialog is showing.
     * If placement is confirmed, call putPiece, possibly after tryBuild depending on mode when dialog was shown.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.0.00
     */
    private class ConfirmPlaceShipDialog extends AskDialog implements Runnable
    {
        private static final long serialVersionUID = 2000L;

        /** Edge to place at or move to */
        private final int edge;

        /** If true, send Build Request before sending PutPiece */
        private final boolean sendBuildReqFirst;

        /** If not -1, do a ship move from this edge, not a placement from player's available pieces */
        private final int isMove_fromEdge;

        /**
         * Creates a new ConfirmPlaceShipDialog.
         * To display the dialog without tying up the client's message-handler thread,
         * call {@link java.awt.EventQueue#invokeLater(Runnable) EventQueue.invokeLater(thisDialog)}.
         *
         * @param edge  The port edge where the ship would be placed
         * @param sendBuildReqFirst  If true, calling from {@link SOCBoardPanel.BoardPopupMenu BoardPopupMenu}, and
         *            after user confirms, client will need to send {@link soc.message.SOCBuildRequest BUILDREQUEST}
         *            before placement request
         * @param isMove_fromEdge  Edge to move ship from, or -1 if a placement from player's available pieces;
         *            if moving a ship, {@code sendBuildReqFirst} must be {@code false}.
         */
        private ConfirmPlaceShipDialog(final int edge, final boolean sendBuildReqFirst, final int isMove_fromEdge)
        {
            super(playerInterface.getMainDisplay(), playerInterface,
                strings.get("dialog.base.place.ship.title"),  // "Place Ship Here?"
                strings.get( (player.getPortMovePotentialLocations(false) != null)
                    ? "game.invitem.sc_ftri.pickup.ask.immed"
                        // "If you place a ship here, this port must be placed now at your coastal settlement or city."
                    : "game.invitem.sc_ftri.pickup.ask.later" ),
                        // "If you place a ship here, you will be given this port to be placed later at your coastal settlement or city."
                strings.get("dialog.base.place.ship"),  // "Place Ship"
                strings.get("dialog.base.place.dont"),  // "Don't Place Here"
                null, 1);

            this.edge = edge;
            this.sendBuildReqFirst = sendBuildReqFirst;
            this.isMove_fromEdge = isMove_fromEdge;
        }

        /**
         * React to the Place Ship button: Call GameMessageSender.buildRequest via BoardPopupMenu.tryBuild,
         * or GameMessageSender.putPiece or movePieceRequest.
         */
        @Override
        public void button1Chosen()
        {
            if (sendBuildReqFirst)
            {
                final int currentHover = hoverTip.hoverShipID;
                hoverTip.hoverShipID = edge;  // set field that tryBuild reads to send PutPiece message after BuildRequest
                popupMenu.tryBuild(SOCPlayingPiece.SHIP);
                hoverTip.hoverShipID = currentHover;
            } else {
                final GameMessageSender gms = md.getGameMessageSender();
                if (isMove_fromEdge == -1)
                    gms.putPiece(game, new SOCShip(player, edge, board));
                else
                    gms.movePieceRequest
                        (game, playerNumber, SOCPlayingPiece.SHIP, isMove_fromEdge, edge);
                clearModeAndHilight(SOCPlayingPiece.SHIP);
            }
        }

        /** React to the Don't Place button. */
        @Override
        public void button2Chosen()
        {
            if (! sendBuildReqFirst)
            {
                // clear hilight but not mode
                hilight = 0;
                SOCBoardPanel.this.repaint();
            }
        }

        /** React to the dialog window closed by user. (Don't place the ship) */
        @Override
        public void windowCloseChosen() { button2Chosen(); }

    }  // nested class ConfirmPlaceShipDialog

    /**
     * Highlight styles to optionally use when drawing a playing piece.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.7.00
     */
    private enum HilightStyle
    {
        /**
         * The most common type: Highlight when mouse is hovered over the piece,
         * usually for showing a potential placement
         */
        HOVER,

        /** Most recently placed piece, including ship moves */
        MOST_RECENT_PLACEMENT
    }

}  // class SOCBoardPanel
