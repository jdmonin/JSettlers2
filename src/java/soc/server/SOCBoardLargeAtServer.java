/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012 Jeremy D Monin <jeremy@nand.net>
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
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import soc.game.SOCBoardLarge;
import soc.game.SOCGameOption;
import soc.game.SOCVillage;
import soc.util.IntTriple;

/**
 * A subclass of {@link SOCBoardLarge} for the server, to isolate
 * {@link #makeNewBoard(Hashtable)} to simplify that parent class.
 * See SOCBoardLarge for more details.
 *<P>
 * A representation of a larger (up to 127 x 127 hexes) JSettlers board,
 * with an arbitrary mix of land and water tiles.
 * Implements {@link SOCBoard#BOARD_ENCODING_LARGE}.
 * Activated with {@link SOCGameOption} <tt>"PLL"</tt>.
 *<P>
 * A {@link SOCGame} uses this board; the board is not given a reference to the game, to enforce layering
 * and keep the board logic simple.  Game rules should be enforced at the game, not the board.
 * Calling board methods won't change the game state.
 *<P>
 * On this large sea board, there can optionally be multiple "land areas"
 * (groups of islands, or subsets of islands), if {@link #getLandAreasLegalNodes()} != null.
 * Land areas are groups of nodes on land; call {@link #getNodeLandArea(int)} to find a node's land area number.
 * The starting land area is {@link #getStartingLandArea()}, if players must start in a certain area.
 * During board setup, {@link #makeNewBoard(Hashtable)} calls
 * {@link #makeNewBoard_placeHexes(int[], int[], int[], int, SOCGameOption)}
 * once for each land area.  In some game scenarios, players and the robber can be
 * {@link #getPlayerExcludedLandAreas() excluded} from placing in some land areas.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCBoardLargeAtServer extends SOCBoardLarge
{
    /**
     * Create a new Settlers of Catan Board, with the v3 encoding.
     * @param gameOpts  if game has options, hashtable of {@link SOCGameOption}; otherwise null.
     * @param maxPlayers Maximum players; must be 4 or 6
     * @throws IllegalArgumentException if <tt>maxPlayers</tt> is not 4 or 6
     */
    public SOCBoardLargeAtServer(Hashtable<String,SOCGameOption> gameOpts, int maxPlayers)
        throws IllegalArgumentException
    {
        super(gameOpts, maxPlayers);
        // Nothing special for now at server
    }


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
        final SOCGameOption opt_breakClumps = (opts != null ? opts.get("BC") : null);

        SOCGameOption opt = (opts != null ? opts.get(SOCGameOption.K_SC_FOG) : null);
        final boolean hasScenarioFog = (opt != null) && opt.getBoolValue();

        // For scenario boards, use 3-player or 4-player or 6-player layout?
        // Always test maxPl for ==6 or < 4 ; actual value may be 6, 4, 3, or 2.
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

        // shuffle and place the land hexes, numbers, and robber:
        // sets robberHex, contents of hexLayout[] and numberLayout[].
        // Adds to landHexLayout and nodesOnLand.
        // Clears cachedGetlandHexCoords.
        // Also checks vs game option BC: Break up clumps of # or more same-type hexes/ports

        final int PORTS_TYPES_MAINLAND[], PORTS_TYPES_ISLANDS[];  // port types
        final int PORT_LOC_FACING_MAINLAND[], PORT_LOC_FACING_ISLANDS[];  // port edge locations and facings

        if (! hasScenarioFog)
        {
            landAreasLegalNodes = new HashSet[5];  // hardcoded max number of land areas
            // TODO revisit, un-hardcode, when we have multiple scenarios

            // - Mainland:
            makeNewBoard_placeHexes
                (makeNewBoard_landHexTypes_v1, LANDHEX_DICEPATH_MAINLAND, makeNewBoard_diceNums_v1, false, 1, opt_breakClumps);

            // - Outlying islands:
            makeNewBoard_placeHexes
                (LANDHEX_TYPE_ISLANDS, LANDHEX_COORD_ISLANDS_ALL, LANDHEX_DICENUM_ISLANDS, true, LANDHEX_LANDAREA_RANGES_ISLANDS, null);

            PORTS_TYPES_MAINLAND = PORTS_TYPE_V1;
            PORTS_TYPES_ISLANDS = PORT_TYPE_ISLANDS;
            PORT_LOC_FACING_MAINLAND = PORT_EDGE_FACING_MAINLAND;
            PORT_LOC_FACING_ISLANDS = PORT_EDGE_FACING_ISLANDS;

        } else {
            landAreasLegalNodes = new HashSet[( (maxPl == 6) ? 4 : 3 )];

            if (maxPl < 4)
            {
                // - East and West islands:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_MAIN_3PL, FOG_ISL_LANDHEX_COORD_MAIN_3PL, FOG_ISL_DICENUM_MAIN_3PL, true, 1, opt_breakClumps);

                // - "Fog Island" in the middle:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_FOG, FOG_ISL_LANDHEX_COORD_FOG_3PL, FOG_ISL_DICENUM_FOG_3PL, true, 2, null);

                PORTS_TYPES_MAINLAND = FOG_ISL_PORT_TYPE_3PL;
                PORT_LOC_FACING_MAINLAND = FOG_ISL_PORT_EDGE_FACING_3PL;
            }
            else if (maxPl == 4)
            {
                // - East and West islands:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_MAIN_4PL, FOG_ISL_LANDHEX_COORD_MAIN_4PL, FOG_ISL_DICENUM_MAIN_4PL, true, 1, opt_breakClumps);

                // - "Fog Island" in the middle:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_FOG, FOG_ISL_LANDHEX_COORD_FOG_4PL, FOG_ISL_DICENUM_FOG_4PL, true, 2, null);

                PORTS_TYPES_MAINLAND = FOG_ISL_PORT_TYPE_4PL;
                PORT_LOC_FACING_MAINLAND = FOG_ISL_PORT_EDGE_FACING_4PL;
            }
            else  // maxPl == 6
            {
                // - Northern main island:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_MAIN_6PL, FOG_ISL_LANDHEX_COORD_MAIN_6PL, FOG_ISL_DICENUM_MAIN_6PL, true, 1, opt_breakClumps);

                // - "Fog Island" in an arc from southwest to southeast:
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_FOG_6PL, FOG_ISL_LANDHEX_COORD_FOG_6PL, FOG_ISL_DICENUM_FOG_6PL, true, 2, opt_breakClumps);

                // - Gold Corners in southwest, southeast
                makeNewBoard_placeHexes
                    (FOG_ISL_LANDHEX_TYPE_GC, FOG_ISL_LANDHEX_COORD_GC, FOG_ISL_DICENUM_GC, false, 3, null);

                PORTS_TYPES_MAINLAND = FOG_ISL_PORT_TYPE_6PL;
                PORT_LOC_FACING_MAINLAND = FOG_ISL_PORT_EDGE_FACING_6PL;
            }
            PORTS_TYPES_ISLANDS = null;  // no ports inside fog island's random layout
            PORT_LOC_FACING_ISLANDS = null;
        }

        // - Players must start on mainland
        //   (for fog, the two large islands)
        startingLandArea = 1;

        // Set up legalRoadEdges:
        makeNewBoard_makeLegalRoadsFromLandNodes();
        makeNewBoard_makeLegalShipEdges();

        // consistency-check land areas
        if (landAreasLegalNodes != null)
        {
            for (int i = 1; i < landAreasLegalNodes.length; ++i)
                if (landAreasLegalNodes[i] == null)
                    throw new IllegalStateException("inconsistent landAreasLegalNodes: null idx " + i);
        }

        // Hide some land hexes behind fog, if the scenario does that
        if (hasScenarioFog)
        {
            final int[] FOGHEXES = (maxPl == 6)
                ? FOG_ISL_LANDHEX_COORD_FOG_6PL
                : ((maxPl < 4) ? FOG_ISL_LANDHEX_COORD_FOG_3PL : FOG_ISL_LANDHEX_COORD_FOG_4PL);
            makeNewBoard_hideHexesInFog(FOGHEXES);
        }

        // Add villages, if the scenario does that
        opt = (opts != null ? opts.get(SOCGameOption.K_SC_CLVI) : null);
        if ((opt != null) && opt.getBoolValue())
            setVillageAndClothLayout(SCEN_CLOTH_VILLAGE_NODES_DICE);
                // also sets board's "general supply"

        // copy and shuffle the ports, and check vs game option BC
        int[] portTypes_main = new int[PORTS_TYPES_MAINLAND.length];
        int[] portTypes_islands;
        System.arraycopy(PORTS_TYPES_MAINLAND, 0, portTypes_main, 0, portTypes_main.length);
        if ((maxPl == 6) || ! hasScenarioFog)
            makeNewBoard_shufflePorts(portTypes_main, opt_breakClumps);
        if (PORTS_TYPES_ISLANDS != null)
        {
            portTypes_islands = new int[PORTS_TYPES_ISLANDS.length];
            System.arraycopy(PORTS_TYPES_ISLANDS, 0, portTypes_islands, 0, portTypes_islands.length);
            makeNewBoard_shufflePorts(portTypes_islands, null);
        } else {
            portTypes_islands = null;
        }

        // copy port types to beginning of portsLayout[]
        portsCount = PORTS_TYPES_MAINLAND.length;
        if (PORTS_TYPES_ISLANDS != null)
            portsCount += PORTS_TYPES_ISLANDS.length;
        if ((portsLayout == null) || (portsLayout.length != (3 * portsCount)))
            portsLayout = new int[3 * portsCount];
        System.arraycopy(portTypes_main, 0, portsLayout, 0, portTypes_main.length);
        if (PORTS_TYPES_ISLANDS != null)
            System.arraycopy(portTypes_islands, 0,
                portsLayout, portTypes_main.length, portTypes_islands.length);

        // place the ports (hex numbers and facing) within portsLayout[] and nodeIDtoPortType.
        // fill out the ports[] vectors with node coordinates where a trade port can be placed.
        nodeIDtoPortType = new Hashtable<Integer, Integer>();

        // - main island(s):
        // i == port type array index
        // j == port edge & facing array index
        final int L = portTypes_main.length;
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
     * @param shuffleDiceNumbers  If true, shuffle the dice <tt>number</tt>s before placing along <tt>numPath</tt>.
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
        (int[] landHexType, final int[] numPath, final int[] number, final boolean shuffleDiceNumbers,
         final int landAreaNumber, SOCGameOption optBC)
        throws IllegalStateException, IllegalArgumentException
    {
        final int[] pathRanges = { landAreaNumber, numPath.length };  // 1 range, uses all of numPath
        makeNewBoard_placeHexes
            (landHexType, numPath, number, shuffleDiceNumbers, pathRanges, optBC);
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
     *                    For the Fog Island (scenario option {@link SOCGameOption#K_SC_FOG _SC_FOG}),
     *                    one land area contains some water.  So, <tt>landHexType[]</tt> may contain {@link #WATER_HEX}.
     * @param numPath  Coordinates within {@link #hexLayoutLg} (also within {@link #numberLayoutLg}) for each land hex;
     *                    same array length as <tt>landHexType[]</tt>.
     *                    <BR> <tt>landAreaPathRanges[]</tt> tells how to split this array of land hex coordindates
     *                    into multiple Land Areas.
     * @param number   Numbers to place into {@link #numberLayoutLg} for each land hex;
     *                    array length is <tt>landHexType[].length</tt> minus 1 for each desert or water in <tt>landHexType[]</tt>
     * @param shuffleDiceNumbers  If true, shuffle the dice <tt>number</tt>s before placing along <tt>numPath</tt>.
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
     * @throws IllegalArgumentException if <tt>landAreaPathRanges</tt> is null or has an uneven length, <BR>
     *             or if the total length of its land areas != <tt>numPath.length</tt>, <BR>
     *             or if <tt>landHexType</tt> contains {@link #FOG_HEX}, <BR>
     *             or if {@link SOCBoard#makeNewBoard_checkLandHexResourceClumps(Vector, int)}
     *                 finds an invalid or uninitialized hex coordinate (hex type -1)
     * @see #makeNewBoard_placeHexes(int[], int[], int[], int, SOCGameOption)
     */
    private final void makeNewBoard_placeHexes
        (int[] landHexType, final int[] numPath, int[] number, final boolean shuffleDiceNumbers,
         final int[] landAreaPathRanges, SOCGameOption optBC)
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
                else if (landHexType[i] == WATER_HEX)
                {
                    numberLayoutLg[r][c] = 0;  // Fog Island's landarea has some water shuffled in
                }
                else if (landHexType[i] == FOG_HEX)
                {
                    throw new IllegalArgumentException("landHexType can't contain FOG_HEX");
                }
                else
                {
                    // place the numbers
                    final int diceNum = number[cnt];
                    numberLayoutLg[r][c] = diceNum;
                    cnt++;

                    if (shuffleDiceNumbers && ((diceNum == 6) || (diceNum == 8)))
                        redHexes.add(numPath[i]);
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

            if (shuffleDiceNumbers && ! clumpsNotOK)
            {
                // Separate adjacent "red numbers" (6s, 8s)
                //   and make sure gold hex dice aren't too frequent
                makeNewBoard_placeHexes_moveFrequentNumbers(numPath, redHexes);
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
     * For {@link #makeNewBoard(Hashtable)}, after placing
     * land hexes and dice numbers into {@link #hexLayoutLg},
     * separate adjacent "red numbers" (6s, 8s)
     * and make sure gold hex dice aren't too frequent.
     * For algorithm details, see comments in this method.
     *<P>
     * If using {@link #FOG_HEX}, no fog should be on the
     * board when calling this method: Don't call after
     * {@link #makeNewBoard_hideHexesInFog(int[])}.
     *
     * @param numPath   Coordinates for each hex being placed; may contain water
     * @param redHexes  Hex coordinates of placed "red" (frequent) dice numbers (6s, 8s)
     * @return  true if able to move all adjacent frequent numbers, false if some are still adjacent.
     * @throws IllegalStateException if a {@link #FOG_HEX} is found
     */
    private final boolean makeNewBoard_placeHexes_moveFrequentNumbers
        (final int[] numPath, ArrayList<Integer> redHexes)
        throws IllegalStateException
    {
        if (redHexes.isEmpty())
            return true;

        // TODO Before anything else, check for frequent gold hexes and swap their numbers with random other hexes.

        // Overall plan:

        // Make an empty list swappedNums to hold all swaps, in case we undo them all and retry
        // Duplicate redHexes in case we need to undo all swaps and retry
        //   (This can be deferred until adjacent redHexes are found)
        // numRetries = 0
        // Top of retry loop:
        // Make sets otherCoastalHexes, otherHexes: all land hexes in numPath not adjacent to redHexes
        //   (This can be deferred until adjacent redHexes are found)
        //   otherCoastalHexes holds ones at the edge of the board,
        //   which have fewer adjacent land hexes, and thus less chance of
        //   taking up the last available un-adjacent places
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
        //   because it wil have 0 adjacents after the swap
        // - Do the Swapping Algorithm on it
        //   (updates redHexes, otherCoastalHexes, otherHexes; see below)
        // - If no swap was available, we should undo all and retry.
        //   (see below)
        // Now, if redHexes is empty, we're done.
        //   Return true.
        //
        // If we need to undo all and retry:
        // - ++numRetries
        // - If numRetries > 5, we've tried enough.
        //   Either the swaps we've done will have to do, or caller will make a new board.
        //   Return false.
        // - Otherwise, restore redHexes from the backup we made
        // - Go backwards through the list of swappedNums, reversing each swap
        // - Jump back to "Make sets otherCoastalHexes, otherHexes"

        // Swapping Algorithm:
        //   Returns a pair (old location, swapped location), or nothing if we failed to swap.
        // - If otherCoastalHexes and otherHexes are empty:
        //   Return nothing.
        // - Pick a random hex from otherCoastalHexes if not empty, otherwise from otherHexes
        // - Swap the numbers and build the pair to return
        // - Remove new location and its adjacents from otherCoastalHexes or otherHexes
        // - Remove old location from redHexes
        // - Check each of its adjacent non-red lands, to see if each should be added to otherCoastalHexes or otherHexes
        //     because the adjacent no longer has any adjacent reds
        // - Check each of its adjacent reds, to see if the adjacent no longer has adjacent reds
        //     If so, we won't need to move it: remove from redHexes
        // - Return the pair.

        // Implementation:

        ArrayList<IntTriple> swappedNums = null;
        ArrayList<Integer> redHexesBk = null;
        int numRetries = 0;
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
                if (redHexesBk == null)
                    redHexesBk = new ArrayList<Integer>(redHexes);
                if (otherCoastalHexes == null)
                {
                    otherCoastalHexes = new HashSet<Integer>();
                    otherHexes = new HashSet<Integer>();
                    makeNewBoard_placeHexes_moveFrequentNumbers_buildArrays
                        (numPath, redHexes, otherCoastalHexes, otherHexes);
                }

                // Now, swap.
                IntTriple midSwap = makeNewBoard_placeHexes_moveFrequentNumbers_swapOne
                    (h0, i, redHexes, otherCoastalHexes, otherHexes);
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
                //   because it wil have 0 adjacents after the swap
                // - Do the Swapping Algorithm on it
                //   (updates redHexes, otherCoastalHexes, otherHexes)
                // - If no swap was available, we should undo all and retry.

                while (! redHexes.isEmpty())
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
                    if (redHexesBk == null)
                        redHexesBk = new ArrayList<Integer>(redHexes);
                    if (otherCoastalHexes == null)
                    {
                        otherCoastalHexes = new HashSet<Integer>();
                        otherHexes = new HashSet<Integer>();
                        makeNewBoard_placeHexes_moveFrequentNumbers_buildArrays
                            (numPath, redHexes, otherCoastalHexes, otherHexes);
                    }

                    // Now, swap.
                    IntTriple hexnumSwap;
                    if (adjacRed > 1)
                    {
                        hexnumSwap = makeNewBoard_placeHexes_moveFrequentNumbers_swapOne
                            (h0, 0, redHexes, otherCoastalHexes, otherHexes);
                    } else {
                        // swap the adjacent instead; the algorithm will also remove this one from redHexes
                        // because it wil have 0 adjacents after the swap
                        final int iAdjac1 = redHexes.indexOf(Integer.valueOf(hAdjac1));
                        hexnumSwap = makeNewBoard_placeHexes_moveFrequentNumbers_swapOne
                            (hAdjac1, iAdjac1, redHexes, otherCoastalHexes, otherHexes);
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

                }  // while (redHexes not empty)
            }

            if (retry)
            {
                // undo all and retry:
                // - ++numRetries
                // - If numRetries > 5, we've tried enough.
                //   Either the swaps we've done will have to do, or caller will make a new board.
                //   Return false.
                // - Otherwise, restore redHexes from the backup we made
                // - Go backwards through the list of swappedNums, reversing each swap

                ++numRetries;
                if (numRetries > 5)
                    return false;

                redHexes = new ArrayList<Integer>(redHexesBk);
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
            }

        } while (retry);

        return true;
    }

    /**
     * Build sets used for {@link #makeNewBoard_placeHexes_moveFrequentNumbers(int[], ArrayList)}.
     * Together, otherCoastalHexes and otherHexes are all land hexes in numPath not adjacent to redHexes.
     * otherCoastalHexes holds ones at the edge of the board,
     * which have fewer adjacent land hexes, and thus less chance of
     * taking up the last available un-adjacent places.
     * @param numPath   Coordinates for each hex being placed; may contain water
     * @param redHexes  Hex coordinates of placed "red" (frequent) dice numbers (6s, 8s)
     * @param otherCoastalHexes  Empty set to build here
     * @param otherHexes         Empty set to build here
     * @throws IllegalStateException if a {@link #FOG_HEX} is found
     */
    private final void makeNewBoard_placeHexes_moveFrequentNumbers_buildArrays
        (final int[] numPath, final ArrayList<Integer> redHexes,
         HashSet<Integer> otherCoastalHexes, HashSet<Integer> otherHexes)
        throws IllegalStateException
    {
        for (int h : numPath)
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
            // (some may have already been removed from redHexes)
            {
                final int dnum = getNumberOnHexFromCoord(h);
                if ((dnum <= 0) || (dnum == 6) || (dnum == 8))
                    continue;
            }

            final Vector<Integer> ahex = getAdjacentHexesToHex(h, false);
            boolean hasAdjacentRed = false;
            if (ahex != null)
            {
                for (int ah : ahex)
                {
                    final int dnum = getNumberOnHexFromCoord(ah);
                    if ((dnum == 6) || (dnum == 8))
                    {
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
     * The swapping algorithm for {@link #makeNewBoard_placeHexes_moveFrequentNumbers(int[], ArrayList)}.
     * If we can, pick a hex in otherCoastalHexes or otherHexes, swap and remove <tt>swaphex</tt>
     * from redHexes, then remove/add hexes from/to otherCoastalHexes, otherHexes, redHexes.
     * See comments for details.
     * @param swaphex  Hex coordinate with a frequent number that needs to be swapped
     * @param swapi    Index of <tt>swaphex</tt> within <tt>redHexes</tt>;
     *                 this is for convenience because the caller is iterating through <tt>redHexes</tt>,
     *                 and this method might remove elements below <tt>swapi</tt>.
     *                 See return value "index delta".
     * @param redHexes  Hex coordinates of placed "red" (frequent) dice numbers
     * @param otherCoastalHexes  Land hexes not adjacent to "red" numbers, at the edge of the island,
     *          for swapping dice numbers.  Coastal hexes have fewer adjacent land hexes, and
     *          thus less chance of taking up the last available un-adjacent places when swapped.
     * @param otherHexes   Land hexes not adjacent to "red" numbers, not at the edge of the island.
     * @return The old frequent-number hex coordinate, its swapped coordinate, and the "index delta",
     *         or null if nothing was available to swap.
     *         If the "index delta" != 0, a hex at <tt>redHexes[j]</tt> was removed
     *         for at least one <tt>j &lt; swapi</tt>, and the caller's loop iterating over <tt>redHexes[]</tt>
     *         should adjust its index (<tt>swapi</tt>) so no elements are skipped; the delta is always negative.
     */
    private final IntTriple makeNewBoard_placeHexes_moveFrequentNumbers_swapOne
        (final int swaphex, int swapi, ArrayList<Integer> redHexes,
         HashSet<Integer> otherCoastalHexes, HashSet<Integer> otherHexes)
    {
        // - If otherCoastalHexes and otherHexes are empty:
        //   Return nothing.
        if (otherCoastalHexes.isEmpty() && otherHexes.isEmpty())
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

        // - Swap the numbers and build the pair to return
        IntTriple triple = new IntTriple(swaphex, ohex, 0);
        {
            final int rs = swaphex >> 8,
                      cs = swaphex & 0xFF,
                      ro = ohex >> 8,
                      co = ohex & 0xFF,
                      ntmp = numberLayoutLg[ro][co];
            numberLayoutLg[ro][co] = numberLayoutLg[rs][cs];
            numberLayoutLg[rs][cs] = ntmp;
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
                if (otherHexes.contains(h))
                    otherHexes.remove(h);
            }
        }

        // - Remove old location from redHexes
        // - Check each of its adjacent non-red lands, to see if each should be added to otherCoastalHexes or otherHexes
        //     because the adjacent no longer has any adjacent reds
        // - Check each of its adjacent reds, to see if the adjacent no longer has adjacent reds
        //     If so, we won't need to move it: remove from redHexes
        redHexes.remove(Integer.valueOf(swaphex));
        ahex = getAdjacentHexesToHex(swaphex, false);
        if (ahex != null)
        {
            int idelta = 0;

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
                            if (j < swapi)
                            {
                                --idelta;
                                --swapi;
                                triple.c = idelta;
                            }
                        }
                    } else {
                        // no longer has any adjacent reds; can add to otherCoastalHexes or otherHexes
                        if (isHexCoastline(h))
                        {
                            if (! otherCoastalHexes.contains(hInt))
                                otherCoastalHexes.add(hInt);
                        } else{
                            if (! otherHexes.contains(hInt))
                                otherHexes.add(hInt);
                        }
                    }
                }
            }
        }

        // - Return the dice-number swap info.
        return triple;
    }

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
     *                    Any hex coordinates here which are {@link #WATER_HEX} are ignored.
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
            final int hex = landHexCoords[i];
            if (getHexTypeFromCoord(hex) == WATER_HEX)
                continue;
            final int[] nodes = getAdjacentNodesToHex(hex);
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
                c = 1;  // odd hex row hexes start at 1
            } else {
                c = 2;  // top row, even row hexes start at 2
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
                    // edge of the board; this check is also isHexAtBoardMargin(hc)
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
     * @param hexCoords  Coordinates of each hex to hide in the fog
     * @throws IllegalStateException  if any hexCoord is already {@link #FOG_HEX} within {@link #hexLayoutLg}
     * @see #revealFogHiddenHexPrep(int)
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
     * The hex coordinates of all land hexes.  Please treat as read-only.
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
     * @see #getLandHexCoordsSet()
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
     * For {@link #makeNewBoard(Hashtable)}, with the {@link SOCGameOption#K_SC_CLVI Cloth Village} scenario,
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
     * {@link SOCBoard#MISC_PORT} is 0.
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
    private static final int LANDHEX_COORD_MAINLAND_FOG[] =
    {
        // 1st row ok
        0x0303, 0x0305, // 2nd row 1st,2nd
                0x0504, 0x0506, // middle row 2nd,3rd
                0x0705  // 4th row 2nd
        // 5th row ok
    };
     */

    /**
     * My sample board layout: Outlying islands' cloth village node locations and dice numbers.
     * Index 0 is the board's "general supply" cloth count.
     * Index 1 is each village's starting cloth count, from {@link SOCVillage#STARTING_CLOTH}.
     * Further indexes are the locations and dice.
     * Paired for each village: [i] = node, [i+1] = dice number.
     * For testing only: An actual cloth village scenario would have a better layout.
     * @see SOCGameOption#K_SC_CLVI
     * @see #setVillageAndClothLayout(int[])
     */
    private static final int SCEN_CLOTH_VILLAGE_NODES_DICE[] =
    {
        SOCVillage.STARTING_GENERAL_CLOTH,  // Board's "general supply" cloth count
        SOCVillage.STARTING_CLOTH,
        0x610, 6,  // SE point of NE island
        0xA0D, 5,  // N point of SE island
        0xE0B, 9,  // SW point of SE island
        0xE05, 4   // midpoint of SW island
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


    ////////////////////////////////////////////
    //
    // Fog Island scenario Layout
    //   Has 3-player, 4-player, 6-player versions;
    //   FOG_ISL_LANDHEX_TYPE_FOG[] is shared between 3p and 4p.
    //

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
     * No defined NumPath; as long as 6 and 8 aren't adjacent, all is OK.
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
     * No defined NumPath; as long as 6 and 8 aren't adjacent, all is OK.
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
     * No defined NumPath; as long as 6 and 8 aren't adjacent, all is OK.
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
     * No defined NumPath; as long as 6 and 8 aren't adjacent, all is OK.
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
     * No defined NumPath; as long as 6 and 8 aren't adjacent, all is OK.
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
     * No defined NumPath; as long as 6 and 8 aren't adjacent, all is OK.
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

}
