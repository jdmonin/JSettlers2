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
package soc.client;

import soc.game.SOCBoard;
import soc.game.SOCCity;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import java.util.Enumeration;
import java.util.Timer;


/**
 * This is a component that can display a Settlers of Catan Board.
 * It can be used in an applet or an application.
 * It loads gifs from a directory named "images" in the same
 * directory as this class.
 *<P>
 * When the mouse is over the game board, a tooltip shows information
 * such as a hex's resource, a piece's owner, a port's ratio, or the
 * number under the robber. See {@link #hoverTip}.
 *<P>
 * During game play, moving the mouse over the board shows ghosted roads,
 * settlements, cities at locations the player can build.  See: {@link #hilight},
 * {@link SOCBoardPanel.BoardToolTip#hoverRoadID}.
 * Right-click to build, or use the {@link SOCBuildingPanel}'s buttons.
 *<P>
 * Before the game begins, the boardpanel is full of water hexes.
 * You can show a short message text of 1 or 2 lines by calling
 * {@link #setSuperimposedText(String, String)}.
 */
public class SOCBoardPanel extends Canvas implements MouseListener, MouseMotionListener
{
    private static String IMAGEDIR = "/soc/client/images";

    /**
     * size of the whole panel, internal-pixels "scale";
     * also minimum acceptable size in screen pixels.
     * For actual current size in screen pixels, see
     * {@link #scaledPanelX} {@link #scaledPanelY};
     * If {@link #isRotated()}, the minimum size swaps {@link #PANELX} and {@link #PANELY}.
     */
    public static final int PANELX = 379, PANELY = 340;

    /** How many pixels to drop for each row of hexes. @see #HEXHEIGHT */
    private static final int deltaY = 46;
    /** How many pixels to move over for a new hex. @see #HEXWIDTH */
    private static final int deltaX = 54;
    /** Each row only moves a half hex over horizontally. @see #deltaX */
    private static final int halfdeltaX = 27;

    /**
     * hex coordinates for drawing
     */
    private static final int[] hexX = 
    {
        deltaX + halfdeltaX, 2 * deltaX + halfdeltaX, 3 * deltaX + halfdeltaX, 4 * deltaX + halfdeltaX,  // row 1 4 hexes
        deltaX, 2 * deltaX, 3 * deltaX, 4 * deltaX, 5 * deltaX,                                          // row 2 5 hexes
        halfdeltaX, deltaX + halfdeltaX, 2 * deltaX + halfdeltaX, 3 * deltaX + halfdeltaX, 4 * deltaX + halfdeltaX, 5 * deltaX + halfdeltaX,  // row 3 6 hexes
        0, deltaX, 2 * deltaX, 3 * deltaX, 4 * deltaX, 5 * deltaX, 6 * deltaX,                           // row 4 7 hexes
        halfdeltaX, deltaX + halfdeltaX, 2 * deltaX + halfdeltaX, 3 * deltaX + halfdeltaX, 4 * deltaX + halfdeltaX, 5 * deltaX + halfdeltaX,  // row 5 6 hexes
        deltaX, 2 * deltaX, 3 * deltaX, 4 * deltaX, 5 * deltaX,                                          // row 6 5 hexes
        deltaX + halfdeltaX, 2 * deltaX + halfdeltaX, 3 * deltaX + halfdeltaX, 4 * deltaX + halfdeltaX   // row 7 4 hexes
    };
    private static final int[] hexY = 
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
     * coordinates for drawing the playing pieces
     */
    /***  road looks like "|" along left edge of hex ***/
    private static final int[] vertRoadX = { -2, 3, 3, -2, -2 };
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

    /** robber polygon. X is -4 to +4; Y is -8 to +8. */
    private static final int[] robberX =
    {
        -2, -4, -4, -2, 2, 4, 4, 2, 4, 4, -4, -4, -2, 2
    };
    private static final int[] robberY =
    {
        -2, -4, -6, -8, -8, -6, -4, -2, 0, 8, 8, 0, -2, -2
    };

    /**
     * Arrow, left-pointing.
     * First point is top of arrow-tip's bevel, last point is bottom of tip.
     * arrowXL[4] is rightmost X coordinate.
     * (These points are important for adjustment when scaling in {@link #rescaleCoordinateArrays()})
     * @see #arrowY
     * @see #ARROW_SZ
     */
    private static final int[] arrowXL =
    {
        0,  17, 18, 18, 36, 36, 18, 18, 17,  0
    };
    /**
     * Arrow, right-pointing.
     * Calculated when needed by flipping {@link #arrowXL} in {@link #rescaleCoordinateArrays()}.
     */
    private static int[] arrowXR = null;
    /**
     * Arrow, y-coordinates: same whether pointing left or right.
     * First point is top of arrow-tip's bevel, last point is bottom of tip.
     */
    private static final int[] arrowY =
    {
        17,  0,  0,  6,  6, 30, 30, 36, 36, 19
    };

    /** Arrow fits in a 37 x 37 square. @see #arrowXL */
    private static final int ARROW_SZ = 37;

    /** Arrow color: cyan: r=106,g=183,b=183 */
    private static final Color ARROW_COLOR = new Color(106, 183, 183);

    /**
     * BoardPanel's {@link #mode}s. NONE is normal gameplay, or not the client's turn.
     * For correlation to game state, see {@link #updateMode()}.
     * If a mode is added, please also update {@link #clearModeAndHilight(int)}.
     */
    public final static int NONE = 0;
    public final static int PLACE_ROAD = 1;
    public final static int PLACE_SETTLEMENT = 2;
    public final static int PLACE_CITY = 3;
    public final static int PLACE_ROBBER = 4;
    public final static int PLACE_INIT_SETTLEMENT = 5;
    public final static int PLACE_INIT_ROAD = 6;
    public final static int CONSIDER_LM_SETTLEMENT = 7;
    public final static int CONSIDER_LM_ROAD = 8;
    public final static int CONSIDER_LM_CITY = 9;
    public final static int CONSIDER_LT_SETTLEMENT = 10;
    public final static int CONSIDER_LT_ROAD = 11;
    public final static int CONSIDER_LT_CITY = 12;
    public final static int TURN_STARTING = 97;
    public final static int GAME_FORMING = 98;
    public final static int GAME_OVER = 99;
    
    /** During initial-piece placement, the tooltip is moved this far over to make room. */
    public final static int HOVER_OFFSET_X_FOR_INIT_PLACE = 9;
    
    /** During robber placement, the tooltip is moved this far over to make room. */
    public final static int HOVER_OFFSET_X_FOR_ROBBER = 15;
    
    /** for popup-menu build request, network send maximum delay (seconds) */
    protected static int BUILD_REQUEST_MAX_DELAY_SEC = 5;
    
    /** for popup-menu build request, length of time after popup to ignore further
     *  mouse-clicks.  Avoids Windows accidental build by popup-click during game's
     *  initial piece placement. (150 ms)
     */ 
    protected static int POPUP_MENU_IGNORE_MS = 150;

    /**
     * Pixel spacing around {@link #superText1}, {@link #superText2}
     * @since 1.1.07
     */
    private static final int SUPERTEXT_INSET = 3, SUPERTEXT_PADDING_HORIZ = 2 * SUPERTEXT_INSET + 2;

    /**
     * hex size, in unscaled internal-pixels: 55 wide, 64 tall.
     * The road polygon coordinate-arrays ({@link #downRoadX}, etc)
     * are plotted against a hex of this size.
     * @see #deltaX
     * @see #deltaY
     */
    private static final int HEXWIDTH = 55, HEXHEIGHT = 64;

    /**
     * The board is visually rotated 90 degrees clockwise (game opt DEBUGROTABOARD)
     * compared to the internal coordinates.
     *<P>
     * Use this for rotation:
     *<UL>
     *<LI> From internal to screen (cw):  P'=({@link #PANELY}-y, x)
     *<LI> From screen to internal (ccw): P'=(y, {@link #PANELX}-x)
     *</UL>
     * When the board is also {@link #isScaled scaled}, go in this order:
     * Rotate clockwise, then scale up; Scale down, then rotate counterclockwise.
     *<P>
     * When calculating position at which to draw an image or polygon,
     * remember that rotation changes which corner is considered (0,0),
     * and the image is offset from that corner.  (For example, {@link #drawHex(Graphics, int)}
     * subtracts HEXHEIGHT from x, after rotation but before scaling.)
     *
     * @see #isScaledOrRotated
     * @since 1.1.08
     */
    protected boolean isRotated;

    /**
     * Convenience flag - The board is {@link #isRotated rotated} and/or {@link #isScaled scaled up}.
     * Check both of those flags when transforming between screen coordinates
     * and internal coordinates.  Go in this order:
     * Rotate clockwise, then scale up; Scale down, then rotate counterclockwise.
     * @since 1.1.08
     */
    protected boolean isScaledOrRotated;

    /**
     * actual size on-screen, not internal-pixels size
     * ({@link #PANELX}, {@link #PANELY})
     */
    protected int scaledPanelX, scaledPanelY;

    /**
     * The board is currently scaled larger than
     * {@link #PANELX} x {@link #PANELY} pixels.
     * Use {@link #scaleToActualX(int)}, {@link #scaleFromActualX(int)},
     * etc to convert between internal and actual screen pixel coordinates.
     *<P>
     * When the board is also {@link #isRotated rotated}, go in this order:
     * Rotate clockwise, then scale up; Scale down, then rotate counterclockwise.
     *
     * @see #isScaledOrRotated
     */
    protected boolean isScaled;

    /**
     * Time of last resize, as returned by {@link System#currentTimeMillis()}.
     * Used with {@link #scaledMissedImage}.
     */
    protected long scaledAt;

    /**
     * Flag used while drawing a scaled board. If board size
     * was recently changed, could be waiting for an image to resize.
     * If it still hasn't appeared after 7 seconds, we'll give
     * up and create a new one.  (This can happen due to AWT bugs.)
     * Set in {@link #drawHex(Graphics, int)}, checked in {@link #drawBoard(Graphics)}.
     * @see #scaledHexFail
     */
    protected boolean scaledMissedImage;

    /**
     * translate hex ID to number to get coords
     */
    private int[] hexIDtoNum;

    /**
     * Hex pix - shared unscaled original-resolution from GIF files.
     * Note that miscPort0.gif - miscPort5.gif are stored in {@link #hexes};
     * {@link #ports} stores the resource ports.
     * @see #scaledHexes
     * @see #rotatHexes
     */
    private static Image[] hexes, ports;

    /**
     * Hex pix - rotated board; from ./images/rotat GIF files.
     * Image references are copied to
     * {@link #scaledHexes}/{@link #scaledPorts} from here. 
     * @see #hexes
     * @since 1.1.08
     */
    private static Image[] rotatHexes, rotatPorts;

    /**
     * Hex pix - private scaled copy, if isScaled. Otherwise points to static copies,
     * either {@link #hexes} or {@link #rotatHexes}
     */
    private Image[] scaledHexes, scaledPorts;

    /**
     * Hex pix - Flag to check if rescaling failed, if isScaled.
     * @see #scaledHexes
     * @see #drawHex(Graphics, int)
     */
    private boolean[] scaledHexFail, scaledPortFail;

    /**
     * number pix (for hexes), original resolution.
     */
    private static Image[] numbers;

    /**
     * number pix (for hexes), current scaled resolution
     */
    private Image[] scaledNumbers;

    /**
     * If an element is true, scaling that number's image previously failed.
     * Don't re-try scaling to same size, instead use {@link #numbers}[i].
     */
    private boolean[] scaledNumberFail;

    /**
     * dice number pix (for arrow). @see #DICE_SZ
     */
    private static Image[] dice;

    /** Dice number graphic fits in a 25 x 25 square. @see #dice */
    private static final int DICE_SZ = 25;

    /**
     * Coordinate arrays for drawing the playing pieces.
     * Local copy if isScaled, otherwise points to static arrays.
     */
    private int[] scaledVertRoadX, scaledVertRoadY;

    /***  road looks like "/"  ***/
    private int[] scaledUpRoadX, scaledUpRoadY;

    /***  road looks like "\"  ***/
    private int[] scaledDownRoadX, scaledDownRoadY;

    /***  settlement  ***/
    private int[] scaledSettlementX, scaledSettlementY;

    /***  city  ***/
    private int[] scaledCityX, scaledCityY;

    /***  robber  ***/
    private int[] scaledRobberX, scaledRobberY;

    /** 
     * arrow, left-pointing and right-pointing.
     * @see #rescaleCoordinateArrays()
     */
    private int[] scaledArrowXL, scaledArrowXR, scaledArrowY; 

    /** hex corners, clockwise from top-center, as located in waterHex.gif and other hex graphics:
     * (27,0) (54,16) (54,47) (27,63) (0,47) (0,16).
     *  If rotated 90deg clockwise, clockwise from center-right, would be:
     * (63,27) (47,54) (16,54) (0,27) (16,0) (47,0).
     * @see #hexCornersY
     * @since 1.1.07
     */
    private static final int[] hexCornersX =
    {
    	27, 54, 54, 27, 0, 0
    };

    /** hex corners, clockwise from top-center.
     * @see #hexCornersX
     * @since 1.1.07
     */
    private static final int[] hexCornersY =
    {
    	0, 16, 47, 63, 47, 16
    };

    /** 
     * hex corner coordinates, as scaled to current board size.
     * @see #hexCornersX
     * @see #rescaleCoordinateArrays()
     * @since 1.1.07
     */
    private int[] scaledHexCornersX, scaledHexCornersY; 

    /**
     * Old pointer coords for interface
     */
    private int ptrOldX, ptrOldY;
    
    /**
     * (tooltip) Hover text.  Its mode uses boardpanel mode
     * constants: Will be NONE, PLACE_ROAD, PLACE_SETTLEMENT,
     *   PLACE_ROBBER for hex, or PLACE_INIT_SETTLEMENT for port.
     */
    private BoardToolTip hoverTip;

    /**
     * Context menu for build/cancel-build
     */
    private BoardPopupMenu popupMenu;

    /**
     * Tracks last menu-popup time.  Avoids misinterpretation of popup-click with placement-click
     * during initial placement: On Windows, popup-click must be caught in mouseReleased,
     * but mousePressed is called immediately afterwards.    
     */
    private long popupMenuSystime;

    /**
     * For right-click build menu; used for fallback part of client-server-client
     * communication of a build request. Created whenever right-click build request sent.
     * This is the fallback for the normal method:
     * <pre>
     *  SOCBoardPanel.popupExpectingBuildRequest
     *  SOCPlayerInterface.updateAtGameState
     *  SOCBoardPanel.popupFireBuildingRequest
     * </pre>
     */
    protected BoardPanelSendBuildTask buildReqTimerTask;

    /**
     * Text to be displayed as 2 lines superimposed over the board graphic (during game setup).
     * Either supertext2, or both, can be null to display nothing.
     * @see #setSuperimposedText(String, String)
     * @since 1.1.07
     */
    private String superText1, superText2;

    /**
     * Width, height of {@link #superText1} and {@link #superText2} if known, or 0.
     * Calculated in {@link #drawSuperText(Graphics)}.
     */
    private int superText1_w, superText_h, superText2_w, superTextBox_x, superTextBox_y, superTextBox_w, superTextBox_h;

    /**
     * Edge or node being pointed to. When placing a road/settlement/city,
     * used for coordinate of "ghost" piece under the mouse pointer.
     */
    private int hilight;

    /**
     * Map grid sectors (from unscaled on-screen coordinates) to hex edges.
     * The grid has 15 columns and 23 rows.
     * This maps graphical coordinates to the board coordinate system.
     *<P>
     * The edge number at grid (x,y) is in edgeMap[x + (y * 15)].
     * @see #findEdge(int, int)
     * @see #initEdgeMapAux(int, int, int, int, int)
     */
    private int[] edgeMap;

    /**
     * Map grid sectors (from unscaled on-screen coordinates) to hex nodes.
     * The grid has 15 columns and 23 rows.
     * This maps graphical coordinates to the board coordinate system.
     * Each row of hexes touches 3 columns and 5 rows here. For instance,
     * hex 0x35 has its top-center point (node) in row y=6, and its bottom-center
     * point in row y=10, of this grid.  Its left edge is column x=3, and right is column x=5.
     *<P>
     * The node number at grid (x,y) is nodeMap[x + (y * 15)].
     * @see #findNode(int, int)
     * @see #initNodeMapAux(int, int, int, int, int)
     */
    private int[] nodeMap;

    /**
     * Map grid sectors (from on-screen coordinates) to hexes.
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
     * The player that is using this interface
     */
    private SOCPlayer player;
    
    /**
     * player number if in a game, or -1.
     */
    private int playerNumber;

    /**
     * When in "consider" mode, this is the player
     * we're talking to
     */
    private SOCPlayer otherPlayer;

    /**
     * offscreen buffer
     */
    private Image buffer;

    /**
     * Modes of interaction; for correlation to game state, see {@link #updateMode()}.
     * For tooltip's mode, see {@link SOCBoardPanel.BoardToolTip#hoverMode}.
     */
    private int mode;

    /**
     * This holds the coord of the last stlmt
     * placed in the initial phase.
     */
    private int initstlmt;

    /**
     * the player interface that this board is a part of
     */
    private SOCPlayerInterface playerInterface;

    /** Cached colors, for use for robber's "ghost"
     *  (previous position) when moving the robber.
     *  Values are determined the first time the
     *  robber is ghosted on that type of tile.
     *  
     *  Index ranges from 0 to SOCBoard.MAX_ROBBER_HEX.
     *  
     *  @see soc.client.ColorSquare
     *  @see #drawRobber(Graphics, int, boolean)
     */
    protected Color[] robberGhostFill, robberGhostOutline;

    /**
     * create a new board panel in an applet
     *
     * @param pi  the player interface that spawned us
     */
    public SOCBoardPanel(SOCPlayerInterface pi)
    {
        super();

        game = pi.getGame();
        playerInterface = pi;
        player = null;
        playerNumber = -1;
        board = game.getBoard();
        isScaled = false;
        scaledPanelX = PANELX;
        scaledPanelY = PANELY;
        scaledMissedImage = false;
        isRotated = isScaledOrRotated = game.isGameOptionSet("DEBUGROTABOARD");

        int i;

        // init coord holders
        ptrOldX = 0;
        ptrOldY = 0;

        hilight = 0;

        // init edge map
        edgeMap = new int[345];

        for (i = 0; i < 345; i++)
        {
            edgeMap[i] = 0;
        }

        initEdgeMapAux(4, 3, 9, 6, 0x37);    // Top row: 0x37 is first land hex of this row
        initEdgeMapAux(3, 6, 10, 9, 0x35);
        initEdgeMapAux(2, 9, 11, 12, 0x33);  // Middle row: 0x33 is leftmost land hex
        initEdgeMapAux(3, 12, 10, 15, 0x53);
        initEdgeMapAux(4, 15, 9, 18, 0x73);  // Bottom row: 0x73 is first land hex of this row

        // init node map
        nodeMap = new int[345];

        for (i = 0; i < 345; i++)
        {
            nodeMap[i] = 0;
        }

        initNodeMapAux(4, 3, 10, 7, 0x37);   // Top row: 0x37 is first land hex of this row
        initNodeMapAux(3, 6, 11, 10, 0x35);
        initNodeMapAux(2, 9, 12, 13, 0x33);  // Middle row: 0x33 is leftmost land hex
        initNodeMapAux(3, 12, 11, 16, 0x53); 
        initNodeMapAux(4, 15, 10, 19, 0x73); // Bottom row: 0x73 is first land hex of this row

        // init hex map
        hexMap = new int[345];

        for (i = 0; i < 345; i++)
        {
            hexMap[i] = 0;
        }

        initHexMapAux(4, 4, 9, 5, 0x37);
        initHexMapAux(3, 7, 10, 8, 0x35);
        initHexMapAux(2, 10, 11, 11, 0x33);
        initHexMapAux(3, 13, 10, 14, 0x53);
        initHexMapAux(4, 16, 9, 17, 0x73);

        hexIDtoNum = new int[0xDE];

        for (i = 0; i < 0xDE; i++)
        {
            hexIDtoNum[i] = 0;
        }

        initHexIDtoNumAux(0x17, 0x7D, 0);
        initHexIDtoNumAux(0x15, 0x9D, 4);
        initHexIDtoNumAux(0x13, 0xBD, 9);
        initHexIDtoNumAux(0x11, 0xDD, 15);
        initHexIDtoNumAux(0x31, 0xDB, 22);
        initHexIDtoNumAux(0x51, 0xD9, 28);
        initHexIDtoNumAux(0x71, 0xD7, 33);

        // set mode of interaction
        mode = NONE;

        // Set up mouse listeners
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        
        // Cached colors to be determined later
        robberGhostFill = new Color [1 + SOCBoard.MAX_ROBBER_HEX];
        robberGhostOutline = new Color [1 + SOCBoard.MAX_ROBBER_HEX];
        
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

        // point to static images, unless we're later resized
        scaledHexes = new Image[hexes.length];
        scaledPorts = new Image[ports.length];
        scaledNumbers = new Image[numbers.length];
        Image[] h, p;
        if (isRotated)
        {
            h = rotatHexes;
            p = rotatPorts;
        } else {
            h = hexes;
            p = ports;
        }
        for (i = hexes.length - 1; i>=0; --i)
            scaledHexes[i] = h[i];
        for (i = ports.length - 1; i>=0; --i)
            scaledPorts[i] = p[i];
        for (i = numbers.length - 1; i>=0; --i)
            scaledNumbers[i] = numbers[i];
        scaledHexFail = new boolean[hexes.length];
        scaledPortFail = new boolean[ports.length];
        scaledNumberFail = new boolean[numbers.length];

        // point to static coordinate arrays, unless we're later resized.
        // If this is the first instance, calculate arrowXR.
        rescaleCoordinateArrays();
    }

    private final void initEdgeMapAux(int x1, int y1, int x2, int y2, int startHex)
    {
        int x;
        int y;
        int facing = 0;
        int count = 0;
        int hexNum;
        int edgeNum = 0;

        for (y = y1; y <= y2; y++)
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

            for (x = x1; x <= x2; x++)
            {
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
         *   As seen for startHex = 0x37.  All numbers below are in hex.
         *         x-- 4     5     6     7     8        4     5     6     7     8
         * row   y
         *  |    |     nodeNums: (0 where blank)       rowState at top of x-loop:
         *  |    |
         *  0    3           38          5A            00    01    00    01    00
         *                /      \    /      \    /       /      \    /      \    /
         *  1    4     27          49          6B      10    11    12    11    12
         *             |           |           |       |           |           |
         *  2    5     0           0           0       20    20    20    20    20
         *             |           |           |       |           |           |
         *  3    6     36          58          7A      30    31    32    31    32
         *           /    \      /    \      /    \       \      /    \      /    \
         *  4    7     0     47    0     69    0       40    41    40    41    40
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
    public Dimension getPreferredSize()
    {
        return new Dimension(scaledPanelX, scaledPanelY);
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public Dimension getMinimumSize()
    {
        return new Dimension(PANELX, PANELY);
    }

    /**
     * Set the board to a new size, rescale graphics and repaint if needed.
     *
     * @param newW New width in pixels, no less than {@link #PANELX}
     * @param newH New height in pixels, no less than {@link #PANELY}
     * @throws IllegalArgumentException if newW or newH is too small but not 0.
     *   During initial layout, the layoutmanager may make calls to setSize(0,0);
     *   such a call is passed to super without scaling graphics.
     */
    public void setSize(int newW, int newH)
        throws IllegalArgumentException
    {
        if ((newW == scaledPanelX) && (newH == scaledPanelY))
            return;  // Already sized.

        // If below min-size, rescaleBoard throws
        // IllegalArgumentException. Pass to our caller.
        rescaleBoard(newW, newH);

        // Resize
        super.setSize(newW, newH);
        repaint();
    }

    /**
     * Set the board to a new size, rescale graphics and repaint if needed.
     *
     * @param sz New size in pixels, no less than {@link #PANELX} wide by {@link #PANELY} tall
     * @throws IllegalArgumentException if sz is too small but not 0.
     *   During initial layout, the layoutmanager may make calls to setSize(0,0);
     *   such a call is passed to super without scaling graphics.
     */
    public void setSize(Dimension sz)
        throws IllegalArgumentException
    {
        setSize (sz.width, sz.height);
    }

    /**
     * Set the board to a new location and size, rescale graphics and repaint if needed.
     * Called from {@link SOCPlayerInterface#doLayout()}.
     *
     * @param x New location's x-coordinate
     * @param y new location's y-coordinate
     * @param w New width in pixels, no less than {@link #PANELX} (or if rotated, {@link #PANELY}})
     * @param h New height in pixels, no less than {@link #PANELY} (or if rotated, {@link #PANELX})
     * @throws IllegalArgumentException if w or h is too small but not 0.
     *   During initial layout, the layoutmanager may make calls to setBounds(0,0,0,0);
     *   such a call is passed to super without scaling graphics.
     */
    public void setBounds(int x, int y, int w, int h)
        throws IllegalArgumentException
    {
        if ((w != scaledPanelX) || (h != scaledPanelY))            
        {
            rescaleBoard(w, h);
        }
        super.setBounds(x, y, w, h);
    }

    /**
     * Set the board fields to a new size, rescale graphics if needed.
     * Does not call repaint.  Does not call setSize.
     * Will update {@link #isScaledOrRotated}, {@link #scaledPanelX}, and other fields.
     *
     * @param newW New width in pixels, no less than {@link #PANELX} (or if rotated, {@link #PANELY}})
     * @param newH New height in pixels, no less than {@link #PANELY} (or if rotated, {@link #PANELX})
     * @throws IllegalArgumentException if newW or newH is too small but not 0.
     *   During initial layout, the layoutmanager may cause calls to rescaleBoard(0,0);
     *   such a call is ignored, no rescaling of graphics is done.
     */
    private void rescaleBoard(final int newW, final int newH)
        throws IllegalArgumentException
    {
        if ((newW == 0) || (newH == 0))
            return;
        final int bMinW, bMinH;
        if (isRotated)
        {
            bMinW = PANELY;  bMinH = PANELX;
        } else {
            bMinW = PANELX;  bMinH = PANELY;
        }
        if ((newW < bMinW) || (newH < bMinH))
            throw new IllegalArgumentException("Below minimum size");
    
        /**
         * Set vars
         */
        scaledPanelX = newW;
        scaledPanelY = newH;
        isScaled = ((scaledPanelX != bMinW) || (scaledPanelY != bMinH));
        scaledAt = System.currentTimeMillis();
        isScaledOrRotated = (isScaled || isRotated);

        /**
         * Off-screen buffer is now the wrong size.
         * paint() will create a new one.
         */
        if (buffer != null)
        {
            buffer.flush();
            buffer = null;
        }

        /**
         * Scale coordinate arrays for drawing pieces,
         * or (if not isScaled) point to static arrays.
         */
        rescaleCoordinateArrays();

        /**
         * Scale images, or point to static arrays.
         */
        Image[] hex, por;
        if (isRotated)
        {
            hex = rotatHexes;
            por = rotatPorts;
        } else {
            hex = hexes;
            por = ports;
        }
        if (! isScaled)
        {
            int i;
            for (i = scaledHexes.length - 1; i>=0; --i)
                scaledHexes[i] = hex[i];
            for (i = ports.length - 1; i>=0; --i)
                scaledPorts[i] = por[i];
            for (i = numbers.length - 1; i>=0; --i)
                scaledNumbers[i] = numbers[i];
        }
        else
        {
            int w = scaleToActualX(hex[0].getWidth(null));
            int h = scaleToActualY(hex[0].getHeight(null));
            for (int i = scaledHexes.length - 1; i>=0; --i)
            {
                scaledHexes[i] = hex[i].getScaledInstance(w, h, Image.SCALE_SMOOTH);
                scaledHexFail[i] = false;
            }

            w = scaleToActualX(por[1].getWidth(null));
            h = scaleToActualY(por[1].getHeight(null));
            for (int i = scaledPorts.length - 1; i>=1; --i)
            {
                scaledPorts[i] = por[i].getScaledInstance(w, h, Image.SCALE_SMOOTH);
                scaledPortFail[i] = false;
            }

            w = scaleToActualX(numbers[0].getWidth(null));
            h = scaleToActualY(numbers[0].getHeight(null));
            for (int i = scaledNumbers.length - 1; i>=0; --i)
            {
                scaledNumbers[i] = numbers[i].getScaledInstance(w, h, Image.SCALE_SMOOTH);
                scaledNumberFail[i] = false;
            }
        }

        if ((superText1 != null) && (superTextBox_w > 0))
        {
            superTextBox_x = (newW - superTextBox_w) / 2;
            superTextBox_y = (newH - superTextBox_h) / 2;
        }
    }

    /**
     * Scale coordinate arrays for drawing pieces
     * (from internal coordinates to actual on-screen pixels),
     * or (if not isScaled) point to static arrays.
     * Called from constructor and rescaleBoard.
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
            } else {
                // (cw):  P'=(width-y, x)
                scaledVertRoadX = rotateScaleCopyYToActualX(vertRoadY, HEXWIDTH, false);
                scaledVertRoadY = vertRoadX;
                scaledUpRoadX   = rotateScaleCopyYToActualX(upRoadY, HEXWIDTH, false);
                scaledUpRoadY   = upRoadX;
                scaledDownRoadX = rotateScaleCopyYToActualX(downRoadY, HEXWIDTH, false);
                scaledDownRoadY = downRoadX;
                scaledHexCornersX = rotateScaleCopyYToActualX(hexCornersY, HEXWIDTH, false);
                scaledHexCornersY = hexCornersX;
            }
            scaledSettlementX = settlementX; scaledSettlementY = settlementY;
            scaledCityX     = cityX;         scaledCityY     = cityY;
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
            if (! isRotated)
            {
                scaledVertRoadX = scaleCopyToActualX(vertRoadX);
                scaledVertRoadY = scaleCopyToActualY(vertRoadY);
                scaledUpRoadX   = scaleCopyToActualX(upRoadX);
                scaledUpRoadY   = scaleCopyToActualY(upRoadY);
                scaledDownRoadX = scaleCopyToActualX(downRoadX);
                scaledDownRoadY = scaleCopyToActualY(downRoadY);
                scaledHexCornersX = scaleCopyToActualX(hexCornersX);
                scaledHexCornersY = scaleCopyToActualY(hexCornersY);
            } else {
                // (cw):  P'=(width-y, x)
                scaledVertRoadX = rotateScaleCopyYToActualX(vertRoadY, HEXWIDTH, true);
                scaledVertRoadY = scaleCopyToActualY(vertRoadX);
                scaledUpRoadX   = rotateScaleCopyYToActualX(upRoadY, HEXWIDTH, true);
                scaledUpRoadY   = scaleCopyToActualY(upRoadX);
                scaledDownRoadX = rotateScaleCopyYToActualX(downRoadY, HEXWIDTH, true);
                scaledDownRoadY = scaleCopyToActualY(downRoadX);
                scaledHexCornersX = rotateScaleCopyYToActualX(hexCornersY, HEXWIDTH, true);
                scaledHexCornersY = scaleCopyToActualY(hexCornersX);
            }
            scaledSettlementX = scaleCopyToActualX(settlementX);
            scaledSettlementY = scaleCopyToActualY(settlementY);
            scaledCityX     = scaleCopyToActualX(cityX);
            scaledCityY     = scaleCopyToActualY(cityY);
            scaledRobberX   = scaleCopyToActualX(robberX);
            scaledRobberY   = scaleCopyToActualY(robberY);
            scaledArrowXL   = scaleCopyToActualX(arrowXL);
            scaledArrowY    = scaleCopyToActualY(arrowY);

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
     * of array, and scale the copy's elements as X coordinates.
     *
     * @param xorig Int array to be scaled; each member is an x-coordinate.
     * @return Scaled copy of xorig
     *
     * @see #scaleToActualX(int[])
     * @see #rotateScaleCopyYToActualX(int[], int, boolean)
     */
    public int[] scaleCopyToActualX(int[] xorig)
    {
        int[] xs = new int[xorig.length];
        for (int i = xorig.length - 1; i >= 0; --i)
            xs[i] = (int) ((xorig[i] * (long) scaledPanelX) / PANELX);
        return xs;
    }

    /**
     * Rescale to actual screen coordinates - Create a copy
     * of array, and scale the copy's elements as Y coordinates.
     *
     * @param yorig Int array to be scaled; each member is a y-coordinate.
     * @return Scaled copy of yorig
     *
     * @see #scaleToActualY(int[])
     */
    public int[] scaleCopyToActualY(int[] yorig)
    {
        int[] ys = new int[yorig.length];
        for (int i = yorig.length - 1; i >= 0; --i)
            ys[i] = (int) ((yorig[i] * (long) scaledPanelY) / PANELY);
        return ys;
    }

    /**
     * Copy and rotate this array of y-coordinates, optionally also rescaling.
     * Rotates internal to actual (clockwise):  P'=(width-y, x)
     * @param yorig Array to copy and rotate, not null
     * @param width Width to rotate against
     * @param rescale Should we also rescale, same formula as {@link #scaleCopyToActualX(int[])}?
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
                xr[i] = (int) ((xr[i] * (long) scaledPanelX) / PANELX);
        return xr;
    }

    /**
     * Redraw the board using double buffering. Don't call this directly, use
     * {@link Component#repaint()} instead.
     */
    public void paint(Graphics g)
    {
        try {
        if (buffer == null)
        {
            buffer = this.createImage(scaledPanelX, scaledPanelY);
        }
        drawBoard(buffer.getGraphics());
        if (hoverTip.isVisible())
            hoverTip.paint(buffer.getGraphics());
        buffer.flush();
        g.drawImage(buffer, 0, 0, this);

        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }

    /**
     * Overriden so the peer isn't painted, which clears background. Don't call
     * this directly, use {@link Component#repaint()} instead.
     */
    public void update(Graphics g)
    {
        paint(g);
    }

    /**
     * draw a board tile
     */
    private final void drawHex(Graphics g, int hexNum)
    {
        int tmp;
        int[] hexLayout = board.getHexLayout();
        int[] numberLayout = board.getNumberLayout();
        int hexType = hexLayout[hexNum];

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
        Image[] hexis = (isRotated ? rotatHexes : hexes);  // Fall back to original or rotated?

        int x = hexX[hexNum];
        int y = hexY[hexNum];
        if (isScaledOrRotated)
        {
            if (isRotated)
            {
                // (cw):  P'=(PANELY-y, x)
                int y1 = x;
                x = PANELY - y - HEXHEIGHT;  // move 1 hex over, since corner of image has rotated
                y = y1;
            }
            if (isScaled)
            {
                x = scaleToActualX(x); 
                y = scaleToActualY(y);
            }
        }
        tmp = hexType & 15; // get only the last 4 bits;

        if (isScaled && (scaledHexes[tmp] == hexis[tmp]))
        {
            recenterPrevMiss = true;
            int w = hexis[tmp].getWidth(null);
            int h = hexis[tmp].getHeight(null);
            xm = (scaleToActualX(w) - w) / 2;
            ym = (scaleToActualY(h) - h) / 2;
            x += xm;
            y += ym;
        }

        /**
         * Draw the hex graphic
         */
        if (! g.drawImage(scaledHexes[tmp], x, y, this))
        {
            // for now, draw the placeholder; try to rescale and redraw soon if we can

            g.translate(x, y);
            g.setColor(hexColor(hexType));
            g.fillPolygon(scaledHexCornersX, scaledHexCornersY, 6);
            g.setColor(Color.BLACK);
            g.drawPolyline(scaledHexCornersX, scaledHexCornersY, 6);
            g.translate(-x, -y);

            if (isScaled && (7000 < (System.currentTimeMillis() - scaledAt)))
            {
                missedDraw = true;

                // rescale the image or give up
                if (scaledHexFail[tmp])
                {
                    scaledHexes[tmp] = hexis[tmp];  // fallback
                }
                else
                {
                    scaledHexFail[tmp] = true;
                    int w = scaleToActualX(hexis[0].getWidth(null));
                    int h = scaleToActualY(hexis[0].getHeight(null));
                    scaledHexes[tmp] = hexis[tmp].getScaledInstance(w, h, Image.SCALE_SMOOTH);
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

        /**
         * Draw the port graphic
         */
        tmp = hexType >> 4; // get the facing of the port

        if (tmp > 0)
        {
            Image[] portis = (isRotated ? rotatPorts : ports);  // Fall back to original or rotated?
            if (isScaled && (scaledPorts[tmp] == portis[tmp]))
            {
                recenterPrevMiss = true;
                int w = portis[tmp].getWidth(null);
                int h = portis[tmp].getHeight(null);
                xm = (scaleToActualX(w) - w) / 2;
                ym = (scaleToActualY(h) - h) / 2;
                x += xm;
                y += ym;
            }
            if (! g.drawImage(scaledPorts[tmp], x, y, this))
            {
                g.drawImage(portis[tmp], x, y, null);  // show small port graphic, instead of a blank space
                if (isScaled && (7000 < (System.currentTimeMillis() - scaledAt)))
                {
                    missedDraw = true;
                    if (scaledPortFail[tmp])
                    {
                        scaledPorts[tmp] = portis[tmp];  // fallback
                    }
                    else
                    {
                        scaledPortFail[tmp] = true;
                        int w = scaleToActualX(portis[1].getWidth(null));
                        int h = scaleToActualY(portis[1].getHeight(null));
                        scaledPorts[tmp] = portis[tmp].getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    }
                }
            }

            // Don't adj x,y to un-"center" further drawing:
            // If scaled, x,y will be recalculated anyway.
            // If not scaled, recenterPrevMiss is false.
        }

        /**
         * Draw the number
         */
        int hnl = numberLayout[hexNum];
        if (hnl >= 0)
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
                x = scaleToActualX(hexX[hexNum] + dx);
                y = scaleToActualY(hexY[hexNum] + dy);
                if (scaledNumbers[hnl] == numbers[hnl])
                {
                    // recenterPrevMiss = true;
                    int w = scaledNumbers[hnl].getWidth(null);
                    int h = scaledNumbers[hnl].getHeight(null);
                    xm = (scaleToActualX(w) - w) / 2;
                    ym = (scaleToActualY(h) - h) / 2;
                    x += xm;
                    y += ym;
                }
            }
            if (! g.drawImage(scaledNumbers[hnl], x, y, this))
            {
                g.drawImage(numbers[hnl], x, y, null);  // must show a number, not a blank space
                if (isScaled && (7000 < (System.currentTimeMillis() - scaledAt)))
                {
                    missedDraw = true;
                    if (scaledNumberFail[hnl])
                    {
                        scaledNumbers[hnl] = numbers[hnl];  // fallback
                    }
                    else
                    {
                        scaledNumberFail[hnl] = true;
                        int w = scaleToActualX(numbers[0].getWidth(null));
                        int h = scaleToActualY(numbers[0].getHeight(null));                    
                        scaledNumbers[hnl] = numbers[hnl].getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    }
                }
            }
        }

        if (missedDraw)
        {
            // drawBoard will check this field after all hexes are drawn.
            scaledMissedImage = true;
        }
    }

    /**
     * draw the robber
     * 
     * @param g       Graphics context
     * @param hexID   Board hex encoded position
     * @param fullNotGhost  Draw normally, not "ghost" of previous position
     *                (as during PLACE_ROBBER movement)
     */
    private final void drawRobber(Graphics g, int hexID, boolean fullNotGhost)
    {
        int hexNum = hexIDtoNum[hexID];
        int hx = hexX[hexNum] + 27;
        int hy = hexY[hexNum] + 31;
        if (isRotated)
        {
            // (cw):  P'=(PANELY-y, x)
            int hy1 = hx;
            hx = PANELY - hy;
            hy = hy1;
        }
        if (isScaled)
        {
            hx = scaleToActualX(hx);
            hy = scaleToActualY(hy);
        }

        Color rFill, rOutline;
        if (fullNotGhost)
        {
            rFill = Color.lightGray;
            rOutline = Color.black;
        } else {
            // Determine "ghost" color, we're moving the robber
            int hexType = board.getHexLayout()[hexNum];
            if (hexType >= robberGhostFill.length)
            {
                // should not happen
                rFill = Color.lightGray;
                rOutline = Color.black;
            } else if (robberGhostFill[hexType] != null)
            {
                // was cached from previous calculation
                rFill = robberGhostFill[hexType];
                rOutline = robberGhostOutline[hexType];
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
        g.setColor(rFill);
        g.fillPolygon(scaledRobberX, scaledRobberY, 13);
        g.setColor(rOutline);
        g.drawPolygon(scaledRobberX, scaledRobberY, 14);
        g.translate(-hx, -hy);
    }

    /**
     * draw a road
     */
    private final void drawRoad(Graphics g, int edgeNum, int pn, boolean isHilight)
    {
        // Draw a road
        int hexNum, roadX[], roadY[];

        if ((((edgeNum & 0x0F) + (edgeNum >> 4)) % 2) == 0)
        { // If first and second digit 
            hexNum = hexIDtoNum[edgeNum + 0x11]; // are even, then it is '|'.
            roadX = scaledVertRoadX;
            roadY = scaledVertRoadY;
        }
        else if (((edgeNum >> 4) % 2) == 0)
        { // If first digit is even,
            hexNum = hexIDtoNum[edgeNum + 0x10]; // then it is '/'.
            roadX = scaledUpRoadX;
            roadY = scaledUpRoadY;
        }
        else
        { // Otherwise it is '\'.
            hexNum = hexIDtoNum[edgeNum + 0x01];
            roadX = scaledDownRoadX;
            roadY = scaledDownRoadY;
        }
        int hx = hexX[hexNum];
        int hy = hexY[hexNum];
        if (isRotated)
        {
            // (cw):  P'=(PANELY-y, x)
            int hy1 = hx;
            hx = PANELY - hy - deltaX;  // -deltaX is because road poly coords are against hex width/height,
                                        // and the hex image gets similar translation in drawHex.
            hy = hy1;
        }            
        if (isScaled)
        {
            hx = scaleToActualX(hx);
            hy = scaleToActualY(hy);
        }

        if (isHilight)
            g.setColor(playerInterface.getPlayerColor(pn, true));
        else
            g.setColor(playerInterface.getPlayerColor(pn));

        g.translate(hx, hy);
        g.fillPolygon(roadX, roadY, 5);
        if (isHilight)
            g.setColor(playerInterface.getPlayerColor(pn, false));
        else
            g.setColor(Color.black);
        g.drawPolygon(roadX, roadY, 5);
        g.translate(-hx, -hy);
    }

    /**
     * draw a settlement
     */
    private final void drawSettlement(Graphics g, int nodeNum, int pn, boolean isHilight)
    {
        int hexNum, hx, hy;

        if (((nodeNum >> 4) % 2) == 0)
        { // If first digit is even,
            hexNum = hexIDtoNum[nodeNum + 0x10]; // then it is a 'Y' node
            hx = hexX[hexNum];
            hy = hexY[hexNum] + 17;
        }
        else
        { // otherwise it is an 'A' node
            hexNum = hexIDtoNum[nodeNum - 0x01];
            hx = hexX[hexNum] + 27;
            hy = hexY[hexNum] + 2;
        }
        if (isRotated)
        {
            // (cw):  P'=(PANELY-y, x)
            int hy1 = hx;
            hx = PANELY - hy;
            hy = hy1;
        }
        if (isScaled)
        {
            hx = scaleToActualX(hx);
            hy = scaleToActualY(hy);
        }

        // System.out.println("NODEID = "+Integer.toHexString(nodeNum)+" | HEXNUM = "+hexNum);
        if (isHilight)
            g.setColor(playerInterface.getPlayerColor(pn, true));
        else
            g.setColor(playerInterface.getPlayerColor(pn));
        g.translate(hx, hy);
        g.fillPolygon(scaledSettlementX, scaledSettlementY, 6);
        if (isHilight)
            g.setColor(playerInterface.getPlayerColor(pn, false));
        else
            g.setColor(Color.black);
        g.drawPolygon(scaledSettlementX, scaledSettlementY, 7);
        g.translate(-hx, -hy);
    }

    /**
     * draw a city
     */
    private final void drawCity(Graphics g, int nodeNum, int pn, boolean isHilight)
    {
        int hexNum, hx, hy;

        if (((nodeNum >> 4) % 2) == 0)
        { // If first digit is even,
            hexNum = hexIDtoNum[nodeNum + 0x10]; // then it is a 'Y' node
            hx = hexX[hexNum];
            hy = hexY[hexNum] + 17;
        }
        else
        { // otherwise it is an 'A' node
            hexNum = hexIDtoNum[nodeNum - 0x01];
            hx = hexX[hexNum] + 27;
            hy = hexY[hexNum] + 2;
        }
        if (isScaledOrRotated)
        {
            if (isRotated)
            {
                // (cw):  P'=(PANELY-y, x)
                int hy1 = hx;
                hx = PANELY - hy;
                hy = hy1;
            }
            if (isScaled)
            {
                hx = scaleToActualX(hx);
                hy = scaleToActualY(hy);
            }
        }

        g.translate(hx, hy);
        if (isHilight)
        {
            g.setColor(playerInterface.getPlayerColor(pn, true));
            g.drawPolygon(scaledCityX, scaledCityY, 8);
            // Draw again, slightly offset, for "ghost", since we can't fill and
            // cover up the underlying settlement.
            g.translate(1,1);
            g.drawPolygon(scaledCityX, scaledCityY, 8);
            g.translate(-(hx+1), -(hy+1));
            return;
        }
        
        g.setColor(playerInterface.getPlayerColor(pn));

        g.fillPolygon(scaledCityX, scaledCityY, 8);
        g.setColor(Color.black);
        g.drawPolygon(scaledCityX, scaledCityY, 8);
        g.translate(-hx, -hy);
    }

    /**
     * draw the arrow that shows whose turn it is.
     *
     * @param g Graphics
     * @param pnum Player position: 0 for top-left, 1 for top-right,
     *             2 for bottom-right, 3 for bottom-left
     * @param diceResult Roll result to show, if rolled.
     *                   To show, diceResult must be at least 2,
     *                   and gamestate not SOCGame.PLAY.
     */
    private final void drawArrow(Graphics g, int pnum, int diceResult)
    {
        int arrowX, arrowY, diceX, diceY;
        boolean arrowLeft;

        switch (pnum)
        {
        case 0:

            // top left
            arrowX = 3;  arrowY = 5;  arrowLeft = true;
            diceX = 13;  diceY = 10;

            break;

        case 1:

            // top right
            arrowX = 339;  arrowY = 5;  arrowLeft = false;
            diceX = 339;  diceY = 10;

            break;

        case 2:

            // bottom right
            arrowX = 339;  arrowY = 298;  arrowLeft = false;
            diceX = 339;  diceY = 303;

            break;

        default:  // 3: (Default prevents compiler var-not-init errors)

            // bottom left
            arrowX = 3;  arrowY = 298;  arrowLeft = true;
            diceX = 13;  diceY = 303;

            break;

        }

        /**
         * Draw Arrow
         */
        if (isScaled)
        {
            arrowX = scaleToActualX(arrowX);
            arrowY = scaleToActualY(arrowY);
        }
        int[] scArrowX;
        if (arrowLeft)
            scArrowX = scaledArrowXL;
        else
            scArrowX = scaledArrowXR;
        g.translate(arrowX, arrowY);
        g.setColor(ARROW_COLOR);        
        g.fillPolygon(scArrowX, scaledArrowY, scArrowX.length);
        g.setColor(Color.BLACK);
        g.drawPolygon(scArrowX, scaledArrowY, scArrowX.length);
        g.translate(-arrowX, -arrowY);

        /**
         * Draw Dice result number
         */
        if ((diceResult >= 2) && (game.getGameState() != SOCGame.PLAY))
        {
            if (isScaled)
            {
                // Dice number is not scaled, but arrow is.
                // Move to keep centered in arrow.
                int adj = (scaleToActualX(DICE_SZ) - DICE_SZ) / 2;
                diceX = scaleToActualX(diceX) + adj;
                diceY = scaleToActualY(diceY) + adj;
            }
            g.drawImage(dice[diceResult], diceX, diceY, this);
        }
    }

    /**
     * draw the whole board
     */
    private void drawBoard(Graphics g)
    {
        g.setPaintMode();

        g.setColor(getBackground());
        g.fillRect(0, 0, scaledPanelX, scaledPanelY);

        scaledMissedImage = false;    // drawHex will set this flag if missed
        for (int i = 0; i < 37; i++)  // TODO 5,6-player largerboard: assumes 37 hexes
        {
            drawHex(g, i);
        }
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

        int gameState = game.getGameState();

        if (board.getRobberHex() != -1)
        {
            drawRobber(g, board.getRobberHex(), (gameState != SOCGame.PLACING_ROBBER));
        }

        if (gameState != SOCGame.NEW)
        {
            drawArrow(g, game.getCurrentPlayerNumber(), game.getCurrentDice());
        }

        /**
         * draw the roads
         */
        Enumeration roads = board.getRoads().elements();

        while (roads.hasMoreElements())
        {
            SOCRoad r = (SOCRoad) roads.nextElement();
            drawRoad(g, r.getCoordinates(), r.getPlayer().getPlayerNumber(), false);
        }

        /**
         * draw the settlements
         */
        Enumeration settlements = board.getSettlements().elements();

        while (settlements.hasMoreElements())
        {
            SOCSettlement s = (SOCSettlement) settlements.nextElement();
            drawSettlement(g, s.getCoordinates(), s.getPlayer().getPlayerNumber(), false);
        }

        /**
         * draw the cities
         */
        Enumeration cities = board.getCities().elements();

        while (cities.hasMoreElements())
        {
            SOCCity c = (SOCCity) cities.nextElement();
            drawCity(g, c.getCoordinates(), c.getPlayer().getPlayerNumber(), false);
        }

        if (player != null)
        {
        /**
         * Draw the hilight when in interactive mode;
         * No hilight when null player (before game started).
         */
        switch (mode)
        {
        case PLACE_ROAD:
        case PLACE_INIT_ROAD:

            if (hilight > 0)
            {
                drawRoad(g, hilight, player.getPlayerNumber(), true);
            }

            break;

        case PLACE_SETTLEMENT:
        case PLACE_INIT_SETTLEMENT:

            if (hilight > 0)
            {
                drawSettlement(g, hilight, player.getPlayerNumber(), true);
            }

            break;

        case PLACE_CITY:

            if (hilight > 0)
            {
                drawCity(g, hilight, player.getPlayerNumber(), true);
            }

            break;

        case CONSIDER_LM_SETTLEMENT:
        case CONSIDER_LT_SETTLEMENT:

            if (hilight > 0)
            {
                drawSettlement(g, hilight, otherPlayer.getPlayerNumber(), true);
            }

            break;

        case CONSIDER_LM_ROAD:
        case CONSIDER_LT_ROAD:

            if (hilight > 0)
            {
                drawRoad(g, hilight, otherPlayer.getPlayerNumber(), false);
            }

            break;

        case CONSIDER_LM_CITY:
        case CONSIDER_LT_CITY:

            if (hilight > 0)
            {
                drawCity(g, hilight, otherPlayer.getPlayerNumber(), true);
            }

            break;

        case PLACE_ROBBER:

            if (hilight > 0)
            {
                drawRobber(g, hilight, true);
            }

            break;
        }  // switch
        }  // if (player != null)

        if (superText1 != null)
        {
            drawSuperText(g);
        }
    }

    /**
     * Draw {@link #superText1}, {@link #superText2}; if necessary, calculate {@link #superText1_w} and other fields.
     * @since 1.1.07
     */
    private void drawSuperText(Graphics g)
    {
        // Do we need to calculate the metrics?

        if ((superText1_w == 0) || ((superText2 != null) && (superText2_w == 0)))
        {
            final Font bpf = getFont();
            if (bpf == null)
            {
                repaint();  // We'll have to try again
                return;
            } else {
                final FontMetrics fm = getFontMetrics(bpf);
                if (fm == null)
                {
                    repaint();
                    return;  // We'll have to try again
                } else {
                    if (superText1_w == 0)
                    {
                        superText1_w = fm.stringWidth(superText1);
                        superText_h = fm.getHeight();
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

                    superTextBox_x = (scaledPanelX - superTextBox_w) / 2;
                    superTextBox_y = (scaledPanelY - superTextBox_h) / 2;
                }
            }
        }
        // adj from center
        g.setColor(Color.black);
        g.fillRoundRect(superTextBox_x, superTextBox_y, superTextBox_w, superTextBox_h, SUPERTEXT_INSET, SUPERTEXT_INSET);
        g.setColor(Color.white);
        g.fillRoundRect(superTextBox_x + SUPERTEXT_INSET, superTextBox_y + SUPERTEXT_INSET,
             superTextBox_w - 2 * SUPERTEXT_INSET, superTextBox_h - 2 * SUPERTEXT_INSET, SUPERTEXT_INSET, SUPERTEXT_INSET);
        g.setColor(Color.black);
        // draw text at center
        int tx = (scaledPanelX - superText1_w) / 2;
        int ty = superTextBox_y + SUPERTEXT_INSET + superText_h;
        g.drawString(superText1, tx, ty);
        if (superText2 != null)
        {
            tx -= (superText2_w - superText1_w) / 2;
            ty += superText_h;
            g.drawString(superText2, tx, ty);
        }
    }

    /**
     * Scale x-array from internal to actual screen-pixel coordinates.
     * If not isScaled, do nothing.
     *
     * @param xa Int array to be scaled; each member is an x-coordinate.
     * 
     * @see #scaleCopyToActualX(int[])
     */
    public void scaleToActualX(int[] xa)
    {
        if (! isScaled)
            return;
        for (int i = xa.length - 1; i >= 0; --i)
            xa[i] = (int) ((xa[i] * (long) scaledPanelX) / PANELX);
    }

    /**
     * Scale y-array from internal to actual screen-pixel coordinates.
     * If not isScaled, do nothing.
     *
     * @param ya Int array to be scaled; each member is an y-coordinate.
     *
     * @see #scaleCopyToActualY(int[])
     */
    public void scaleToActualY(int[] ya)
    {
        if (! isScaled)
            return;
        for (int i = ya.length - 1; i >= 0; --i)
            ya[i] = (int) ((ya[i] * (long) scaledPanelY) / PANELY);
    }

    /**
     * Scale x-coordinate from internal to actual screen-pixel coordinates.
     * If not isScaled, return input.
     *
     * @param x x-coordinate to be scaled
     */
    public int scaleToActualX(int x)
    {
        if (! isScaled)
            return x;
        else
            return (int) ((x * (long) scaledPanelX) / PANELX);
    }

    /**
     * Scale y-coordinate from internal to actual screen-pixel coordinates.
     * If not isScaled, return input.
     *
     * @param y y-coordinate to be scaled
     */
    public int scaleToActualY(int y)
    {
        if (! isScaled)
            return y;
        else
            return (int) ((y * (long) scaledPanelY) / PANELY);
    }

    /**
     * Convert an x-coordinate from actual-scaled to internal-scaled coordinates.
     * If not isScaled, return input.
     *
     * @param x x-coordinate to be scaled
     */
    public int scaleFromActualX(int x)
    {
        if (! isScaled)
            return x;
        return (int) ((x * (long) PANELX) / scaledPanelX);
    }

    /**
     * Convert a y-coordinate from actual-scaled to internal-scaled coordinates.
     * If not isScaled, return input.
     *
     * @param y y-coordinate to be scaled
     */
    public int scaleFromActualY(int y)
    {
        if (! isScaled)
            return y;
        return (int) ((y * (long) PANELY) / scaledPanelY);
    }

    /**
     * Is the board currently scaled larger than
     * {@link #PANELX} x {@link #PANELY} pixels?
     * If so, use {@link #scaleToActualX(int)}, {@link #scaleFromActualY(int)},
     * etc to convert between internal and actual screen pixel coordinates.
     *
     * @return Is the board scaled larger than default size?
     * @see #isRotated()
     */
    public boolean isScaled()
    {
        return isScaled;
    }

    /**
     * Is the board currently rotated 90 degrees clockwise?
     * If so, the minimum size swaps {@link #PANELX} and {@link #PANELY}.
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
     * update the type of interaction mode.
     * Also calls {@link #updateHoverTipToMode()}.
     */
    public void updateMode()
    {
        if (player != null)
        {
            if (game.getCurrentPlayerNumber() == player.getPlayerNumber())
            {
                switch (game.getGameState())
                {
                case SOCGame.START1A:
                case SOCGame.START2A:
                    mode = PLACE_INIT_SETTLEMENT;

                    break;

                case SOCGame.START1B:
                case SOCGame.START2B:
                    mode = PLACE_INIT_ROAD;

                    break;

                case SOCGame.PLACING_ROAD:
                case SOCGame.PLACING_FREE_ROAD1:
                case SOCGame.PLACING_FREE_ROAD2:
                    mode = PLACE_ROAD;

                    break;

                case SOCGame.PLACING_SETTLEMENT:
                    mode = PLACE_SETTLEMENT;

                    break;

                case SOCGame.PLACING_CITY:
                    mode = PLACE_CITY;

                    break;

                case SOCGame.PLACING_ROBBER:
                    mode = PLACE_ROBBER;

                    break;
                    
                case SOCGame.NEW:
                case SOCGame.READY:
                    mode = GAME_FORMING;

                    break;

                case SOCGame.OVER:
                    mode = GAME_OVER;

                    break;

                case SOCGame.PLAY:
                    mode = TURN_STARTING;

                    break;

                default:
                    mode = NONE;

                    break;
                }
            }
            else
            {
                mode = NONE;
            }
        }
        else
        {
            mode = NONE;
        }
                
        updateHoverTipToMode();
    }
    
    protected void updateHoverTipToMode()
    {
        if ((mode == NONE) || (mode == TURN_STARTING) || (mode == GAME_OVER))            
            hoverTip.setOffsetX(0);
        else if (mode == PLACE_ROBBER)
            hoverTip.setOffsetX(HOVER_OFFSET_X_FOR_ROBBER);
        else if ((mode == PLACE_INIT_SETTLEMENT) || (mode == PLACE_INIT_ROAD))
            hoverTip.setOffsetX(HOVER_OFFSET_X_FOR_INIT_PLACE);
        else
            hoverTip.setHoverText(null);
    }

    /**
     * Set board mode to {@link #NONE}, no hilight, usually from a piece-placement mode.
     *
     * @param ptype Piece type to clear, like {@link SOCPlayingPiece#ROAD}, or -1 for robber.
     *              Used to avoid race condition during initial placement,
     *              where server has already replied with mode for another piece type,
     *              and board has already set mode to place that piece type.
     *              If ptype doesn't match the board's current mode/piece type,
     *              board's mode is not changed to NONE.
     */
    protected void clearModeAndHilight(final int ptype)
    {
        int expectedPtype;  // based on current mode

        switch (mode)
        {
        case PLACE_ROAD:
        case PLACE_INIT_ROAD:
        case CONSIDER_LM_ROAD:
        case CONSIDER_LT_ROAD:
            expectedPtype = SOCPlayingPiece.ROAD;
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

        case PLACE_ROBBER:
            expectedPtype = -1;
            break;

        default:
            expectedPtype = ptype;  // Not currently placing
        }

        if (ptype == expectedPtype)
        {
            mode = NONE;
            hilight = 0;
        }
        updateHoverTipToMode();
    }

    /**
     * set the player that is using this board panel.
     */
    public void setPlayer()
    {
        player = game.getPlayer(playerInterface.getClient().getNickname());
        playerNumber = player.getPlayerNumber();
    }

    /**
     * set the other player
     *
     * @param op  the other player
     */
    public void setOtherPlayer(SOCPlayer op)
    {
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
        try {
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
            wantsRepaint = true;
        }
        if (wantsRepaint)
            repaint();
    }

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mouseMoved(MouseEvent e)
    {
        try {
        int x = e.getX();
        int y = e.getY();
        int xb, yb;
        if (isScaled)
        {
            xb = scaleFromActualX(x);
            yb = scaleFromActualY(y);
        }
        else
        {
            xb = x;
            yb = y;
        }
        if (isRotated)
        {
            // (ccw): P'=(y, PANELX-x)
            int xb1 = yb;
            yb = PANELX - xb - deltaY;  // -deltaY for similar reasons as -HEXHEIGHT in drawHex
            xb = xb1;
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
                edgeNum = findEdge(xb, yb);

                // Figure out if this is a legal road
                // It must be attached to the last stlmt
                if ((player == null) ||
                     ! ((player.isPotentialRoad(edgeNum)) && ((edgeNum == initstlmt) || (edgeNum == (initstlmt - 0x11)) || (edgeNum == (initstlmt - 0x01)) || (edgeNum == (initstlmt - 0x10)))))
                {
                    edgeNum = 0;
                }

                if (hilight != edgeNum)
                {
                    hilight = edgeNum;
                    repaint();
                }
            }

            break;

        case PLACE_ROAD:

            /**** Code for finding an edge ********/
            edgeNum = 0;

            if ((ptrOldX != x) || (ptrOldY != y))
            {
                ptrOldX = x;
                ptrOldY = y;
                edgeNum = findEdge(xb, yb);

                if ((player == null) || !player.isPotentialRoad(edgeNum))
                {
                    edgeNum = 0;
                }

                if (hilight != edgeNum)
                {
                    hilight = edgeNum;
                    repaint();
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

                if ((player == null) || !player.isPotentialSettlement(nodeNum))
                {
                    nodeNum = 0;
                }

                if (hilight != nodeNum)
                {
                    hilight = nodeNum;
                    if (mode == PLACE_INIT_SETTLEMENT)
                        hoverTip.handleHover(x,y);
                    repaint();
                }
                else if (mode == PLACE_INIT_SETTLEMENT)
                {
                    hoverTip.handleHover(x,y);  // Will call repaint() if needed
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

                if ((player == null) || !player.isPotentialCity(nodeNum))
                {
                    nodeNum = 0;
                }

                if (hilight != nodeNum)
                {
                    hilight = nodeNum;
                    repaint();
                }
            }

            break;

        case PLACE_ROBBER:

            /**** Code for finding a hex *********/
            hexNum = 0;

            if ((ptrOldX != x) || (ptrOldY != y))
            {
                ptrOldX = x;
                ptrOldY = y;
                hexNum = findHex(xb, yb);

                if (! game.canMoveRobber(playerNumber, hexNum))
                {
                    hexNum = 0;
                }

                if (hilight != hexNum)
                {
                    hilight = hexNum;
                    hoverTip.handleHover(x,y);
                    repaint();
                }
                else
                {
                    hoverTip.positionToMouse(x,y); // calls repaint
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
                edgeNum = findEdge(xb, yb);

                if (!otherPlayer.isPotentialRoad(edgeNum))
                {
                    edgeNum = 0;
                }

                if (hilight != edgeNum)
                {
                    hilight = edgeNum;
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

                if (!otherPlayer.isPotentialCity(nodeNum))
                {
                    nodeNum = 0;
                }

                if (hilight != nodeNum)
                {
                    hilight = nodeNum;
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
                hoverTip.handleHover(x,y);
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
    public void mouseClicked(MouseEvent evt)
    {
        try {
        int x = evt.getX();
        int y = evt.getY();
        
        if (evt.isPopupTrigger())
        {
            popupMenuSystime = evt.getWhen();
            evt.consume();
            doBoardMenuPopup(x,y);
            return;  // <--- Pop up menu, nothing else to do ---
        }

        if (evt.getWhen() < (popupMenuSystime + POPUP_MENU_IGNORE_MS))
        {
            return;  // <--- Ignore click: too soon after popup click ---
        }

        if ((hilight > 0) && (player != null))
        {
            SOCPlayerClient client = playerInterface.getClient();

            switch (mode)
            {
            case NONE:
                break;

            case TURN_STARTING:
                break;

            case PLACE_INIT_ROAD:
            case PLACE_ROAD:

                if (player.isPotentialRoad(hilight))
                {
                    client.putPiece(game, new SOCRoad(player, hilight));

                    // Now that we've placed, clear the mode and the hilight.
                    clearModeAndHilight(SOCPlayingPiece.ROAD);
                }

                break;

            case PLACE_INIT_SETTLEMENT:
                initstlmt = hilight;

                if (player.isPotentialSettlement(hilight))
                {
                    client.putPiece(game, new SOCSettlement(player, hilight));
                    clearModeAndHilight(SOCPlayingPiece.SETTLEMENT);
                }

                break;

            case PLACE_SETTLEMENT:

                if (player.isPotentialSettlement(hilight))
                {
                    client.putPiece(game, new SOCSettlement(player, hilight));
                    clearModeAndHilight(SOCPlayingPiece.SETTLEMENT);
                }

                break;

            case PLACE_CITY:

                if (player.isPotentialCity(hilight))
                {
                    client.putPiece(game, new SOCCity(player, hilight));
                    clearModeAndHilight(SOCPlayingPiece.CITY);
                }

                break;

            case PLACE_ROBBER:

                if (hilight != board.getRobberHex())
                {
                    client.moveRobber(game, player, hilight);
                    clearModeAndHilight(-1);
                }

                break;

            case CONSIDER_LM_SETTLEMENT:

                if (otherPlayer.isPotentialSettlement(hilight))
                {
                    client.considerMove(game, otherPlayer.getName(), new SOCSettlement(otherPlayer, hilight));
                    clearModeAndHilight(SOCPlayingPiece.SETTLEMENT);
                }

                break;

            case CONSIDER_LM_ROAD:

                if (otherPlayer.isPotentialRoad(hilight))
                {
                    client.considerMove(game, otherPlayer.getName(), new SOCRoad(otherPlayer, hilight));
                    clearModeAndHilight(SOCPlayingPiece.ROAD);
                }

                break;

            case CONSIDER_LM_CITY:

                if (otherPlayer.isPotentialCity(hilight))
                {
                    client.considerMove(game, otherPlayer.getName(), new SOCCity(otherPlayer, hilight));
                    clearModeAndHilight(SOCPlayingPiece.CITY);
                }

                break;

            case CONSIDER_LT_SETTLEMENT:

                if (otherPlayer.isPotentialSettlement(hilight))
                {
                    client.considerTarget(game, otherPlayer.getName(), new SOCSettlement(otherPlayer, hilight));
                    clearModeAndHilight(SOCPlayingPiece.SETTLEMENT);
                }

                break;

            case CONSIDER_LT_ROAD:

                if (otherPlayer.isPotentialRoad(hilight))
                {
                    client.considerTarget(game, otherPlayer.getName(), new SOCRoad(otherPlayer, hilight));
                    clearModeAndHilight(SOCPlayingPiece.ROAD);
                }

                break;

            case CONSIDER_LT_CITY:

                if (otherPlayer.isPotentialCity(hilight))
                {
                    client.considerTarget(game, otherPlayer.getName(), new SOCCity(otherPlayer, hilight));
                    clearModeAndHilight(SOCPlayingPiece.CITY);
                }

                break;
            }
        }
        else if ((player != null) && (game.getCurrentPlayerNumber() == player.getPlayerNumber()))
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
        
        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }
    
    /**
     * Bring up the popup menu; called from mousePressed.
     *
     * @param x x-coordinate of click, actual screen pixels (not unscaled internal)
     * @param y y-coordinate of click, actual screen pixels (not unscaled internal)
     */
    protected void doBoardMenuPopup (int x, int y)
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
            
        case PLACE_INIT_ROAD:
            popupMenu.showBuild(x, y, hilight, 0, 0);
            break;
            
        case PLACE_INIT_SETTLEMENT:
            popupMenu.showBuild(x, y, 0, hilight, 0);
            break;
            
        default:  // NONE, GAME_FORMING, PLACE_ROBBER, etc
            popupMenu.showBuild(x, y, hoverTip.hoverRoadID, hoverTip.hoverSettlementID, hoverTip.hoverCityID);
        }
    }
    
    /** If the client has used the board popup menu to request building a piece,  
     *  this method is used in client network-receive message treatment.
     */
    public boolean popupExpectingBuildRequest()
    {
        if (buildReqTimerTask == null)
            return false;
        return ! buildReqTimerTask.wasItSentAlready();
    }

    public void popupSetBuildRequest(int coord, int ptype)
    {
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
    
    /** Have received gamestate placing message; send the building request in reply. */
    public void popupFireBuildingRequest()
    {
        Timer piTimer = playerInterface.getEventTimer();
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
     * Text to be displayed as 2 lines superimposed over the board graphic (during game setup).
     * Either text2, or both, can be null to display nothing.
     * Keep the text short, because boardPanel may not be very wide ({@link #PANELX} pixels).
     * Will trigger a repaint.
     * @param text1 Line 1 (or only line) of text, or null
     * @param text2 Line 2 of text, or null; must be null if text1 is null
     * @throws IllegalArgumentException if text1 null, text2 non-null
     * @since 1.1.07
     */
    public void setSuperimposedText(String text1, String text2)
        throws IllegalArgumentException
    {
        if ((superText1 == text1) && (superText2 == text2))
            return;
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
     * given a pixel on the board, find the edge that contains it
     *
     * @param x  x coordinate, in unscaled board, not actual pixels;
     *           use {@link #scaleFromActualX(int)} to convert
     * @param y  y coordinate, in unscaled board, not actual pixels
     * @return the coordinates of the edge, or 0 if none
     */
    private final int findEdge(int x, int y)
    {
        // find which grid section the pointer is in 
        // ( 46 is the y-distance between the centers of two hexes )
        //int sector = (x / 18) + ((y / 10) * 15);
        int sector = (x / 27) + ((y / 15) * 15);

        // System.out.println("SECTOR = "+sector+" | EDGE = "+edgeMap[sector]);
        if ((sector >= 0) && (sector < edgeMap.length))
            return edgeMap[sector];
        else
            return 0;
    }

    /**
     * given a pixel on the board, find the node that contains it
     *
     * @param x  x coordinate, in unscaled board, not actual pixels;
     *           use {@link #scaleFromActualX(int)} to convert
     * @param y  y coordinate, in unscaled board, not actual pixels
     * @return the coordinates of the node, or 0 if none
     */
    private final int findNode(int x, int y)
    {
        // find which grid section the pointer is in 
        // ( 46 is the y-distance between the centers of two hexes )
        //int sector = ((x + 9) / 18) + (((y + 5) / 10) * 15);
        int sector = ((x + 13) / 27) + (((y + 7) / 15) * 15);

        // System.out.println("SECTOR = "+sector+" | NODE = "+nodeMap[sector]);
        if ((sector >= 0) && (sector < nodeMap.length))
            return nodeMap[sector];
        else
            return 0;
    }

    /**
     * given a pixel on the board, find the hex that contains it
     *
     * @param x  x coordinate, in unscaled board, not actual pixels;
     *           use {@link #scaleFromActualX(int)} to convert
     * @param y  y coordinate, in unscaled board, not actual pixels
     * @return the coordinates of the hex, or 0 if none
     */
    private final int findHex(int x, int y)
    {
        // find which grid section the pointer is in 
        // ( 46 is the y-distance between the centers of two hexes )
        //int sector = (x / 18) + ((y / 10) * 15);
        int sector = (x / 27) + ((y / 15) * 15);

        // System.out.println("SECTOR = "+sector+" | HEX = "+hexMap[sector]);
        if ((sector >= 0) && (sector < hexMap.length))
            return hexMap[sector];
        else
            return 0;
    }

    /**
     * set the interaction mode
     *
     * @param m  mode
     * 
     * @see #updateMode()
     */
    public void setMode(int m)
    {
        mode = m;
        updateHoverTipToMode();
    }

    /**
     * get the interaction mode
     *
     * @return the mode
     */
    public int getMode()
    {
        return mode;
    }

    /**
     * load the images for the board
     * we need to know if this board is in an applet
     * or an application
     */
    private static synchronized void loadImages(Component c, boolean wantsRotated)
    {
        if ((hexes != null) && ((rotatHexes != null) || ! wantsRotated))
            return;

        Toolkit tk = c.getToolkit();
        Class clazz = c.getClass();

        if (hexes == null)
        {
            MediaTracker tracker = new MediaTracker(c);
        
            hexes = new Image[13];
            numbers = new Image[10];
            ports = new Image[7];
            dice = new Image[14];

            loadHexesPortsImages(hexes, ports, IMAGEDIR, tracker, tk, clazz);

            numbers[0] = tk.getImage(clazz.getResource(IMAGEDIR + "/two.gif"));
            numbers[1] = tk.getImage(clazz.getResource(IMAGEDIR + "/three.gif"));
            numbers[2] = tk.getImage(clazz.getResource(IMAGEDIR + "/four.gif"));
            numbers[3] = tk.getImage(clazz.getResource(IMAGEDIR + "/five.gif"));
            numbers[4] = tk.getImage(clazz.getResource(IMAGEDIR + "/six.gif"));
            numbers[5] = tk.getImage(clazz.getResource(IMAGEDIR + "/eight.gif"));
            numbers[6] = tk.getImage(clazz.getResource(IMAGEDIR + "/nine.gif"));
            numbers[7] = tk.getImage(clazz.getResource(IMAGEDIR + "/ten.gif"));
            numbers[8] = tk.getImage(clazz.getResource(IMAGEDIR + "/eleven.gif"));
            numbers[9] = tk.getImage(clazz.getResource(IMAGEDIR + "/twelve.gif"));

            for (int i = 0; i < 10; i++)
            {
                tracker.addImage(numbers[i], 0);
            }

            for (int i = 2; i < 13; i++)
            {
                dice[i] = tk.getImage(clazz.getResource(IMAGEDIR + "/dice" + i + ".gif"));
                tracker.addImage(dice[i], 0);
            }

            try
            {
                tracker.waitForID(0);
            }
            catch (InterruptedException e) {}

            if (tracker.isErrorID(0))
            {
                System.out.println("Error loading board images");
            }
        }

        if (wantsRotated && (rotatHexes == null))
        {
            MediaTracker tracker = new MediaTracker(c);
        
            rotatHexes = new Image[13];
            rotatPorts = new Image[7];
            loadHexesPortsImages(rotatHexes, rotatPorts, IMAGEDIR + "/rotat", tracker, tk, clazz);

            try
            {
                tracker.waitForID(0);
            }
            catch (InterruptedException e) {}

            if (tracker.isErrorID(0))
            {
                System.out.println("Error loading rotated board images");
            }
        }
    }

    /**
     * Load hex and port images from either normal, or rotated, resource location.
     * Remember that miscPort0.gif - miscPort5.gif are loaded into hexes, not ports.
     * @param newHexes Array to store hex images into; {@link #hexes} or {@link #rotatHexes}
     * @param newPorts Array to store port images into; {@link #ports} or {@link #rotatPorts}
     * @param imageDir Location for {@link Class#getResource(String)}
     * @param tracker Track image loading progress here
     * @param tk   Toolkit to load image from resource
     * @param clazz  Class for getResource
     * @since 1.1.08
     */
    private static final void loadHexesPortsImages
        (Image[] newHexes, Image[] newPorts, String imageDir,
         MediaTracker tracker, Toolkit tk, Class clazz)
    {
        newHexes[0] = tk.getImage(clazz.getResource(imageDir + "/desertHex.gif"));
        newHexes[1] = tk.getImage(clazz.getResource(imageDir + "/clayHex.gif"));
        newHexes[2] = tk.getImage(clazz.getResource(imageDir + "/oreHex.gif"));
        newHexes[3] = tk.getImage(clazz.getResource(imageDir + "/sheepHex.gif"));
        newHexes[4] = tk.getImage(clazz.getResource(imageDir + "/wheatHex.gif"));
        newHexes[5] = tk.getImage(clazz.getResource(imageDir + "/woodHex.gif"));
        newHexes[6] = tk.getImage(clazz.getResource(imageDir + "/waterHex.gif"));
        for (int i = 0; i < 7; i++)
        {
            tracker.addImage(newHexes[i], 0);
        }

        for (int i = 0; i < 6; i++)
        {
            newHexes[i + 7] = tk.getImage(clazz.getResource(imageDir + "/miscPort" + i + ".gif"));
            tracker.addImage(newHexes[i + 7], 0);
        }

        for (int i = 0; i < 6; i++)
        {
            newPorts[i + 1] = tk.getImage(clazz.getResource(imageDir + "/port" + i + ".gif"));
            tracker.addImage(newPorts[i + 1], 0);
        }
    }

    ///
    // ----- Utility methods -----
    ///

    /**
     * Hex color for a hex resource type
     * @param hexType  hexType value, as in {@link SOCBoard#DESERT_HEX}, {@link SOCBoard#WOOD_HEX},
     *                 {@link SOCBoard#WATER_HEX}.
     *                 Same value and meaning as those in {@link SOCBoard#getHexLayout()}.
     * @return The corresponding color from ColorSquare, or {@link ColorSquare#WATER} if hexType not recognized.
     * @since 1.1.07
     */
    public static final Color hexColor(int hexType)
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
     * @author Jeremy D Monin <jeremy@nand.net>
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
                Thread.sleep(3000);
            }
            catch (InterruptedException e) {}
            finally
            {
                bp.repaint();
                alreadyActive = false;
            }
        }
    }  // static class DelayedRepaint



    protected class BoardToolTip
    {
        private SOCBoardPanel bpanel;
        
        /** Text to hover-display, or null if nothing to show */
        private String hoverText;

        /** Uses board mode constants: Will be {@link SOCBoardPanel#NONE NONE},
         *  {@link SOCBoardPanel#PLACE_ROAD PLACE_ROAD}, PLACE_SETTLEMENT,
         *  PLACE_ROBBER for hex, or PLACE_INIT_SETTLEMENT for port.
         */
        private int hoverMode;

        /** "ID" of coord as returned by {@link SOCBoardPanel#findNode(int, int) findNode}, findEdge, findHex */
        private int hoverID;

        /** Object last pointed at; null for hexes and ports */
        private SOCPlayingPiece hoverPiece;

        /** hover road ID, or 0. Readonly please from outside this inner class. Drawn in {@link #paint(Graphics)}. */
        int hoverRoadID;

        /** hover settlement or city node ID, or 0. Readonly please from outside this inner class. Drawn in {@link #paint(Graphics)}. */
        int hoverSettlementID, hoverCityID;

        /** is hover a port at coordinate hoverID? */
        boolean hoverIsPort;

        /** Mouse position */
        private int mouseX, mouseY;

        /** Our position (upper-left of tooltip box) */
        private int boxX, boxY;

        /** Requested X-offset from mouse pointer (used for robber placement) */
        private int offsetX;

        /** Our size.
         *  If boxw == 0, also indicates need fontmetrics - see setHoverText, paint.
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
            hoverIsPort = false;
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
        
        public boolean isVisible()
        {
            return ((hoverText != null) || (hoverRoadID != 0)
                    || (hoverSettlementID != 0) || (hoverCityID != 0));
        }

        /**
         * Show tooltip at appropriate location when mouse
         * is at (x,y) relative to the board.
         * @param x x-coordinate of mouse, actual screen pixels (not unscaled internal)
         * @param y y-coordinate of mouse, actual screen pixels (not unscaled internal)
         */
        public void positionToMouse(int x, int y)
        {
            mouseX = x;
            mouseY = y;

            boxX = mouseX + offsetX;
            boxY = mouseY;
            if (offsetX < 5)
                boxY += 12;

            if (SOCBoardPanel.PANELX < ( boxX + boxW ))
            {
                // Try to float it to left of mouse pointer
                boxX = mouseX - boxW - offsetX;
                if (boxX < 0)
                {
                    // Not enough room, just place flush against right-hand side
                    boxX = SOCBoardPanel.PANELX - boxW;
                }
            }
            
            bpanel.repaint();
            // JM TODO consider repaint(boundingbox).            
        }
        
        public void setOffsetX(int ofsX)
        {
            offsetX = ofsX;
        }

        /**
         * Set the hover text (tooltip) based on where the mouse is now,
         * and repaint the board.
         * @param t Hover text contents, or null to clear that text (but
         *          not hovering pieces) and repaint
         * @see #hideHoverAndPieces()
         */
        public void setHoverText(String t)
        {
            hoverText = t;
            if (t == null)
            {
                bpanel.repaint();
                return;
            }

            final Font bpf = bpanel.getFont();
            if (bpf == null)
            {
                boxW = 0;  // Paint method will look it up
            } else {
                final FontMetrics fm = getFontMetrics(bpf);
                if (fm == null)
                {
                    boxW = 0;
                } else {
                    boxW = fm.stringWidth(hoverText) + PADDING_HORIZ;
                    boxH = fm.getHeight();
                }
            }
            positionToMouse(mouseX, mouseY);  // Also calls repaint
        }
        
        /** Clear hover text, and cancel any hovering roads/settlements/cities */
        public void hideHoverAndPieces()
        {
            hoverRoadID = 0;
            hoverSettlementID = 0;
            hoverCityID = 0;
            hoverIsPort = false;
            setHoverText(null);
        }
        
        /** Draw; Graphics should be the boardpanel's gc, as seen in its paint method. */
        public void paint(Graphics g)
        {
            if (playerNumber != -1)
            {
                if (hoverRoadID != 0)
                {
                    drawRoad(g, hoverRoadID, playerNumber, true);
                }
                if (hoverSettlementID != 0)
                {
                    drawSettlement(g, hoverSettlementID, playerNumber, true);
                }
                if (hoverCityID != 0)
                {
                    drawCity(g, hoverCityID, playerNumber, true);
                }
            }
            String ht = hoverText;  // cache against last-minute change in another thread
            if (ht == null)
                return;

            if (boxW == 0)
            {
                // Deferred fontmetrics lookup from earlier setHoverText
                final Font bpf = bpanel.getFont();
                if (bpf == null)
                    return;
                final FontMetrics fm = getFontMetrics(bpf);
                if (fm == null)
                    return;
                boxW = fm.stringWidth(hoverText) + PADDING_HORIZ;
                boxH = fm.getHeight();
            }
            g.setColor(Color.WHITE);
            g.fillRect(boxX, boxY, boxW - 1, boxH - 1);
            g.setColor(Color.BLACK);
            g.drawRect(boxX, boxY, boxW - 1, boxH - 1);
            g.drawString(ht, boxX + TEXT_INSET, boxY + boxH - TEXT_INSET);
        }

        /**
         * Mouse is hovering during normal play; look for info for tooltip text.
         * Assumes x or y has changed since last call.
         * Does not affect the "hilight" variable used by SOCBoardPanel during
         * initial placement, and during placement from clicking "Buy" buttons.
         *<P>
         * If the board mode doesn't allow hovering pieces (ghost pieces), will clear
         * hoverRoadID, hoverSettlementID, and hoverCityID to 0.
         * Otherwise, these are set when the mouse is at a location where the
         * player can build or upgrade, and they have resources to build.
         *
         * @param x Cursor x, from upper-left of board: actual coordinates, not board-internal coordinates
         * @param y Cursor y, from upper-left of board: actual coordinates, not board-internal coordinates
         */
        private void handleHover(int x, int y)
        {
            mouseX = x;
            mouseY = y;
            int xb = x, yb = y;  // internal board coordinates
            if (isScaledOrRotated)
            {
                if (isScaled)
                {
                    xb = scaleFromActualX(xb);
                    yb = scaleFromActualY(yb);
                }
                if (isRotated)
                {
                    // (ccw): P'=(y, PANELX-x)
                    int xb1 = yb;
                    yb = PANELX - xb - deltaY;  // -deltaY for similar reasons as -HEXHEIGHT in drawHex
                    xb = xb1;
                }
            }

            // Variables set in previous call to handleHover:
            // hoverMode, hoverID, hoverText.
            // Check whether they have changed.
            // If not, just move the tooltip with positionToMouse.

            /** Coordinates on board (a node, edge, or hex) */
            int id;
            boolean modeAllowsHoverPieces = ((mode != PLACE_INIT_SETTLEMENT)
                && (mode != PLACE_INIT_ROAD) && (mode != PLACE_ROBBER)
                && (mode != TURN_STARTING) && (mode != GAME_OVER));

            boolean playerIsCurrent = (player != null) && playerInterface.clientIsCurrentPlayer();
            boolean hoverTextSet = false;  // True once text is determined

            if (! modeAllowsHoverPieces)
            {
                hoverRoadID = 0;
                hoverSettlementID = 0;
                hoverCityID = 0;
            }

            // Look first for settlements
            id = findNode(xb,yb);
            if (id == 0)
            {
                StringBuffer sbd = new StringBuffer("grid sector: ");
                int sector = ((xb + 13) / 27) + (((yb + 7) / 15) * 15);
                sbd.append(sector);
                setHoverText(sbd.toString());
                hoverTextSet = true;
            }
            if (id > 0)
            {
                StringBuffer sbd = new StringBuffer("node: 0x");
                sbd.append(Integer.toHexString(id));
                setHoverText(sbd.toString());
                hoverTextSet = true;
                
                // Are we already looking at it?
                if ((hoverMode == PLACE_SETTLEMENT) && (hoverID == id))
                {
                    positionToMouse(x,y);
                    return;  // <--- Early ret: No work needed ---
                }
                
                // Is anything there?
                SOCPlayingPiece p = board.settlementAtNode(id);
                if (p != null)
                {
                    hoverMode = PLACE_SETTLEMENT;
                    hoverPiece = p;
                    hoverID = id;

                    StringBuffer sb = new StringBuffer();
                    String portDesc = portDescAtNode(id);
                    if (portDesc != null)
                    {
                        sb.append(portDesc);  // "3:1 Port", "2:1 Wood port"
                        if (p.getType() == SOCPlayingPiece.CITY)
                            sb.append(" city: ");
                        else
                            sb.append(": ");  // port, not port city
                        hoverIsPort = true;
                    }
                    else
                    {
                        if (p.getType() == SOCPlayingPiece.CITY)
                            sb.append("City: ");
                        else
                            sb.append("Settlement: ");
                    }
                    String plName = p.getPlayer().getName();
                    if (plName == null)
                        plName = "unowned";
                    sb.append(plName);
                    setHoverText(sb.toString());
                    hoverTextSet = true;

                    // If we're at the player's settlement, ready to upgrade to city
                    if (modeAllowsHoverPieces && playerIsCurrent
                         && (p.getPlayer() == player)
                         && (p.getType() == SOCPlayingPiece.SETTLEMENT)
                         && (player.isPotentialCity(id))
                         && (player.getResources().contains(SOCGame.CITY_SET)))
                    {
                        hoverCityID = id;
                    } else {
                        hoverCityID = 0;
                    }
                    hoverSettlementID = 0;
                }
                else {
                    if (playerIsCurrent)
                    {
                        // Nothing currently here.
                        hoverCityID = 0;
                        if (modeAllowsHoverPieces
                            && player.isPotentialSettlement(id)
                            && player.getResources().contains(SOCGame.SETTLEMENT_SET))
                            hoverSettlementID = id;
                        else
                            hoverSettlementID = 0;
                    }
                    
                    // Port check.  At most one adjacent will be a port.
                    if ((hoverMode == PLACE_INIT_SETTLEMENT) && (hoverID == id))
                    {
                        // Already looking at a port at this coordinate.
                        positionToMouse(x,y);
                        hoverTextSet = true;
                    }
                    else
                    {
                        String portDesc = portDescAtNode(id);
                        if (portDesc != null)
                        {
                            setHoverText(portDesc);
                            hoverTextSet = true;
                            hoverMode = PLACE_INIT_SETTLEMENT;  // const used for hovering-at-port
                            hoverID = id;
                            hoverIsPort = true;
                        }
                    }
                }  // end if-node-has-settlement
            }
            else
            {
                hoverSettlementID = 0;
                hoverCityID = 0;                
            }

            // If not over a settlement, look for a road
            id = findEdge(xb,yb);
            if (id > 0)
            {
                // Are we already looking at it?
                if ((hoverMode == PLACE_ROAD) && (hoverID == id))
                {
                    positionToMouse(x,y);
                    return;  // <--- Early ret: No work needed ---
                }

                // Is anything there?
                SOCPlayingPiece p = board.roadAtEdge(id);
                if (p != null)
                {
                    if (! hoverTextSet)
                    {
                        hoverMode = PLACE_ROAD;
                        hoverPiece = p;
                        hoverID = id;
                        String plName = p.getPlayer().getName();
                        if (plName == null)
                            plName = "unowned";
                        setHoverText("Road: " + plName);
                    }
                    hoverRoadID = 0;
                    
                    return;  // <--- Early return: Found road ---
                }
                else if (playerIsCurrent)
                {
                    // No piece there
                    if (modeAllowsHoverPieces
                        && player.isPotentialRoad(id)
                        && player.getResources().contains(SOCGame.ROAD_SET))
                        hoverRoadID = id;
                    else
                        hoverRoadID = 0;
                }
            }
            
            // By now we've set hoverRoadID, hoverCityID, hoverSettlementID, hoverIsPort.
            if (hoverTextSet)
            {
                return;  // <--- Early return: Text and hover-pieces set ---
            }

            // If no road, look for a hex
            //  - reminder: socboard.getHexTypeFromCoord, getNumberOnHexFromCoord, socgame.getPlayersOnHex
            id = findHex(xb,yb);
            if (id > 0)
            {
                // Are we already looking at it?
                if ((hoverMode == PLACE_ROBBER) && (hoverID == id))
                {
                    positionToMouse(x,y);
                    return;  // <--- Early ret: No work needed ---
                }
                
                hoverMode = PLACE_ROBBER;  // const used for hovering-at-hex
                hoverPiece = null;
                hoverID = id;
                {
                    StringBuffer sb = new StringBuffer();
                    switch (board.getHexTypeFromCoord(id))
                    {
                    case SOCBoard.DESERT_HEX:
                        sb.append("Desert");  break;
                    case SOCBoard.CLAY_HEX:
                        sb.append("Clay");    break;
                    case SOCBoard.ORE_HEX:
                        sb.append("Ore");     break;
                    case SOCBoard.SHEEP_HEX:
                        sb.append("Sheep");   break;
                    case SOCBoard.WHEAT_HEX:
                        sb.append("Wheat");   break;
                    case SOCBoard.WOOD_HEX:                     
                        sb.append("Wood");    break;
                    case SOCBoard.WATER_HEX:
                        sb.append("Water");   break;
                    default:
                        sb.append("Hex type ");
                        sb.append(board.getHexTypeFromCoord(id));
                    }
                    if (board.getRobberHex() == id)
                    {
                        int num = board.getNumberOnHexFromCoord(id);
                        if (num > 0)
                        {
                            sb.append(": ");
                            sb.append(num);
                        }
                        sb.append(" (ROBBER)");
                    }
                    setHoverText(sb.toString());                     
                }
                
                return;  // <--- Early return: Found hex ---
            }

            if (hoverRoadID != 0)
            {
                setHoverText(null); // hoverMode = PLACE_ROAD;
                bpanel.repaint();
                return;
            }

            // If no hex, nothing.
            hoverMode = NONE;
            setHoverText(null);
        }

        /**
         * Check at this node coordinate for a port, and return its descriptive text.
         * Does not check for players' settlements or cities, only for the port.
         *
         * @param id Node coordinate ID for potential port
         *
         * @return Port text description, or null if no port at that node id.
         *    Text format is "3:1 Port" or "2:1 Wood port".
         */
        public String portDescAtNode(int id)
        {
            int portType;
            Integer coordInteger = new Integer(id);

            for (portType = SOCBoard.MISC_PORT; portType <= SOCBoard.WOOD_PORT; portType++)
            {
                if (game.getBoard().getPortCoordinates(portType).contains(coordInteger))
                {
                    break;
                }
            }
            if (portType > SOCBoard.WOOD_PORT)
                return null;  // <--- No port found ---

            String portDesc;
            switch (portType)
            {
            case SOCBoard.MISC_PORT:
                portDesc = "3:1 Port";
                break;

            case SOCBoard.CLAY_PORT:
                portDesc = "2:1 Clay port";
                break;

            case SOCBoard.ORE_PORT:
                portDesc = "2:1 Ore port";
                break;

            case SOCBoard.SHEEP_PORT:
                portDesc = "2:1 Sheep port";
                break;

            case SOCBoard.WHEAT_PORT:
                portDesc = "2:1 Wheat port";
                break;

            case SOCBoard.WOOD_PORT:
                portDesc = "2:1 Wood port";
                break;                            

            default:
                // Just in case
                portDesc = "port type " + portType;                        
            }

            return portDesc;
        }
        
    }  // inner class BoardToolTip



    /**
     * This class creates a popup menu on the board,
     * to trade or build or cancel building.
     */
    private class BoardPopupMenu extends PopupMenu
        implements java.awt.event.ActionListener
    {
      /** our parent boardpanel */
      SOCBoardPanel bp;

      MenuItem buildRoadItem, buildSettleItem, upgradeCityItem;
      MenuItem cancelBuildItem;

      /** determined at menu-show time, only over a useable port. Added then, and removed at next menu-show */
      SOCHandPanel.ResourceTradePopupMenu portTradeSubmenu;

      /** determined at menu-show time */
      private int menuPlayerID;

      /** determined at menu-show time */
      private boolean menuPlayerIsCurrent;

      /** determined at menu-show time */
      private boolean wantsCancel;

      private int cancelBuildType;

      /** hover road ID, or 0, at menu-show time */
      private int hoverRoadID;

      /** hover settlement or city node ID, or 0, at menu-show time */
      private int hoverSettlementID, hoverCityID;

      /** Will this be for initial placement (send putpiece right away),
       *  or for placement during game (send build, receive gamestate, send putpiece)?
       */
      protected boolean isInitialPlacement;

      /** create a new BoardPopupMenu on this board */
      public BoardPopupMenu(SOCBoardPanel bpanel)
      {
        super ("JSettlers");
        bp = bpanel;

        buildRoadItem = new MenuItem("Build Road");         
        buildSettleItem = new MenuItem("Build Settlement");
        upgradeCityItem = new MenuItem("Upgrade to City");
        cancelBuildItem = new MenuItem("Cancel build");
        portTradeSubmenu = null;

        add(buildRoadItem);
        add(buildSettleItem);
        add(upgradeCityItem);
        addSeparator();
        add(cancelBuildItem);

        buildRoadItem.addActionListener(this);
        buildSettleItem.addActionListener(this);
        upgradeCityItem.addActionListener(this);
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
          menuPlayerIsCurrent = (player != null) && playerInterface.clientIsCurrentPlayer();
          wantsCancel = true;
          cancelBuildType = buildType;
          hoverRoadID = 0;
          hoverSettlementID = 0;
          hoverCityID = 0;

          buildRoadItem.setEnabled(false);
          buildSettleItem.setEnabled(false);
          upgradeCityItem.setEnabled(false);
          cancelBuildItem.setEnabled(menuPlayerIsCurrent);

          // Check for initial placement (for different cancel message)
          switch (game.getGameState())
          {
          case SOCGame.START1A:
          case SOCGame.START2A:
          case SOCGame.START1B:
          case SOCGame.START2B:
              isInitialPlacement = true;
              break;
          
          default:
              isInitialPlacement = false;
          }

          switch (buildType)
          {
          case SOCPlayingPiece.ROAD:
              cancelBuildItem.setLabel("Cancel road");
              buildRoadItem.setEnabled(menuPlayerIsCurrent);
              hoverRoadID = hilightAt; 
              break;
              
          case SOCPlayingPiece.SETTLEMENT:
              cancelBuildItem.setLabel("Cancel settlement");
              buildSettleItem.setEnabled(menuPlayerIsCurrent);
              hoverSettlementID = hilightAt; 
              break;
              
          case SOCPlayingPiece.CITY:
              cancelBuildItem.setLabel("Cancel city upgrade");
              upgradeCityItem.setEnabled(menuPlayerIsCurrent);
              hoverCityID = hilightAt;
              break;
              
          default:
              throw new IllegalArgumentException ("bad buildtype: " + buildType);
          }
          
          super.show(bp, x, y);
      }
      
      /**
       * Custom show method that finds current game status and player status.
       * Also checks for hovering-over-port for port-trade submenu.
       *
       * @param x   Mouse x-position
       * @param y   Mouse y-position
       * @param hR  Hover road ID, or 0
       * @param hS  Hover settle ID, or 0
       * @param hC  Hover city ID, or 0
       */
      public void showBuild(int x, int y, int hR, int hS, int hC)
      {
          wantsCancel = false;
          isInitialPlacement = false;
          cancelBuildItem.setEnabled(false);
          cancelBuildItem.setLabel("Cancel build");
          if (portTradeSubmenu != null)
          {
              // Cleanup from last time
              remove(portTradeSubmenu);
              portTradeSubmenu.destroy();
              portTradeSubmenu = null;
          }
         
          menuPlayerIsCurrent = (player != null) && playerInterface.clientIsCurrentPlayer();
          if (menuPlayerIsCurrent)
          {
              int gs = game.getGameState();
              switch (gs)
              {
              case SOCGame.START1A:
              case SOCGame.START2A:
                  isInitialPlacement = true;  // Settlement
                  buildRoadItem.setEnabled(false);
                  buildSettleItem.setEnabled(hS != 0);
                  upgradeCityItem.setEnabled(false);
                  break;

              case SOCGame.START1B:
              case SOCGame.START2B:
                  isInitialPlacement = true;  // Road
                  buildRoadItem.setEnabled(hR != 0);
                  buildSettleItem.setEnabled(false);
                  upgradeCityItem.setEnabled(false);
                  cancelBuildItem.setLabel("Cancel settlement");  // Initial settlement
                  cancelBuildItem.setEnabled(true);
                  cancelBuildType = SOCPlayingPiece.SETTLEMENT;
                  break;
              
              default:
                  if (gs < SOCGame.PLAY1)
                      menuPlayerIsCurrent = false;  // Not in a state to place items
              }
          }
          
          if (! menuPlayerIsCurrent)
          {
              buildRoadItem.setEnabled(false);
              buildSettleItem.setEnabled(false);
              upgradeCityItem.setEnabled(false);
              hoverRoadID = 0;
              hoverSettlementID = 0;
              hoverCityID = 0;
          }
          else
          {
              int cpn = game.getCurrentPlayerNumber();

              if (! isInitialPlacement)
              {
                  buildRoadItem.setEnabled(game.couldBuildRoad(cpn) && player.isPotentialRoad(hR));
                  buildSettleItem.setEnabled(game.couldBuildSettlement(cpn) && player.isPotentialSettlement(hS));
                  upgradeCityItem.setEnabled(game.couldBuildCity(cpn) && player.isPotentialCity(hC));
              }
              hoverRoadID = hR;
              hoverSettlementID = hS;
              hoverCityID = hC;
              
              // Is it a port?
              int portType = -1;
              int portId = 0;
              if (hS != 0)
                  portId = hS;
              else if (hC != 0)
                  portId = hC;
              else if (bp.hoverTip.hoverIsPort)
                  portId = bp.hoverTip.hoverID;

              if (portId != 0)
              {
                  Integer coordInteger = new Integer(portId);
                  for (portType = SOCBoard.MISC_PORT; portType <= SOCBoard.WOOD_PORT; portType++)
                  {
                      if (game.getBoard().getPortCoordinates(portType).contains(coordInteger))
                          break;
                  }

                  if (portType > SOCBoard.WOOD_PORT)
                      portType = -1;
              }

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

      /** Handling the menu items **/
      public void actionPerformed(ActionEvent e)
      {
          if (! playerInterface.clientIsCurrentPlayer())
              return;
          if (! menuPlayerIsCurrent)
              return;
          Object target = e.getSource();
          if (target == buildRoadItem)
              tryBuild(SOCPlayingPiece.ROAD);
          else if (target == buildSettleItem)
              tryBuild(SOCPlayingPiece.SETTLEMENT);
          else if (target == upgradeCityItem)
              tryBuild(SOCPlayingPiece.CITY);
          else if (target == cancelBuildItem)
              tryCancel();
      } 

      /**
       * Send message to server to request placing this piece, if allowable.
       * If not initial placement, set up a reaction to send the 2nd message (putpiece).
       * when server says it's OK to build.
       * Assumes player is current, and player is non-null, when called.
       *
       * @param ptype Piece type, like {@link SOCPlayingPiece#ROAD}
       */
      void tryBuild(int ptype)
      {
          int cpn = playerInterface.getClientPlayerNumber();
          int buildLoc;      // location
          boolean canBuild;  // resources, rules
          String btarget;    // button name on buildpanel
          
          // If we're in initial placement, or cancel/build during game, send putpiece right now.
          // Otherwise, multi-phase send.
          
          // Note that if we're in gameplay have clicked the "buy road" button
          // and trying to place it, game.couldBuildRoad will be false because
          // we've already spent the resources.  So, wantsCancel won't check it.
          
          switch (ptype)
          {
          case SOCPlayingPiece.ROAD:
              buildLoc = hoverRoadID;
              canBuild = player.isPotentialRoad(buildLoc);
              if (! (isInitialPlacement || wantsCancel))
                  canBuild = canBuild && game.couldBuildRoad(cpn);
              if (canBuild && (isInitialPlacement || wantsCancel))
                  playerInterface.getClient().putPiece(game, new SOCRoad(player, buildLoc));
              btarget = SOCBuildingPanel.ROAD;
              break;

          case SOCPlayingPiece.SETTLEMENT:
              buildLoc = hoverSettlementID;
              canBuild = player.isPotentialSettlement(buildLoc);
              if (! (isInitialPlacement || wantsCancel))
                  canBuild = canBuild && game.couldBuildSettlement(cpn);
              if (canBuild && (isInitialPlacement || wantsCancel))
              {
                  playerInterface.getClient().putPiece(game, new SOCSettlement(player, buildLoc));
                  if (isInitialPlacement)
                      initstlmt = buildLoc;  // track for initial road mouseover hilight
              }
              btarget = SOCBuildingPanel.STLMT;
              break;
          
          case SOCPlayingPiece.CITY:
              buildLoc = hoverCityID;
              canBuild = game.couldBuildCity(cpn) && player.isPotentialCity(buildLoc);
              if (! (isInitialPlacement || wantsCancel))             
                  canBuild = canBuild && game.couldBuildCity(cpn);
              if (canBuild && (isInitialPlacement || wantsCancel))
                  playerInterface.getClient().putPiece(game, new SOCCity(player, buildLoc));
              btarget = SOCBuildingPanel.CITY;
              break;

          default:
              throw new IllegalArgumentException ("Bad build type: " + ptype);
          }
          
          if (! canBuild)
          {
              playerInterface.print("Sorry, you cannot build there.");
              return;
          }
          
          if (isInitialPlacement || wantsCancel)
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
              (game, playerInterface.getClient(), btarget, true);          
      }
      
      void tryCancel()
      {
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
          }          
          // Use buttons to cancel the build request
          playerInterface.getBuildingPanel().clickBuildingButton
              (game, playerInterface.getClient(), btarget, false);
      }

    }  // inner class BoardPopupMenu    

    /**
     * Menu for right-click on 3-for-1 port to trade all resource types with bank/port.
     * Menu items won't necessarily say "trade 3", because the user may have a 2-for-1
     * port, or may not have a 3-for-1 port (cost 4).
     *
     * @author Jeremy D Monin <jeremy@nand.net>
     */
    /* package-access */ static class ResourceTradeAllMenu extends SOCHandPanel.ResourceTradePopupMenu
    {
        private SOCBoardPanel bpanel;
        private SOCHandPanel.ResourceTradeTypeMenu[] tradeFromTypes;        

        /**
         * Temporary menu for board popup menu
         *
         * @throws IllegalStateException If client not current player
         */
        public ResourceTradeAllMenu(SOCBoardPanel bp, SOCHandPanel hp)
            throws IllegalStateException
        {
            super(hp, "Trade Port");
            bpanel = bp;
            SOCPlayerInterface pi = hp.getPlayerInterface();
            if (! pi.clientIsCurrentPlayer())
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
     * 
     * This timer will probably not be called, unless there's a large lag
     * between the server and client.  It's here just in case.
     * Ideally the server responds right away, and the client responds then.
     * 
     * @see SOCHandPanel#autoRollSetupTimer()
     */
    protected class BoardPanelSendBuildTask extends java.util.TimerTask
    {
        protected int buildLoc, pieceType;
        protected boolean wasSentAlready;

        /** Send this after maximum delay.
         * 
         * @param coord Board coordinates, as used in SOCPutPiece message
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
        public void run()
        {
            // for debugging
            if (Thread.currentThread().getName().startsWith("Thread-"))
            {
                try {
                    Thread.currentThread().setName("timertask-boardpanel");
                }
                catch (Throwable th) {}
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
            if (! playerInterface.clientIsCurrentPlayer())
                return;  // Stale request, player's already changed
            
            SOCPlayerClient client = playerInterface.getClient();

            switch (pieceType)
            {
            case SOCPlayingPiece.ROAD:
                if (player.isPotentialRoad(buildLoc))
                    client.putPiece(game, new SOCRoad(player, buildLoc));
                break;
            case SOCPlayingPiece.SETTLEMENT:
                if (player.isPotentialSettlement(buildLoc))
                    client.putPiece(game, new SOCSettlement(player, buildLoc));
                break;
            case SOCPlayingPiece.CITY:
                if (player.isPotentialCity(buildLoc))
                    client.putPiece(game, new SOCCity(player, buildLoc));
                break;
            }

            clearModeAndHilight(pieceType);           
        }
        
    }  // inner class BoardPanelSendBuildTask
    
}  // class SOCBoardPanel
