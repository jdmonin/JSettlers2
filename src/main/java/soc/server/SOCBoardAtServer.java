/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012-2019 Jeremy D Monin <jeremy@nand.net>
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

package soc.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Vector;

import soc.game.SOCBoard;
import soc.game.SOCBoard4p;
import soc.game.SOCBoard6p;
import soc.game.SOCBoardLarge;
import soc.game.SOCDevCardConstants;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCScenario;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCVillage;
import soc.game.SOCBoard.BoardFactory;
import soc.util.IntPair;
import soc.util.IntTriple;

/**
 * A subclass of {@link SOCBoardLarge} for the server to hold server-only
 * per-game board state, isolate {@link #makeNewBoard(Map)}, and simplify
 * that parent class. See SOCBoardLarge for more details.
 * For the board layout geometry, see that class javadoc's "Coordinate System" section.
 *<P>
 * Sea board layout: A representation of a larger (up to 127 x 127 hexes) JSettlers board,
 * with an arbitrary mix of land and water tiles.
 * Implements {@link SOCBoard#BOARD_ENCODING_LARGE}.
 * Activated with {@link SOCGameOption} {@code "SBL"}.
 *<P>
 * A {@link SOCGame} uses this board; the board is not given a reference to the game, to enforce layering
 * and keep the board logic simple.  Game rules should be enforced at the game, not the board.
 * Calling board methods won't change the game state.
 *<P>
 * On this large sea board, there can optionally be multiple "land areas"
 * (groups of islands, or subsets of islands), if {@link #getLandAreasLegalNodes()} != null.
 * Land areas are groups of nodes on land; call {@link #getNodeLandArea(int)} to find a node's land area number.
 * The starting land area is {@link #getStartingLandArea()}, if players must start in a certain area.
 * During board setup, {@link #makeNewBoard(Map)} calls
 * {@link #makeNewBoard_placeHexes(int[], int[], int[], boolean, boolean, int, boolean, int, SOCGameOption, String, Map)}
 * once for each land area.  In some game scenarios, players and the robber can be
 * {@link #getPlayerExcludedLandAreas() excluded} from placing in some land areas.
 *<P>
 * It's also possible for an island to have more than one landarea (as in scenario SC_TTD).
 * In this case you should have a border zone of hexes with no landarea, because the landareas
 * are tracked by their hexes' nodes, not by the hexes, and a node can't be in multiple LAs.
 *<P>
 * <H3> To Add a New Board:</H3>
 * To add a new board layout type, for a new game option or scenario:
 * You'll need to declare all parts of its layout, recognize its
 * scenario or game option in makeNewBoard, and call methods to set up the structure.
 * These layout parts' values can be different for 3, 4, or 6 players.
 *<P>
 * A good example is SC_PIRI "Pirate Islands"; see commits 57073cb, f9623e5, and 112e289,
 * or search this class for the string SC_PIRI.
 *<P>
 * Parts of the layout:
 *<UL>
 * <LI> Its height and width, if not default; set in {@link #getBoardSize(Map, int)}
 * <LI> Its set of land hex types, usually shuffled *
 * <LI> Land hex coordinates *
 * <LI> Dice numbers to place at land hex coordinates, sometimes shuffled
 * <LI> Its set of port types (3:1, 2:1), usually shuffled
 * <LI> Its port edge locations and facing directions
 * <LI> Pirate and/or robber starting coordinate (optional)
 * <LI> If multiple Land Areas, its land area ranges within the array of land hex coordinates
 *</UL>
 * &nbsp; * Some "land areas" may include water hexes, to vary the coastline from game to game. <BR>
 *<P>
 * Some scenarios may add other "layout parts" related to their scenario board layout.
 * For example, scenario {@code _SC_PIRI} adds {@code "PP"} for the path followed by the pirate fleet.
 * See the list of Added Layout Parts documented at {@link SOCBoardLarge#getAddedLayoutPart(String)}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCBoardAtServer extends SOCBoardLarge
{
    /**
     * Flag property {@code jsettlers.debug.board.fog}: When present, about 20% of the board's
     * land hexes will be randomly covered by fog during {@code makeNewBoard} generation.
     * Ignored if {@link SOCGameOption#K_SC_FOG} is set.
     */
    public static final String PROP_JSETTLERS_DEBUG_BOARD_FOG = "jsettlers.debug.board.fog";

    private static final long serialVersionUID = 2000L;

    /**
     * For layout testing, the optional listener called during all boards' {@link #makeNewBoard(Map)}, or {@code null}.
     */
    private static transient NewBoardProgressListener newBoardProgressListener;

    /**
     * For game scenarios such as {@link SOCGameOption#K_SC_FTRI _SC_FTRI},
     * dev cards or other items waiting to be claimed by any player.
     * Otherwise null.
     *<P>
     * Initialized in {@link #startGame_putInitPieces(SOCGame)} based on game options.
     * Accessed with {@link #drawItemFromStack()}.
     *<P>
     * In {@code _SC_FTRI}, each item is a {@link SOCDevCardConstants} card type.
     */
    private Stack<Integer> drawStack;
        // if you add a scenario here that uses drawStack, also update the SOCBoardLarge.drawItemFromStack javadoc.

    /**
     * For game scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * the pirate fleet's position on its path (PP).  Otherwise unused.
     * @see #movePirateHexAlongPath(int)
     */
    private int piratePathIndex;

    /**
     * Create a new Settlers of Catan Board, with the v3 encoding.
     * Called by {@link SOCBoardAtServer.BoardFactoryAtServer#createBoard(Map, boolean, int)}
     * to get the right board size and layout based on game options and optional {@link SOCScenario}.
     *<P>
     * The board will be empty (all hexes are water, no dice numbers on any hex).
     * The layout contents are set up later by calling {@link #makeNewBoard(Map)} when the game is about to begin,
     * see {@link SOCBoardLarge} class javadoc for how the layout is sent to clients.
     *<P>
     * If the board should have a Visual Shift at the client, this constructor sets Added Layout Part "VS";
     * see {@link #getAddedLayoutPart(String)} javadoc for details on "VS".
     *
     * @param gameOpts  Game's options if any, otherwise null
     * @param maxPlayers Maximum players; must be default 4, or 6 from SOCGameOption "PL" &gt; 4 or "PLB"
     * @param boardHeightWidth  Board's height and width.
     *        The constants for default size are {@link #BOARDHEIGHT_LARGE}, {@link #BOARDWIDTH_LARGE}.
     * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6, or <tt>boardHeightWidth</tt> is null
     */
    public SOCBoardAtServer
        (final Map<String,SOCGameOption> gameOpts, final int maxPlayers, final IntPair boardHeightWidth)
        throws IllegalArgumentException
    {
        super(gameOpts, maxPlayers, boardHeightWidth);

        int[] boardVS = getBoardShift(gameOpts);
        if (boardVS != null)
            setAddedLayoutPart("VS", boardVS);
    }

    /**
     * Set or clear this class's sole {@link NewBoardProgressListener}.
     * The listener field is static, it will be called for all boards.
     * @param li  Listener to use, or {@code null} to remove the current listener if any.
     */
    public static final void setNewBoardProgressListener(final NewBoardProgressListener li)
    {
        newBoardProgressListener = li;
    }

    // javadoc inherited from SOCBoardLarge.
    // If this scenario has dev cards or items waiting to be claimed by any player, draw the next item from that stack.
    public Integer drawItemFromStack()
    {
        if ((drawStack == null) || drawStack.isEmpty())
            return null;

        return drawStack.pop();
    }

    /**
     * For game scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * move the pirate fleet's position along its path. Updates board's internal index along the path.
     * Calls {@link SOCBoardLarge#setPirateHex(int, boolean) setPirateHex(newHex, true)}.
     *<P>
     * If the pirate fleet is already defeated (all fortresses recaptured), returns 0.
     *
     * @param numSteps  Number of steps to move along the path
     * @return  new pirate hex coordinate, or 0
     * @throws IllegalStateException if this board doesn't have layout part "PP" for the Pirate Path.
     */
    @Override
    public int movePirateHexAlongPath(final int numSteps)
        throws IllegalStateException
    {
        final int[] path = getAddedLayoutPart("PP");
        if (path == null)
            throw new IllegalStateException();
        if (pirateHex == 0)
            return 0;  // fleet already defeated (all fortresses recaptured)

        int i = piratePathIndex + numSteps;
        while (i >= path.length)
            i -= path.length;
        piratePathIndex = i;
        final int ph = path[i];

        setPirateHex(ph, true);
        return ph;
    }

    ////////////////////////////////////////////
    //
    // Make New Board
    //
    // To add a new board layout, see the class javadoc.
    //


    /**
     * Shuffle the hex tiles and layout a board.
     * Sets up land hex types, water, ports, dice numbers, Land Areas' contents, starting Land Area if any,
     * and the legal/potential node sets ({@link SOCBoardLarge#getLegalSettlements()}).
     * Sets up any Added Layout Parts such as {@code "PP", "CE", "VE", "N1"}, etc.
     *<P>
     * This is called at server, but not at client;
     * client instead calls methods such as {@link #setLandHexLayout(int[])}
     * and {@link #setLegalSettlements(Collection, int, HashSet[])},
     * see {@link SOCBoardLarge} class javadoc.
     * @param opts {@link SOCGameOption Game options}, which may affect
     *          tile placement on board, or null.  <tt>opts</tt> must be
     *          the same as passed to constructor, and thus give the same size and layout
     *          (same {@link #getBoardEncodingFormat()}).
     */
    @SuppressWarnings("unchecked")
    @Override
    public void makeNewBoard(final Map<String, SOCGameOption> opts)
    {
        final SOCGameOption opt_breakClumps = (opts != null ? opts.get("BC") : null);

        SOCGameOption opt = (opts != null ? opts.get(SOCGameOption.K_SC_FOG) : null);
        final boolean hasScenarioFog = (opt != null) && opt.getBoolValue();

        final String scen;  // scenario key, such as SOCScenario.K_SC_4ISL, or empty string
        {
            final SOCGameOption optSC = (opts != null ? opts.get("SC") : null);
            if (optSC != null)
            {
                final String ostr = optSC.getStringValue();
                scen = (ostr != null) ? ostr : "";
            } else {
                scen = "";
            }
        }

        /** _SC_4ISL doesn't require startingLandArea, it remains 0; all other scenarios require it. */
        final boolean hasScenario4ISL = scen.equals(SOCScenario.K_SC_4ISL);

        // For scenario boards, use 3-player or 4-player or 6-player layout?
        // Always test maxPl for ==6 or < 4 ; actual value may be 6, 4, 3, or 2.
        // Same maxPl initialization as in getBoardShift(opts) and getBoardSize(..).
        final int maxPl;
        if (maxPlayers == 6)
        {
            maxPl = 6;
        } else {
            opt = (opts != null ? opts.get("PL") : null);
            if (opt == null)
                maxPl = 4;
            else if (opt.getIntValue() > 4)
                maxPl = 6;
            else
                maxPl = opt.getIntValue();
        }

        // Players must start on Land Area 1 (mainland, or for SC_FOG the two large islands),
        // unless hasScenario4ISL. Set that field now, in case a board-setup method wants it.
        if (! hasScenario4ISL)
            startingLandArea = 1;

        // shuffle and place the land hexes, numbers, and robber:
        // sets robberHex, contents of hexLayout[] and numberLayout[].
        // Adds to landHexLayout and nodesOnLand.
        // Clears cachedGetlandHexCoords.
        // Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports

        final int PORTS_TYPES_MAINLAND[], PORTS_TYPES_ISLANDS[];  // port types, or null if none
        final int PORT_LOC_FACING_MAINLAND[], PORT_LOC_FACING_ISLANDS[];  // port edge locations and facings
            // Either PORTS_TYPES_MAINLAND or PORTS_TYPES_ISLANDS can be null.
            // PORTS_TYPES_MAINLAND will be checked for "clumps" of several adjacent 3-for-1 or 2-for-1
            // ports in makeNewBoard_shufflePorts. PORTS_TYPES_ISLANDS will not be checked.

        if (hasScenario4ISL)
        {
            // Four Islands (SC_4ISL)

            if (maxPl < 4)
            {
                landAreasLegalNodes = new HashSet[5];
                makeNewBoard_placeHexes
                    (FOUR_ISL_LANDHEX_TYPE_3PL, FOUR_ISL_LANDHEX_COORD_3PL, FOUR_ISL_DICENUM_3PL, true, true,
                     FOUR_ISL_LANDHEX_LANDAREA_RANGES_3PL, false, maxPl, opt_breakClumps, scen, opts);
                PORTS_TYPES_MAINLAND = FOUR_ISL_PORT_TYPE_3PL;
                PORT_LOC_FACING_MAINLAND = FOUR_ISL_PORT_EDGE_FACING_3PL;
                pirateHex = FOUR_ISL_PIRATE_HEX[0];
            }
            else if (maxPl != 6)
            {
                landAreasLegalNodes = new HashSet[5];
                makeNewBoard_placeHexes
                    (FOUR_ISL_LANDHEX_TYPE_4PL, FOUR_ISL_LANDHEX_COORD_4PL, FOUR_ISL_DICENUM_4PL, true, true,
                     FOUR_ISL_LANDHEX_LANDAREA_RANGES_4PL, false, maxPl, opt_breakClumps, scen, opts);
                PORTS_TYPES_MAINLAND = FOUR_ISL_PORT_TYPE_4PL;
                PORT_LOC_FACING_MAINLAND = FOUR_ISL_PORT_EDGE_FACING_4PL;
                pirateHex = FOUR_ISL_PIRATE_HEX[1];
            } else {
                // Six Islands
                landAreasLegalNodes = new HashSet[7];
                makeNewBoard_placeHexes
                    (FOUR_ISL_LANDHEX_TYPE_6PL, FOUR_ISL_LANDHEX_COORD_6PL, FOUR_ISL_DICENUM_6PL, true, true,
                     FOUR_ISL_LANDHEX_LANDAREA_RANGES_6PL, false, maxPl, opt_breakClumps, scen, opts);
                PORTS_TYPES_MAINLAND = FOUR_ISL_PORT_TYPE_6PL;
                PORT_LOC_FACING_MAINLAND = FOUR_ISL_PORT_EDGE_FACING_6PL;
                pirateHex = FOUR_ISL_PIRATE_HEX[2];
            }

            PORT_LOC_FACING_ISLANDS = null;
            PORTS_TYPES_ISLANDS = null;
        }
        else if (scen.equals(SOCScenario.K_SC_TTD))
        {
            // Through The Desert
            final int idx = (maxPl > 4) ? 2 : (maxPl > 3) ? 1 : 0;  // 6-player, 4, or 3-player board

            // Land Area count varies by number of players;
            // 2 + number of small islands.  Array length has + 1 for unused landAreasLegalNodes[0].
            landAreasLegalNodes = new HashSet[1 + 2 + (TTDESERT_LANDHEX_RANGES_SMALL[idx].length / 2)];

            // - Desert strip (not part of any landarea)
            {
                final int[] desertLandHexCoords = TTDESERT_LANDHEX_COORD_DESERT[idx];
                final int[] desertDiceNum = new int[desertLandHexCoords.length];  // all 0
                final int[] desertLandhexType = new int[desertLandHexCoords.length];
                Arrays.fill(desertLandhexType, DESERT_HEX);

                makeNewBoard_placeHexes
                    (desertLandhexType, desertLandHexCoords, desertDiceNum,
                     false, false, 0, false, maxPl, null, scen, opts);
            }

            // - Main island (landarea 1 and 2)
            makeNewBoard_placeHexes
                (TTDESERT_LANDHEX_TYPE_MAIN[idx], TTDESERT_LANDHEX_COORD_MAIN[idx], TTDESERT_DICENUM_MAIN[idx],
                 true, true, TTDESERT_LANDHEX_RANGES_MAIN[idx], false, maxPl, opt_breakClumps, scen, opts);

            // - Small islands (LA 3 to n)
            makeNewBoard_placeHexes
                (TTDESERT_LANDHEX_TYPE_SMALL[idx], TTDESERT_LANDHEX_COORD_SMALL[idx], TTDESERT_DICENUM_SMALL[idx],
                 true, true, TTDESERT_LANDHEX_RANGES_SMALL[idx], false, maxPl, null, scen, opts);

            pirateHex = TTDESERT_PIRATE_HEX[idx];

            PORTS_TYPES_MAINLAND = TTDESERT_PORT_TYPE[idx];
            PORTS_TYPES_ISLANDS = null;
            PORT_LOC_FACING_MAINLAND = TTDESERT_PORT_EDGE_FACING[idx];
            PORT_LOC_FACING_ISLANDS = null;
        }
        else if (scen.equals(SOCScenario.K_SC_PIRI))
        {
            // Pirate Islands
            landAreasLegalNodes = new HashSet[2];
            final int idx = (maxPl > 4) ? 1 : 0;  // 4-player or 6-player board

            // - Large starting island
            makeNewBoard_placeHexes
                (PIR_ISL_LANDHEX_TYPE_MAIN[idx], PIR_ISL_LANDHEX_COORD_MAIN[idx], PIR_ISL_DICENUM_MAIN[idx],
                 false, false, 1, false, maxPl, opt_breakClumps, scen, opts);

            // - Pirate islands
            //  (LA # 0: Player can't place there except at their Lone Settlement coordinate within "LS".)
            makeNewBoard_placeHexes
                (PIR_ISL_LANDHEX_TYPE_PIRI[idx], PIR_ISL_LANDHEX_COORD_PIRI[idx], PIR_ISL_DICENUM_PIRI[idx],
                 false, false, 0, false, maxPl, opt_breakClumps, scen, opts);

            pirateHex = PIR_ISL_PIRATE_HEX[idx];

            PORTS_TYPES_MAINLAND = PIR_ISL_PORT_TYPE[idx];
            PORTS_TYPES_ISLANDS = null;
            PORT_LOC_FACING_MAINLAND = PIR_ISL_PORT_EDGE_FACING[idx];
            PORT_LOC_FACING_ISLANDS = null;

            setAddedLayoutPart("PP", PIR_ISL_PPATH[idx]);
        }
        else if (scen.equals(SOCScenario.K_SC_FTRI))
        {
            // Forgotten Tribe
            landAreasLegalNodes = new HashSet[2];
            final int idx = (maxPl > 4) ? 1 : 0;  // 4-player or 6-player board

            // - Larger main island
            makeNewBoard_placeHexes
                (FOR_TRI_LANDHEX_TYPE_MAIN[idx], FOR_TRI_LANDHEX_COORD_MAIN[idx], FOR_TRI_DICENUM_MAIN[idx],
                 true, true, 1, false, maxPl, opt_breakClumps, scen, opts);

            // - Small outlying islands for tribe
            //  (LA # 0; Player can't place there)
            makeNewBoard_placeHexes
                (FOR_TRI_LANDHEX_TYPE_ISL[idx], FOR_TRI_LANDHEX_COORD_ISL[idx], null,
                 false, false, 0, false, maxPl, null, scen, opts);

            pirateHex = FOR_TRI_PIRATE_HEX[idx];

            // Break up ports (opt_breakClumps) for the 6-player board only.
            // The 4-player board doesn't have enough 3-for-1 ports for that to work.
            if (maxPl > 4)
            {
                PORTS_TYPES_MAINLAND = FOR_TRI_PORT_TYPE[idx];  // PORTS_TYPES_MAINLAND breaks clumps
                PORTS_TYPES_ISLANDS = null;
                PORT_LOC_FACING_MAINLAND = FOR_TRI_PORT_EDGE_FACING[idx];
                PORT_LOC_FACING_ISLANDS = null;
            } else {
                PORTS_TYPES_MAINLAND = null;
                PORTS_TYPES_ISLANDS = FOR_TRI_PORT_TYPE[idx];  // PORTS_TYPES_ISLAND doesn't break clumps
                PORT_LOC_FACING_MAINLAND = null;
                PORT_LOC_FACING_ISLANDS = FOR_TRI_PORT_EDGE_FACING[idx];
            }

            setAddedLayoutPart("CE", FOR_TRI_DEV_CARD_EDGES[idx]);
            setAddedLayoutPart("VE", FOR_TRI_SVP_EDGES[idx]);

            // startGame_putInitPieces will set aside dev cards for players to reach "CE" special edges.
        }
        else if (scen.equals(SOCScenario.K_SC_CLVI))
        {
            // Cloth Villages
            landAreasLegalNodes = new HashSet[2];
            final int idx = (maxPl > 4) ? 1 : 0;  // 4-player or 6-player board

            // - Larger main islands
            makeNewBoard_placeHexes
                (CLVI_LANDHEX_TYPE_MAIN[idx], CLVI_LANDHEX_COORD_MAIN[idx], CLVI_DICENUM_MAIN[idx],
                 true, true, 1, false, maxPl, opt_breakClumps, scen, opts);

            // - Small middle islands for villages
            //  (LA # 0; Players can't place there)
            makeNewBoard_placeHexes
                (CLVI_LANDHEX_TYPE_ISL[idx], CLVI_LANDHEX_COORD_ISL[idx], null,
                 false, false, 0, false, maxPl, null, scen, opts);

            pirateHex = CLVI_PIRATE_HEX[idx];

            // Break up ports (opt_breakClumps) for the 6-player board only.
            // The 4-player board doesn't have enough 3-for-1 ports for that to work.
            if (maxPl > 4)
            {
                PORTS_TYPES_MAINLAND = CLVI_PORT_TYPE[idx];  // PORTS_TYPES_MAINLAND breaks clumps
                PORTS_TYPES_ISLANDS = null;
                PORT_LOC_FACING_MAINLAND = CLVI_PORT_EDGE_FACING[idx];
                PORT_LOC_FACING_ISLANDS = null;
            } else {
                PORTS_TYPES_MAINLAND = null;
                PORTS_TYPES_ISLANDS = CLVI_PORT_TYPE[idx];  // PORTS_TYPES_ISLAND doesn't break clumps
                PORT_LOC_FACING_MAINLAND = null;
                PORT_LOC_FACING_ISLANDS = CLVI_PORT_EDGE_FACING[idx];
            }

            final int[] cl = CLVI_CLOTH_VILLAGE_AMOUNTS_NODES_DICE[idx];
            setVillageAndClothLayout(cl);  // also sets board's "general supply"
            setAddedLayoutPart("CV", cl);
        }
        else if (scen.equals(SOCScenario.K_SC_WOND))
        {
            // Wonders
            landAreasLegalNodes = new HashSet[3];
            final int idx = (maxPl > 4) ? 1 : 0;  // 4-player or 6-player board

            // - Large main island
            makeNewBoard_placeHexes
                (WOND_LANDHEX_TYPE_MAIN[idx], WOND_LANDHEX_COORD_MAIN[idx], WOND_DICENUM_MAIN[idx],
                 true, true, 1, false, maxPl, opt_breakClumps, scen, opts);

            // - Desert on main island (not shuffled with other hexes)
            int[] desert = new int[WOND_LANDHEX_COORD_DESERT[idx].length];
            Arrays.fill(desert, DESERT_HEX);
            makeNewBoard_placeHexes
                (desert, WOND_LANDHEX_COORD_DESERT[idx], null,
                 false, false, 1, true, maxPl, null, scen, opts);

            // - Small outlying islands (LA #2)
            makeNewBoard_placeHexes
                (WOND_LANDHEX_TYPE_ISL[idx], WOND_LANDHEX_COORD_ISL[idx], WOND_DICENUM_ISL[idx],
                 false, false, 2, false, maxPl, null, scen, opts);

            pirateHex = 0;

            // ports
            PORTS_TYPES_MAINLAND = WOND_PORT_TYPE[idx];  // PORTS_TYPES_MAINLAND breaks clumps
            PORTS_TYPES_ISLANDS = null;
            PORT_LOC_FACING_MAINLAND = WOND_PORT_EDGE_FACING[idx];
            PORT_LOC_FACING_ISLANDS = null;

            // special node sets "N1","N2","N3": set Added Layout Parts and remove from starting legal/potential nodes.
            // (This adds the node set numbers to Added Layout Part "AL".)
            // Will have to re-add after initial placement, via addLegalNodes from SOCGame.updateAtGameFirstTurn().
            for (int i = 0; i <= 2; ++i)
                makeNewBoard_removeLegalNodes(WOND_SPECIAL_NODES[idx][i], startingLandArea, i + 1, (i == 2));
        }
        else if (hasScenarioFog)  // _SC_FOG
        {
            final int idx;  // for pirate: 3, 4, or 6-player board

            landAreasLegalNodes = new HashSet[( (maxPl == 6) ? 4 : 3 )];

            if (maxPl < 4)
            {
                // - East and West islands:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_MAIN_3PL, FOG_ISL_LANDHEX_COORD_MAIN_3PL, FOG_ISL_DICENUM_MAIN_3PL,
                     true, true, 1, false, maxPl, opt_breakClumps, scen, opts);

                // - "Fog Island" in the middle:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_FOG, FOG_ISL_LANDHEX_COORD_FOG_3PL, FOG_ISL_DICENUM_FOG_3PL,
                     true, true, 2, false, maxPl, null, scen, opts);

                PORTS_TYPES_MAINLAND = FOG_ISL_PORT_TYPE_3PL;
                PORT_LOC_FACING_MAINLAND = FOG_ISL_PORT_EDGE_FACING_3PL;
                idx = 0;
            }
            else if (maxPl == 4)
            {
                // - East and West islands:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_MAIN_4PL, FOG_ISL_LANDHEX_COORD_MAIN_4PL, FOG_ISL_DICENUM_MAIN_4PL,
                     true, true, 1, false, maxPl, opt_breakClumps, scen, opts);

                // - "Fog Island" in the middle:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_FOG, FOG_ISL_LANDHEX_COORD_FOG_4PL, FOG_ISL_DICENUM_FOG_4PL,
                     true, true, 2, false, maxPl, null, scen, opts);

                PORTS_TYPES_MAINLAND = FOG_ISL_PORT_TYPE_4PL;
                PORT_LOC_FACING_MAINLAND = FOG_ISL_PORT_EDGE_FACING_4PL;
                idx = 1;
            }
            else  // maxPl == 6
            {
                // - Northern main island:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_MAIN_6PL, FOG_ISL_LANDHEX_COORD_MAIN_6PL, FOG_ISL_DICENUM_MAIN_6PL,
                     true, true, 1, false, maxPl, opt_breakClumps, scen, opts);

                // - "Fog Island" in an arc from southwest to southeast:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_FOG_6PL, FOG_ISL_LANDHEX_COORD_FOG_6PL, FOG_ISL_DICENUM_FOG_6PL,
                     true, true, 2, false, maxPl, opt_breakClumps, scen, opts);

                // - Gold Corners in southwest, southeast
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_GC, FOG_ISL_LANDHEX_COORD_GC, FOG_ISL_DICENUM_GC,
                     false, false, 3, false, maxPl, null, scen, opts);

                PORTS_TYPES_MAINLAND = FOG_ISL_PORT_TYPE_6PL;
                PORT_LOC_FACING_MAINLAND = FOG_ISL_PORT_EDGE_FACING_6PL;
                idx = 2;
            }
            PORTS_TYPES_ISLANDS = null;  // no ports inside fog island's random layout
            PORT_LOC_FACING_ISLANDS = null;

            pirateHex = FOG_ISL_PIRATE_HEX[idx];

            // Fog hex hiding is done below after some other steps; search for hasScenarioFog

        } else if (scen.equals(SOCScenario.K_SC_NSHO)) {

            // New Shores: Uses original 4- or 6-player board like fallback layout does, + outlying islands.

            // 3pl has 4 LAs, 4pl has 4, 6pl has 7
            landAreasLegalNodes = new HashSet[(maxPl == 6) ? 8 : 5];

            final int idx = (maxPl == 6) ? 2 : (maxPl == 4) ? 1 : 0; // 3-player, 4-player, or 6-player board

            // - Main island:
            makeNewBoard_placeHexes
                (NSHO_LANDHEX_TYPE_MAIN[idx], NSHO_LANDHEX_COORD_MAIN[idx], NSHO_DICENUM_MAIN[idx],
                 (maxPl < 4), true, 1, false, maxPl, opt_breakClumps, scen, opts);

            // - Outlying islands:
            makeNewBoard_placeHexes
                (NSHO_LANDHEX_TYPE_ISL[idx], NSHO_LANDHEX_COORD_ISL[idx], NSHO_DICENUM_ISL[idx],
                 true, true, NSHO_LANDHEX_LANDAREA_RANGES[idx], false, maxPl, null, scen, opts);

            pirateHex = NSHO_PIRATE_HEX[idx];

            // ports
            PORTS_TYPES_MAINLAND = NSHO_PORT_TYPE[idx];
            PORTS_TYPES_ISLANDS = null;
            PORT_LOC_FACING_MAINLAND = NSHO_PORT_EDGE_FACING[idx];
            PORT_LOC_FACING_ISLANDS = null;

        } else {

            // This is the fallback layout, the large sea board used when no scenario is chosen.
            // Size is BOARDHEIGHT_LARGE by BOARDWIDTH_LARGE for 4 players.
            // For 6 players, there's an extra row of hexes: BOARDHEIGHT_LARGE + 3.

            landAreasLegalNodes = new HashSet[5];  // hardcoded max number of land areas

            // - Mainland:
            makeNewBoard_placeHexes
                ((maxPl > 4) ? SOCBoard6p.makeNewBoard_landHexTypes_v2 : SOCBoard4p.makeNewBoard_landHexTypes_v1,
                 (maxPl > 4) ? LANDHEX_DICEPATH_MAINLAND_6PL : LANDHEX_DICEPATH_MAINLAND_4PL,
                 (maxPl > 4) ? SOCBoard6p.makeNewBoard_diceNums_v2 : SOCBoard4p.makeNewBoard_diceNums_v1,
                 false, true, 1, false, maxPl, opt_breakClumps, scen, opts);

            // - Outlying islands:
            makeNewBoard_placeHexes
                ((maxPl > 4) ? LANDHEX_TYPE_ISLANDS_6PL : LANDHEX_TYPE_ISLANDS_4PL,
                 (maxPl > 4) ? LANDHEX_COORD_ISLANDS_ALL_6PL : LANDHEX_COORD_ISLANDS_ALL_4PL,
                 (maxPl > 4) ? LANDHEX_DICENUM_ISLANDS_6PL : LANDHEX_DICENUM_ISLANDS_4PL,
                 true, true,
                 (maxPl > 4) ? LANDHEX_LANDAREA_RANGES_ISLANDS_6PL : LANDHEX_LANDAREA_RANGES_ISLANDS_4PL,
                 false, maxPl, null, scen, opts);

            PORTS_TYPES_MAINLAND = (maxPl > 4) ? SOCBoard6p.PORTS_TYPE_V2 : SOCBoard4p.PORTS_TYPE_V1;
            PORTS_TYPES_ISLANDS = (maxPl > 4) ? PORT_TYPE_ISLANDS_6PL : PORT_TYPE_ISLANDS_4PL;
            PORT_LOC_FACING_MAINLAND = (maxPl > 4) ? PORT_EDGE_FACING_MAINLAND_6PL : PORT_EDGE_FACING_MAINLAND_4PL;
            PORT_LOC_FACING_ISLANDS = (maxPl > 4) ? PORT_EDGE_FACING_ISLANDS_6PL : PORT_EDGE_FACING_ISLANDS_4PL;
        }

        // Set up legalRoadEdges:
        initLegalRoadsFromLandNodes();
        initLegalShipEdges();

        // consistency-check land areas
        if (landAreasLegalNodes != null)
        {
            for (int i = 1; i < landAreasLegalNodes.length; ++i)
                if (landAreasLegalNodes[i] == null)
                    throw new IllegalStateException("inconsistent landAreasLegalNodes: null idx " + i);
        }

        if (newBoardProgressListener != null)
            newBoardProgressListener.boardProgress(this, opts, NewBoardProgressListener.ALL_HEXES_PLACED);

        // Hide some land hexes behind fog, if the scenario does that
        if (hasScenarioFog)
        {
            final int[] FOGHEXES = (maxPl == 6)
                ? FOG_ISL_LANDHEX_COORD_FOG_6PL
                : ((maxPl < 4) ? FOG_ISL_LANDHEX_COORD_FOG_3PL : FOG_ISL_LANDHEX_COORD_FOG_4PL);
            makeNewBoard_hideHexesInFog(FOGHEXES);
        } else if (null != System.getProperty(PROP_JSETTLERS_DEBUG_BOARD_FOG)) {
            // Select n random hexes (20% of landHexLayout):
            // Knuth, "The Art of Computer Programming" Vol 2 Seminumerical Algorithms, Algorithm 3.4.2 S

            int d = landHexLayout.size();
            int n = (int) (d * .20f);
            if (n == 0)
                n = 1;  // round up
            final int[] hideCoord = new int[n];
            for (int hexcoord : landHexLayout)
            {
                if (rand.nextFloat() <= (n / (float) d))
                {
                    hideCoord[hideCoord.length - n] = hexcoord;
                    n -= 1;
                    if (n == 0)
                        break;
                }
                d -= 1;
            }

            makeNewBoard_hideHexesInFog(hideCoord);
        }

        if ((PORTS_TYPES_MAINLAND == null) && (PORTS_TYPES_ISLANDS == null))
        {
            return;  // <--- Early return: No ports to place ---
        }

        // check port locations & facings, make sure no overlap with land hexes
        if (PORTS_TYPES_MAINLAND != null)
            makeNewBoard_checkPortLocationsConsistent(PORT_LOC_FACING_MAINLAND);
        if (PORT_LOC_FACING_ISLANDS != null)
            makeNewBoard_checkPortLocationsConsistent(PORT_LOC_FACING_ISLANDS);

        // copy and shuffle the ports, and check vs game option BC
        int[] portTypes_main;
        int[] portTypes_islands;
        if (PORTS_TYPES_MAINLAND != null)
        {
            final int ptL = PORTS_TYPES_MAINLAND.length, plfL = PORT_LOC_FACING_MAINLAND.length;
            if ((2 * ptL) != plfL)
                throw new IllegalArgumentException
                    ("Mismatched port-array lengths: PORT_LOC_FACING_MAINLAND (" + plfL
                     + ") should be 2 x PORTS_TYPES_MAINLAND (" + ptL + ")");

            portTypes_main = new int[ptL];
            System.arraycopy(PORTS_TYPES_MAINLAND, 0, portTypes_main, 0, ptL);
            if ((maxPl == 6) || ! hasScenarioFog)
                makeNewBoard_shufflePorts(portTypes_main, opt_breakClumps);
        } else {
            portTypes_main = null;
        }
        if (PORTS_TYPES_ISLANDS != null)
        {
            final int ptL = PORTS_TYPES_ISLANDS.length, plfL = PORT_LOC_FACING_ISLANDS.length;
            if ((2 * ptL) != plfL)
                throw new IllegalArgumentException
                    ("Mismatched port-array lengths: PORT_LOC_FACING_ISLANDS (" + plfL
                     + ") should be 2 x PORTS_TYPES_ISLANDS (" + ptL + ")");

            portTypes_islands = new int[ptL];
            System.arraycopy(PORTS_TYPES_ISLANDS, 0, portTypes_islands, 0, ptL);
            makeNewBoard_shufflePorts(portTypes_islands, null);
        } else {
            portTypes_islands = null;
        }

        // copy port types to beginning of portsLayout[]
        final int pcountMain = (PORTS_TYPES_MAINLAND != null) ? portTypes_main.length : 0;
        portsCount = pcountMain;
        if (PORTS_TYPES_ISLANDS != null)
            portsCount += PORTS_TYPES_ISLANDS.length;
        if ((portsLayout == null) || (portsLayout.length != (3 * portsCount)))
            portsLayout = new int[3 * portsCount];
        if (PORTS_TYPES_MAINLAND != null)
            System.arraycopy(portTypes_main, 0, portsLayout, 0, pcountMain);
        if (PORTS_TYPES_ISLANDS != null)
            System.arraycopy(portTypes_islands, 0,
                portsLayout, pcountMain, portTypes_islands.length);

        // place the ports (hex numbers and facing) within portsLayout[] and nodeIDtoPortType.
        // fill out the ports[] vectors with node coordinates where a trade port can be placed.
        nodeIDtoPortType = new HashMap<Integer, Integer>();

        // - main island(s):
        // i == port type array index
        // j == port edge & facing array index
        final int L = (portTypes_main != null) ? portTypes_main.length : 0;
        if (L > 0)
        {
            for (int i = 0, j = 0; i < L; ++i)
            {
                final int ptype = portTypes_main[i];  // also == portsLayout[i]
                final int edge = PORT_LOC_FACING_MAINLAND[j];
                ++j;
                final int facing = PORT_LOC_FACING_MAINLAND[j];
                ++j;
                final int[] nodes = getAdjacentNodesToEdge_arr(edge);
                placePort(ptype, -1, facing, nodes[0], nodes[1]);
                // portsLayout[i] is set already, from portTypes_main[i]
                portsLayout[i + portsCount] = edge;
                portsLayout[i + (2 * portsCount)] = facing;
            }
        }

        // - outlying islands:
        // i == port type array index
        // j == port edge & facing array index
        if (PORTS_TYPES_ISLANDS != null)
        {
            for (int i = 0, j = 0; i < PORTS_TYPES_ISLANDS.length; ++i)
            {
                final int ptype = portTypes_islands[i];  // also == portsLayout[i+L]
                final int edge = PORT_LOC_FACING_ISLANDS[j];
                ++j;
                final int facing = PORT_LOC_FACING_ISLANDS[j];
                ++j;
                final int[] nodes = getAdjacentNodesToEdge_arr(edge);
                placePort(ptype, -1, facing, nodes[0], nodes[1]);
                // portsLayout[L+i] is set already, from portTypes_islands[i]
                portsLayout[L + i + portsCount] = edge;
                portsLayout[L + i + (2 * portsCount)] = facing;
            }
        }

        if (newBoardProgressListener != null)
            newBoardProgressListener.boardProgress(this, opts, NewBoardProgressListener.DONE_PORTS_PLACED);
    }

    /**
     * For {@link #makeNewBoard(Map)}, place the land hexes, number, and robber,
     * after shuffling landHexType[].
     * Sets robberHex, contents of hexLayoutLg[] and numberLayoutLg[].
     * Adds to {@link #landHexLayout} and {@link SOCBoard#nodesOnLand}.
     * Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
     * (for land hex resource types).
     * If <tt>landAreaNumber</tt> != 0, also adds to {@link #landAreasLegalNodes}.
     *<P>
     * Called from {@link #makeNewBoard(Map)} at server only; client has its board layout sent from the server.
     *<P>
     * For the board layout geometry, see the {@link SOCBoardLarge} class javadoc's "Coordinate System" section.
     *<P>
     * This method does not clear out {@link #hexLayoutLg} or {@link #numberLayoutLg}
     * before it starts placement.  You can call it multiple times to set up multiple
     * areas of land hexes: Call once for each land area.
     *<P>
     * If scenario requires some nodes to be removed from legal placement, after the last call to this method
     * call {@link #makeNewBoard_removeLegalNodes(int[], int, int, boolean)}.
     *<P>
     * This method clears {@link #cachedGetLandHexCoords} to <tt>null</tt>.
     *
     * @param landHexType  Resource type to place into {@link #hexLayoutLg} for each land hex; will be shuffled.
     *                    Values are {@link #CLAY_HEX}, {@link #DESERT_HEX}, etc.
     *                    There should be no {@link #FOG_HEX} in here; land hexes are hidden by fog later.
     * @param landPath  Coordinates within {@link #hexLayoutLg} (also within {@link #numberLayoutLg}) for each land hex;
     *                    same array length as <tt>landHexType[]</tt>
     * @param number   Numbers to place into {@link #numberLayoutLg} for each land hex;
     *                    array length is <tt>landHexType[].length</tt> minus 1 for each desert in <tt>landHexType[]</tt>.
     *                    If only some land hexes have dice numbers, <tt>number[]</tt> can be shorter; each
     *                    <tt>number[i]</tt> will be placed at <tt>landPath[i]</tt> until <tt>i >= number.length</tt>.
     *                    Can be <tt>null</tt> if none of these land hexes have dice numbers.
     * @param shuffleDiceNumbers  If true, shuffle the dice <tt>number</tt>s before placing along <tt>landPath</tt>.
     *                 Also only if true, calls
     *                 {@link #makeNewBoard_placeHexes_moveFrequentNumbers(int[], ArrayList, int, String)}
     *                 to make sure 6s, 8s aren't adjacent and gold hexes aren't on 6 or 8.
     *                 <tt>number[]</tt> must not be <tt>null</tt>.
     * @param shuffleLandHexes    If true, shuffle <tt>landHexType[]</tt> before placing along <tt>landPath</tt>.
     * @param landAreaNumber  0 unless there will be more than 1 Land Area (group of islands).
     *                    If != 0, updates {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt>
     *                    with the same nodes added to {@link SOCBoard#nodesOnLand}.
     * @param addToExistingLA  True if {@code landAreaNumber} already has hexes and nodes, and {@code landPath[]}
     *                    contents should be added to it.  Ignored if {@code landAreaNumber == 0}.
     *                    Otherwise this should always be {@code false}, to avoid typos and cut-and-paste
     *                    errors with Land Area numbers.
     * @param maxPl  For scenario boards, use 3-player or 4-player or 6-player layout?
     *               Always tests maxPl for ==6 or &lt; 4; actual value may be 6, 4, 3, or 2.
     * @param optBC  Game option "BC" from the options for this board, or <tt>null</tt>.
     * @param scen   Game scenario, such as {@link SOCScenario#K_SC_FTRI}, or "";
     *               some scenarios might want special distribution of certain hex types or dice numbers.
     *               Handled via {@link #makeNewBoard_placeHexes_moveFrequentNumbers(int[], ArrayList, int, String)}.
     * @param opts  Game options passed to board constructor and {@code makeNewBoard}, or {@code null}.
     * @throws IllegalStateException  if <tt>landAreaNumber</tt> != 0 and either
     *             {@link #landAreasLegalNodes} == null, or not long enough, or
     *             {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt> != null
     *             because the land area has already been placed.
     *             To avoid this exception if the second placement is deliberate, call with {@code addToExistingLA} true.
     * @throws IllegalArgumentException  if <tt>landHexType</tt> contains {@link #FOG_HEX}, <BR>
     *             or if <tt>landHexType.length != landPath.length</tt>, <BR>
     *             or if <tt>number</tt> contains a negative value
     * @see #makeNewBoard_placeHexes(int[], int[], int[], boolean, boolean, int[], boolean, int, SOCGameOption, String, Map)
     */
    private final void makeNewBoard_placeHexes
        (int[] landHexType, final int[] landPath, final int[] number, final boolean shuffleDiceNumbers,
         final boolean shuffleLandHexes, final int landAreaNumber, final boolean addToExistingLA,
         final int maxPl, final SOCGameOption optBC, final String scen, final Map<String, SOCGameOption> opts)
        throws IllegalStateException, IllegalArgumentException
    {
        final int[] pathRanges = { landAreaNumber, landPath.length };  // 1 range, uses all of landPath
        makeNewBoard_placeHexes
            (landHexType, landPath, number, shuffleDiceNumbers, shuffleLandHexes,
             pathRanges, addToExistingLA, maxPl, optBC, scen, opts);
    }

    /**
     * For {@link #makeNewBoard(Map)}, place the land hexes, number, and robber
     * for multiple land areas, after shuffling their common landHexType[].
     * Sets robberHex, contents of hexLayoutLg[] and numberLayoutLg[].
     * Adds to {@link #landHexLayout} and {@link SOCBoard#nodesOnLand}.
     * Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports
     * (for land hex resource types).
     * If Land Area Number != 0, also adds to {@link #landAreasLegalNodes}.
     *<P>
     * Called from {@link #makeNewBoard(Map)} at server only; client has its board layout sent from the server.
     *<P>
     * This method does not clear out {@link #hexLayoutLg} or {@link #numberLayoutLg}
     * before it starts placement.  You can call it multiple times to set up multiple
     * areas of land hexes: Call once for each group of Land Areas which shares a landPath and landHexType.
     * For each land area, it updates {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt>
     * with the same nodes added to {@link SOCBoard#nodesOnLand}.
     *<P>
     * If only some parts of a Land Area should be shuffled: Call once with the hex coordinates of the shuffled part,
     * with {@code shuffleDiceNumbers} and/or {@code shuffleLandHexes} true.  Then, call again with the coordinates of
     * the non-shuffled hexes, with {@code shuffleLandHexes} false and {@code addToExistingLA} true.
     *<P>
     * If part of the board will be hidden by {@link #FOG_HEX}, wait before doing that:
     * This method must shuffle and place the unobscured land hexes.
     * After the last call to <tt>makeNewBoard_placeHexes</tt>, call
     * {@link #makeNewBoard_hideHexesInFog(int[])}.
     *<P>
     * If scenario requires some nodes to be removed from legal placement, after the last call to this method
     * call {@link #makeNewBoard_removeLegalNodes(int[], int, int, boolean)}.
     *<P>
     * This method clears {@link #cachedGetLandHexCoords} to <tt>null</tt>.
     *
     * @param landHexType  Resource type to place into {@link #hexLayoutLg} for each land hex; will be shuffled.
     *                    Values are {@link #CLAY_HEX}, {@link #DESERT_HEX}, etc.
     *                    There should be no {@link #FOG_HEX} in here; land hexes are hidden by fog later.
     *                    For the Fog Island (scenario option {@link SOCGameOption#K_SC_FOG _SC_FOG}),
     *                    one land area contains some water.  So, <tt>landHexType[]</tt> may contain {@link #WATER_HEX}.
     * @param landPath  Coordinates within {@link #hexLayoutLg} (also within {@link #numberLayoutLg}) for each hex to place;
     *                    same array length as {@code landHexType[]}.  May contain {@code WATER_HEX}.
     *                    <BR> {@code landAreaPathRanges[]} tells how to split this array of hex coordinates
     *                    into multiple Land Areas.
     * @param number   Numbers to place into {@link #numberLayoutLg} for each land hex;
     *                    array length is <tt>landHexType[].length</tt> minus 1 for each desert or water in <tt>landHexType[]</tt>
     *                    if every land hex has a dice number.
     *                    If only some land hexes have dice numbers, <tt>number[]</tt> can be shorter; each
     *                    <tt>number[i]</tt> will be placed at <tt>landPath[i]</tt> until <tt>i >= number.length</tt>.
     *                    Can be <tt>null</tt> if none of these land hexes have dice numbers.
     * @param shuffleDiceNumbers  If true, shuffle the dice <tt>number</tt>s before placing along <tt>landPath</tt>.
     *                    <tt>number[]</tt> must not be <tt>null</tt>.
     * @param shuffleLandHexes    If true, shuffle <tt>landHexType[]</tt> before placing along <tt>landPath</tt>.
     * @param landAreaPathRanges  <tt>landPath[]</tt>'s Land Area Numbers, and the size of each land area.
     *                    Array length is 2 x the count of land areas included.
     *                    Index 0 is the first landAreaNumber, index 1 is the length of that land area (number of hexes).
     *                    Index 2 is the next landAreaNumber, index 3 is that one's length, etc.
     *                    The sum of those lengths must equal <tt>landPath.length</tt>.
     *                    Use landarea number 0 for hexes which will not have a land area.
     * @param addToExistingLA  True if the land area number already has hexes and nodes, and {@code landPath[]}
     *                    contents should be added to it.  Ignored if land area number == 0.
     *                    Otherwise this should always be {@code false}, to avoid typos and cut-and-paste
     *                    errors with Land Area numbers.
     * @param maxPl  For scenario boards, use 3-player or 4-player or 6-player layout?
     *               Always tests maxPl for ==6 or &lt; 4; actual value may be 6, 4, 3, or 2.
     * @param optBC  Game option "BC" from the options for this board, or <tt>null</tt>.
     * @param scen   Game scenario, such as {@link SOCScenario#K_SC_FTRI}, or "";
     *               some scenarios might want special distribution of certain hex types or dice numbers.
     *               Handled via {@link #makeNewBoard_placeHexes_moveFrequentNumbers(int[], ArrayList, int, String)}.
     * @param opts  Game options passed to board constructor and {@code makeNewBoard}, or {@code null}.
     * @throws IllegalStateException  if land area number != 0 and either
     *             {@link #landAreasLegalNodes} == null, or not long enough, or any
     *             {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt> != null
     *             because the land area has already been placed.
     *             To avoid this exception if the second placement is deliberate, call with {@code addToExistingLA} true.
     * @throws IllegalArgumentException if <tt>landAreaPathRanges</tt> is null or has an uneven length, <BR>
     *             or if the total length of its land areas != <tt>landPath.length</tt>, <BR>
     *             or if <tt>landHexType.length != landPath.length</tt>, <BR>
     *             or if <tt>landHexType</tt> contains {@link #FOG_HEX}, <BR>
     *             or if <tt>number</tt> contains a negative value, <BR>
     *             or if {@link SOCBoard#makeNewBoard_checkLandHexResourceClumps(Vector, int)}
     *                 finds an invalid or uninitialized hex coordinate (hex type -1)
     * @see #makeNewBoard_placeHexes(int[], int[], int[], boolean, boolean, int, boolean, int, SOCGameOption, String, Map)
     */
    private final void makeNewBoard_placeHexes
        (int[] landHexType, final int[] landPath, int[] number, final boolean shuffleDiceNumbers,
         final boolean shuffleLandHexes, final int[] landAreaPathRanges, final boolean addToExistingLA,
         final int maxPl, final SOCGameOption optBC, final String scen,
         final Map<String, SOCGameOption> opts)
        throws IllegalStateException, IllegalArgumentException
    {
        final boolean checkClumps = (optBC != null) && optBC.getBoolValue();
        final int clumpSize = checkClumps ? optBC.getIntValue() : 0;
        boolean clumpsNotOK = checkClumps;
        final boolean hasRobber = ! SOCScenario.K_SC_PIRI.equals(scen);  // all other known scenarios have a robber

        // Validate length of landHexType, landPath
        if (landHexType.length != landPath.length)
            throw new IllegalArgumentException
                ("length mismatch: landHexType " + landHexType.length + ", landPath " + landPath.length);

        // Validate landAreaPathRanges lengths within landPath
        if ((landAreaPathRanges == null) || ((landAreaPathRanges.length % 2) != 0))
            throw new IllegalArgumentException("landAreaPathRanges: uneven length");
        if (landAreaPathRanges.length <= 2)
        {
            final int L = landAreaPathRanges[1];
            if (L != landPath.length)
                throw new IllegalArgumentException
                    ("landAreaPathRanges: landarea " + landAreaPathRanges[0]
                     + ": range length " + L + " should be " + landPath.length);
        } else {
            int L = 0, i;
            for (i = 1; i < landAreaPathRanges.length; i += 2)
            {
                L += landAreaPathRanges[i];
                if (L > landPath.length)
                    throw new IllegalArgumentException
                        ("landAreaPathRanges: landarea " + landAreaPathRanges[i-1]
                          + ": total range length " + L + " should be " + landPath.length);
            }
            if (L < landPath.length)
                throw new IllegalArgumentException
                    ("landAreaPathRanges: landarea " + landAreaPathRanges[i-3]
                      + ": total range length " + L + " should be " + landPath.length);
        }

        // Shuffle, place, then check layout for clumps:
        int iterRemain = 20;
        do   // will re-do placement until clumpsNotOK is false or iterRemain == 0
        {
            if (shuffleLandHexes)
            {
                // shuffle the land hexes 10x
                for (int j = 0; j < 10; j++)
                {
                    int idx, tmp;
                    for (int i = 0; i < landHexType.length; i++)
                    {
                        // Swap a random hex below the ith hex with the ith hex
                        idx = Math.abs(rand.nextInt() % (landHexType.length - i));
                        if (idx == i)
                            continue;
                        tmp = landHexType[idx];
                        landHexType[idx] = landHexType[i];
                        landHexType[i] = tmp;
                    }
                }
            }

            if (shuffleDiceNumbers)
            {
                // shuffle the dice #s 10x
                for (int j = 0; j < 10; j++)
                {
                    int idx, tmp;
                    for (int i = 0; i < number.length; i++)
                    {
                        idx = Math.abs(rand.nextInt() % (number.length - i));
                        if (idx == i)
                            continue;
                        tmp = number[idx];
                        number[idx] = number[i];
                        number[i] = tmp;
                    }
                }
            }

            // Place the land hexes, robber, dice numbers.
            // If we've shuffled the numbers, track where the
            // 6s and 8s ("red" frequently-rolled numbers) go.
            ArrayList<Integer> redHexes = (shuffleDiceNumbers ? new ArrayList<Integer>() : null);
            int cnt = 0;
            for (int i = 0; i < landHexType.length; i++)
            {
                final int r = landPath[i] >> 8,
                          c = landPath[i] & 0xFF;

                try
                {
                    // place the land hexes
                    hexLayoutLg[r][c] = landHexType[i];

                    // place the robber on the desert
                    if (landHexType[i] == DESERT_HEX)
                    {
                        if (hasRobber)
                            setRobberHex(landPath[i], false);
                        numberLayoutLg[r][c] = 0;
                        // TODO do we want to not set robberHex? or a specific point?
                    }
                    else if (landHexType[i] == WATER_HEX)
                    {
                        numberLayoutLg[r][c] = 0;  // Fog Island's landarea has some water shuffled in
                    }
                    else if (landHexType[i] == FOG_HEX)
                    {
                        throw new IllegalArgumentException("landHexType can't contain FOG_HEX");
                    }
                    else if ((number != null) && (cnt < number.length))
                    {
                        // place the numbers
                        final int diceNum = number[cnt];
                        if (diceNum < 0)
                            throw new IllegalArgumentException
                                ("makeNewBoard_placeHexes: number[" + cnt + "] below 0: " + diceNum);
                        numberLayoutLg[r][c] = diceNum;
                        cnt++;

                        if (shuffleDiceNumbers && ((diceNum == 6) || (diceNum == 8)))
                            redHexes.add(landPath[i]);
                    }

                } catch (Exception ex) {
                    throw new IllegalArgumentException
                        ("Problem placing landPath[" + i + "] at 0x"
                         + Integer.toHexString(landPath[i])
                         + " [" + r + "][" + c + "]" + ": " + ex.toString(), ex);
                }

            }  // for (i in landHex)

            if (newBoardProgressListener != null)
                newBoardProgressListener.hexesProgress
                    (this, opts, NewBoardProgressListener.HEXES_PLACE, landPath);

            if (shuffleLandHexes && checkClumps)
            {
                // Check the newly placed land area(s) for clumps;
                // ones placed in previous method calls are ignored
                Vector<Integer> unvisited = new Vector<Integer>();  // contains each hex's coordinate
                for (int i = 0; i < landHexType.length; ++i)
                    unvisited.addElement(Integer.valueOf(landPath[i]));

                clumpsNotOK = makeNewBoard_checkLandHexResourceClumps(unvisited, clumpSize);
            } else {
                clumpsNotOK = false;
            }

            if (newBoardProgressListener != null)
                newBoardProgressListener.hexesProgress
                    (this, opts, NewBoardProgressListener.HEXES_CHECK_CLUMPS, landPath);

            if (shuffleLandHexes && ! clumpsNotOK)
            {
                // Separate adjacent gold hexes.  Does not change landPath or redHexes, only hexLayoutLg.
                //   In scenario SC_TTD, this also makes sure the main island's only GOLD_HEX is placed
                //   within landarea 2 (the small strip past the desert).
                makeNewBoard_placeHexes_arrangeGolds(landPath, landAreaPathRanges, scen);
            }

            if (shuffleDiceNumbers && ! clumpsNotOK)
            {
                // Separate adjacent "red numbers" (6s, 8s)
                //   and make sure gold hex dice aren't too frequent
                makeNewBoard_placeHexes_moveFrequentNumbers(landPath, redHexes, maxPl, scen);
            }

            if ((newBoardProgressListener != null) && ! clumpsNotOK)
                newBoardProgressListener.hexesProgress
                    (this, opts, NewBoardProgressListener.HEXES_MOVE_FREQ_NUMS, landPath);

            --iterRemain;
        } while (clumpsNotOK && (iterRemain > 0));

        // Now that we know this layout is okay,
        // add the hex coordinates to landHexLayout,
        // and the hexes' nodes to nodesOnLand.
        // Throws IllegalStateException if landAreaNumber incorrect
        // vs size/contents of landAreasLegalNodes
        // from previously placed land areas.

        cachedGetLandHexCoords = null;  // invalidate the previous cached set

        for (int i = 0; i < landHexType.length; i++)
            landHexLayout.add(Integer.valueOf(landPath[i]));

        for (int i = 0, hexIdx = 0; i < landAreaPathRanges.length; i += 2)
        {
            final int landAreaNumber = landAreaPathRanges[i],
                      landAreaLength = landAreaPathRanges[i + 1],
                      nextHexIdx = hexIdx + landAreaLength;
            makeNewBoard_fillNodesOnLandFromHexes
                (landPath, hexIdx, nextHexIdx, landAreaNumber, addToExistingLA);
            hexIdx = nextHexIdx;
        }

    }  // makeNewBoard_placeHexes

    /**
     * For {@link #makeNewBoard(Map)}, after placing
     * land hexes and dice numbers into {@link #hexLayoutLg},
     * fine-tune the randomized gold hex placement:
     *<UL>
     * <LI> Find and separate adjacent gold hexes.
     * <LI> For scenario {@link SOCScenario#K_SC_TTD SC_TTD}, ensure the main island's only <tt>GOLD_HEX</tt>
     *      is placed in land area 2, the small strip of land past the desert.
     *</UL>
     *
     * @param hexCoords  All hex coordinates being shuffled; includes gold hexes and non-gold hexes, may include water
     * @param landAreaPathRanges  <tt>landPath[]</tt>'s Land Area Numbers, and the size of each land area;
     *     see this parameter's javadoc at
     *     {@link #makeNewBoard_placeHexes(int[], int[], int[], boolean, boolean, int[], boolean, int, SOCGameOption, String, Map)}.
     * @param scen  Game scenario, such as {@link SOCScenario#K_SC_TTD}, or "";
     *              some scenarios might want special distribution of certain hex types or dice numbers.
     */
    private final void makeNewBoard_placeHexes_arrangeGolds
        (final int[] hexCoords, final int[] landAreaPathRanges, final String scen)
    {
        // map of gold hex coords to all their adjacent land hexes, if any;
        // golds with no adjacent land are left out of the map.
        HashMap<Integer, Vector<Integer>> goldAdjac = new HashMap<Integer, Vector<Integer>>();

        // Find each gold hex's adjacent land hexes:
        for (int hex : hexCoords)
        {
            if (GOLD_HEX != getHexTypeFromCoord(hex))
                continue;

            Vector<Integer> adjacLand = getAdjacentHexesToHex(hex, false);
            if (adjacLand == null)
                continue;  // no adjacents, ignore this GOLD_HEX; getAdjacentHexesToHex never returns an empty vector

            goldAdjac.put(Integer.valueOf(hex), adjacLand);
        }

        // Scenario SC_TTD: Special handling for the main island's only gold hex:
        // Make sure that gold's in landarea 2 (small strip past the desert).
        // (For the main island, landAreaPathRanges[] contains landarea 1 and landarea 2.)
        if (scen.equals(SOCScenario.K_SC_TTD)
            && (landAreaPathRanges != null) && (landAreaPathRanges[0] == 1))
        {
            if ((goldAdjac.size() != 1) || (landAreaPathRanges.length != 4))
                throw new IllegalArgumentException("SC_TTD: Main island should have 1 gold hex, 2 landareas");

            final int goldHex = (Integer) (goldAdjac.keySet().toArray()[0]);

            boolean foundInLA2 = false;
            // Search landarea 2 within landHexCoords[] for gold hex coord;
            // landAreaPathRanges[1] == size of LA1 == index of first hex of LA2 within landHexCoords[]
            for (int i = landAreaPathRanges[1]; i < hexCoords.length; ++i)
            {
                if (hexCoords[i] == goldHex)
                {
                    foundInLA2 = true;
                    break;
                }
            }

            if (! foundInLA2)
            {
                // The gold is in landarea 1. Pick a random non-gold hex in landarea 2, and swap hexLayoutLg values.

                final int i = landAreaPathRanges[1] + rand.nextInt(landAreaPathRanges[3]);  // ranges[3] == size of LA2
                final int nonGoldHex = hexCoords[i];

                final int gr = goldHex >> 8,
                          gc = goldHex & 0xFF,
                          nr = nonGoldHex >> 8,
                          nc = nonGoldHex & 0xFF;
                hexLayoutLg[gr][gc] = hexLayoutLg[nr][nc];
                hexLayoutLg[nr][nc] = GOLD_HEX;
            }

            // Will always return from method just past here, because goldAdjac.size is 1.
        }

        if (goldAdjac.size() < 2)
        {
            return;  // <--- Early return: no adjacent gold hexes to check ---
        }

        // See if any adjacents are another gold hex:
        // If so, add both to goldAdjacGold.
        HashMap<Integer, List<Integer>> goldAdjacGold = new HashMap<Integer, List<Integer>>();
        int maxAdjac = 0;
        Integer maxHex = null;

        for (Integer gHex : goldAdjac.keySet())
        {
            for (Integer adjHex : goldAdjac.get(gHex))
            {
                if (goldAdjac.containsKey(adjHex))
                {
                    int n = makeNewBoard_placeHexes_arrGolds_addToAdjacList(goldAdjacGold, gHex, adjHex);
                    if (n > maxAdjac)
                    {
                        maxAdjac = n;
                        maxHex = gHex;
                    }

                    n = makeNewBoard_placeHexes_arrGolds_addToAdjacList(goldAdjacGold, adjHex, gHex);
                    if (n > maxAdjac)
                    {
                        maxAdjac = n;
                        maxHex = adjHex;
                    }
                }
            }
        }

        if (goldAdjacGold.isEmpty())
        {
            return;  // <--- Early return: no adjacent gold hexes ---
        }

        // Build a set of all the other (non-gold) land hexes that aren't adjacent to gold.
        // Then, we'll swap out the gold hex that has the most adjacent golds, in case that
        // was the middle one and no other golds are adjacent.

        HashSet<Integer> nonAdjac = new HashSet<Integer>();
        for (int hex : hexCoords)
        {
            {
                final int htype = getHexTypeFromCoord(hex);
                if ((htype == GOLD_HEX) || (htype == WATER_HEX))
                    continue;
            }

            final Integer hexInt = Integer.valueOf(hex);
            boolean adjac = false;
            for (Vector<Integer> goldAdjacList : goldAdjac.values())
            {
                if (goldAdjacList.contains(hexInt))
                {
                    adjac = true;
                    break;
                }
            }

            if (! adjac)
                nonAdjac.add(hexInt);
        }

        if (nonAdjac.isEmpty())
        {
            return;  // <--- Early return: nowhere non-adjacent to swap it to
        }

        // pick a random nonAdjac, and swap with "middle" gold hex
        makeNewBoard_placeHexes_arrGolds_swapWithRandom
            (maxHex, nonAdjac, goldAdjacGold);

        // if any more, take care of them while we can
        while (! (goldAdjacGold.isEmpty() || nonAdjac.isEmpty()))
        {
            // goldAdjacGold, goldAdjac, and nonAdjac are mutated by swapWithRandom,
            // so we need a new iterator each time.

            final Integer oneGold = goldAdjacGold.keySet().iterator().next();
            makeNewBoard_placeHexes_arrGolds_swapWithRandom
                (oneGold, nonAdjac, goldAdjacGold);
        }
    }

    /**
     * Add hex1 to hex0's adjacency list in this map; create that list if needed.
     * Used by makeNewBoard_placeHexes_arrangeGolds.
     * @param goldAdjacGold  Map from gold hexes to their adjacent gold hexes
     * @param hex0  Hex coordinate that will have a list of adjacents in <tt>goldAdjacGold</tt>
     * @param hex1  Hex coordinate to add to <tt>hex0</tt>'s list
     * @return length of hex0's list after adding hex1
     */
    private final int makeNewBoard_placeHexes_arrGolds_addToAdjacList
        (HashMap<Integer, List<Integer>> goldAdjacGold, final Integer hex0, Integer hex1)
    {
        List<Integer> al = goldAdjacGold.get(hex0);
        if (al == null)
        {
            al = new ArrayList<Integer>();
            goldAdjacGold.put(hex0, al);
        }
        al.add(hex1);
        return al.size();
    }

    /**
     * Swap this gold hex with a random non-adjacent hex in <tt>hexLayoutLg</tt>.
     * Updates <tt>nonAdjac</tt> and <tt>goldAdjacGold</tt>.
     * Used by makeNewBoard_placeHexes_arrangeGolds.
     * @param goldHex  Coordinate of gold hex to swap
     * @param nonAdjac  All land hexes not currently adjacent to a gold hex;
     *                  should not include coordinates of any {@code WATER_HEX}
     * @param goldAdjacGold  Map of golds adjacent to each other
     * @throws IllegalArgumentException  if goldHex coordinates in hexLayoutLg aren't GOLD_HEX
     */
    private final void makeNewBoard_placeHexes_arrGolds_swapWithRandom
        (final Integer goldHex, HashSet<Integer> nonAdjac, HashMap<Integer, List<Integer>> goldAdjacGold)
        throws IllegalArgumentException
    {
        // get a random non-adjacent hex to swap with gold:
        //    not efficient, but won't be called more than once or twice
        //    per board with adjacent golds. Most boards won't have any.
        final Integer nonAdjHex;
        {
            int n = nonAdjac.size();
            Iterator<Integer> nai = nonAdjac.iterator();
            if (n > 1)
                for (n = rand.nextInt(n); n > 0; --n)
                    nai.next();  // skip

            nonAdjHex = nai.next();
        }

        // swap goldHex, nonAdjHex in hexLayoutLg:
        {
            int gr = goldHex >> 8,
                gc = goldHex & 0xFF,
                nr = nonAdjHex >> 8,
                nc = nonAdjHex & 0xFF;
            if (hexLayoutLg[gr][gc] != GOLD_HEX)
                throw new IllegalArgumentException("goldHex coord not gold in hexLayoutLg: 0x" + Integer.toHexString(goldHex));
            hexLayoutLg[gr][gc] = hexLayoutLg[nr][nc];  // gets nonAdjHex's land hex type
            hexLayoutLg[nr][nc] = GOLD_HEX;
        }

        // since it's gold now, remove nonAdjHex and its adjacents from nonAdjac:
        nonAdjac.remove(nonAdjHex);
        Vector<Integer> adjs = getAdjacentHexesToHex(nonAdjHex, false);
        if (adjs != null)
            for (Integer ahex : adjs)
                nonAdjac.remove(ahex);

        // Remove goldHex from goldAdjacGold (both directions):

        // - as value: look for it in its own adjacents' adjacency lists
        final List<Integer> adjacHexesToSwapped = goldAdjacGold.get(goldHex);
        if (adjacHexesToSwapped != null)
        {
            for (Integer ahex : adjacHexesToSwapped)
            {
                List<Integer> adjacToAdjac = goldAdjacGold.get(ahex);
                if (adjacToAdjac != null)
                {
                    adjacToAdjac.remove(goldHex);
                    if (adjacToAdjac.isEmpty())
                        goldAdjacGold.remove(ahex);  // ahex had no other adjacents
                }
            }
        }

        // - as key: remove it from goldAdjacGold
        goldAdjacGold.remove(goldHex);
    }

    /**
     * For {@link #makeNewBoard(Map)}, after placing
     * land hexes and dice numbers into {@link #hexLayoutLg} and {@link #numberLayoutLg},
     * separate adjacent "red numbers" (6s, 8s)
     * and make sure gold hex dice aren't too frequent.
     * For algorithm details, see comments in this method and
     * {@link #makeNewBoard_placeHexes_moveFrequentNumbers_checkSpecialHexes(int[], ArrayList, int, String)}.
     *<P>
     * Call {@link #makeNewBoard_placeHexes_arrangeGolds(int[], int[], String)} before this
     * method, not after, so that gold hexes will already be in their final locations.
     *<P>
     * If using {@link #FOG_HEX}, no fog should be on the
     * board when calling this method: Don't call after
     * {@link #makeNewBoard_hideHexesInFog(int[])}.
     *
     * @param landPath  Final coordinates for each hex being placed; may contain water
     * @param redHexes  Hex coordinates of placed "red" (frequent) dice numbers (6s, 8s)
     * @param maxPl  For scenario boards, use 3-player or 4-player or 6-player layout?
     *               Always tests maxPl for ==6 or &lt; 4; actual value may be 6, 4, 3, or 2.
     * @param scen  Game scenario, such as {@link SOCScenario#K_SC_4ISL}, or "";
     *              some scenarios might want special distribution of certain hex types or dice numbers.
     *              Currently recognized here: {@link SOCScenario#K_SC_FTRI}, {@link SOCScenario#K_SC_WOND}.
     * @return  true if able to move all adjacent frequent numbers, false if some are still adjacent.
     * @throws IllegalStateException if a {@link #FOG_HEX} is found
     */
    private final boolean makeNewBoard_placeHexes_moveFrequentNumbers
        (final int[] landPath, ArrayList<Integer> redHexes, final int maxPl, final String scen)
        throws IllegalStateException
    {
        if (redHexes.isEmpty())
            return true;

        // Before anything else, check for frequent gold hexes and
        // swap their numbers with random other hexes in landPath
        // which are less-frequent dice numbers (< 6 or > 8).
        {
            ArrayList<Integer> frequentGold = new ArrayList<Integer>();
            for (Integer hexCoord : redHexes)
                if (getHexTypeFromCoord(hexCoord.intValue()) == GOLD_HEX)
                    frequentGold.add(hexCoord);

            if (! frequentGold.isEmpty())
            {
                int iterRemain = 100;
                for (int hex : frequentGold)
                {
                    int swapHex, diceNum;
                    do {
                        swapHex = landPath[Math.abs(rand.nextInt()) % landPath.length];
                        diceNum = getNumberOnHexFromCoord(swapHex);
                        --iterRemain;
                    } while (((swapHex == hex)
                              || (diceNum == 0) || ((diceNum >= 6) && (diceNum <= 8))
                              || (getHexTypeFromCoord(swapHex) == GOLD_HEX))
                             && (iterRemain > 0));

                    if (iterRemain == 0)
                        break;  // good enough effort

                    int hr = hex >> 8,
                        hc = hex & 0xFF,
                        sr = swapHex >> 8,
                        sc = swapHex & 0xFF;
                    numberLayoutLg[sr][sc] = numberLayoutLg[hr][hc];  // gets 6 or 8
                    numberLayoutLg[hr][hc] = diceNum;  // gets 2, 3, 4, 5, 9, 10, 11, or 12

                    redHexes.remove(Integer.valueOf(hex));
                    redHexes.add(swapHex);
                }
            }
        }

        // Next, check for any hexes that forbid too-frequent dice numbers (only a few scenarios).
        // These will be at most 2 or 3 hexes; try once to move any that are found.
        final HashSet<Integer> moveAnyRedFromHexes
            = makeNewBoard_placeHexes_moveFrequentNumbers_checkSpecialHexes(landPath, redHexes, maxPl, scen);

        // Main part of the method begins here.

        // Overall plan:

        // Make an empty list swappedNums to hold all dice-number swaps, in case we undo them all and retry
        // numOverallRetries = 4
        // Top of retry loop:
        // Make sets otherCoastalHexes, otherHexes: all land hexes in landPath not adjacent to redHexes
        //   which aren't desert or gold (This can be deferred until adjacent redHexes are found)
        //   otherCoastalHexes holds ones at the edge of the board,
        //   which have fewer adjacent land hexes, and thus less chance of
        //   taking up the last available non-adjacent places
        // Loop through redHexes for 3 or more adjacents in a row
        //   but not in a clump (so, middle one has 2 adjacent reds that aren't next to each other)
        // - If the middle one is moved, the other 2 might have no other adjacents
        //   and not need to move anymore; they could then be removed from redHexes
        // - So, do the Swapping Algorithm on that middle one
        //   (updates redHexes, otherCoastalHexes, otherHexes; see below)
        // - If no swap was available, we should undo all and retry.
        //   (see below)
        // If redHexes is empty, we're done.
        //   Return true.
        // Otherwise, loop through redHexes for each remaining hex
        // - If it has no adjacent reds, remove it from redHexes and loop to next
        // - If it has 1 adjacent red, should swap that adjacent instead;
        //   the algorithm will also remove this one from redHexes
        //   because it will have 0 adjacents after the swap
        // - Do the Swapping Algorithm on it
        //   (updates redHexes, otherCoastalHexes, otherHexes; see below)
        // - If no swap was available, we should undo all and retry.
        //   (see below)
        // Now, if redHexes is empty, we're done.
        //   Return true.
        //
        // If we need to undo all and retry:
        // - --numOverallRetries
        // - If numOverallRetries < 0, we've tried enough.
        //   Either the swaps we've done will have to do, or caller will make a new board.
        //   Return false.
        // - Otherwise, go backwards through the list of swappedNums, reversing each swap
        // - Rebuild redHexes from current board contents
        // - Jump back to "Make sets otherCoastalHexes, otherHexes"

        // Swapping Algorithm for dice numbers:
        //   Returns a triple for swap info (old location, swapped location, delta to index numbers),
        //   or nothing if we failed to swap.
        // - If otherCoastalHexes and otherHexes are empty:
        //   Return nothing.
        // - Pick a random hex from otherCoastalHexes if not empty, otherwise from otherHexes
        // - Swap the numbers and build the swap-info to return
        // - Remove new location and its adjacents from otherCoastalHexes or otherHexes
        // - Remove old location from redHexes
        // - Check each of its adjacent non-red lands, to see if each should be added to otherCoastalHexes or otherHexes
        //     because the adjacent no longer has any adjacent reds
        // - Check each of its adjacent reds, to see if the adjacent no longer has adjacent reds
        //     If so, we won't need to move it: remove from redHexes
        // - Return the swap info.

        // Implementation:

        ArrayList<IntTriple> swappedNums = null;

        int numOverallRetries = 4;
        boolean retry = false;
        do
        {
            HashSet<Integer> otherCoastalHexes = null, otherHexes = null;

            // Loop through redHexes for 3 or more adjacents in a row
            //   but not in a clump (so, middle one has 2 adjacent reds that aren't next to each other)
            // - If the middle one is moved, the other 2 might have no other adjacents
            //   and not need to move anymore; they could then be removed from redHexes
            // - So, do the Swapping Algorithm on that middle one

            for (int i = 0; i < redHexes.size(); )
            {
                final int h0 = redHexes.get(i);

                Vector<Integer> ahex = getAdjacentHexesToHex(h0, false);
                if (ahex == null)
                {
                    redHexes.remove(i);
                    continue;  // <--- No adjacent hexes at all: remove ---
                }

                // count and find red adjacent hexes, remove non-reds from ahex
                int adjacRed = 0, hAdjac1 = 0, hAdjacNext = 0;
                for (Iterator<Integer> ahi = ahex.iterator(); ahi.hasNext(); )
                {
                    final int h = ahi.next();
                    final int dnum = getNumberOnHexFromCoord(h);
                    if ((dnum == 6) || (dnum == 8))
                    {
                        ++adjacRed;
                        if (adjacRed == 1)
                            hAdjac1 = h;
                        else
                            hAdjacNext = h;
                    } else {
                        ahi.remove();  // remove from ahex
                    }
                }

                if (adjacRed == 0)
                {
                    redHexes.remove(i);
                    continue;  // <--- No adjacent red hexes: remove ---
                }
                else if (adjacRed == 1)
                {
                    ++i;
                    continue;  // <--- Not in the middle: skip ---
                }

                // Looking for 3 in a row but not in a clump
                // (so, middle one has 2 adjacent reds that aren't next to each other).
                // If the 2+ adjacent reds are next to each other,
                // swapping one will still leave adjacency.

                if ((adjacRed > 3) || isHexAdjacentToHex(hAdjac1, hAdjacNext))
                {
                    ++i;
                    continue;  // <--- Clump, not 3 in a row: skip ---
                }
                else if (adjacRed == 3)
                {
                    // h0 is that middle one only if the 3 adjacent reds
                    // are not adjacent to each other.
                    // We already checked that hAdjac1 and hAdjacNext aren't adjacent,
                    // so check the final one.
                    boolean clump = false;
                    for (int h : ahex)  // ahex.size() == 3
                    {
                        if ((h == hAdjac1) || (h == hAdjacNext))
                            continue;
                        clump = isHexAdjacentToHex(h, hAdjac1) || isHexAdjacentToHex(h, hAdjacNext);
                    }
                    if (clump)
                    {
                        ++i;
                        continue;  // <--- Clump, not 3 in a row: skip ---
                    }
                }

                // h0 is the middle hex.

                // Before swapping, build arrays if we need to.
                if (otherCoastalHexes == null)
                {
                    otherCoastalHexes = new HashSet<Integer>();
                    otherHexes = new HashSet<Integer>();
                    makeNewBoard_placeHexes_moveFrequentNumbers_buildArrays
                        (landPath, redHexes, moveAnyRedFromHexes, otherCoastalHexes, otherHexes, null, null);
                }

                // Now, swap.
                IntTriple midSwap = makeNewBoard_placeHexes_moveFrequentNumbers_swapOne
                    (h0, i, redHexes, moveAnyRedFromHexes, otherCoastalHexes, otherHexes);
                if (midSwap == null)
                {
                    retry = true;
                    break;
                } else {
                    if (swappedNums == null)
                        swappedNums = new ArrayList<IntTriple>();
                    swappedNums.add(midSwap);
                    if (midSwap.c != 0)
                    {
                        // other hexes were removed from redHexes.
                        // Update our index position with the delta
                        i += midSwap.c;  // c is always negative
                        if (i < 0)
                            i = 0;  // if all redHexes were removed, fix i for loop's next iteration test
                    }
                }

            }  // for (each h0 in redHexes)

            // If redHexes is empty, we're done.
            if (redHexes.isEmpty())
                return true;

            if (! retry)
            {
                // Otherwise, loop through redHexes for each remaining hex
                // - If it has no adjacent reds, remove it from redHexes and loop to next
                // - If it has 1 adjacent red, should swap that adjacent instead;
                //   the algorithm will also remove this one from redHexes
                //   because it will have 0 adjacents after the swap
                // - Do the Swapping Algorithm on it
                //   (updates redHexes, otherCoastalHexes, otherHexes)
                // - If no swap was available, we should undo all and retry.

                for (int redIterRemain = 200; (redIterRemain > 0) && ! redHexes.isEmpty(); --redIterRemain)
                {
                    final int h0 = redHexes.get(0);

                    Vector<Integer> ahex = getAdjacentHexesToHex(h0, false);
                    if (ahex == null)
                    {
                        redHexes.remove(0);
                        continue;  // <--- No adjacent hexes at all: remove ---
                    }

                    // count and find red adjacent hexes
                    int adjacRed = 0, hAdjac1 = 0;
                    for ( final int h : ahex )
                    {
                        final int dnum = getNumberOnHexFromCoord(h);
                        if ((dnum == 6) || (dnum == 8))
                        {
                            ++adjacRed;
                            if (adjacRed == 1)
                                hAdjac1 = h;
                        }
                    }

                    if (adjacRed == 0)
                    {
                        redHexes.remove(0);
                        continue;  // <--- No adjacent red hexes: remove ---
                    }

                    // We'll either swap h0, or its single adjacent red hex hAdjac1.

                    // Before swapping, build arrays if we need to.
                    if (otherCoastalHexes == null)
                    {
                        otherCoastalHexes = new HashSet<Integer>();
                        otherHexes = new HashSet<Integer>();
                        makeNewBoard_placeHexes_moveFrequentNumbers_buildArrays
                            (landPath, redHexes, moveAnyRedFromHexes, otherCoastalHexes, otherHexes, null, null);
                    }

                    // Now, swap and remove from redHexes.
                    IntTriple hexnumSwap;
                    if (adjacRed > 1)
                    {
                        hexnumSwap = makeNewBoard_placeHexes_moveFrequentNumbers_swapOne
                            (h0, 0, redHexes, moveAnyRedFromHexes, otherCoastalHexes, otherHexes);
                    } else {
                        // swap the adjacent instead; the algorithm will also remove this one from redHexes
                        // because it will have 0 adjacents after the swap
                        final int iAdjac1 = redHexes.indexOf(Integer.valueOf(hAdjac1));
                        hexnumSwap = makeNewBoard_placeHexes_moveFrequentNumbers_swapOne
                            (hAdjac1, iAdjac1, redHexes, moveAnyRedFromHexes, otherCoastalHexes, otherHexes);
                    }

                    if (hexnumSwap == null)
                    {
                        retry = true;
                        break;
                    } else {
                        if (swappedNums == null)
                            swappedNums = new ArrayList<IntTriple>();
                        swappedNums.add(hexnumSwap);
                    }

                }  // loop while redHexes not empty and redIterRemain > 0

                if (! redHexes.isEmpty())
                {
                    // Didn't make enough progress with random swaps,
                    // so undo and give it another overall try
                    retry = true;
                }
            }

            if (retry)
            {
                // undo all and retry:
                // - --numOverallRetries
                // - If numOverallRetries <= 0, we've tried enough.
                //   Either the swaps we've done will have to do, or caller will make a new board.
                //   Return false.
                // - Otherwise, go backwards through the list of swappedNums, reversing each swap
                // - Rebuild redHexes from current board contents

                --numOverallRetries;
                if (numOverallRetries <= 0)
                {
                    return false;
                }

                final int L = (swappedNums != null) ? swappedNums.size() : 0;
                for (int i = L - 1; i >= 0; --i)
                {
                    final IntTriple swap = swappedNums.get(i);
                    final int rs = swap.b >> 8,
                              cs = swap.b & 0xFF,
                              ro = swap.a >> 8,
                              co = swap.a & 0xFF,
                              ntmp = numberLayoutLg[ro][co];
                    numberLayoutLg[ro][co] = numberLayoutLg[rs][cs];
                    numberLayoutLg[rs][cs] = ntmp;
                }

                redHexes.clear();
                for (int hc : landPath)
                {
                    final int r = hc >> 8,
                              c = hc & 0xFF,
                              diceNum = numberLayoutLg[r][c];
                    if ((diceNum == 6) || (diceNum == 8))
                        redHexes.add(hc);
                }
            }

        } while (retry);

        return true;
    }

    /**
     * Build sets used for {@link #makeNewBoard_placeHexes_moveFrequentNumbers(int[], ArrayList, int, String)}.
     * Together, otherCoastalHexes and otherHexes are all land hexes in landPath not adjacent to redHexes.
     * otherCoastalHexes holds ones at the edge of the board,
     * which have fewer adjacent land hexes, and thus less chance of
     * taking up the last available non-adjacent places.
     * Water, desert, and gold hexes won't be added to either set.
     *
     * @param landPath  Coordinates for each hex being placed; may contain water
     * @param redHexes  Hex coordinates of placed "red" (frequent) dice numbers (6s, 8s)
     * @param ignoreHexes  Hex coordinates to ignore in adjacency checks while building
     *            {@code otherCoastalHexes} and {@code otherHexes} and to leave out of those
     *            sets, or {@code null}. Used only in certain scenarios where any red number
     *            on these hexes will definitely be moved elsewhere.
     * @param otherCoastalHexes  Empty set to build here
     * @param otherHexes         Empty set to build here
     * @param scen  Game scenario, such as {@link SOCScenario#K_SC_4ISL}, or "" or null;
     *            some scenarios might want special distribution of certain hex types or dice numbers.
     *            <BR>
     *            Currently recognized here:
     *            <UL>
     *            <LI> {@link SOCScenario#K_SC_FTRI} - Build a set of hexes from {@code landPath} which have
     *                 a dice number other than 5, 6, 8, or 9, excluding {@code ignoreHexes}
     *            </UL>
     * @param otherHexesForScen  Empty set to build here for some scenarios, see {@code scen} for list and details;
     *            normally this parameter is {@code null}
     * @throws IllegalStateException if a {@link #FOG_HEX} is found
     */
    private final void makeNewBoard_placeHexes_moveFrequentNumbers_buildArrays
        (final int[] landPath, final ArrayList<Integer> redHexes, final HashSet<Integer> ignoreHexes,
         HashSet<Integer> otherCoastalHexes, HashSet<Integer> otherHexes,
         final String scen, final HashSet<Integer> otherHexesForScen)
        throws IllegalStateException
    {
        final boolean buildOtherHexesLessFreq_59 =
            (scen != null) && (otherHexesForScen != null) && scen.equals(SOCScenario.K_SC_FTRI);

        for (int h : landPath)
        {
            // Don't consider water, desert, gold
            {
                final int htype = getHexTypeFromCoord(h);
                if ((htype == WATER_HEX) || (htype == DESERT_HEX) || (htype == GOLD_HEX))
                    continue;
                if (htype == FOG_HEX)
                    // FOG_HEX shouldn't be on the board yet
                    throw new IllegalStateException("Don't call this after placing fog");
            }

            // Don't consider unnumbered or 6s or 8s
            // (check here because some may have already been removed from redHexes)
            final int dnum = getNumberOnHexFromCoord(h);
            if ((dnum <= 0) || (dnum == 6) || (dnum == 8))
                continue;

            // Don't consider ignored hexes
            if ((ignoreHexes != null) && ignoreHexes.contains(Integer.valueOf(h)))
                continue;

            if (((dnum < 5) || (dnum > 9)) && buildOtherHexesLessFreq_59)
                otherHexesForScen.add(Integer.valueOf(h));

            final Vector<Integer> ahex = getAdjacentHexesToHex(h, false);
            boolean hasAdjacentRed = false;
            if (ahex != null)
            {
                for (int ah : ahex)
                {
                    final int adnum = getNumberOnHexFromCoord(ah);
                    if ((adnum == 6) || (adnum == 8))
                    {
                        if ((ignoreHexes != null) && ignoreHexes.contains(Integer.valueOf(ah)))
                            continue;

                        hasAdjacentRed = true;
                        break;
                    }
                }
            }

            if (! hasAdjacentRed)
            {
                final Integer hInt = Integer.valueOf(h);

                if (isHexCoastline(h))
                    otherCoastalHexes.add(hInt);
                else
                    otherHexes.add(hInt);
            }
        }
    }

    /**
     * Check for any hexes that forbid too-frequent dice numbers (only a few scenarios).
     * These will be at most 2 or 3 hexes; try once to move any that are found.
     * These hexes' coordinates, if any, are returned so that later parts of
     * {@link #makeNewBoard_placeHexes_moveFrequentNumbers(int[], ArrayList, int, String)}
     * won't move any red numbers back onto them.
     *
     * @param landPath  Coordinates for each hex being placed; may contain water
     * @param redHexes  Hex coordinates of placed "red" (frequent) dice numbers (6s, 8s);
     *      updated here if anything is moved.
     * @param maxPl  For scenario boards, use 3-player or 4-player or 6-player layout?
     *               Always tests maxPl for ==6 or &lt; 4; actual value may be 6, 4, 3, or 2.
     * @param scen  Game scenario, such as {@link SOCScenario#K_SC_FTRI}, or "";
     *              some scenarios might want special distribution of certain hex types or dice numbers.
     *              Currently recognized here: {@link SOCScenario#K_SC_FTRI}, {@link SOCScenario#K_SC_WOND}.
     * @return  Scenario's set of hexes which shouldn't contain red numbers, or {@code null}
     */
    private HashSet<Integer> makeNewBoard_placeHexes_moveFrequentNumbers_checkSpecialHexes
        (final int[] landPath, ArrayList<Integer> redHexes, final int maxPl, final String scen)
    {
        /** If scenario calls for it, hex coordinates from which to move any "red" dice numbers 6 and 8 */
        final HashSet<Integer> moveAnyRedFromHexes;
        /** If true also move 5s and 9s, not just 6s and 8s */
        final boolean moveAnyRedFromAlso59;
        {
            final int[] moveFrom;
            if (scen.equals(SOCScenario.K_SC_FTRI))
            {
                moveFrom = FOR_TRI_LANDHEX_COORD_MAIN_FAR_COASTAL[(maxPl == 6) ? 1 : 0];
                moveAnyRedFromAlso59 = true;
            } else if (scen.equals(SOCScenario.K_SC_WOND)) {
                moveFrom = WOND_LANDHEX_COORD_MAIN_AT_DESERT[(maxPl == 6) ? 1 : 0];
                moveAnyRedFromAlso59 = false;
            } else {
                moveFrom = null;
                moveAnyRedFromAlso59 = false;
            }

            if (moveFrom != null)
            {
                moveAnyRedFromHexes = new HashSet<Integer>(moveFrom.length);
                for (int i = 0; i < moveFrom.length; ++i)
                    moveAnyRedFromHexes.add(Integer.valueOf(moveFrom[i]));
            } else {
                moveAnyRedFromHexes = null;
            }
        }

        if (moveAnyRedFromHexes == null)
        {
            return null;  // <--- Early return: Scenario has no special hexes to check ---
        }

        HashSet<Integer> hexesToMove = null;

        // Check if any dice numbers there need to be moved
        for (int hc : moveAnyRedFromHexes)
        {
            final int dnum = getNumberOnHexFromCoord(hc);
            if ((dnum == 6) || (dnum == 8)
                || ( moveAnyRedFromAlso59 && ((dnum == 5) || (dnum == 9)) ))
            {
                if (hexesToMove == null)
                    hexesToMove = new HashSet<Integer>();
                hexesToMove.add(Integer.valueOf(hc));
            }
        }

        if (hexesToMove != null)
        {
            // Build set of places we can swap dice numbers to.
            // For _SC_FTRI (moveAnyRedFromAlso59), build otherHexesForScen with possible swap locations.
            HashSet<Integer> otherCoastalHexes = new HashSet<Integer>(), otherHexes = new HashSet<Integer>();
            HashSet<Integer> otherHexesForScen = (moveAnyRedFromAlso59) ? new HashSet<Integer>() : null;
            makeNewBoard_placeHexes_moveFrequentNumbers_buildArrays
                (landPath, redHexes, moveAnyRedFromHexes, otherCoastalHexes, otherHexes, scen, otherHexesForScen);

            // Swap each of hexesToMove with another from otherCoastalHexes or otherHexes (6s and 8s)
            // or landPath (5s and 9s).
            for (int hc : hexesToMove)
            {
                final int dnum = getNumberOnHexFromCoord(hc);

                IntTriple swapped;
                if (moveAnyRedFromAlso59)
                {
                    // Swap a 5, 6, 8, or 9.
                    // After this loop is done, the method checks for clumps of red hexes.
                    // So, we can swap them here with any hex location (otherHexesForScen),
                    // not only otherCostalHexes/otherHexes, to avoid limiting options here.
                    final int rhIdx = ((dnum == 6) || (dnum == 8))
                        ? redHexes.indexOf(Integer.valueOf(hc))
                        : -1;
                    swapped = makeNewBoard_placeHexes_moveFrequentNumbers_swapOne
                        (hc, rhIdx, redHexes, moveAnyRedFromHexes, otherHexesForScen, null);
                } else {
                    // Swap a 6 or 8 with the usual process.
                    swapped = makeNewBoard_placeHexes_moveFrequentNumbers_swapOne
                        (hc, redHexes.indexOf(Integer.valueOf(hc)), redHexes, moveAnyRedFromHexes,
                         otherCoastalHexes, otherHexes);
                }

                if (((dnum == 6) || (dnum == 8)) && (swapped != null))
                    redHexes.add(Integer.valueOf(swapped.b));  // Keep in list for the main rearrangement
            }
        }

        return moveAnyRedFromHexes;
    }

    /**
     * The dice-number swapping algorithm for
     * {@link #makeNewBoard_placeHexes_moveFrequentNumbers(int[], ArrayList, int, String)}.
     * If we can, pick a hex in otherCoastalHexes or otherHexes, swap and remove <tt>swaphex</tt>
     * from redHexes, then remove/add hexes from/to otherCoastalHexes, otherHexes, redHexes.
     * See comments for details.
     *<P>
     * This method's primary use case is un-clumping "red" dice numbers by moving them to other hexes
     * from <tt>otherCoastalHexes</tt> or <tt>otherHexes</tt>, and updating related sets and data structures.
     * It can also swap non-red dice number hexes with random hexes from a single set instead of those two.
     * If calling for that, set <tt>otherHexes</tt> to null; if <tt>swaphex</tt> isn't a member of redHexes,
     * use -1 for <tt>swapi</tt>.  When <tt>otherHexes</tt> is null, the hexes adjacent to <tt>swaphex</tt>
     * will never be added to <tt>otherCoastalHexes</tt>, but the new location of a red number and its adjacent
     * hexes will still be removed from <tt>otherCoastalHexes</tt>.
     *
     * @param swaphex  Hex coordinate with a frequent number that needs to be swapped.
     *                 Must not be a member of <tt>otherCoastalHexes</tt> or <tt>otherHexes</tt>.
     * @param swapi    Index of <tt>swaphex</tt> within <tt>redHexes</tt>;
     *                 this is for convenience because the caller is iterating through <tt>redHexes</tt>,
     *                 and this method might remove elements below <tt>swapi</tt>.
     *                 See return value "index delta".
     *                 If <tt>swaphex</tt> isn't a red number and isn't in <tt>redHexes</tt>, use -1.
     * @param redHexes  Hex coordinates of placed "red" (frequent) dice numbers.
     *                  Treated here as a worklist for the caller: The old location <tt>swapHex</tt> is removed from
     *                  <tt>redHexes</tt>, and its adjacents might also be removed if they no longer have adjacent
     *                  reds and so don't need to be moved.  The new location isn't added to <tt>redHexes</tt>.
     * @param ignoreHexes  Hex coordinates to never add to <tt>otherCoastalHexes</tt> or <tt>otherHexes</tt>,
     *            or <tt>null</tt>. Used only in certain scenarios where any red number or 5 or 9 on these hexes has
     *            been moved elsewhere, and no dice number should be swapped with it. No hex that's a member of
     *            <tt>ignoreHexes</tt> may be a member of <tt>otherCoastalHexes</tt> or <tt>otherHexes</tt> when called.
     * @param otherCoastalHexes  Land hexes not adjacent to "red" numbers, at the edge of the island,
     *          for swapping dice numbers.  Coastal hexes have fewer adjacent land hexes, and
     *          thus less chance of taking up the last available non-adjacent places when swapped.
     *          Should not contain gold, desert, or water hexes.
     *          Swap locations are randomly chosen from this set unless empty.
     * @param otherHexes   Land hexes not adjacent to "red" numbers, not at the edge of the island.
     *          Should not contain gold, desert, or water hexes.  Can be null.
     * @return The old frequent-number hex coordinate, its swapped coordinate, and the "index delta",
     *         or null if nothing was available to swap.
     *         If the "index delta" != 0, a hex at <tt>redHexes[j]</tt> was removed
     *         for at least one <tt>j &lt; swapi</tt>, and the caller's loop iterating over <tt>redHexes[]</tt>
     *         should adjust its index (<tt>swapi</tt>) so no elements are skipped; the delta is always negative.
     */
    private final IntTriple makeNewBoard_placeHexes_moveFrequentNumbers_swapOne
        (final int swaphex, int swapi, ArrayList<Integer> redHexes, final HashSet<Integer> ignoreHexes,
         HashSet<Integer> otherCoastalHexes, HashSet<Integer> otherHexes)
    {
        // - If otherCoastalHexes and otherHexes are empty:
        //   Return nothing.
        if (otherCoastalHexes.isEmpty() && ((otherHexes == null) || otherHexes.isEmpty()))
            return null;

        // - Pick a random hex (ohex) from otherCoastalHexes if not empty, otherwise from otherHexes
        HashSet<Integer> others = otherCoastalHexes.isEmpty() ? otherHexes : otherCoastalHexes;
        final int ohex;
        {
            final int olen = others.size();
            final int oi = (olen == 1) ? 0 : rand.nextInt(olen);

            // Although this is a linear search,
            // we chose to optimize the sets for
            // lookups since those are more frequent.
            int i = 0, h = 0;
            for (int hex : others)
            {
                if (oi == i)
                {
                    h = hex;
                    break;
                }
                ++i;
            }
            ohex = h;
        }

        // - Swap the numbers and build the swap-info to return
        final int swapNum;
        IntTriple triple = new IntTriple(swaphex, ohex, 0);
        {
            final int rs = swaphex >> 8,
                      cs = swaphex & 0xFF,
                      ro = ohex >> 8,
                      co = ohex & 0xFF;
            swapNum = numberLayoutLg[rs][cs];
            numberLayoutLg[rs][cs] = numberLayoutLg[ro][co];
            numberLayoutLg[ro][co] = swapNum;
        }

        // - If old location wasn't a red dice# hex, work is done
        if ((swapNum != 6) && (swapNum != 8))
        {
            return triple;  // <---- Early return: Wasn't a red number hex ----
        }

        // - Remove new location and its adjacents from otherCoastalHexes or otherHexes
        others.remove(ohex);
        Vector<Integer> ahex = getAdjacentHexesToHex(ohex, false);
        if (ahex != null)
        {
            for (int h : ahex)
            {
                if (otherCoastalHexes.contains(h))
                    otherCoastalHexes.remove(h);
                if ((otherHexes != null) && otherHexes.contains(h))
                    otherHexes.remove(h);
            }
        }

        // - Remove old location from redHexes
        // - Check each of its adjacent non-red lands, to see if each should be added to otherCoastalHexes or otherHexes
        //     because the adjacent no longer has any adjacent reds
        // - Check each of its adjacent reds, to see if the adjacent no longer has adjacent reds
        //     If so, we won't need to move it: remove that adjacent from redHexes
        redHexes.remove(Integer.valueOf(swaphex));
        ahex = getAdjacentHexesToHex(swaphex, false);
        if (ahex != null)
        {
            int idelta = 0;  // ahex loop does --idelta for each hex removed from redHexes

            for (int h : ahex)
            {
                final int dnum = getNumberOnHexFromCoord(h);
                final boolean hexIsRed = (dnum == 6) || (dnum == 8);
                boolean hasAdjacentRed = false;
                Vector<Integer> aahex = getAdjacentHexesToHex(h, false);
                if (aahex != null)
                {
                    // adjacents to swaphex's adjacent
                    for (int aah : aahex)
                    {
                        final int aanum = getNumberOnHexFromCoord(aah);
                        if ((aanum == 6) || (aanum == 8))
                        {
                            hasAdjacentRed = true;
                            break;
                        }
                    }
                }

                if (! hasAdjacentRed)
                {
                    final Integer hInt = Integer.valueOf(h);

                    if (hexIsRed)
                    {
                        // no longer has any adjacent reds; we won't need to move it
                        // Remove from redHexes
                        int j = redHexes.indexOf(hInt);
                        if (j != -1)  // if redHexes.contains(h)
                        {
                            redHexes.remove(j);
                            if ((j < swapi) && (swapi != -1))
                            {
                                --idelta;
                                --swapi;  // to match redHexes change, for this block in later iterations
                                triple.c = idelta;
                            }
                        }
                    } else {
                        // no longer has any adjacent reds; can add to otherCoastalHexes or otherHexes
                        if ((otherHexes != null) && ((ignoreHexes == null) || ! ignoreHexes.contains(hInt)))
                        {
                            if (isHexCoastline(h))
                            {
                                if (! otherCoastalHexes.contains(hInt))
                                    otherCoastalHexes.add(hInt);
                            } else {
                                if (! otherHexes.contains(hInt))
                                    otherHexes.add(hInt);
                            }
                        }
                    }
                }
            }
        }

        // - Return the dice-number swap info.
        return triple;
    }

    /**
     * For {@link #makeNewBoard(Map)}, check port locations and facings, and make sure
     * no port overlaps with a land hex.  Each port's edge coordinate has 2 valid perpendicular
     * facing directions, and ports should be on a land/water edge, facing the land side.
     * Call this method after placing all land hexes.
     * @param portsLocFacing  Array of port location edges and "port facing" directions
     *            ({@link SOCBoard#FACING_NE FACING_NE} = 1, etc), such as {@link #PORT_EDGE_FACING_MAINLAND_4PL}.
     *            Each port has 2 consecutive elements: Edge coordinate (0xRRCC), Port Facing (towards land).
     * @throws IllegalArgumentException  If a port's facing direction isn't possible,
     *            or its location causes its water portion to "overlap" land.
     *            Stops with the first error, doesn't keep checking other ports afterwards.
     *            The detail string will be something like:<BR>
     *            Inconsistent layout: Port at index 2 edge 0x803 covers up land hex 0x703 <BR>
     *            Inconsistent layout: Port at index 2 edge 0x803 faces water, not land, hex 0x904 <BR>
     *            Inconsistent layout: Port at index 2 edge 0x802 facing should be NE or SW, not 3
     */
    private final void makeNewBoard_checkPortLocationsConsistent
        (final int[] portsLocFacing)
        throws IllegalArgumentException
    {
        String err = null;

        int i = 0;
        while (i < portsLocFacing.length)
        {
            final int portEdge = portsLocFacing[i++];
            int portFacing = portsLocFacing[i++];

            // make sure port facing direction makes sense for this type of edge
            // similar to code in SOCBoardLarge.getPortFacingFromEdge, with more specific error messages
            {
                final int r = (portEdge >> 8),
                          c = (portEdge & 0xFF);

                // "|" if r is odd
                if ((r % 2) == 1)
                {
                    if ((portFacing != FACING_E) && (portFacing != FACING_W))
                        err = " facing should be E or W";
                }

                // "/" if (r/2,c) is even,odd or odd,even
                else if ((c % 2) != ((r/2) % 2))
                {
                    if ((portFacing != FACING_NW) && (portFacing != FACING_SE))
                        err = " facing should be NW or SE";
                }
                else
                {
                    // "\" if (r/2,c) is odd,odd or even,even
                    if ((portFacing != FACING_NE) && (portFacing != FACING_SW))
                        err = " facing should be NE or SW";
                }

                if (err != null)
                    err += ", not " + portFacing;
            }

            // check edge's land hex in Port Facing direction
            int hex = getAdjacentHexToEdge(portEdge, portFacing);
            if ((err == null) && ((hex == 0) || (getHexTypeFromCoord(hex) == WATER_HEX)))
                err = " faces water, not land, hex 0x" + Integer.toHexString(hex);

            // facing + 3 rotates to "sea" direction from the port's edge
            portFacing += 3;
            if (portFacing > 6)
                portFacing -= 6;
            hex = getAdjacentHexToEdge(portEdge, portFacing);
            if ((err == null) && ((hex != 0) && (getHexTypeFromCoord(hex) != WATER_HEX)))
                  err = " covers up land hex 0x" + Integer.toHexString(hex);

            if (err != null)
                throw new IllegalArgumentException
                  ("Inconsistent layout: Port at index " + (i-2) + " edge 0x" + Integer.toHexString(portEdge) + err);
        }
    }

    /**
     * Calculate the board's legal settlement/city nodes, based on land hexes.
     * All corners of these hexes are legal for settlements/cities.
     * Called from
     * {@link #makeNewBoard_placeHexes(int[], int[], int[], boolean, boolean, int[], boolean, int, SOCGameOption, String, Map)}.
     * Can use all or part of a <tt>landHexCoords</tt> array.
     *<P>
     * Iterative: Can call multiple times, giving different hexes each time.
     * Each call will add those hexes to {@link #nodesOnLand}.
     * If <tt>landAreaNumber</tt> != 0, also adds them to {@link #landAreasLegalNodes}.
     * Call this method once for each land area.
     *<P>
     * Before the first call, clear <tt>nodesOnLand</tt>.
     *<P>
     * If scenario requires some nodes to be removed from legal placement, after the last call to this method
     * call {@link #makeNewBoard_removeLegalNodes(int[], int, int, boolean)}.
     *
     * @param landHexCoords  Coordinates of a contiguous group of land hexes.
     *                    If <tt>startIdx</tt> and <tt>pastEndIdx</tt> partially use this array,
     *                    only that part needs to be contiguous, the rest of <tt>landHexCoords</tt> is ignored.
     *                    Any hex coordinates here which are {@link #WATER_HEX} are ignored.
     * @param startIdx    First index to use within <tt>landHexCoords[]</tt>; 0 if using the entire array
     * @param pastEndIdx  Just past the last index to use within <tt>landHexCoords[]</tt>;
     *                    If this call uses landHexCoords up through its end, this is <tt>landHexCoords.length</tt>
     * @param landAreaNumber  0 unless there will be more than 1 Land Area (groups of islands).
     *                    If != 0, updates {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt>
     *                    with the same nodes added to {@link SOCBoard#nodesOnLand}.
     * @param addToExistingLA  True if {@code landAreaNumber} already has hexes and nodes, and {@code landHexCoords[]}
     *                    contents should be added to it.  Ignored if {@code landAreaNumber == 0}.
     *                    Otherwise this should always be {@code false}.
     * @see SOCBoardLarge#initLegalRoadsFromLandNodes()
     * @see SOCBoardLarge#initLegalShipEdges()
     * @throws IllegalStateException  if <tt>landAreaNumber</tt> != 0 and either
     *             {@link #landAreasLegalNodes} == null, or not long enough, or
     *             {@link #landAreasLegalNodes}<tt>[landAreaNumber]</tt> != null
     */
    private void makeNewBoard_fillNodesOnLandFromHexes
        (final int landHexCoords[], final int startIdx, final int pastEndIdx,
         final int landAreaNumber, final boolean addToExistingLA)
        throws IllegalStateException
    {
        if (landAreaNumber != 0)
        {
            if ((landAreasLegalNodes == null)
                || (landAreaNumber >= landAreasLegalNodes.length))
                throw new IllegalStateException("landarea " + landAreaNumber + " out of range");

            if (landAreasLegalNodes[landAreaNumber] != null)
            {
                if (! addToExistingLA)
                    throw new IllegalStateException("landarea " + landAreaNumber + " already has landAreasLegalNodes");
            } else {
                landAreasLegalNodes[landAreaNumber] = new HashSet<Integer>();
            }
        }

        for (int i = startIdx; i < pastEndIdx; ++i)
        {
            final int hex = landHexCoords[i];
            if (getHexTypeFromCoord(hex) == WATER_HEX)
                continue;

            for (Integer ni : getAdjacentNodesToHex(hex))
            {
                nodesOnLand.add(ni);
                if (landAreaNumber != 0)
                    landAreasLegalNodes[landAreaNumber].add(ni);
                        // it's ok to add ni even if set already contains an Integer equal to it
            }
        }

    }

    /**
     * For {@link #makeNewBoard(Map)}, remove some nodes from legal/potential initial placement
     * locations.  Does not remove from {@link SOCBoard#nodesOnLand nodesOnLand}.
     * Used in some scenarios ({@link SOCScenario#K_SC_WOND _SC_WOND}) after the last call to
     * {@link #makeNewBoard_placeHexes(int[], int[], int[], boolean, boolean, int, boolean, int, SOCGameOption, String, Map)}.
     *<P>
     * To re-add nodes after initial placement, call {@link SOCBoardLarge#addLegalNodes(int[], int)}.
     * This is done automatically by {@link SOCGame#updateAtGameFirstTurn()} if the nodes are
     * in lists referenced from Added Layout Part {@code "AL"} (see parameter {@code addNodeListNumber}).
     *
     * @param nodeCoords  Nodes to remove from {@link SOCBoardLarge#landAreasLegalNodes landAreasLegalNodes}
     *     [{@code landAreaNumber}] and {@link SOCBoardLarge#getLegalSettlements()}
     * @param landAreaNumber  Land Area to remove nodes from.  If this is
     *     {@link SOCBoardLarge#startingLandArea startingLandArea},
     *     will also remove the nodes from potential initial settlement locations.
     * @param addNodeListNumber  If != 0, these nodes will be re-added to legal locations after initial placement.
     *     Adds this node list number to Added Layout Part {@code "AL"} and calls
     *     {@link #setAddedLayoutPart(String, int[]) setAddedLayoutPart("N" + addNodeListNumber, nodeCoords)}
     *     to add a Layout Part such as {@code "N1"}, {@code "N2"}, etc.
     *     For details see "AL" in the "Added Layout Parts" section of
     *     {@link SOCBoardLarge#getAddedLayoutPart(String)}'s javadoc.
     * @param emptyPartAfterInitPlace  If true, the Added Layout Part ({@code "N1"}, {@code "N2"}, etc) is used only
     *     during initial placement, and its contents should be emptied after that
     * @throws IllegalArgumentException if {@code landAreaNumber} &lt;= 0 or {@code addNodeListNumber} &lt; 0
     */
    private final void makeNewBoard_removeLegalNodes
        (final int[] nodeCoords, final int landAreaNumber,
         final int addNodeListNumber, final boolean emptyPartAfterInitPlace)
        throws IllegalArgumentException
    {
        if (landAreaNumber <= 0)
            throw new IllegalArgumentException("landAreaNumber: " + landAreaNumber);
        if (addNodeListNumber < 0)
            throw new IllegalArgumentException("addNodeListNumber: " + addNodeListNumber);

        final HashSet<Integer> legals = landAreasLegalNodes[landAreaNumber];

        for (final int node : nodeCoords)
            legals.remove(Integer.valueOf(node));

        if (addNodeListNumber != 0)
        {
            setAddedLayoutPart("N" + addNodeListNumber, nodeCoords);

            // create or append to Part "AL"
            int L;
            int[] partAL = getAddedLayoutPart("AL");
            if (partAL == null)
            {
                L = 0;
                partAL = new int[2];
            } else {
                L = partAL.length;
                int[] newAL = new int[L + 2];
                System.arraycopy(partAL, 0, newAL, 0, L);
                partAL = newAL;
            }

            partAL[L] = addNodeListNumber;
            partAL[L + 1] = (emptyPartAfterInitPlace) ? -landAreaNumber : landAreaNumber;
            setAddedLayoutPart("AL", partAL);
        }
    }

    /**
     * For {@link #makeNewBoard(Map)}, hide these hexes under {@link #FOG_HEX} to be revealed later.
     * The hexes will be stored in {@link #fogHiddenHexes}; their {@link #hexLayoutLg} and {@link #numberLayoutLg}
     * elements will be set to {@link #FOG_HEX} and 0.
     *<P>
     * After hexes are hidden, calls the {@link NewBoardProgressListener} if one is registered.
     *<P>
     * Does not remove anything from {@link #nodesOnLand} or {@link #landAreasLegalNodes}.
     * To prevent leaking information about the hex being hidden if it's a {@link #WATER_HEX} which is in
     * {@link #landHexLayout}, adds all its non-coastal nodes and edges to {@code nodesOnLand},
     * {@code landAreasLegalNodes}, and {@link #legalRoadEdges} as if it was a land hex.
     * (Ignores Added Layout Part "AL".) When revealed later during game play, {@link #revealFogHiddenHex(int)}
     * will remove those nodes/edges.
     *<P>
     * To simplify the bot, client, and network, hexes can be hidden only during makeNewBoard,
     * before the board layout is made and sent to the client.
     *
     * @param hexCoords  Coordinates of each hex to hide in the fog. Any elements with value 0 are ignored.
     * @throws IllegalStateException  if any hexCoord is already {@link #FOG_HEX} within {@link #hexLayoutLg}
     * @see #revealFogHiddenHexPrep(int)
     */
    protected void makeNewBoard_hideHexesInFog(final int[] hexCoords)
        throws IllegalStateException
    {
        for (int i = 0; i < hexCoords.length; ++i)
        {
            final int hexCoord = hexCoords[i];
            if (hexCoord == 0)
                continue;
            final int r = hexCoord >> 8,
                      c = hexCoord & 0xFF;
            final int hex = hexLayoutLg[r][c];
            if (hex == FOG_HEX)
                throw new IllegalStateException("Already fog: 0x" + Integer.toHexString(hexCoord));

            fogHiddenHexes.put(Integer.valueOf(hexCoord), (hex << 8) | (numberLayoutLg[r][c] & 0xFF));
            hexLayoutLg[r][c] = FOG_HEX;
            numberLayoutLg[r][c] = 0;

            if ((hex == WATER_HEX) && landHexLayout.contains(hexCoord))
            {
                legalRoadEdges.addAll(getAdjacentEdgesToHex(hexCoord));

                final List<Integer> cornerNodes = getAdjacentNodesToHex(hexCoord);
                nodesOnLand.addAll(cornerNodes);
                if (landAreasLegalNodes != null)
                {
                    // We don't store hexes' Land Area numbers, but one of its corners is
                    // likely already part of an Area; use that Area number for the missing ones.

                    int lan = 0;
                    for (final int node : cornerNodes)
                    {
                        lan = getNodeLandArea(node);
                        if (lan != 0)
                            break;
                    }
                    if ((lan != 0) && (landAreasLegalNodes[lan] != null))
                        landAreasLegalNodes[lan].addAll(cornerNodes);
                }
            }
        }

        if (newBoardProgressListener != null)
            newBoardProgressListener.boardProgress(this, null, NewBoardProgressListener.FOG_HIDE_HEXES);
    }

    /**
     * Get the board size for
     * {@link BoardFactoryAtServer#createBoard(Map, boolean, int) BoardFactoryAtServer.createBoard}:
     * The default size {@link SOCBoardLarge#BOARDHEIGHT_LARGE BOARDHEIGHT_LARGE} by
     * {@link SOCBoardLarge#BOARDWIDTH_LARGE BOARDWIDTH_LARGE},
     * unless <tt>gameOpts</tt> contains a scenario (<tt>"SC"</tt>) whose layout has a custom height/width.
     * The fallback 6-player layout size is taller,
     * {@link SOCBoardLarge#BOARDHEIGHT_LARGE BOARDHEIGHT_LARGE} + 3 by {@link SOCBoardLarge#BOARDWIDTH_LARGE BOARDWIDTH_LARGE}.
     * @param gameOpts  Game options, or null
     * @param maxPlayers  Maximum players; must be 4 or 6 (from game option {@code "PL"} &gt; 4 or {@code "PLB"}).
     *     If {@code maxPlayers} == 4 and {@code gameOpts} contains {@code "PL"},
     *     that overrides {@code maxPlayers} using the same logic as in {@link #makeNewBoard(Map)}.
     * @return encoded size (0xRRCC), the same format as game option {@code "_BHW"}
     * @see SOCBoardLarge#getBoardSize(Map, int)
     */
    private static int getBoardSize(final Map<String, SOCGameOption> gameOpts, final int maxPlayers)
    {
        int heightWidth = 0;

        // Always test maxPl for ==6 or < 4 ; actual value may be 6, 4, 3, or 2.
        // Same maxPl initialization as in getBoardShift(opts) and makeNewBoard(..).
        final int maxPl;
        SOCGameOption opt = (gameOpts != null ? gameOpts.get("PL") : null);
        if ((opt == null) || (maxPlayers == 6))
            maxPl = maxPlayers;
        else if (opt.getIntValue() > 4)
            maxPl = 6;
        else
            maxPl = opt.getIntValue();

        SOCGameOption scOpt = null;
        if (gameOpts != null)
            scOpt = gameOpts.get("SC");

        if (scOpt != null)
        {
            // Check scenario name; not all scenarios have a custom board size.

            final String sc = scOpt.getStringValue();
            if (sc.equals(SOCScenario.K_SC_4ISL))
            {
                heightWidth = FOUR_ISL_BOARDSIZE[(maxPl == 6) ? 1 : 0];  // 3, 4-player boards have same board size
            }
            else if (sc.equals(SOCScenario.K_SC_FOG))
            {
                heightWidth = FOG_ISL_BOARDSIZE[(maxPl == 6) ? 1 : 0];  // 3, 4-player boards have same board size
            }
            else if (sc.equals(SOCScenario.K_SC_TTD))
            {
                heightWidth = TTDESERT_BOARDSIZE[(maxPl == 6) ? 2 : (maxPl == 4) ? 1 : 0];
            }
            else if (sc.equals(SOCScenario.K_SC_PIRI))
            {
                heightWidth = PIR_ISL_BOARDSIZE[(maxPl == 6) ? 1 : 0];
            }
            else if (sc.equals(SOCScenario.K_SC_FTRI))
            {
                heightWidth = FOR_TRI_BOARDSIZE[(maxPl == 6) ? 1 : 0];
            }
            else if (sc.equals(SOCScenario.K_SC_CLVI))
            {
                heightWidth = CLVI_BOARDSIZE[(maxPl == 6) ? 1 : 0];
            }
            else if (sc.equals(SOCScenario.K_SC_WOND))
            {
                heightWidth = WOND_BOARDSIZE[(maxPl == 6) ? 1 : 0];
            }
            else if (sc.equals(SOCScenario.K_SC_NSHO))
            {
                heightWidth = NSHO_BOARDSIZE[(maxPl == 6) ? 2 : (maxPl == 4) ? 1 : 0];
            }
        }

        if (heightWidth == 0)
        {
            // No recognized scenario, so use the fallback board.
            heightWidth = FALLBACK_BOARDSIZE[(maxPl == 6) ? 1 : 0];
        }

        return heightWidth;
    }

    /**
     * Given max players and scenario from {@code gameOpts},
     * get this board's Visual Shift amount if any (layout part "VS").
     * See {@link #getAddedLayoutPart(String)} javadoc for details on "VS".
     * @param gameOpts  Game options, or null.
     *     Looks for {@code "PL"} for max players and {@code "SC"} for scenario name key.
     * @return array with vsDown, vsRight, or {@code null}
     */
    private static int[] getBoardShift
        (final Map<String, SOCGameOption> gameOpts)
    {
        SOCGameOption opt;

        // Use 3-player or 4-player or 6-player layout?
        // Always test maxPl for ==6 or < 4 ; actual value may be 6, 4, 3, or 2.
        // Same maxPl initialization as in makeNewBoard(opts).
        final int maxPl;
        opt = (gameOpts != null ? gameOpts.get("PLB") : null);
        if ((opt != null) && opt.getBoolValue())
        {
            maxPl = 6;
        } else {
            opt = (gameOpts != null ? gameOpts.get("PL") : null);
            if (opt == null)
                maxPl = 4;
            else if (opt.getIntValue() > 4)
                maxPl = 6;
            else
                maxPl = opt.getIntValue();
        }

        final String scen;  // scenario key, such as SOCScenario.K_SC_4ISL, or empty string
        opt = (gameOpts != null ? gameOpts.get("SC") : null);
        if (opt != null)
        {
            final String ostr = opt.getStringValue();
            scen = (ostr != null) ? ostr : "";
        } else {
            scen = "";
        }

        // when 1 choice (same VS for all player count): return it directly.
        // otherwise set boardVS and choose below based on maxPl.

        int[][] boardVS = null;

        if (scen.length() == 0)
            boardVS = FALLBACK_VIS_SHIFT;
        else if (scen.equals(SOCScenario.K_SC_NSHO))
            boardVS = NSHO_VIS_SHIFT;
        else if (scen.equals(SOCScenario.K_SC_4ISL))
            return FOUR_ISL_VIS_SHIFT;
        else if (scen.equals(SOCScenario.K_SC_PIRI))
            return PIR_ISL_VIS_SHIFT;
        else if (scen.equals(SOCScenario.K_SC_TTD))
            return TTDESERT_VIS_SHIFT;
        else if (scen.equals(SOCScenario.K_SC_FTRI))
            boardVS = FOR_TRI_VIS_SHIFT;
        else if (scen.equals(SOCScenario.K_SC_CLVI))
            boardVS = CLVI_VIS_SHIFT;
        else if (scen.equals(SOCScenario.K_SC_WOND))
            boardVS = WOND_VIS_SHIFT;
        else if (gameOpts != null)
        {
            opt = gameOpts.get(SOCGameOption.K_SC_FOG);
            if ((opt != null) && opt.getBoolValue())
                boardVS = FOG_ISL_VIS_SHIFT;
        }

        if (boardVS == null)
            return null;

        final int idx =
            (boardVS.length == 2)
            ? ((maxPl > 4) ? 1 : 0)  // 6-player or 4-player board layout
            : ((maxPl > 4) ? 2 : (maxPl > 3) ? 1 : 0);  // 6-player, 4, or 3-player board layout

        return boardVS[idx];  // may return null
    }

    /**
     * For scenario game option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * get the list of Legal Sea Edges arranged for the players not vacant.
     * Arranged in same player order as the Lone Settlement locations in Added Layout Part {@code "LS"}.
     *
     * @param ga  Game data, for {@link SOCGame#maxPlayers} and {@link SOCGame#isSeatVacant(int)}
     * @param forPN  -1 for all players, or a specific player number
     * @return  Edge data from {@link #PIR_ISL_SEA_EDGES}, containing either
     *          one array when {@code forPN} != -1, or an array for each
     *          player from 0 to {@code ga.maxPlayers}, where vacant players
     *          get empty subarrays of length 0.
     *          <P>
     *          Each player's list is their individual edge coordinates and/or ranges.
     *          Ranges are designated by a pair of positive,negative numbers: 0xC04, -0xC0D
     *          is a range of the valid edges from C04 through C0D inclusive.
     *          <P>
     *          If game doesn't have {@link SOCGameOption#K_SC_PIRI}, returns {@code null}.
     * @see #startGame_putInitPieces(SOCGame)
     */
    public static final int[][] getLegalSeaEdges(final SOCGame ga, final int forPN)
    {
        if (! (ga.hasSeaBoard && ga.isGameOptionSet(SOCGameOption.K_SC_PIRI)))
            return null;

        final int[][] LEGAL_SEA_EDGES = PIR_ISL_SEA_EDGES[(ga.maxPlayers > 4) ? 1 : 0];
        if (forPN != -1)
        {
            final int[][] lse = { LEGAL_SEA_EDGES[forPN] };
            return lse;
        }

        final int[][] lseArranged = new int[ga.maxPlayers][];

        int i = 0;  // iterate i only when player present, to avoid spacing gaps from vacant players
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            if (ga.isSeatVacant(pn))
            {
                lseArranged[pn] = new int[0];
            } else {
                lseArranged[pn] = LEGAL_SEA_EDGES[i];
                ++i;
            }
        }

        return lseArranged;
    }

    /**
     * For a game that's starting, now that the board layout is known,
     * do any extra setup required by certain scenarios.
     *<P>
     * If ! {@link SOCGame#hasSeaBoard ga.hasSeaBoard}, does nothing and returns {@code null}.
     *<P>
     * In this order:
     *<UL>
     * <LI> Calls {@link #getLegalSeaEdges(SOCGame, int) getLegalSeaEdges(ga, -1)}:
     *      In scenario {@link SOCGameOption#K_SC_PIRI _SC_PIRI}, that will return non-{@code null} because
     *      ship placement is restricted. If so, calls each player's {@link SOCPlayer#setRestrictedLegalShips(int[])}.
     * <LI> Calls {@link #startGame_putInitPieces(SOCGame)}:
     *      Used in {@link SOCGameOption#K_SC_PIRI _SC_PIRI} and {@link SOCGameOption#K_SC_FTRI _SC_FTRI}.
     *</UL>
     *
     * @param ga  Game to set up; assumes {@link SOCGame#startGame()} has just been called
     * @return  this board layout's {@link #getLegalSeaEdges(SOCGame, int) getLegalSeaEdges(ga, -1)}
     *     if placement is restricted, or {@code null}
     */
    public static int[][] startGame_scenarioSetup(final SOCGame ga)
    {
        if (! ga.hasSeaBoard)
            return null;  // just in case; such a game shouldn't be using this class anyway

        final int[][] legalSeaEdges;  // used on sea board; if null, all are legal
        legalSeaEdges = getLegalSeaEdges(ga, -1);
        if (legalSeaEdges != null)
            for (int pn = 0; pn < ga.maxPlayers; ++pn)
                ga.getPlayer(pn).setRestrictedLegalShips(legalSeaEdges[pn]);

        if (ga.isGameOptionSet(SOCGameOption.K_SC_FTRI) || ga.isGameOptionSet(SOCGameOption.K_SC_PIRI))
        {
            // scenario has initial pieces
            ((SOCBoardAtServer) (ga.getBoard())).startGame_putInitPieces(ga);
        }

        return legalSeaEdges;
    }

    /**
     * For scenario game option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * place each player's initial pieces.  For {@link SOCGameOption#K_SC_FTRI _SC_FTRI},
     * set aside some dev cards to be claimed later at Special Edges.
     * Otherwise do nothing.
     *<P>
     * For {@code _SC_PIRI}, also calls each player's {@link SOCPlayer#addLegalSettlement(int, boolean)}
     * for their Lone Settlement location (adds layout part "LS").
     * Vacant player numbers get 0 for their {@code "LS"} element.
     *<P>
     * Called only at server. For a method called during game start
     * at server and clients, see {@link SOCGame#updateAtBoardLayout()}.
     *<P>
     * Called from {@link #startGame_scenarioSetup(SOCGame)} for those
     * scenario game options; if you need it called for your game, add
     * a check there for your scenario's {@link SOCGameOption}.
     *<P>
     * This is called after {@link #makeNewBoard(Map)} and before
     * {@link SOCGameHandler#getBoardLayoutMessage}.  So if needed,
     * it can call {@link SOCBoardLarge#setAddedLayoutPart(String, int[])}.
     *<P>
     * If ship placement is restricted by the scenario, please call each player's
     * {@link SOCPlayer#setRestrictedLegalShips(int[])} before calling this method,
     * so the legal and potential arrays will be initialized.
     *
     * @see #getLegalSeaEdges(SOCGame, int)
     */
    public void startGame_putInitPieces(SOCGame ga)
    {
        if (ga.isGameOptionSet(SOCGameOption.K_SC_FTRI))
        {
            // Set aside dev cards for players to be given when reaching "CE" Special Edges.

            final int cpn = ga.getCurrentPlayerNumber();
            ga.setCurrentPlayerNumber(-1);  // to call buyDevCard without giving it to a player

            drawStack = new Stack<Integer>();
            final int n = FOR_TRI_DEV_CARD_EDGES[(ga.maxPlayers > 4) ? 1 : 0].length;
            for (int i = 0; i < n; ++i)
                drawStack.push(ga.buyDevCard());

            ga.setCurrentPlayerNumber(cpn);
            return;
        }

        if (! ga.isGameOptionSet(SOCGameOption.K_SC_PIRI))
            return;

        final int gstate = ga.getGameState();
        ga.setGameState(SOCGame.READY);  // prevent ga.putPiece from advancing turn

        final int[] inits = PIR_ISL_INIT_PIECES[(ga.maxPlayers > 4) ? 1 : 0];
        int[] possiLoneSettles = new int[ga.maxPlayers];  // lone possible-settlement node on the way to the island.
            // vacant players will get 0 here, will not get free settlement, ship, or pirate fortress.

        int i = 0;  // iterate i only when player present, to avoid spacing gaps from vacant players
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            if (ga.isSeatVacant(pn))
                continue;

            SOCPlayer pl = ga.getPlayer(pn);
            ga.putPiece(new SOCSettlement(pl, inits[i], this));  ++i;
            ga.putPiece(new SOCShip(pl, inits[i], this));  ++i;
            ga.putPiece(new SOCFortress(pl, inits[i], this));  ++i;
            possiLoneSettles[pn] = inits[i];  ga.getPlayer(pn).addLegalSettlement(inits[i], false);  ++i;
        }
        setAddedLayoutPart("LS", possiLoneSettles);

        ga.setGameState(gstate);
    }


    ////////////////////////////////////////////
    //
    // Sample Layout: Sea Board fallback,
    // for game opt "SBL" if no scenario chosen
    //

    /**
     * Fallback sea board layout board size:
     * 4 players max 0x11, 0x13.
     * 6 players max 0x13, 0x13.
     */
    private static final int FALLBACK_BOARDSIZE[] = { 0x1113, 0x1313 };

    /** Fallback sea board layout: Visual Shift ("VS") */
    private static final int FALLBACK_VIS_SHIFT[][] = { {2,1}, {2,2} };

    /**
     * Fallback board layout for 4 players: Main island's ports, clockwise from its northwest.
     * Each port has 2 consecutive elements.
     * First: Port edge coordinate, in hex: 0xRRCC.
     * Second: Port Facing direction: {@link SOCBoard#FACING_E FACING_E}, etc.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     */
    private static final int PORT_EDGE_FACING_MAINLAND_4PL[] =
    {
        0x0003, FACING_SE,  0x0006, FACING_SW,
        0x0209, FACING_SW,  0x050B, FACING_W,
        0x0809, FACING_NW,  0x0A06, FACING_NW,
        0x0A03, FACING_NE,  0x0702, FACING_E,
        0x0302, FACING_E
    };

    /**
     * Fallback board layout, 4 players: Outlying islands' ports.
     * Each port has 2 elements.
     * First: Coordinate, in hex: 0xRRCC.
     * Second: Facing
     */
    private static final int PORT_EDGE_FACING_ISLANDS_4PL[] =
    {
        0x060E, FACING_NW,   // - northeast island
        0x0A0F, FACING_SW,  0x0E0C, FACING_NW,        // - southeast island
        0x0E06, FACING_SE    // - southwest island
    };

    /**
     * Port types for the 4 outlying-island ports on the 4-player fallback board.
     * For the mainland's port types, use {@link SOCBoard4p#PORTS_TYPE_V1}.
     */
    private static final int PORT_TYPE_ISLANDS_4PL[] =
    {
        MISC_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT
    };

    /**
     * Fallback board layout for 4 players: Dice-number path (hex coordinates)
     * on the main island, spiraling inward from the shore.
     * The outlying islands have no dice path.
     * For the mainland's dice numbers, see {@link SOCBoard4p#makeNewBoard_diceNums_v1}.
     */
    private static final int LANDHEX_DICEPATH_MAINLAND_4PL[] =
    {
        // clockwise from northwest
        0x0104, 0x0106, 0x0108, 0x0309, 0x050A,
        0x0709, 0x0908, 0x0906, 0x0904, 0x0703,
        0x0502, 0x0303, 0x0305, 0x0307, 0x0508,
        0x0707, 0x0705, 0x0504, 0x0506
    };

    /**
     * Fallback board layout, 4 players: All the outlying islands' land hex coordinates.
     *<P>
     * The first outlying island (land area 2) is upper-right on board.
     * Second island (landarea 3) is lower-right.
     * Third island (landarea 4) is lower-left.
     * @see #LANDHEX_COORD_ISLANDS_EACH
     * @see #LANDHEX_LANDAREA_RANGES_ISLANDS_4PL
     */
    private static final int LANDHEX_COORD_ISLANDS_ALL_4PL[] =
    {
        0x010E, 0x030D, 0x030F, 0x050E, 0x0510,
        0x0B0D, 0x0B0F, 0x0B11, 0x0D0C, 0x0D0E,
        0x0D02, 0x0D04, 0x0F05, 0x0F07
    };

    /**
     * Fallback board layout for 4 players: Each outlying island's land hex coordinates.
     * @see #LANDHEX_COORD_ISLANDS_ALL_4PL
     * @see #LANDHEX_LANDAREA_RANGES_ISLANDS_4PL
     */
    @SuppressWarnings("unused")  // TODO is this field useful to keep for reference?
    private static final int LANDHEX_COORD_ISLANDS_EACH[][] =
    {
        { 0x010E, 0x030D, 0x030F, 0x050E, 0x0510 },
        { 0x0B0D, 0x0B0F, 0x0B11, 0x0D0C, 0x0D0E },
        { 0x0D02, 0x0D04, 0x0F05, 0x0F07 }
    };

    /**
     * 4-player island hex counts and land area numbers within {@link #LANDHEX_COORD_ISLANDS_ALL_4PL}.
     * Allows us to shuffle them all together ({@link #LANDHEX_TYPE_ISLANDS_4PL}).
     * @see #LANDHEX_COORD_ISLANDS_EACH
     */
    private static final int LANDHEX_LANDAREA_RANGES_ISLANDS_4PL[] =
    {
        2, 5,  // landarea 2 is an island with 5 hexes
        3, 5,  // landarea 3
        4, 4   // landarea 4
    };

    /**
     * Fallback board layout, 4 players: Land hex types for the 3 small islands,
     * to be used with (for the main island) {@link SOCBoard4p#makeNewBoard_landHexTypes_v1}[].
     */
    private static final int LANDHEX_TYPE_ISLANDS_4PL[] =
    {
        CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, WHEAT_HEX, WHEAT_HEX, DESERT_HEX,
        WOOD_HEX, WOOD_HEX, GOLD_HEX, GOLD_HEX
    };

    /**
     * Fallback board layout, 4 players: Dice numbers for the outlying islands.
     * These islands have no required path for numbers; as long as the frequently rolled ("red") 6 and 8 aren't
     * adjacent, and as long as GOLD_HEXes have rare numbers, all is OK.
     * To make the islands more attractive, avoids the infrequntly rolled 2 and 12.
     */
    private static final int LANDHEX_DICENUM_ISLANDS_4PL[] =
    {
        5, 4, 6, 3, 8,
        10, 9, 11, 5, 9,
        4, 10, 5  // leave 1 un-numbered, for the DESERT_HEX
    };

    /**
     * Fallback board layout for 6 players: Dice-number path (hex coordinates)
     * on the main island, spiraling inward from the shore.
     * The outlying islands have no dice path.
     * For the mainland's dice numbers, see {@link SOCBoard6p#makeNewBoard_diceNums_v2}.
     */
    private static final int LANDHEX_DICEPATH_MAINLAND_6PL[] =
    {
        // clockwise inward from western corner
        0x0701, 0x0502, 0x0303, 0x0104, 0x0106, 0x0108, 0x0309, 0x050A,
        0x070B, 0x090A, 0x0B09, 0x0D08, 0x0D06, 0x0D04, 0x0B03, 0x0902,  // end of outside of spiral
        0x0703, 0x0504, 0x0305, 0x0307, 0x0508,
        0x0709, 0x0908, 0x0B07, 0x0B05, 0x0904,  // end of middle layer of spiral
        0x0705, 0x0506, 0x0707, 0x0906
    };

    /**
     * Fallback board layout for 6 players: Main island's ports, clockwise from its western corner (like dice path).
     * Each port has 2 consecutive elements.
     * First: Port edge coordinate, in hex: 0xRRCC.
     * Second: Port Facing direction: {@link SOCBoard#FACING_E FACING_E}, etc.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     */
    private static final int PORT_EDGE_FACING_MAINLAND_6PL[] =
    {
        0x0501, FACING_E,   0x0202, FACING_SE,
        0x0005, FACING_SE,  0x0008, FACING_SW,
        0x040A, FACING_SW,  0x070C, FACING_W,
        0x0A0A, FACING_NW,  0x0E08, FACING_NW,
        0x0E05, FACING_NE,  0x0C02, FACING_NE,
        0x0901, FACING_E
    };

    /**
     * Fallback board layout, 6 players: All the outlying islands' land hex coordinates.
     *<P>
     * The first outlying island (land area 2) is upper-right on board.
     * Second island (landarea 3) is lower-right.
     * Third island (landarea 4) is lower-left.
     * @see #LANDHEX_LANDAREA_RANGES_ISLANDS_6PL
     */
    private static final int LANDHEX_COORD_ISLANDS_ALL_6PL[] =
    {
        0x010E, 0x0110, 0x030D, 0x030F, 0x0311, 0x050E, 0x0510, 0x0711,
        0x0B0D, 0x0B0F, 0x0B11, 0x0D0C, 0x0D0E, 0x0D10, 0x0F0F, 0x0F11,
        0x1102, 0x1104, 0x1106, 0x1108, 0x110A
    };

    /**
     * 4-player island hex counts and land area numbers within {@link #LANDHEX_COORD_ISLANDS_ALL_4PL}.
     * Allows us to shuffle them all together ({@link #LANDHEX_TYPE_ISLANDS_6PL}).
     * @see #LANDHEX_COORD_ISLANDS_EACH
     */
    private static final int LANDHEX_LANDAREA_RANGES_ISLANDS_6PL[] =
    {
        2, 8,  // landarea 2 is an island with 8 hexes
        3, 8,  // landarea 3
        4, 5   // landarea 4
    };

    /**
     * Fallback board layout, 6 players: Land hex types for the 3 small islands,
     * to be used with (for the main island) {@link SOCBoard6p#makeNewBoard_landHexTypes_v2}[].
     */
    private static final int LANDHEX_TYPE_ISLANDS_6PL[] =
    {
        CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        DESERT_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, GOLD_HEX, GOLD_HEX
    };

    /**
     * Fallback board layout, 6 players: Dice numbers for the outlying islands.
     * These islands have no required path for numbers; as long as the frequently rolled ("red") 6 and 8 aren't
     * adjacent, and as long as GOLD_HEXes have rare numbers, all is OK.
     * To make the islands more attractive, avoids the infrequntly rolled 2 and 12.
     */
    private static final int LANDHEX_DICENUM_ISLANDS_6PL[] =
    {
        3, 3, 4, 4, 5, 5, 5, 6, 6, 6, 8, 8, 8, 9, 9, 9, 10, 10, 11, 11
        // leave 1 un-numbered, for the DESERT_HEX
    };

    /**
     * Fallback board layout, 6 players: Outlying islands' ports.
     * Each port has 2 elements.
     * First: Coordinate, in hex: 0xRRCC.
     * Second: Facing
     */
    private static final int PORT_EDGE_FACING_ISLANDS_6PL[] =
    {
        0x060F, FACING_NE,  0x010D, FACING_E,    // - northeast island
        0x0A0F, FACING_SW,  0x0E0D, FACING_NE,   // - southeast island
        0x1006, FACING_SW    // - southwest island
    };

    /**
     * Port types for the 4 outlying-island ports on the 6-player fallback board.
     * For the mainland's port types, use {@link SOCBoard6p#PORTS_TYPE_V2}.
     */
    private static final int PORT_TYPE_ISLANDS_6PL[] =
    {
        MISC_PORT, MISC_PORT, CLAY_PORT, WOOD_PORT, MISC_PORT
    };


    ////////////////////////////////////////////
    //
    // New Shores scenario layout (_SC_NSHO)
    // Has 3-player, 4-player, 6-player versions;
    // each array here uses index [0] for 3-player, [1] for 4, [2] for 6.
    // LA#1 has the main island.
    // Other Land Areas are the small outlying islands; players can't start there.
    //

    /**
     * New Shores: Board size:
     * 3 players max row 0x0E, max col 0x0E.
     * 4 players max 0x0E, 0x10.
     * 6 players max 0x0E, 0x14.
     */
    private static final int NSHO_BOARDSIZE[] = { 0x0E0E, 0x0E10, 0x0E14 };

    /** New Shores: Visual Shift ("VS") */
    private static final int NSHO_VIS_SHIFT[][] = { {0,1}, {0,1}, {3,0} };

    /**
     * New Shores: Starting pirate water hex coordinate for 3, 4, 6 players.
     * The 6-player layout starts with the pirate off the board.
     */
    private static final int NSHO_PIRATE_HEX[] = { 0x070D, 0x070F, 0 };

    /** New Shores: Land hex types for the main island. Shuffled. */
    private static final int NSHO_LANDHEX_TYPE_MAIN[][] =
    {
        {
            // 3 players:
            CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
            WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
        },
        SOCBoard4p.makeNewBoard_landHexTypes_v1,  // 4 players
        SOCBoard6p.makeNewBoard_landHexTypes_v2   // 6 players
    };

    /**
     * New Shores: Land hex coordinates for the main island.
     * Defines the path and sequence of dice numbers in {@link #NSHO_DICENUM_MAIN}.
     * Land hex types (shuffled) are {@link #NSHO_LANDHEX_TYPE_MAIN}.
     * @see #NSHO_LANDHEX_COORD_ISL
     */
    private static final int NSHO_LANDHEX_COORD_MAIN[][] =
    {{
        // 3 players: clockwise from west (dice numbers are shuffled)
        0x0902, 0x0703, 0x0504, 0x0506, 0x0707,
        0x0908, 0x0B07, 0x0D06, 0x0D04, 0x0B03,
        0x0904, 0x0705, 0x0906, 0x0B05
    }, {
        // 4 players: clockwise from northwest (dice path)
        0x0504, 0x0506, 0x0508, 0x0709, 0x090A,
        0x0B09, 0x0D08, 0x0D06, 0x0D04, 0x0B03,
        0x0902, 0x0703, 0x0705, 0x0707, 0x0908,
        0x0B07, 0x0B05, 0x0904, 0x0906
    }, {
        // 6 players: clockwise from west (dice path)
        0x0705, 0x0506, 0x0307, 0x0108, 0x010A, 0x010C, 0x030D,
        0x050E, 0x070F, 0x090E, 0x0B0D, 0x0D0C, 0x0D0A, 0x0D08,
        0x0B07, 0x0906, 0x0707, 0x0508, 0x0309, 0x030B, 0x050C,
        0x070D, 0x090C, 0x0B0B, 0x0B09, 0x0908, 0x0709, 0x050A,
        0x070B, 0x090A
    }};

    /**
     * New Shores: Dice numbers for hexes on the main island along {@link #NSHO_LANDHEX_COORD_MAIN}.
     * Shuffled for 3-player board only.
     * @see #NSHO_DICENUM_ISL
     */
    private static final int NSHO_DICENUM_MAIN[][] =
    {
        { 2, 3, 4, 5, 5, 6, 6, 8, 8, 9, 10, 10, 11, 11 },  // 3 players
        SOCBoard4p.makeNewBoard_diceNums_v1,  // 4 players
        SOCBoard6p.makeNewBoard_diceNums_v2   // 6 players
    };

    /**
     * New Shores: Port edges and facings. There are no ports on the small islands, only the main one.
     *<P>
     * Clockwise, starting at northwest corner of board.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     *<P>
     * Port types ({@link #NSHO_PORT_TYPE}) are shuffled.
     */
    private static final int NSHO_PORT_EDGE_FACING[][] =
    {{
        0x0503, FACING_E,   0x0507, FACING_W,   0x0808, FACING_SW,  0x0D07, FACING_W,
        0x0E05, FACING_NE,  0x0D03, FACING_E,   0x0A01, FACING_NE,  0x0801, FACING_SE
    }, {
        0x0403, FACING_SE,  0x0406, FACING_SW,  0x0609, FACING_SW,  0x090B, FACING_W,
        0x0C09, FACING_NW,  0x0E06, FACING_NW,  0x0E03, FACING_NE,  0x0B02, FACING_E,
        0x0702, FACING_E
    }, {
        0x000A, FACING_SW,  0x020D, FACING_SW,  0x050F, FACING_W,   0x080F, FACING_NW,
        0x0B0E, FACING_W,   0x0E0C, FACING_NW,  0x0E09, FACING_NE,  0x0C06, FACING_NE,
        0x0804, FACING_NE,  0x0505, FACING_E,   0x0206, FACING_SE
    }};

    /** New Shores: Port types on main island; will be shuffled. */
    private static final int NSHO_PORT_TYPE[][] =
    {
        { 0, 0, 0, CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT },  // 3 players
        SOCBoard4p.PORTS_TYPE_V1,  // 4 players
        SOCBoard6p.PORTS_TYPE_V2   // 6 players
    };

    /** New Shores: Land hex types on the several small islands. Shuffled. */
    private static final int NSHO_LANDHEX_TYPE_ISL[][] =
    {{
        CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, SHEEP_HEX, WHEAT_HEX, GOLD_HEX, GOLD_HEX
    }, {
        CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, SHEEP_HEX, WHEAT_HEX, WOOD_HEX, GOLD_HEX, GOLD_HEX
    }, {
        CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, SHEEP_HEX, WHEAT_HEX, WOOD_HEX, GOLD_HEX, GOLD_HEX, GOLD_HEX
    }};

    /**
     * New Shores: Island hex counts and land area numbers within {@link #NSHO_LANDHEX_COORD_ISL}.
     * Allows them to be defined, shuffled, and placed together.
     */
    private static final int NSHO_LANDHEX_LANDAREA_RANGES[][] =
    {{
        2, 2,  // landarea 2 is the northwest island with 2 hexes
        3, 4,  // landarea 3 NE, 4 hexes
        4, 2,  // landarea 4 SE, 2 hexes
    }, {
        2, 2,
        3, 5,
        4, 2
    }, {
        2, 3,
        3, 3,
        4, 1,  // single-hex islands on east side of board
        5, 1,
        6, 1,
        7, 1
    }};

    /**
     * New Shores: Land hex coordinates for the several small islands.
     * Each island is a separate land area, split up the array using {@link #NSHO_LANDHEX_LANDAREA_RANGES}.
     * Dice numbers on the islands (shuffled together) are {@link #NSHO_DICENUM_ISL}.
     * @see #NSHO_LANDHEX_COORD_MAIN
     */
    private static final int NSHO_LANDHEX_COORD_ISL[][] =
    {{
        0x0104, 0x0106,
        0x0309, 0x030B, 0x050A, 0x070B,
        0x0B0B, 0x0D0A
    }, {
        0x0104, 0x0106,
        0x010A, 0x030B, 0x030D, 0x050C, 0x070D,
        0x0B0D, 0x0D0C
    }, {
        0x0104, 0x0303, 0x0502,
        0x0902, 0x0B03, 0x0D04,
        0x0110,   0x0512,   0x0912,   0x0D10
    }};

    /**
     * New Shores: Dice numbers for hexes on the several small islands
     * ({@link #NSHO_LANDHEX_COORD_ISL}).  Shuffled.
     * @see #NSHO_DICENUM_MAIN
     */
    private static final int NSHO_DICENUM_ISL[][] =
    {{
        3, 4, 4, 5, 8, 9, 10, 12
    }, {
        2, 3, 4, 5, 6, 8, 9, 10, 11
    }, {
        2, 3, 4, 5, 6, 8, 9, 10, 11, 12
    }};


    ////////////////////////////////////////////
    //
    // Fog Island scenario Layout
    //   Has 3-player, 4-player, 6-player versions;
    //   FOG_ISL_LANDHEX_TYPE_FOG[] is shared between 3p and 4p.
    //

    /**
     * Fog Island: Board size:
     * 3 or 4 players max row 0x10, max col 0x11.
     * 6 players max row 0x10, max col 0x14.
     */
    private static final int FOG_ISL_BOARDSIZE[] = { 0x1011, 0x1014 };

    /** Fog Island: Visual Shift ("VS") */
    private static final int FOG_ISL_VIS_SHIFT[][] = { {0,2}, {2,1}, {-1,0} };

    /**
     * Fog Island: Pirate ship's starting hex coordinate for 3,4,6 players.
     * The 4-player board has the pirate start at a port hex, which looks cluttered.
     */
    private static final int FOG_ISL_PIRATE_HEX[] =
    {
        0x070F, 0x070F, 0x0910
    };

    //
    // 3-player
    //

    /**
     * Fog Island: Land hex types for the large southwest and northeast islands.
     */
    private static final int FOG_ISL_LANDHEX_TYPE_MAIN_3PL[] =
    {
        // 14 hexes total: southwest, northeast islands are 7 each
        CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
    };

    /**
     * Fog Island: Land hex coordinates for the large southwest and northeast islands.
     */
    private static final int FOG_ISL_LANDHEX_COORD_MAIN_3PL[] =
    {
        // Southwest island: 7 hexes centered on rows 7, 9, b, d, columns 2-6
        0x0703, 0x0902, 0x0904, 0x0B03, 0x0B05, 0x0D04, 0x0D06,

        // Northeast island: 7 hexes centered on rows 1, 3, 5, 7, columns 9-d
        0x010A, 0x0309, 0x030B, 0x050A, 0x050C, 0x070B, 0x070D
    };

    /**
     * Fog Island: Dice numbers for hexes on the large southwest and northeast islands.
     * Shuffled, no required path; as long as 6 and 8 aren't adjacent, all is OK.
     */
    private static final int FOG_ISL_DICENUM_MAIN_3PL[] =
    {
        3, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 11, 11, 12
    };

    /**
     * Fog Island: Port edges and facings on the large southwest and northeast islands.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     *<P>
     * Each port's type will be {@link #FOG_ISL_PORT_TYPE_3PL}.
     */
    private static final int FOG_ISL_PORT_EDGE_FACING_3PL[] =
    {
        // Southwest island: 4 ports; placing counterclockwise from northwest end
        0x0702, FACING_E,   0x0A01, FACING_NE,
        0x0D03, FACING_E,   0x0E05, FACING_NE,

        // Northeast island: 4 ports; placing counterclockwise from southeast end
        0x060D, FACING_SW,  0x040C, FACING_SW,
        0x010B, FACING_W,   0x0208, FACING_SE
    };

    /**
     * Fog Island: Port types on the large southwest and northeast islands.
     * Since these won't be shuffled, they will line up with {@link #FOG_ISL_PORT_EDGE_FACING_3PL}.
     */
    private static final int FOG_ISL_PORT_TYPE_3PL[] =
    {
        // Southwest island: 4 ports; placing counterclockwise from northwest end
        MISC_PORT, WHEAT_PORT, SHEEP_PORT, MISC_PORT,

        // Northeast island: 4 ports; placing counterclockwise from southeast end
        ORE_PORT, WOOD_PORT, MISC_PORT, CLAY_PORT
    };

    /**
     * Fog Island: Land and water hex types for the fog island.
     * Shared by 3-player and 4-player layouts.
     */
    private static final int FOG_ISL_LANDHEX_TYPE_FOG[] =
    {
        // 12 hexes total (includes 2 water)
        WATER_HEX, WATER_HEX,
        CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, WHEAT_HEX, WHEAT_HEX, WOOD_HEX,
        GOLD_HEX, GOLD_HEX
    };

    /**
     * Fog Island: Land and water hex coordinates for the fog island.
     * Hex types for the fog island are {@link #FOG_ISL_LANDHEX_TYPE_FOG}.
     */
    private static final int FOG_ISL_LANDHEX_COORD_FOG_3PL[] =
    {
        // Fog island: 12 hexes, from northwest to southeast;
        // the main part of the fog island is a diagonal line
        // of hexes from (1, 4) to (D, A).
        0x0104, 0x0106, 0x0303,
        0x0305, 0x0506, 0x0707, 0x0908, 0x0B09, 0x0D0A,
        0x0B0B, 0x0B0D, 0x0D0C
    };

    /**
     * Fog Island: Dice numbers for hexes on the fog island.
     * Shuffled, no required path; as long as 6 and 8 aren't adjacent, all is OK.
     */
    private static final int FOG_ISL_DICENUM_FOG_3PL[] =
    {
        // 10 numbered hexes (excludes 2 water)
        3, 3, 4, 5, 6, 8, 9, 10, 11, 12
    };

    //
    // 4-player
    //

    /**
     * Fog Island: Land hex types for the large southwest and northeast islands.
     */
    private static final int FOG_ISL_LANDHEX_TYPE_MAIN_4PL[] =
    {
        // 17 hexes total for southwest, northeast islands
        CLAY_HEX, CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
    };

    /**
     * Fog Island: Land hex coordinates for the large southwest and northeast islands.
     */
    private static final int FOG_ISL_LANDHEX_COORD_MAIN_4PL[] =
    {
        // Southwest island: 10 hexes centered on rows 5-d, columns 2-8
        0x0502, 0x0703, 0x0902, 0x0904, 0x0B03, 0x0B05, 0x0B07, 0x0D04, 0x0D06,0x0D08,

        // Northeast island: 7 hexes centered on rows 1-7, columns a-e
        0x010A, 0x010C, 0x030B, 0x030D, 0x050C, 0x050E, 0x070D
    };

    /**
     * Fog Island: Dice numbers for hexes on the large southwest and northeast islands.
     * Shuffled, no required path; as long as 6 and 8 aren't adjacent, all is OK.
     */
    private static final int FOG_ISL_DICENUM_MAIN_4PL[] =
    {
        2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 12
    };

    /**
     * Fog Island: Port edges and facings on the large southwest and northeast islands.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     *<P>
     * Each port's type will be {@link #FOG_ISL_PORT_TYPE_4PL}.
     */
    private static final int FOG_ISL_PORT_EDGE_FACING_4PL[] =
    {
        // Southwest island: 5 ports; placing counterclockwise from northern end
        0x0601, FACING_NE,  0x0A01, FACING_NE,  0x0D03, FACING_E,
        0x0E04, FACING_NW,  0x0E07, FACING_NE,

        // Northeast island: 4 ports; placing counterclockwise from southeast end
        0x070E, FACING_W,   0x040E, FACING_SW,
        0x010D, FACING_W,   0x000B, FACING_SE
    };

    /**
     * Fog Island: Port types on the large southwest and northeast islands.
     * Since these won't be shuffled, they will line up with {@link #FOG_ISL_PORT_EDGE_FACING_4PL}.
     */
    private static final int FOG_ISL_PORT_TYPE_4PL[] =
    {
        // Southwest island: 5 ports; placing counterclockwise from northern end
        MISC_PORT, CLAY_PORT, MISC_PORT, WHEAT_PORT, SHEEP_PORT,

        // Northeast island: 4 ports; placing counterclockwise from southeast end
        WOOD_PORT, ORE_PORT, MISC_PORT, MISC_PORT
    };

    /**
     * Fog Island: Land and water hex coordinates for the fog island.
     * Hex types for the fog island are {@link #FOG_ISL_LANDHEX_TYPE_FOG}.
     */
    private static final int FOG_ISL_LANDHEX_COORD_FOG_4PL[] =
    {
        // Fog island: 12 hexes, from northwest to southeast;
        // the main part of the fog island is a diagonal line
        // of hexes from (1, 6) to (D, C).
        0x0104, 0x0305, 0x0506, 0x0707,
        0x0106, 0x0307, 0x0508, 0x0709, 0x090A, 0x0B0B, 0x0D0C,
        0x0B0D
    };

    /**
     * Fog Island: Dice numbers for hexes on the fog island.
     * Shuffled, no required path; as long as 6 and 8 aren't adjacent, all is OK.
     */
    private static final int FOG_ISL_DICENUM_FOG_4PL[] =
    {
        // 10 numbered hexes (excludes 2 water)
        3, 4, 5, 6, 8, 9, 10, 11, 11, 12
    };

    //
    // 6-player
    //

    /**
     * Fog Island: Land hex types for the northern main island.
     */
    private static final int FOG_ISL_LANDHEX_TYPE_MAIN_6PL[] =
    {
        // 24 hexes on main island
        CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX,
        DESERT_HEX
    };

    /**
     * Fog Island: Land hex coordinates for the northern main island.
     */
    private static final int FOG_ISL_LANDHEX_COORD_MAIN_6PL[] =
    {
        // 24 hexes centered on rows 3-9, columns 3-0x11 (northmost row is 8 hexes across)
        0x0303, 0x0305, 0x0307, 0x0309, 0x030B, 0x030D, 0x030F, 0x0311,
        0x0504, 0x0506, 0x0508, 0x050A, 0x050C, 0x050E, 0x0510,
        0x0705, 0x0707, 0x0709, 0x070B, 0x070D, 0x070F,
        0x0908, 0x090A, 0x090C
    };

    /**
     * Fog Island: Dice numbers for hexes on the northern main island.
     * Shuffled, no required path; as long as 6 and 8 aren't adjacent, all is OK.
     */
    private static final int FOG_ISL_DICENUM_MAIN_6PL[] =
    {
        // 23 numbered hexes (excludes 1 desert)
        2, 3, 3, 3, 4, 4, 5, 5, 6, 6, 6, 8, 8, 8, 9, 9, 10, 10, 11, 11, 11, 12, 12
    };

    /**
     * Fog Island: Port edges and facings on the northern main island.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     *<P>
     * Each port's type will be from {@link #FOG_ISL_PORT_TYPE_6PL}
     * which is shuffled.
     */
    private static final int FOG_ISL_PORT_EDGE_FACING_6PL[] =
    {
        // 9 ports; placing counterclockwise from northwest end
        0x0503, FACING_E,   0x0806, FACING_NE,  0x0A0A, FACING_NW,
        0x080D, FACING_NW,  0x0511, FACING_W,   0x0210, FACING_SE,
        0x020B, FACING_SW,  0x0206, FACING_SE,  0x0203, FACING_SW
    };

    /**
     * Fog Island: Port types on the northern main island.
     * These will be shuffled. Port locations are in {@link #FOG_ISL_PORT_EDGE_FACING_6PL}.
     */
    private static final int FOG_ISL_PORT_TYPE_6PL[] =
    {
        // 9 ports; 4 generic (3:1), 1 each of 2:1.
        MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT,
        CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT
    };

    /**
     * Fog Island: Land and water hex types for the fog island.
     */
    private static final int FOG_ISL_LANDHEX_TYPE_FOG_6PL[] =
    {
        // 25 hexes total (includes 12 water)
        WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX,
        WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX, WATER_HEX,
        CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, GOLD_HEX
    };

    /**
     * Fog Island: Land and water hex coordinates for the fog island.
     * Hex types for the fog island are {@link #FOG_ISL_LANDHEX_TYPE_FOG_6PL}.
     */
    private static final int FOG_ISL_LANDHEX_COORD_FOG_6PL[] =
    {
        // Fog island: 25 hexes, in a wide arc from southwest to southeast.
        // The westernmost hex is centered at column 1, easternmost at 0x13.
        // The 2 thick rows along the south are row D and F.
        0x0701, 0x0713, 0x0902, 0x0912,
        0x0B01, 0x0B03, 0x0B05, 0x0B0F, 0x0B11, 0x0B13,
        0x0D02, 0x0D04, 0x0D06, 0x0D08, 0x0D0A, 0x0D0C, 0x0D0E, 0x0D10, 0x0D12,
        0x0F05, 0x0F07, 0x0F09, 0x0F0B, 0x0F0D, 0x0F0F
    };

    /**
     * Fog Island: Dice numbers for hexes on the fog island.
     * Shuffled, no required path; as long as 6 and 8 aren't adjacent, all is OK.
     */
    private static final int FOG_ISL_DICENUM_FOG_6PL[] =
    {
        // 13 numbered hexes total (excludes 12 water)
        2, 2, 3, 4, 5, 5, 6, 8, 9, 9, 10, 11, 12
    };

    /**
     * Fog Island: Land hex types for the gold corners (6-player only).
     */
    private static final int FOG_ISL_LANDHEX_TYPE_GC[] =
    {
        GOLD_HEX, GOLD_HEX
    };

    /**
     * Fog Island: Land hex coordinates for the gold corners (6-player only).
     */
    private static final int FOG_ISL_LANDHEX_COORD_GC[] =
    {
        0x0F03, 0x0F11
    };

    /**
     * Fog Island: Dice numbers for hexes on the gold corners (6-player only).
     */
    private static final int FOG_ISL_DICENUM_GC[] =
    {
        4, 10
    };


    ////////////////////////////////////////////
    //
    // The 4 Islands scenario Layout (SC_4ISL)
    //   Has 3-player, 4-player, 6-player versions
    //

    /**
     * Four Islands: Board size:
     * 3 or 4 players max row 0x0f, max col 0x0e.
     * 6 players max row 0x10, max col 0x14.
     */
    private static final int FOUR_ISL_BOARDSIZE[] = { 0x0f0e, 0x1014 };

    /** Four Islands: Visual Shift ("VS"), same for all player counts */
    private static final int FOUR_ISL_VIS_SHIFT[] = {3, 0};

    /** Four Islands: Pirate ship's starting hex coordinate for 3,4,6 players */
    private static final int FOUR_ISL_PIRATE_HEX[] =
    {
        0x0707, 0x070D, 0x0701
    };

    //
    // 3-player
    //

    /**
     * Four Islands: Land hex types for all 4 islands.
     */
    private static final int FOUR_ISL_LANDHEX_TYPE_3PL[] =
    {
        // 20 hexes total: 4 each of 5 resources
        CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
    };

    /**
     * Four Islands: Land hex coordinates for all 4 islands.
     * The 4 island land areas are given by {@link #FOUR_ISL_LANDHEX_LANDAREA_RANGES_3PL}.
     */
    private static final int FOUR_ISL_LANDHEX_COORD_3PL[] =
    {
        // Northwest island: 4 hexes centered on rows 3,5, columns 2-5
        0x0303, 0x0305, 0x0502, 0x0504,

        // Southwest island: 6 hexes centered on rows 9,b,d, columns 2-6
        0x0902, 0x0904, 0x0906, 0x0B03, 0x0B05, 0x0D04,

        // Northeast island: 6 hexes centered on rows 1,3,5, columns 8-b
        0x0108, 0x010A, 0x0309, 0x030B, 0x0508, 0x050A,

        // Southeast island: 4 hexes centered on rows 9,b, columns 9-c
        0x090A, 0x090C, 0x0B09, 0x0B0B
    };

    /**
     * Four Islands: Dice numbers for all 4 islands.
     * Shuffled, no required path; as long as 6 and 8 aren't adjacent, all is OK.
     */
    private static final int FOUR_ISL_DICENUM_3PL[] =
    {
        // 20 hexes total, no deserts
        2, 3, 3, 4, 4, 5, 5, 5, 6, 6, 8, 8, 9, 9, 9, 10, 10, 11, 11, 12
    };

    /**
     * Four Islands: Island hex counts and land area numbers within {@link #FOUR_ISL_LANDHEX_COORD_3PL}.
     * Allows them to be defined, shuffled, and placed together.
     */
    private static final int FOUR_ISL_LANDHEX_LANDAREA_RANGES_3PL[] =
    {
            1, 4,  // landarea 1 is the northwest island with 4 hexes
            2, 6,  // landarea 2 SW
            3, 6,  // landarea 3 NE
            4, 4   // landarea 4 SE
    };

    /**
     * Four Islands: Port edges and facings on all 4 islands.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     *<P>
     * Each port's type will be from {@link #FOUR_ISL_PORT_TYPE_3PL}.
     */
    private static final int FOUR_ISL_PORT_EDGE_FACING_3PL[] =
    {
        0x0401, FACING_SE,  0x0405, FACING_NW, // Northwest island
        0x0802, FACING_SW,  0x0A01, FACING_NE,  0x0C05, FACING_NW,  // SW island
        0x020B, FACING_SW,  0x0609, FACING_NE, // NE island
        0x0A08, FACING_SE,  0x0A0C, FACING_NW  // SE island
    };

    /**
     * Four Islands: Port types on all 4 islands.  OK to shuffle.
     */
    private static final int FOUR_ISL_PORT_TYPE_3PL[] =
    {
        MISC_PORT, ORE_PORT,
        WOOD_PORT, MISC_PORT, SHEEP_PORT,
        MISC_PORT, CLAY_PORT,
        MISC_PORT, WHEAT_PORT
    };

    //
    // 4-player
    //

    /**
     * Four Islands: Land hex types for all 4 islands.
     */
    private static final int FOUR_ISL_LANDHEX_TYPE_4PL[] =
    {
        // 23 hexes total: 4 or 5 each of 5 resources
        CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
    };

    /**
     * Four Islands: Land hex coordinates for all 4 islands.
     */
    private static final int FOUR_ISL_LANDHEX_COORD_4PL[] =
    {
        // Northwest island: 4 hexes centered on rows 1,3,5, columns 2-4
        0x0104, 0x0303, 0x0502, 0x0504,

        // Southwest island: 7 hexes centered on rows 9,b,d, columns 2-7
        0x0902, 0x0904, 0x0B03, 0x0B05, 0x0B07, 0x0D04, 0x0D06,

        // Northeast island: 8 hexes centered on rows 1,3,5, columns 7-b, outlier at (7,7)
        0x0108, 0x010A, 0x0307, 0x0309, 0x030B, 0x0508, 0x050A, 0x0707,

        // Southeast island: 4 hexes centered on rows 9,b,d, columns a-c
        0x090A, 0x090C, 0x0B0B, 0x0D0A
    };

    /**
     * Four Islands: Dice numbers for hexes on all 4 islands.
     * Shuffled, no required path; as long as 6 and 8 aren't adjacent, all is OK.
     */
    private static final int FOUR_ISL_DICENUM_4PL[] =
    {
        // 23 hexes total, no deserts
        2, 3, 3, 4, 4, 4, 5, 5, 5, 6, 6, 8, 8, 9, 9, 9, 10, 10, 10, 11, 11, 11, 12
    };

    /**
     * Four Islands: Island hex counts and land area numbers within {@link #FOUR_ISL_LANDHEX_COORD_4PL}.
     * Allows them to be defined, shuffled, and placed together.
     */
    private static final int FOUR_ISL_LANDHEX_LANDAREA_RANGES_4PL[] =
    {
            1, 4,  // landarea 1 is the northwest island with 4 hexes
            2, 7,  // landarea 2 SW
            3, 8,  // landarea 3 NE
            4, 4   // landarea 4 SE
    };

    /**
     * Four Islands: Port edges and facings on all 4 islands.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Each port's type will be from {@link #FOUR_ISL_PORT_TYPE_4PL}.
     */
    private static final int FOUR_ISL_PORT_EDGE_FACING_4PL[] =
    {
        0x0302, FACING_E,   0x0602, FACING_NW,
        0x0A01, FACING_NE,  0x0A05, FACING_SW,  0x0D03, FACING_E,
        0x0606, FACING_SE,  0x040B, FACING_NW,
        0x080B, FACING_SE,  0x0A0C, FACING_NW
    };

    /**
     * Four Islands: Port types on all 4 islands.  OK to shuffle.
     */
    private static final int FOUR_ISL_PORT_TYPE_4PL[] =
    {
        WHEAT_PORT, MISC_PORT,
        CLAY_PORT, MISC_PORT, SHEEP_PORT,
        MISC_PORT, WOOD_PORT,
        ORE_PORT, MISC_PORT
    };

    //
    // 6-player
    //

    /**
     * Six Islands: Land hex types for all 6 islands.
     */
    private static final int FOUR_ISL_LANDHEX_TYPE_6PL[] =
    {
        // 32 hexes total: 6 or 7 each of 5 resources
        CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
    };

    /**
     * Six Islands: Land hex coordinates for all 6 islands.
     */
    private static final int FOUR_ISL_LANDHEX_COORD_6PL[] =
    {
        // All 3 Northern islands are centered on rows 1,3,5.

        // Northwest island: 6 hexes centered on columns 2-6
        0x0104, 0x0106, 0x0303, 0x0305, 0x0502, 0x0504,

        // Center North island: 5 hexes  on columns 8-b
        0x010A, 0x0309, 0x030B, 0x0508, 0x050A,

        // Northeast island: 5 hexes  on columns e-0x11
        0x010E, 0x0110, 0x030F, 0x0311, 0x0510,

        // All 3 Southern islands are centered on rows 9,b,d.

        // Southwest island: 5 hexes centered on columns 3-6
        0x0904, 0x0B03, 0x0B05, 0x0D04, 0x0D06,

        // Center South island: 5 hexes on columns 9-c
        0x090A, 0x090C, 0x0B09, 0x0B0B, 0x0D0A,

        // Southeast island: 6 hexes on columns e-0x12
        0x0910, 0x0912, 0x0B0F, 0x0B11, 0x0D0E, 0x0D10
    };

    /**
     * Six Islands: Dice numbers for hexes on all 6 islands.
     * Shuffled, no required path; as long as 6 and 8 aren't adjacent, all is OK.
     */
    private static final int FOUR_ISL_DICENUM_6PL[] =
    {
        // 32 hexes total, no deserts
        2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 6,
        8, 8, 8, 9, 9, 9, 9, 10, 10, 10, 10, 11, 11, 12, 12
    };

    /**
     * Six Islands: Island hex counts and land area numbers within {@link #FOUR_ISL_LANDHEX_COORD_6PL}.
     * Allows them to be defined, shuffled, and placed together.
     */
    private static final int FOUR_ISL_LANDHEX_LANDAREA_RANGES_6PL[] =
    {
            1, 6,  // landarea 1 is the northwest island with 6 hexes
            2, 5,  // landarea 2 is center north
            3, 5,  // landarea 3 NE
            4, 5,  // SW
            5, 5,  // S
            6, 6   // SE
    };

    /**
     * Six Islands: Port edges and facings on all 6 islands.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Each port's type will be from {@link #FOUR_ISL_PORT_TYPE_6PL}.
     */
    private static final int FOUR_ISL_PORT_EDGE_FACING_6PL[] =
    {
        // 1 row here per island, same order as hexes
        0x0005, FACING_SE,  0x0405, FACING_NW,  0x0603, FACING_NE,
        0x000A, FACING_SW,
        0x0010, FACING_SW,  0x040E, FACING_NE,
        0x0B02, FACING_E,   0x0C06, FACING_SW,
        0x0C08, FACING_NE,
        0x0B12, FACING_W,   0x0E0D, FACING_NE
    };

    /**
     * Six Islands: Port types on all 6 islands.  OK to shuffle.
     */
    private static final int FOUR_ISL_PORT_TYPE_6PL[] =
    {
        // 5 3:1, 6 2:1 ports (extra is sheep)
        CLAY_PORT, ORE_PORT, SHEEP_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT,
        MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT
    };


    ////////////////////////////////////////////
    //
    // Pirate Island scenario Layout (_SC_PIRI)
    //   Has 4-player, 6-player versions;
    //   each array here uses index [0] for 4-player, [1] for 6-player.
    //   LA#1 is starting land area.  Pirate Islands have no land area,
    //   the player can place 1 lone settlement (added layout part "LS")
    //   at a coordinate within PIR_ISL_INIT_PIECES.
    //

    /** Pirate Islands: Visual Shift ("VS"), same for all player counts */
    private static final int PIR_ISL_VIS_SHIFT[] = {3, -1};

    /**
     * Pirate Islands: Board size:
     * 4 players max row 0x10, max col 0x12.
     * 6 players max row 0x10, max col 0x16.
     */
    private static final int PIR_ISL_BOARDSIZE[] = { 0x1012, 0x1016 };

    /**
     * Pirate Islands: Starting pirate water hex coordinate for 4, 6 players.
     */
    private static final int PIR_ISL_PIRATE_HEX[] = { 0x0D0A, 0x0D0A };

    /**
     * Pirate Islands: Land hex types for the large eastern starting island.
     * Each row from west to east.  These won't be shuffled.
     */
    private static final int PIR_ISL_LANDHEX_TYPE_MAIN[][] =
    {{
        // 4-player: 17 hexes; 7 rows, each row 2 or 3 hexes across
        WHEAT_HEX, CLAY_HEX, ORE_HEX, WOOD_HEX,
        WOOD_HEX, SHEEP_HEX, WOOD_HEX,
        WHEAT_HEX, CLAY_HEX, SHEEP_HEX,
        SHEEP_HEX, WOOD_HEX, SHEEP_HEX,
        ORE_HEX, WOOD_HEX, WHEAT_HEX, CLAY_HEX
    }, {
        // 6-player: 24 hexes; 7 rows, each row 3 or 4 hexes across
        SHEEP_HEX, WHEAT_HEX, CLAY_HEX,
        ORE_HEX, CLAY_HEX, WOOD_HEX,
        WOOD_HEX, WOOD_HEX, SHEEP_HEX, WOOD_HEX,
        WHEAT_HEX, WHEAT_HEX, ORE_HEX, SHEEP_HEX,
        SHEEP_HEX, SHEEP_HEX, CLAY_HEX, SHEEP_HEX,
        WHEAT_HEX, ORE_HEX, CLAY_HEX,
        WOOD_HEX, WHEAT_HEX, WOOD_HEX
    }};

    /**
     * Pirate Islands: Land hex coordinates for the large eastern starting island.
     * Indexes line up with {@link #PIR_ISL_LANDHEX_TYPE_MAIN}, because they won't be shuffled.
     */
    private static final int PIR_ISL_LANDHEX_COORD_MAIN[][] =
    {{
        // 4-player: 17 hexes; 7 rows, centered on columns b - 0x10
        0x010C, 0x010E, 0x030D, 0x030F,
        0x050C, 0x050E, 0x0510,
        0x070B, 0x070D, 0x070F,
        0x090C, 0x090E, 0x0910,
        0x0B0D, 0x0B0F, 0x0D0C, 0x0D0E
    }, {
        // 6-player: 24 hexes; 7 rows, centered on columns d - 0x14
        0x010E, 0x0110, 0x0112,
        0x030F, 0x0311, 0x0313,
        0x050E, 0x0510, 0x0512, 0x0514,
        0x070D, 0x070F, 0x0711, 0x0713,
        0x090E, 0x0910, 0x0912, 0x0914,
        0x0B0F, 0x0B11, 0x0B13,
        0x0D0E, 0x0D10, 0x0D12
    }};

    /**
     * Pirate Islands: Dice numbers for hexes on the large eastern starting island.
     * Will be placed along {@link #PIR_ISL_LANDHEX_COORD_MAIN}.
     */
    private static final int PIR_ISL_DICENUM_MAIN[][] =
    {{
        4, 5, 9, 10, 3, 8, 5, 6, 9, 12, 11, 8, 9, 5, 2, 10, 4
    }, {
        5, 4, 9, 3, 10, 11, 12, 6, 4, 10,
        6, 4, 5, 8,  // <-- center row
        2, 10, 3, 5, 11, 9, 8, 9, 5, 4
    }};

    /**
     * Pirate Islands: Port edges and facings on the large eastern starting island.
     * Clockwise, starting at northwest corner of island.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     *<P>
     * Each port's type will be {@link #PIR_ISL_PORT_TYPE}[i].
     */
    private static final int PIR_ISL_PORT_EDGE_FACING[][] =
    {{
        // 4 players
        0x000B, FACING_SE,  0x000D, FACING_SE,  0x010F, FACING_W,
        0x0310, FACING_W,   0x0710, FACING_W,   0x0A10, FACING_NW,
        0x0C0F, FACING_NW,  0x0E0C, FACING_NW
    }, {
        // 6 players
        0x000F, FACING_SE,  0x0011, FACING_SE,  0x0113, FACING_W,
        0x0314, FACING_W,   0x0714, FACING_W,   0x0B14, FACING_W,
        0x0D13, FACING_W,   0x0E11, FACING_NE,  0x0E0F, FACING_NE
    }};

    /**
     * Pirate Islands: Port types on the large eastern starting island.  Will be shuffled.
     */
    private static final int PIR_ISL_PORT_TYPE[][] =
    {{
        // 4 players:
        MISC_PORT, MISC_PORT, MISC_PORT,
        CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT
    }, {
        // 6 players:
        MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT,
        CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT
    }};

    /**
     * Pirate Islands: Hex land types on the several pirate islands.
     * Only the first several have dice numbers.
     */
    private static final int PIR_ISL_LANDHEX_TYPE_PIRI[][] =
    {{
        // 4 players, see PIR_ISL_LANDHEX_COORD_PIRI for layout details
        ORE_HEX, GOLD_HEX, WHEAT_HEX, ORE_HEX, GOLD_HEX, WHEAT_HEX, ORE_HEX,
        CLAY_HEX, CLAY_HEX, DESERT_HEX, DESERT_HEX, DESERT_HEX, SHEEP_HEX
    }, {
        // 6 players
        GOLD_HEX, ORE_HEX, GOLD_HEX, ORE_HEX, GOLD_HEX, ORE_HEX, GOLD_HEX, ORE_HEX,
        DESERT_HEX, DESERT_HEX, DESERT_HEX, DESERT_HEX, DESERT_HEX
    }};

    /**
     * Pirate Islands: Land hex coordinates for the several pirate islands.
     * Hex types for the pirate island are {@link #PIR_ISL_LANDHEX_TYPE_PIRI}.
     * Only the first several have dice numbers ({@link #PIR_ISL_DICENUM_PIRI}).
     */
    private static final int PIR_ISL_LANDHEX_COORD_PIRI[][] =
    {{
        // 4 players: With dice numbers: Northwest and southwest islands, center-west island
        0x0106, 0x0104, 0x0502, 0x0D06, 0x0D04, 0x0902, 0x0705,
        //            Without numbers: Northwest, southwest, north-center, south-center
        0x0303, 0x0B03, 0x0309, 0x0508, 0x0908, 0x0B09
    }, {
        // 6 players: With dice numbers: Scattered from north to south
        0x0104, 0x0108, 0x0502, 0x0506, 0x0902, 0x0906, 0x0D04, 0x0D08,
        //            Without numbers: Scattered north to south, center or west
        0x030B, 0x0504, 0x0709, 0x0904, 0x0B0B
    }};

    /**
     * Pirate Islands: Dice numbers for the first few land hexes along {@link #PIR_ISL_LANDHEX_COORD_PIRI}
     */
    private static final int PIR_ISL_DICENUM_PIRI[][] =
    {{
        // 4 players
        6, 11, 4, 6, 3, 10, 8
    }, {
        // 6 players
        3, 6, 11, 8, 11, 6, 3, 8
    }};

    /**
     * Pirate Islands: The hex-coordinate path for the Pirate Fleet; SOCBoardLarge additional part <tt>"PP"</tt>.
     * First element is the pirate starting position.  {@link #piratePathIndex} is already 0.
     */
    private static final int PIR_ISL_PPATH[][] =
    {{
        // 4 players
        0x0D0A, 0x0D08, 0x0B07, 0x0906, 0x0707, 0x0506, 0x0307,
        0x0108, 0x010A, 0x030B, 0x050A, 0x0709, 0x090A, 0x0B0B
    }, {
        // 6 players
        0x0D0A, 0x0B09, 0x0908, 0x0707, 0x0508, 0x0309, 0x010A,
        0x010C, 0x030D, 0x050C, 0x070B, 0x090C, 0x0B0D, 0x0D0C
    }};

    /**
     * Pirate Islands: Sea edges legal/valid for each player to build ships directly to their Fortress.
     * Each player has 1 array, in same player order as {@link #PIR_ISL_INIT_PIECES}
     * (given out to non-vacant players, not strictly matching player number).
     *<P>
     * Note {@link SOCPlayer#doesTradeRouteContinuePastNode(SOCBoard, boolean, int, int, int)}
     * assumes there is just 1 legal sea edge, not 2, next to each player's free initial settlement,
     * so it won't need to check if the ship route would branch in 2 directions there.
     *<P>
     * See {@link #getLegalSeaEdges(SOCGame, int)} for how this is rearranged to be sent to
     * active player clients as part of a {@code SOCPotentialSettlements} message,
     * and the format of each player's array.
     */
    private static final int PIR_ISL_SEA_EDGES[][][] =
    {{
        // 4 players
        { 0xC07, -0xC0B, 0xD07, -0xD0B, 0xE04, -0xE0A },
        { 0x207, -0x20B, 0x107, -0x10B, 0x004, -0x00A },
        { 0x803, -0x80A, 0x903, 0x905, 0xA03, 0xA04 },
        { 0x603, -0x60A, 0x503, 0x505, 0x403, 0x404 }
    }, {
        // 6 players
        { 0xC04, -0xC0D }, { 0x204, -0x20D },
        { 0x803, -0x80C }, { 0x603, -0x60C },
        { 0x003, -0x00C }, { 0xE03, -0xE0C }
    }};

    /**
     * Pirate Islands: Initial piece coordinates for each player,
     * in same player order as {@link #PIR_ISL_SEA_EDGES}
     * (given out to non-vacant players, not strictly matching player number).
     * Each player has 4 elements, starting at index <tt>4 * playerNumber</tt>:
     * Initial settlement node, initial ship edge, pirate fortress node,
     * and the node on the pirate island where they are allowed to build
     * a settlement on the way to the fortress (layout part "LS").
     */
    private static final int PIR_ISL_INIT_PIECES[][] =
    {{
        // 4 players
        0x0C0C, 0x0C0B, 0x0E04, 0x0E07,
        0x020C, 0x020B, 0x0004, 0x0007,
        0x080B, 0x080A, 0x0A03, 0x0805,
        0x060B, 0x060A, 0x0403, 0x0605
    }, {
        // 6 players
        0x0C0E, 0x0C0D, 0x0C04, 0x0C08,
        0x020E, 0x020D, 0x0204, 0x0208,
        0x080D, 0x080C, 0x0803, 0x0807,
        0x060D, 0x060C, 0x0603, 0x0607,
        0x000D, 0x000C, 0x0003, 0x0009,
        0x0E0D, 0x0E0C, 0x0E03, 0x0E09
    }};


    ////////////////////////////////////////////
    //
    // Through The Desert scenario Layout (SC_TTD)
    //   Has 3-player, 4-player, 6-player versions;
    //   each array here uses index [0] for 3-player, [1] for 4-player, [2] for 6-player.
    //

    /**
     * Through The Desert: Board size for 3, 4, 6 players: Each is 0xrrcc (max row, max col).
     */
    private static final int TTDESERT_BOARDSIZE[] = { 0x100E, 0x1011, 0x1016 };

    /** Through The Desert: Visual Shift ("VS"), same for all player counts */
    private static final int TTDESERT_VIS_SHIFT[] = {0, 1};

    /**
     * Through The Desert: Starting pirate water hex coordinate for 3, 4, 6 players.
     */
    private static final int TTDESERT_PIRATE_HEX[] = { 0x070D, 0x070F, 0x0D10 };

    /**
     * Through The Desert: Land hex types for the main island (land areas 1, 2). These will be shuffled.
     * The main island also includes 3 or 5 deserts that aren't shuffled, so they aren't in this array.
     *<P>
     * The main island has just 1 <tt>GOLD_HEX</tt>, which will always be placed in landarea 2,
     * the small strip of land past the desert.  This is handled by
     * {@link #makeNewBoard_placeHexes_arrangeGolds(int[], int[], String)}.
     *
     * @see #TTDESERT_LANDHEX_COORD_MAIN
     */
    private static final int TTDESERT_LANDHEX_TYPE_MAIN[][] =
    {{
        // 3-player: 17 (14+3) hexes
        CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX,
        GOLD_HEX
    }, {
        // 4-player: 20 (17+3) hexes
        CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX,
        GOLD_HEX
    }, {
        // 6-player: 30 (21+9) hexes
        CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX,
        GOLD_HEX
    }};

    /**
     * Through The Desert: Land Area 1, 2: Land hex coordinates for the main island,
     * excluding its desert strip but including the small fertile area on the desert's far side.
     * Landarea 1 is most of the island.
     * Landarea 2 is the small fertile strip.
     * @see #TTDESERT_LANDHEX_COORD_DESERT
     * @see #TTDESERT_LANDHEX_COORD_SMALL
     */
    private static final int TTDESERT_LANDHEX_COORD_MAIN[][] =
    {{
        // 3-player: 14 hexes; 6 rows, hex centers on columns 2-8
        0x0307, 0x0506, 0x0508, 0x0705, 0x0707,
        0x0902, 0x0904, 0x0906, 0x0908,
        0x0B03, 0x0B05, 0x0B07, 0x0D04, 0x0D06,
        // Past the desert: 3 more:
        0x0104, 0x0303, 0x0502
    }, {
        // 4-player: 17 hexes; 7 rows, columns 2-A
        0x0108, 0x0307, 0x0309,
        0x0506, 0x0508, 0x050A,
        0x0705, 0x0707, 0x0709,
        0x0902, 0x0904, 0x0906, 0x0908,
        0x0B03, 0x0B05, 0x0B07, 0x0D06,
        // Past the desert: 3 more:
        0x0104, 0x0303, 0x0502
    }, {
        // 6-player: 21 hexes; 5 rows, columns 2-F
        0x0508, 0x050A, 0x050C, 0x050E,
        0x0703, 0x0705, 0x0707, 0x0709, 0x070B, 0x070D, 0x070F,
        0x0902, 0x0904, 0x0906, 0x0908, 0x090A, 0x090C, 0x090E,
        0x0B03, 0x0B05, 0x0D04,
        // Past the desert: 9 more:
        0x0305, 0x0303, 0x0104, 0x0106, 0x0108, /* 1-hex gap, */ 0x010C, 0x010E, 0x0110, 0x0112
    }};

    /**
     * Through The Desert: Land Areas 1, 2:
     * Hex counts and Land area numbers for the main island, excluding its desert,
     * within {@link #TTDESERT_LANDHEX_COORD_MAIN}.
     * Allows us to shuffle them all together within {@link #TTDESERT_LANDHEX_TYPE_MAIN}.
     * Dice numbers are {@link #TTDESERT_DICENUM_MAIN}.
     * Landarea 1 is most of the island.
     * Landarea 2 is the small fertile strip on the other side of the desert.
     */
    private static final int TTDESERT_LANDHEX_RANGES_MAIN[][] =
    {{
        // 3 players
        1, TTDESERT_LANDHEX_COORD_MAIN[0].length - 3,
        2, 3
    }, {
        // 4 players
        1, TTDESERT_LANDHEX_COORD_MAIN[1].length - 3,
        2, 3
    }, {
        // 6 players
        1, TTDESERT_LANDHEX_COORD_MAIN[2].length - 9,
        2, 9
    }};

    /**
     * Through The Desert: Land hex coordinates for the strip of desert hexes on the main island.
     * @see #TTDESERT_LANDHEX_COORD_MAIN
     */
    private static final int TTDESERT_LANDHEX_COORD_DESERT[][] =
    {{ 0x0106, 0x0305, 0x0504 },  // 3-player
     { 0x0106, 0x0305, 0x0504 },  // 4-player same as 3-player
     { 0x0307, 0x0309, 0x030B, 0x030D, 0x030F }  // 6-player
    };

    /**
     * Through The Desert: Dice numbers for hexes on the main island,
     * including the strip of land past the desert.  Will be shuffled.
     * Will be placed along {@link #TTDESERT_LANDHEX_COORD_MAIN}.
     * @see #TTDESERT_DICENUM_SMALL
     */
    private static final int TTDESERT_DICENUM_MAIN[][] =
    {{
        // 3 players
        2, 3, 3, 4, 4, 4, 5, 6, 6, 6, 8, 8, 9, 9, 10, 10, 11
    }, {
        // 4 players
        3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 8, 9, 9, 10, 10, 10, 11, 11, 11, 12
    }, {
        // 6 players
        2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 5, 5,
        6, 6, 6, 6, 8, 8, 9, 9, 9, 10, 10, 10,
        11, 11, 11, 12, 12, 12
    }};

    /**
     * Through The Desert: Port edges and facings on the main island.
     * Clockwise, starting at northwest corner of island.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     *<P>
     * Each port's type will be {@link #TTDESERT_PORT_TYPE}[i][j].
     */
    private static final int TTDESERT_PORT_EDGE_FACING[][] =
    {{
        // 3 players
        0x0207, FACING_SW,  0x0808, FACING_SW,  0x0A08, FACING_NW,
        0x0D07, FACING_W,   0x0E04, FACING_NW,  0x0D03, FACING_E,
        0x0A01, FACING_NE,  0x0803, FACING_SE
    }, {
        // 4 players
        0x0109, FACING_W,   0x040A, FACING_SW,  0x060A, FACING_NW,
        0x0A08, FACING_NW,  0x0D07, FACING_W,   0x0E05, FACING_NE,
        0x0C03, FACING_NW,  0x0B02, FACING_E,   0x0704, FACING_E
    }, {
        // 6 players
        0x0B02, FACING_E,   0x0801, FACING_SE,  0x0602, FACING_SE,
        0x0606, FACING_SE,  0x050F, FACING_W,   0x080F, FACING_NW,
        0x0A0D, FACING_NE,  0x0A09, FACING_NE,  0x0A06, FACING_NW,
        0x0C05, FACING_NW,  0x0E03, FACING_NE
    }};

    /**
     * Through The Desert: Port types on the main island.  Will be shuffled.
     * The small islands have no ports.
     * Each port's edge and facing will be {@link #TTDESERT_PORT_EDGE_FACING}[i][j].
     */
    private static final int TTDESERT_PORT_TYPE[][] =
    {{
        // 3 players:
        MISC_PORT, MISC_PORT, MISC_PORT,
        CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT
    }, {
        // 4 players:
        MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT,
        CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT
    }, {
        // 6 players:
        MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT,
        CLAY_PORT, ORE_PORT, SHEEP_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT
    }};

    /**
     * Through The Desert: Hex land types on the several small islands.
     * Coordinates for these islands are {@link #TTDESERT_LANDHEX_COORD_SMALL}.
     */
    private static final int TTDESERT_LANDHEX_TYPE_SMALL[][] =
    {{
        // 3 players
        ORE_HEX, ORE_HEX, SHEEP_HEX, WHEAT_HEX, GOLD_HEX
    }, {
        // 4 players
        CLAY_HEX, ORE_HEX, ORE_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, GOLD_HEX
    }, {
        // 6 players
        ORE_HEX, SHEEP_HEX, SHEEP_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, GOLD_HEX, GOLD_HEX
    }};

    /**
     * Through The Desert: Land Areas 3 to n:
     * Hex counts and Land area numbers for each of the small "foreign" islands, one per island,
     * within {@link #TTDESERT_LANDHEX_COORD_SMALL}.
     * Allows us to shuffle them all together within {@link #TTDESERT_LANDHEX_TYPE_SMALL}.
     * Dice numbers are {@link #TTDESERT_DICENUM_SMALL}.
     * Total land area count for this layout varies, will be 2 + (<tt>TTDESERT_LANDHEX_RANGES_SMALL[i].length</tt> / 2).
     */
    private static final int TTDESERT_LANDHEX_RANGES_SMALL[][] =
    {{
        // 3 players
        3, 2,  // landarea 3 is an island with 2 hexes (see TTDESERT_LANDHEX_COORD_SMALL)
        4, 1,  // landarea 4
        5, 2   // landarea 5
    }, {
        // 4 players
        3, 3,
        4, 2,
        5, 2
    }, {
        // 6 players
        3, 2,
        4, 1,
        5, 4,
        6, 1
    }};

    /**
     * Through The Desert: Land Areas 3 to n:
     * Land hex coordinates for all of the small "foreign" islands, one LA per island.
     * Hex types for these islands are {@link #TTDESERT_LANDHEX_TYPE_SMALL}.
     * Dice numbers are {@link #TTDESERT_DICENUM_SMALL}.
     * Land area numbers are split up via {@link #TTDESERT_LANDHEX_RANGES_SMALL}.
     */
    private static final int TTDESERT_LANDHEX_COORD_SMALL[][] =
    {{
        // 3 players
        0x010A, 0x030B,  // landarea 3 is an island with 2 hexes (see TTDESERT_LANDHEX_RANGES_SMALL)
        0x070B,          // landarea 4
        0x0B0B, 0x0D0A   // landarea 5
    }, {
        // 4 players
        0x010C, 0x030D, 0x050E,
        0x090C, 0x090E,
        0x0D0A, 0x0D0C
    }, {
        // 6 players
        0x0D08, 0x0D0A,
        0x0D0E,
        0x0D12, 0x0B11, 0x0B13, 0x0914,
        0x0514
    }};

    /**
     * Through The Desert: Dice numbers for all the small islands ({@link #TTDESERT_LANDHEX_COORD_SMALL}).
     * @see #TTDESERT_DICENUM_MAIN
     */
    private static final int TTDESERT_DICENUM_SMALL[][] =
    {{
        // 3 players
        5, 5, 8, 9, 11
    }, {
        // 4 players
        2, 3, 4, 5, 6, 9, 12
    }, {
        // 6 players
        2, 3, 4, 8, 8, 9, 10, 11
    }};


    ////////////////////////////////////////////
    //
    // Forgotten Tribe scenario Layout (_SC_FTRI)
    //   Has 4-player, 6-player versions;
    //   each array here uses index [0] for 4-player, [1] for 6-player.
    //   LA#1 is the larger main island.
    //   LA#0 is the surrounding islands; players can't settle there.
    //   No ports on the main island, only surrounding islands.
    //

    /**
     * Forgotten Tribe: Board size:
     * 4 players max row 0x0E, max col 0x11.
     * 6 players max row 0x0E, max col 0x15.
     */
    private static final int FOR_TRI_BOARDSIZE[] = { 0x0E11, 0x0E15 };

    /** Forgotten Tribe: Visual Shift ("VS") */
    private static final int FOR_TRI_VIS_SHIFT[][] = { {3,-2}, {2,-1} };

    /**
     * Forgotten Tribe: Starting pirate water hex coordinate for 4, 6 players.
     */
    private static final int FOR_TRI_PIRATE_HEX[] = { 0x0108, 0x010E };

    /**
     * Forgotten Tribe: Land hex types for the main island. Shuffled.
     */
    private static final int FOR_TRI_LANDHEX_TYPE_MAIN[][] =
    {{
        // 4-player: 18 hexes
        CLAY_HEX, CLAY_HEX, CLAY_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, WHEAT_HEX, WHEAT_HEX,
        WHEAT_HEX, WHEAT_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
    }, {
        // 6-player: 29 hexes
        CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
    }};

    /**
     * Forgotten Tribe: Land hex coordinates for the main island.
     * 3 eastern-coast hexes included here which shouldn't be given too-favorable
     * dice numbers are {@link #FOR_TRI_LANDHEX_COORD_MAIN_FAR_COASTAL}.
     */
    private static final int FOR_TRI_LANDHEX_COORD_MAIN[][] =
    {{
        // 4-player: 18 hexes; 3 rows, centered on columns 2 - d
        0x0502, 0x0504, 0x0506, 0x0508, 0x050A, 0x050C,
        0x0703, 0x0705, 0x0707, 0x0709, 0x070B, 0x070D,
        0x0902, 0x0904, 0x0906, 0x0908, 0x090A, 0x090C
    }, {
        // 6-player: 29 hexes; 3 rows, centered on columns 2 - 0x14
        0x0502, 0x0504, 0x0506, 0x0508, 0x050A, 0x050C, 0x050E, 0x0510, 0x0512, 0x0514,
        0x0703, 0x0705, 0x0707, 0x0709, 0x070B, 0x070D, 0x070F, 0x0711, 0x0713,
        0x0902, 0x0904, 0x0906, 0x0908, 0x090A, 0x090C, 0x090E, 0x0910, 0x0912, 0x0914
    }};

    /**
     * Forgotten Tribe: 3 Land hex coordinates on main island (within
     * {@link #FOR_TRI_LANDHEX_COORD_MAIN}) on the short eastern side.
     * These shouldn't be given the favorable "red" dice numbers 6 or 8, nor 5 or 9.
     */
    private static final int FOR_TRI_LANDHEX_COORD_MAIN_FAR_COASTAL[][] =
    {{
        // 4-player:
        0x050C, 0x070D, 0x090C
    }, {
        // 6-player:
        0x0514, 0x0713, 0x0914
    }};

    /**
     * Forgotten Tribe: Dice numbers for hexes on the main island. Shuffled.
     */
    private static final int FOR_TRI_DICENUM_MAIN[][] =
    {{
        2, 3, 3, 4, 4, 5, 5, 6, 6,
        8, 8, 9, 9, 10, 10, 11, 11, 12
    }, {
        2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6,
        8, 8, 8, 9, 9, 9, 10, 10, 10, 11, 11, 11, 12
    }};

    /**
     * Forgotten Tribe: Port edges and facings. There are no ports on the main island, only the surrounding islands.
     *<P>
     * Clockwise, starting at northwest corner of board.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     *<P>
     * Port types ({@link #FOR_TRI_PORT_TYPE}) are shuffled.
     */
    private static final int FOR_TRI_PORT_EDGE_FACING[][] =
    {{
        // 4 players
        0x0003, FACING_SE,  0x0009, FACING_SE,  0x0410, FACING_SW,
        0x0A10, FACING_NW,  0x0E0A, FACING_NW,  0x0E03, FACING_NE
    }, {
        // 6 players
        0x0006, FACING_SW,  0x0009, FACING_SE,  0x000F, FACING_SE, 0x0012, FACING_SW,
        0x0E0F, FACING_NE,  0x0E0C, FACING_NW,  0x0E05, FACING_NE, 0x0E03, FACING_NE
    }};

    /**
     * Forgotten Tribe: Port types; will be shuffled.
     */
    private static final int FOR_TRI_PORT_TYPE[][] =
    {{
        // 4 players: 6 ports:
        CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT, MISC_PORT
    }, {
        // 6 players: 8 ports:
        CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT,
        MISC_PORT, MISC_PORT, MISC_PORT
    }};

    /**
     * Forgotten Tribe: Hex land types on the several small islands.
     * None have dice numbers.  Not shuffled; coordinates for these
     * land hexes are {@link #FOR_TRI_LANDHEX_COORD_ISL}.
     */
    private static final int FOR_TRI_LANDHEX_TYPE_ISL[][] =
    {{
        // 4 players: Clockwise from northwest corner of board:
        GOLD_HEX, ORE_HEX,    DESERT_HEX, ORE_HEX, WHEAT_HEX,    SHEEP_HEX,
        WOOD_HEX,    GOLD_HEX, DESERT_HEX, CLAY_HEX,    CLAY_HEX, DESERT_HEX
    }, {
        // 6 players: Northern islands west to east, then southern west to east:
        GOLD_HEX, WHEAT_HEX,    CLAY_HEX, ORE_HEX,    DESERT_HEX, DESERT_HEX,
        SHEEP_HEX, SHEEP_HEX,   GOLD_HEX, GOLD_HEX,   DESERT_HEX, DESERT_HEX
    }};

    /**
     * Forgotten Tribe: Land hex coordinates for the several small islands.
     * Hex types for these small islands are {@link #FOR_TRI_LANDHEX_TYPE_ISL}.
     * None have dice numbers.
     */
    private static final int FOR_TRI_LANDHEX_COORD_ISL[][] =
    {{
        // 4 players: Clockwise from northwest corner of board:
        0x0104, 0x0106,    0x010A, 0x010C, 0x010E,    0x0510,
        0x0910,    0x0D0E, 0x0D0C, 0x0D0A,    0x0D06, 0x0D04
    }, {
        // 6 players: Northern islands west to east, then southern west to east:
        0x0104, 0x0106,    0x010A, 0x010C,    0x0110, 0x0112,
        0x0D04, 0x0D06,    0x0D0A, 0x0D0C,    0x0D10, 0x0D12
    }};

    /**
     * Forgotten Tribe: Special Victory Point Edges: edge coordinates where ship placement gives an SVP.
     * SOCBoardLarge additional part {@code "VE"}.
     */
    private static final int FOR_TRI_SVP_EDGES[][] =
    {{
        // 4 players
        0x0004, 0x000A, 0x000E, 0x0511,
        0x0E04, 0x0E09, 0x0E0E, 0x0911
    }, {
        // 6 players
        0x0003, 0x0005, 0x000A, 0x000B, 0x0010,
        0x0E06, 0x0E0A, 0x0E0B, 0x0E10, 0x0E12
    }};

    /**
     * Forgotten Tribe: Dev Card Edges: Edge coordinates where ship placement gives a free development card.
     * SOCBoardLarge additional part {@code "CE"}.
     */
    private static final int FOR_TRI_DEV_CARD_EDGES[][] =
    {{
        // 4 players
        0x0103, 0x010F, 0x0D03, 0x0D0F
    }, {
        // 6 players
        0x0103, 0x000C, 0x0113, 0x0D03, 0x0E09, 0x0D13
    }};


    ////////////////////////////////////////////
    //
    // Cloth Trade with Villages scenario Layout (_SC_CLVI)
    //   Has 4-player, 6-player versions;
    //   each array here uses index [0] for 4-player, [1] for 6-player.
    //   LA#1 has the two main islands.
    //   LA#0 has the small middle islands; players can't settle there.
    //   No ports on the small islands, only main islands.
    //

    /**
     * Cloth Villages: Board size:
     * 4 players max row 0x0E, max col 0x10.
     * 6 players max row 0x0E, max col 0x15.
     */
    private static final int CLVI_BOARDSIZE[] = { 0x0E10, 0x0E15 };

    /** Cloth Villages: Visual Shift ("VS") */
    private static final int CLVI_VIS_SHIFT[][] = {{2,-1}, {2,0}};

    /**
     * Cloth Villages: Starting pirate water hex coordinate for 4, 6 players.
     */
    private static final int CLVI_PIRATE_HEX[] = { 0x070F, 0x0713 };

    /**
     * Cloth Villages: Land hex types for the main island. Shuffled.
     */
    private static final int CLVI_LANDHEX_TYPE_MAIN[][] =
    {{
        // 4-player:
        CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
    }, {
        // 6-player:
        CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
    }};

    /**
     * Cloth Villages: Land hex coordinates for the main islands.
     */
    private static final int CLVI_LANDHEX_COORD_MAIN[][] =
    {{
        // 4-player: Each 10 hexes; 3 rows, main row centered on columns 4 - c
        0x0104, 0x0106, 0x0108, 0x010A, 0x010C,
        0x0303, 0x0305, 0x030B, 0x030D, 0x0502,

        0x0B03, 0x0B05, 0x0B0B, 0x0B0D, 0x090E,
        0x0D04, 0x0D06, 0x0D08, 0x0D0A, 0x0D0C
    }, {
        // 6-player: Each 13 hexes; 3 rows, main row centered on columns 4 - 0x10
        0x0104, 0x0106, 0x0108, 0x010A, 0x010C, 0x010E, 0x0110,
        0x0303, 0x0305, 0x030F, 0x0311, 0x0502, 0x0512,

        0x0B03, 0x0B05, 0x0B0F, 0x0B11, 0x0902, 0x0912,
        0x0D04, 0x0D06, 0x0D08, 0x0D0A, 0x0D0C, 0x0D0E, 0x0D10
    }};

    /**
     * Cloth Villages: Dice numbers for hexes on the main island. Shuffled.
     */
    private static final int CLVI_DICENUM_MAIN[][] =
    {{
        2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        8, 8, 9, 9, 10, 10, 11, 11, 12, 12
    }, {
        2, 2, 3, 3, 3, 3, 4, 4, 5, 5, 6, 6, 6,
        8, 8, 8, 9, 9, 10, 10, 11, 11, 11, 11, 12, 12
    }};

    /**
     * Cloth Villages: Port edges and facings. There are no ports on the small islands, only the main ones.
     *<P>
     * West to east on each island.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     *<P>
     * Port types ({@link #CLVI_PORT_TYPE}) are shuffled.
     */
    private static final int CLVI_PORT_EDGE_FACING[][] =
    {{
        // 4 players
        0x0302, FACING_E,  0x0004, FACING_SW,  0x0009, FACING_SE,  0x030E, FACING_W,
        0x0B02, FACING_E,  0x0E05, FACING_NE,  0x0E0A, FACING_NW,  0x0C0D, FACING_NW,  0x090F, FACING_W
    }, {
        // 6 players
        0x0302, FACING_E,  0x0004, FACING_SW,  0x0009, FACING_SE,  0x000D, FACING_SE,  0x0312, FACING_W,
        0x0B02, FACING_E,  0x0E05, FACING_NE,  0x0E08, FACING_NW,  0x0E0E, FACING_NW,
        0x0C11, FACING_NW, 0x0913, FACING_W
    }};

    /**
     * Cloth Villages: Port types; will be shuffled.
     * Port edge coordinates and facings are {@link #CLVI_PORT_EDGE_FACING}.
     */
    private static final int CLVI_PORT_TYPE[][] =
    {{
        // 4 players: 5 special, 4 generic:
        CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT,
        MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT
    }, {
        // 6 players: 6 special, 5 generic:
        CLAY_PORT, ORE_PORT, SHEEP_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT,
        MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT
    }};

    /**
     * Cloth Villages: Hex land types on the several small middle islands, west to east.
     * None have dice numbers.  Not shuffled; coordinates for these
     * land hexes are {@link #CLVI_LANDHEX_COORD_ISL}.
     */
    private static final int CLVI_LANDHEX_TYPE_ISL[][] =
    {{
        // 4 players:
        GOLD_HEX, DESERT_HEX, DESERT_HEX, GOLD_HEX
    }, {
        // 6 players:
        GOLD_HEX, DESERT_HEX, DESERT_HEX, DESERT_HEX, DESERT_HEX, GOLD_HEX
    }};

    /**
     * Cloth Villages: Land hex coordinates for the several small middle islands, west to east.
     * Hex types for these small islands are {@link #CLVI_LANDHEX_TYPE_ISL}.
     * None have dice numbers.
     * Each has two villages, see {@link #CLVI_CLOTH_VILLAGE_AMOUNTS_NODES_DICE}.
     */
    private static final int CLVI_LANDHEX_COORD_ISL[][] =
    {{
        // 4 players:
        0x0705, 0x0508, 0x0908, 0x070B
    }, {
        // 6 players:
        0x0705, 0x0508, 0x0908, 0x050C, 0x090C, 0x070F
    }};

    /**
     * Cloth Villages: Small islands' cloth village node locations and dice numbers.
     * Within the 4pl and 6pl subarrays:
     * Index 0 is the board's "general supply" cloth count.
     * Index 1 is each village's starting cloth count, from {@link SOCVillage#STARTING_CLOTH}.
     * Further indexes are the locations and dice,
     * paired for each village: [i] = node, [i+1] = dice number.
     * SOCBoardLarge additional part {@code "CV"}.
     *<P>
     * Each small island has two villages; the islands' coordinates are {@link #CLVI_LANDHEX_COORD_ISL}.
     * @see SOCGameOption#K_SC_CLVI
     * @see #setVillageAndClothLayout(int[])
     */
    private static final int CLVI_CLOTH_VILLAGE_AMOUNTS_NODES_DICE[][] =
    {{
        // 4 players:
        SOCVillage.STARTING_GENERAL_CLOTH,  // Board's "general supply" cloth count is same for 4pl, 6pl
        SOCVillage.STARTING_CLOTH,
        0x0605, 10,  0x0805, 9,  0x0408, 11,  0x0608, 8,  0x0808, 6,  0x0A08, 3,  0x060B, 4,  0x080B, 5
    }, {
        // 6 players:
        SOCVillage.STARTING_GENERAL_CLOTH,
        SOCVillage.STARTING_CLOTH,
        0x0605, 4,   0x0805, 9,  0x0408, 2,  0x0608, 5,   0x0808, 6,  0x0A08, 12,
        0x040C, 10,  0x060C, 8,  0x080C, 9,  0x0A0C, 10,  0x060F, 4,  0x080F, 5
    }};


    ////////////////////////////////////////////
    //
    // Wonders scenario Layout (_SC_WOND)
    //   Has 4-player, 6-player versions;
    //   each array here uses index [0] for 4-player, [1] for 6-player.
    //   LA#1 has the main island.
    //   LA#2 has the small outlying islands; players can't start there.
    //   Has a few small sets of nodes as Added Layout Parts where players can't start.
    //

    /**
     * Wonders: Board size:
     * 4 players max row 0x0E, max col 0x12.
     * 6 players max row 0x10, max col 0x14 (incl ports' hexes).
     */
    private static final int WOND_BOARDSIZE[] = { 0x0E12, 0x1014 };

    /** Wonders: Visual Shift ("VS") */
    private static final int WOND_VIS_SHIFT[][] = { {4,-1}, {-1,-1} };

    /**
     * Wonders: Land hex types for the main island, excluding the desert. Shuffled.
     */
    private static final int WOND_LANDHEX_TYPE_MAIN[][] =
    {{
        // 4-player:
        CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
    }, {
        // 6-player:
        CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX, CLAY_HEX,
        ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX, ORE_HEX,
        SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX, SHEEP_HEX,
        WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX, WHEAT_HEX,
        WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX, WOOD_HEX
    }};

    /**
     * Wonders: Land hex coordinates for the main island, excluding
     * deserts ({@link #WOND_LANDHEX_COORD_DESERT}).
     * Dice numbers (shuffled) are {@link #WOND_DICENUM_MAIN}.
     * Main part on rows 7 and 9, with outlying parts north and south.
     * 2 hexes included here adjacent to the desert are {@link #WOND_LANDHEX_COORD_MAIN_AT_DESERT}.
     */
    private static final int WOND_LANDHEX_COORD_MAIN[][] =
    {{
        // 4-player:
        0x0108, 0x010A, 0x010E, 0x0307, 0x0309, 0x030D, 0x0508, 0x050E, 0x0510,
        0x0707, 0x0709, 0x070B, 0x070D, 0x070F,
        0x0906, 0x0908, 0x090A, 0x090C, 0x090E, 0x0910,
        0x0B09, 0x0D08
    }, {
        // 6-player:
        0x0307, 0x0309, 0x030D, 0x030F, 0x0506, 0x0508, 0x050C, 0x050E, 0x0705, 0x0707, 0x0709, 0x070D, 0x070F,
        0x0904, 0x0906, 0x0908, 0x090A, 0x090C, 0x090E, 0x0910,
        0x0B05, 0x0B07, 0x0B09, 0x0B0B, 0x0B0D, 0x0B0F, 0x0B11,
        0x0D0A, 0x0D0E, 0x0F09, 0x0F0D
    }};

    /**
     * Wonders: 2 land hex coordinates on main island (within {@link #WOND_LANDHEX_COORD_MAIN})
     * adjacent to the deserts ({@link #WOND_LANDHEX_COORD_DESERT}).
     * These shouldn't be given the favorable "red" dice numbers 6 or 8.
     */
    private static final int WOND_LANDHEX_COORD_MAIN_AT_DESERT[][] =
    {{
        // 4-player:
        0x0707, 0x0906
    }, {
        // 6-player:
        0x0904, 0x0B05
    }};

    /**
     * Wonders: Land hex coordinates for the deserts in the southwest of the main island.
     * The rest of the main island's hexes are {@link #WOND_LANDHEX_COORD_MAIN}.
     * Main island hexes adjacent to the desert are {@link #WOND_LANDHEX_COORD_MAIN_AT_DESERT}.
     */
    private static final int WOND_LANDHEX_COORD_DESERT[][] =
    {{
        // 4-player:
        0x0705, 0x0904, 0x0B05
    }, {
        // 6-player:
        0x0902, 0x0B03, 0x0D02, 0x0D04
    }};

    /**
     * Wonders: Dice numbers for hexes on the main island. Shuffled.
     * Dice numbers for small islands are {@link #WOND_DICENUM_ISL}.
     */
    private static final int WOND_DICENUM_MAIN[][] =
    {{
        2, 3, 3, 3, 4, 4, 5, 5, 6, 6,
        8, 8, 9, 9, 9, 10, 10, 10, 11, 11, 11, 12
    }, {
        2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 5, 5, 6, 6,
        8, 8, 8, 9, 9, 9, 9, 10, 10, 10, 10, 11, 11, 11, 11, 12, 12
    }};

    /**
     * Wonders: Port edges and facings. There are no ports on the small islands, only the main one.
     *<P>
     * North to South on main island.
     * Each port has 2 elements: Edge coordinate (0xRRCC), Port Facing.
     *<P>
     * Port Facing is the direction from the port edge, to the land hex touching it
     * which will have 2 nodes where a port settlement/city can be built.
     *<P>
     * Port types ({@link #WOND_PORT_TYPE}) are shuffled.
     */
    private static final int WOND_PORT_EDGE_FACING[][] =
    {{
        // 4 players
        0x0009, FACING_SE, 0x0206, FACING_SE,  0x030E, FACING_W,  0x0406, FACING_NE,  0x060C, FACING_SE,
        0x0710, FACING_W,  0x0A07, FACING_NE,  0x0A0A, FACING_NW, 0x0A0E, FACING_NW
    }, {
        // 6 players
        0x0208, FACING_SE,  0x020E, FACING_SE, 0x0405, FACING_SE,  0x050F, FACING_W,  0x0604, FACING_SE,  0x0710, FACING_W,
        0x080B, FACING_SE,  0x0B12, FACING_W,  0x0C08, FACING_NE,  0x0C0C, FACING_NE, 0x0C0F, FACING_NW
    }};

    /**
     * Wonders: Port types; will be shuffled.
     * Port edge coordinates and facings are {@link #WOND_PORT_EDGE_FACING}.
     */
    private static final int WOND_PORT_TYPE[][] =
    {{
        // 4 players: 5 special, 4 generic:
        CLAY_PORT, ORE_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT,
        MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT
    }, {
        // 6 players: 6 special, 5 generic:
        CLAY_PORT, ORE_PORT, SHEEP_PORT, SHEEP_PORT, WHEAT_PORT, WOOD_PORT,
        MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT, MISC_PORT
    }};

    /**
     * Wonders: Hex land types on the several small islands, west to east.
     * Not shuffled; coordinates for these land hexes are {@link #WOND_LANDHEX_COORD_ISL}.
     * Dice numbers are {@link #WOND_DICENUM_ISL}.
     */
    private static final int WOND_LANDHEX_TYPE_ISL[][] =
    {{
        // 4 players:
        GOLD_HEX, WOOD_HEX, WHEAT_HEX, CLAY_HEX, GOLD_HEX
    }, {
        // 6 players:
        WOOD_HEX, GOLD_HEX, GOLD_HEX, GOLD_HEX
    }};

    /**
     * Wonders: Land hex coordinates for the several small islands, west to east.
     * Hex types for these small islands are {@link #WOND_LANDHEX_TYPE_ISL}.
     * Dice numbers are {@link #WOND_DICENUM_ISL}.
     */
    private static final int WOND_LANDHEX_COORD_ISL[][] =
    {{
        // 4 players:
        0x0502, 0x0303, 0x0104, 0x0D0C, 0x0D0E
    }, {
        // 6 players:
        0x0502, 0x0303, 0x0F11, 0x0512
    }};

    /**
     * Wonders: Dice numbers for hexes on the several small islands, same order as {@link #WOND_LANDHEX_COORD_ISL}.
     * Not shuffled.
     */
    private static final int WOND_DICENUM_ISL[][] =
    {{
        6, 4, 5, 2, 8
    }, {
        4, 6, 8, 6
    }};

    /**
     * Wonders: Special Node locations.  Subarrays for each of 3 types:
     * Great wall at desert wasteland; great bridge at strait; no-build nodes next to strait.
     * See {@link SOCScenario#K_SC_WOND} for details.
     *<P>
     * SOCBoardLarge additional Layout Parts {@code "N1", "N2", "N3"}.
     */
    private static final int WOND_SPECIAL_NODES[][][] =
    {{
        // 4 players:
        { 0x0606, 0x0806, 0x0805, 0x0A05, 0x0A06 },
        { 0x020B, 0x020C },
        { 0x000B, 0x020A, 0x020D, 0x040C },
    }, {
        // 6 players:
        { 0x0803, 0x0A03, 0x0A04, 0x0C04, 0x0C05 },
        { 0x040A, 0x040B, 0x0E0B, 0x0E0C },
        { 0x020A, 0x0409, 0x040C, 0x060B, 0x0C0B, 0x0E0A, 0x0E0D, 0x100C }
    }};


    ////////////////////////////////////////////
    //
    // Nested class for board factory
    //


    /**
     * Server-side implementation of {@link BoardFactory} to create {@link SOCBoardAtServer}s.
     * Called by game constructor via <tt>static {@link SOCGame#boardFactory}</tt>.
     * @author Jeremy D Monin
     * @since 2.0.00
     */
    public static class BoardFactoryAtServer implements BoardFactory
    {
        /**
         * Create a new Settlers of Catan Board based on <tt>gameOpts</tt>; this is a factory method.
         * Board size is based on <tt>maxPlayers</tt> and optional scenario (game option <tt>"SC"</tt>).
         *<P>
         * From v1.1.11 through all v1.x.xx, this was SOCBoard.createBoard.  Moved to new factory class in 2.0.00.
         *
         * @param gameOpts  if game has options, its map of {@link SOCGameOption}; otherwise null.
         *                  If <tt>largeBoard</tt>, and
         *                  {@link SOCBoardAtServer#getBoardSize(Map, int) getBoardSize(Map, int)}
         *                  gives a non-default size, <tt>"_BHW"</tt> will be added to <tt>gameOpts</tt>.
         * @param largeBoard  true if {@link SOCBoardLarge} should be used (v3 encoding)
         * @param maxPlayers Maximum players; must be default 4, or 6 from SOCGameOption "PL" &gt; 4 or "PLB"
         * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6
         *                  or (unlikely internal error) game option "_BHW" isn't known in SOCGameOption.getOption.
         */
        public SOCBoard createBoard
            (final Map<String,SOCGameOption> gameOpts, final boolean largeBoard, final int maxPlayers)
            throws IllegalArgumentException
        {
            if (! largeBoard)
            {
                return DefaultBoardFactory.staticCreateBoard(gameOpts, false, maxPlayers);
            } else {
                // Check board size, set _BHW if not default.
                final int boardHeightWidth = getBoardSize(gameOpts, maxPlayers);
                final int bH = boardHeightWidth >> 8, bW = boardHeightWidth & 0xFF;

                if (gameOpts != null)
                {
                    // gameOpts should never be null if largeBoard: largeBoard requires opt "SBL".
                    int bhw = 0;
                    SOCGameOption bhwOpt = gameOpts.get("_BHW");
                    if (bhwOpt != null)
                        bhw = bhwOpt.getIntValue();

                    if (((bH != SOCBoardLarge.BOARDHEIGHT_LARGE) || (bW != SOCBoardLarge.BOARDWIDTH_LARGE))
                        && (bhw != boardHeightWidth))
                    {
                        if (bhwOpt == null)
                            bhwOpt = SOCGameOption.getOption("_BHW", true);
                        if (bhwOpt != null)
                        {
                            bhwOpt.setIntValue(boardHeightWidth);
                            gameOpts.put("_BHW", bhwOpt);
                        } else {
                            throw new IllegalArgumentException("Internal error: Game opt _BHW not known");
                        }
                    }
                }

                return new SOCBoardAtServer
                    (gameOpts, maxPlayers, new IntPair(bH, bW));
            }
        }

    }  // nested class BoardFactoryAtServer


    /**
     * For layout testing, a listener to examine the new board's data
     * at various steps of {@link SOCBoardAtServer#makeNewBoard(Map)}.
     *
     *<H5>Steps in chronological order:</H5>
     *<UL>
     * <LI> Place the hexes of each land area, or other set of hexes:
     *      <P>
     *      These steps may occur multiple times per board because of multiple
     *      land areas/sets, and multiple times because layout may fail partway through
     *      and need to be restarted.
     *      <P>
     *      {@link #hexesProgress(SOCBoardAtServer, Map, int, int[])} is called for these steps.
     *  <UL>
     *   <LI> {@link #HEXES_PLACE}
     *   <LI> {@link #HEXES_CHECK_CLUMPS}
     *   <LI> {@link #HEXES_MOVE_FREQ_NUMS}
     *   <LI> After those steps, the hexes are added to {@link SOCBoardLarge#getLandHexCoordsSet()}
     *  </UL>
     * <LI> {@link #boardProgress(SOCBoardAtServer, Map, int)} is called for the remaining steps:
     * <LI> {@link #ALL_HEXES_PLACED}
     * <LI> {@link #FOG_HIDE_HEXES}
     * <LI> {@link #DONE_PORTS_PLACED}
     *</UL>
     *
     * Listener is called only for {@link SOCBoardLarge}, not the original {@link SOCBoard} 4- or 6-player boards.
     *
     * @see SOCBoardAtServer#setNewBoardProgressListener(NewBoardProgressListener)
     */
    public interface NewBoardProgressListener
    {
        /**
         * A set of hexes has been placed on the board, but not yet checked for clumps.<BR>
         * Next: {@link #HEXES_CHECK_CLUMPS}
         */
        public static final int HEXES_PLACE = 1;

        /**
         * A set of hexes has been checked for clumps, after being placed on the board.
         * Some hexes may have been swapped.<BR>
         * Next: {@link #HEXES_MOVE_FREQ_NUMS}
         */
        public static final int HEXES_CHECK_CLUMPS = 2;

        /**
         * A set of hexes has been checked for adjacent frequent numbers.
         * Some dice numbers may have been swapped.<BR>
         * Next: {@link #ALL_HEXES_PLACED}
         */
        public static final int HEXES_MOVE_FREQ_NUMS = 3;

        /**
         * All sets of hexes have been placed on the board.<BR>
         * Next: {@link #FOG_HIDE_HEXES} or {@link #DONE_PORTS_PLACED}
         */
        public static final int ALL_HEXES_PLACED = 4;

        /**
         * Hex placement is finished.
         * Because of game options, some hexes have been hidden behind fog.<BR>
         * {@code opts} is null here, because opts isn't passed into fog-hex-hiding method.<BR>
         * Next: {@link #DONE_PORTS_PLACED}
         */
        public static final int FOG_HIDE_HEXES = 5;

        /**
         * Hex placement and port placement is finished. All steps are now complete.
         */
        public static final int DONE_PORTS_PLACED = 6;

        /**
         * A hex-placement step of the board generation has completed. These steps may occur
         * multiple times per board if layout has multiple areas being placed,
         * and then occur multiple times because layout may fail partway through
         * and need to be restarted.
         *
         * @param board  The board being made
         * @param opts   Game options passed to board constructor, or {@code null} if none
         * @param step   {@code HEXES_*} generation step that's just completed;
         *     see {@link NewBoardProgressListener} javadoc for list
         * @param landPath  Coordinates of each hex that was placed or checked in this step
         */
        public void hexesProgress
            (final SOCBoardAtServer board, final Map<String, SOCGameOption> opts, final int step, final int[] landPath);

        /**
         * A step of the overall board generation has completed.
         *
         * @param board  The board being made
         * @param opts   Game options passed to board constructor, or {@code null} if none
         *     Also null for step {@link #FOG_HIDE_HEXES}.
         * @param step   Board generation step that's just completed;
         *     see {@link NewBoardProgressListener} javadoc for list
         */
        public void boardProgress
            (final SOCBoardAtServer board, final Map<String, SOCGameOption> opts, final int step);

    }  // nested interface NewBoardProgressListener

}
