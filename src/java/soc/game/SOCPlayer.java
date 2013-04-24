/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2013 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
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

import soc.disableDebug.D;

import soc.message.SOCMessage;
import soc.util.IntPair;
import soc.util.NodeLenVis;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.Vector;


/**
 * A class for holding and manipulating player data.
 * The player exists within one SOCGame, not persistent between games like SOCPlayerClient or SOCClientData.
 *<P>
 * At the start of each player's turn, {@link SOCGame#updateAtTurn()} will call {@link SOCPlayer#updateAtTurn()},
 * then call the current player's {@link #updateAtOurTurn()}.
 *<P>
 * For more information about the "legal" and "potential" road/settlement/city terms,
 * see page 61 of Robert S Thomas' dissertation.  Briefly:
 * "Legal" locations are where pieces can be placed, according to the game rules.
 * "Potential" locations are where pieces can be placed <em>soon</em>, based on the
 * current state of the game board.  For example, every legal settlement location is
 * also a potential settlement during initial placement (game state {@link SOCGame#START1A START1A}
 * through {@link SOCGame#START3A START3A}.  Once the player's final initial settlement is placed,
 * all potential settlement locations are cleared.  Only when they build 2 connected road
 * segments, will another potential settlement location be set.
 *<P>
 * If the board layout changes from game to game, as with {@link SOCLargeBoard} /
 * {@link SOCBoard#BOARD_ENCODING_LARGE}, use these methods to update the player's board data
 * after {@link SOCBoard#makeNewBoard(Hashtable)}, in this order:
 *<UL>
 * <LI> {@link #getPlayerNumber()}.{@link SOCPlayerNumbers#setLandHexCoordinates(int[]) setLandHexCoordinates(int[])}
 * <LI> {@link #setPotentialAndLegalSettlements(Collection, boolean, HashSet[])}
 * <LI> Optionally, {@link #setRestrictedLegalShips(int[])}
 *</UL>
 *<P>
 * On the {@link SOCLargeBoard large sea board}, our list of the player's roads also
 * contains their ships.  They are otherwise treated separately.
 *
 * @author Robert S Thomas
 */
public class SOCPlayer implements SOCDevCardConstants, Serializable, Cloneable
{
    /**
     * Number of ships a player can build (15) if {@link SOCGame#hasSeaBoard}.
     * @see #getNumPieces(int)
     * @since 2.0.00
     */
    public static final int SHIP_COUNT = 15;

    /**
     * the name of the player
     */
    private String name;

    /**
     * The integer id for this player (0 to n-1).
     */
    private int playerNumber;

    /**
     * the game that this player is in
     */
    private SOCGame game;

    /**
     * the number of pieces not in play.
     * Indexes match SOCPlayingPiece constants:
     * {@link SOCPlayingPiece#ROAD},
     * {@link SOCPlayingPiece#SETTLEMENT},
     * {@link SOCPlayingPiece#CITY},
     * {@link SOCPlayingPiece#SHIP}.
     */
    private int[] numPieces;

    /**
     * For scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * the number of {@link SOCShip}s that are converted to warships.
     * See {@link #getNumWarships()} for details.
     * @since 2.0.00
     */
    private int numWarships;

    /**
     * a list of this player's pieces in play
     * (does not include any {@link #fortress}).
     */
    private Vector<SOCPlayingPiece> pieces;

    /**
     * a list of this player's roads and ships in play.
     * Although roads and ships are kept together here,
     * in {@link #numPieces}[] they're counted separately.
     * @see #getRoadOrShip(int)
     */
    private Vector<SOCRoad> roads;

    /**
     * a list of this player's settlements in play
     */
    private Vector<SOCSettlement> settlements;

    /**
     * a list of this player's cities in play
     */
    private Vector<SOCCity> cities;

    /**
     * For scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * the "pirate fortress" that this player must defeat to win.
     * The player's warships are used to defeat the fortress; see {@link #getNumWarships()}.
     * Null if fortress has been defeated and converted to a settlement.
     * Null unless game has that scenario option.
     *<P>
     * There is no setFortress method; use putPiece. For details see {@link #getFortress()}.
     * @since 2.0.00
     */
    private SOCFortress fortress;

    /**
     * The node coordinate of our most recent settlement.
     * Useful during initial placement.
     */
    protected int lastSettlementCoord;

    /**
     * The edge coordinate of our most recent road or ship.
     * Useful during initial placement.
     */
    protected int lastRoadCoord;

    /**
     * length of the longest road for this player
     */
    private int longestRoadLength;

    /**
     * list of longest road / longest trade-route paths
     */
    private Vector<SOCLRPathData> lrPaths;

    /**
     * how many of each resource this player has
     */
    private SOCResourceSet resources;

    /**
     * Resources gained from dice roll of the current turn.
     * Set in {@link #addRolledResources(SOCResourceSet)},
     * cleared in {@link #updateAtTurn()}.
     * @since 2.0.00
     */
    private SOCResourceSet rolledResources;

    /**
     * For use at server by SOCGame, if the player's previous action this turn was a
     * bank trade, the resources involved.  Used to decide if they can undo the trade.
     *<P>
     * Ignore unless {@link SOCGame#canUndoBankTrade(SOCResourceSet, SOCResourceSet)} is true.
     *
     * @since 1.1.13
     */
    SOCResourceSet lastActionBankTrade_give, lastActionBankTrade_get;

    /**
     * server-only total count of how many of each known resource the player has received this game
     * from dice rolls.
     * The used indexes are {@link SOCResourceConstants#CLAY} - {@link SOCResourceConstants#WOOD}.
     * @since 1.1.09
     */
    private int[] resourceStats;

    /**
     * how many of each type of development card this player has
     */
    private SOCDevCardSet devCards;

    /**
     * how many knights this player has in play
     */
    private int numKnights;

    /**
     * the number of victory points for settlements and cities
     */
    private int buildingVP;

    /**
     * The number of Special Victory Points (SVPs), which are awarded in certain game scenarios on the large sea board.
     * Does not include VPs from {@link #numCloth}, cloth is part of {@link #getTotalVP()}.
     *<P>
     * When updating this value, if the SVP came from a piece, also set or check {@link SOCPlayingPiece#specialVP}
     * and {@link SOCPlayingPiece#specialVPEvent}.
     * @see #svpInfo
     * @since 2.0.00
     */
    private int specialVP;

    /**
     * The details behind the total SVP count in {@link #specialVP}, or null if none.
     * This is filled at the server (because it has the text strings) when
     * {@link SOCGame#scenarioEventListener} != null, and sent out to clients.
     * @see #addSpecialVPInfo(int, String)
     */
    private ArrayList<SpecialVPInfo> svpInfo;

    /**
     * the final total score (pushed from server at end of game),
     * or 0 if no score has been forced.
     * 
     * @see #forceFinalVP(int)
     */
    private int finalTotalVP;

    /**
     * For some game scenarios, how many cloth does this player have?
     * Every 2 pieces of cloth is worth 1 VP.
     * @see #specialVP
     * @since 2.0.00
     */
    private int numCloth;

    /**
     * this flag is true if the player needs to discard
     * and must pick which resources to lose.
     * @see #needToPickGoldHexResources
     */
    private boolean needToDiscard;

    /**
     * If nonzero, waiting for player to pick this many gold-hex resources,
     * after a dice roll or placing their final initial settlement.
     *<P>
     * Game state {@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE}
     * or {@link SOCGame#STARTS_WAITING_FOR_PICK_GOLD_RESOURCE}.
     * @see #needToDiscard
     * @since 2.0.00
     */
    private int needToPickGoldHexResources;

    /**
     * all of the nodes that this player's roads and ships touch;
     * this is used to calculate longest road / longest trade route.
     */
    private Vector<Integer> roadNodes;

    /**
     * A graph of what adjacent nodes are connected by this
     * player's roads and ships.
     * If <tt>roadNodeGraph</tt>[node1][node2], then a road or ship
     * connects them; <tt>roadNodeGraph</tt>[node2][node1]
     * will also be true.
     *<P>
     * Implementation: <BR>
     *    Key = <tt>node1</tt>'s coordinate {@link Integer}.<BR>
     *    Value = int[] array of length 3, for the 3 adjacent nodes.<BR>
     *    If an element of the array is 0, no connection.
     *    A non-zero element is <tt>node2</tt>'s coordinate
     *    at the other end of the road connecting them.
     * @see #isConnectedByRoad(int, int)
     */
    private Hashtable<Integer,int[]> roadNodeGraph;

    /**
     * a list of edges where it is legal to place a road.
     * an edge is legal if a road could eventually be
     * placed there.
     *<P>
     * If not {@link SOCGame#hasSeaBoard}, initialized in constructor
     * from {@link SOCBoard#initPlayerLegalRoads()}.
     *<P>
     * If {@link SOCGame#hasSeaBoard}, empty until {@link SOCBoard#makeNewBoard(Hashtable)
     * and {@link SOCGame#startGame()}, because the board layout and legal settlements
     * vary from game to game.
     */
    private HashSet<Integer> legalRoads;

    /**
     * a set of nodes where it is legal to place a
     * settlement. A node is legal if a settlement
     * can ever be placed there.
     * Placing a settlement will clear its node and adjacent nodes.
     *<P>
     * Key = node coordinate, as {@link Integer}.
     * If {@link HashSet#contains(Object) legalSettlements.contains(new Integer(nodeCoord))},
     * then <tt>nodeCoord</tt> is a legal settlement.
     *<P>
     * If not {@link SOCGame#hasSeaBoard}, initialized in constructor
     * from {@link SOCBoard#initPlayerLegalAndPotentialSettlements()}.
     *<P>
     * If {@link SOCGame#hasSeaBoard}, empty until {@link SOCBoard#makeNewBoard(Hashtable)
     * and {@link SOCGame#startGame()}, because the board layout and legal settlements vary
     * from game to game.
     * @see #potentialSettlements
     * @see SOCBoard#nodesOnLand
     */
    private HashSet<Integer> legalSettlements;

    /**
     * The most recently added node from {@link #addLegalSettlement(int)}, or 0.
     * @since 2.0.00
     */
    private int addedLegalSettlement;

    /**
     * a list of edges where it is legal to place a ship.
     * an edge is legal if a ship could eventually be
     * placed there.
     *<P>
     * If the game doesn't use the large sea board (<tt>! {@link SOCGame#hasSeaBoard}</tt>),
     * this set is empty but non-null.
     *<P>
     * May be updated during game play by {@link #updateLegalShipsAddHex(int)}.
     * @see #legalShipsRestricted
     * @since 2.0.00
     */
    private HashSet<Integer> legalShips;

    /**
     * A list of edges if the legal sea edges for ships are restricted
     * by the game's scenario ({@link SOCGameOption#K_SC_PIRI _SC_PIRI}),
     * or {@code null} if all sea edges are legal for ships.
     * If the player has no legal ship edges, this list is empty (not null).
     *<P>
     * This list, separate from {@link #legalShips}, is necessary because some methods
     * change {@code legalShips} by removing or adding edges.
     *
     * @since 2.0.00
     */
    private HashSet<Integer> legalShipsRestricted;

    /**
     * a set of edges where a road could be placed
     * on the next turn.
     * At start of the game, this is clear/empty.
     * Elements are set true when the player places adjacent settlements or roads, via
     * {@link #updatePotentials(SOCPlayingPiece)}.
     * Elements are set false when a road or ship is placed on their edge.
     */
    private HashSet<Integer> potentialRoads;

    /**
     * a set of nodes where a settlement could be
     * placed on the next turn.
     * At start of the game, all {@link #legalSettlements} are also potential.
     * When the second settlement is placed, <tt>potentialSettlements</tt> is cleared,
     * and then re-set via {@link #updatePotentials(SOCPlayingPiece) updatePotentials(SOCRoad)}.
     * Placing a settlement will clear its node and adjacent nodes.
     *<P>
     * Key = node coordinate, as {@link Integer}.
     * If {@link HashSet#contains(Object) potentialSettlements.contains(new Integer(nodeCoord))},
     * then this is a potential settlement.
     * @see #legalSettlements
     * @see SOCBoard#nodesOnLand
     */
    private HashSet<Integer> potentialSettlements;

    /**
     * a set of nodes where a city could be
     * placed on the next turn.
     * At start of the game, this is clear/empty because the player has no settlements yet.
     * Elements are set true when the player places settlements, via
     * {@link #updatePotentials(SOCPlayingPiece)}.
     * Elements are set false when cities are placed.
     * Unlike other piece types, there is no "<tt>legalCities</tt>" set,
     * because we use {@link #legalSettlements} before placing a settlement,
     * and settlements can always become cities.
     */
    private HashSet<Integer> potentialCities;

    /**
     * a set of edges where a ship could be placed
     * on the next turn.
     * At start of the game, this is clear/empty.
     * Elements are set true when the player places adjacent settlements or ships, via
     * {@link #updatePotentials(SOCPlayingPiece)}.
     * Elements are set false when a road or ship is placed on their edge.
     *<P>
     * If the game doesn't use the large sea board (<tt>! {@link SOCGame#hasSeaBoard}</tt>),
     * this set is empty but non-null.
     * @since 2.0.00
     */
    private HashSet<Integer> potentialShips;

    /**
     * a boolean array stating wheather this player is touching a
     * particular kind of port.
     * Index == port type, in range {@link SOCBoard#MISC_PORT} to {@link SOCBoard#WOOD_PORT}
     */
    private boolean[] ports;

    /**
     * this is the current trade offer that this player is making, or null if none
     */
    private SOCTradeOffer currentOffer;

    /**
     * this is true if the player played a development card this turn
     */
    private boolean playedDevCard;

    /**
     * this is true if the player asked to reset the board this turn
     */
    private boolean boardResetAskedThisTurn;

    /**
     * In 6-player mode, is the player asking to build during the Special Building Phase?
     * @see #hasSpecialBuiltThisTurn
     * @since 1.1.08
     */
    private boolean askedSpecialBuild;

    /**
     * In 6-player mode, has the player already built during the Special Building Phase?
     * @see #askedSpecialBuild
     * @since 1.1.09
     */
    private boolean hasSpecialBuiltThisTurn;

    /**
     * Track one-time player events for scenarios on the {@link SOCGame#hasSeaBoard large sea board}.
     * As events occur during a game, each one's {@link SOCScenarioPlayerEvent#flagValue} bit is set here.
     *<P>
     * Example events: {@link SOCScenarioPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA},
     * {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     *<P>
     * Bits are kept here, not in separate boolean fields, to keep them together and send over the network.
     * Not all player events are here; some can't be represented in a single flag bit,
     * such as {@link #scenario_svpFromEachLandArea_bitmask}.
     *
     * @see #getScenarioPlayerEvents()
     * @since 2.0.00
     */
    private int scenario_playerEvents_bitmask;

    /**
     * For some scenarios on the {@link SOCGame#hasSeaBoard large sea board},
     * bitmask: true if the player has been given a Special Victory Point for placing
     * a settlement in a given new land area.
     * The bit value is (1 &lt;&lt; (landAreaNumber - 1)).
     *
     * @see SOCScenarioPlayerEvent#SVP_SETTLED_EACH_NEW_LANDAREA
     * @see #scenario_playerEvents_bitmask
     * @see #getScenarioSVPLandAreas()
     * @since 2.0.00
     */
    private int scenario_svpFromEachLandArea_bitmask;

    /**
     * The land area(s) of the player's initial settlements,
     * if {@link SOCGame#hasSeaBoard} and the board defines Land Areas
     * (<tt>null != {@link SOCBoardLarge#getLandAreasLegalNodes()}</tt>).
     * Used for Special Victory Points in some scenarios.
     * 0 otherwise.
     *<P>
     * If both initial settlements are in the same land area,
     * then {@link #startingLandArea2} is 0.
     *<P>
     * Also: If {@link SOCBoardLarge#getStartingLandArea()} != 0,
     * the players must start in that land area.  This is enforced
     * at the server during makeNewBoard, by using that land area
     * for the only initial potential/legal settlement locations.
     *
     * @since 2.0.00
     */
    private int startingLandArea1, startingLandArea2;
        // skip startingLandArea3: Although some scenarios have 3 initial settlements,
        // none have placement in 3 initial land areas and SVPs for settling new areas.

    /**
     * this is true if this player is a robot
     */
    private boolean robotFlag;

    /**
     * Is this robot connection the built-in robot (not a 3rd-party),
     * with the original AI?
     * @see soc.message.SOCImARobot
     * @since 1.1.09
     */
    private boolean builtInRobotFlag;

    /**
     * which face image this player is using
     */
    private int faceId;

    /**
     * the numbers that our settlements are touching
     */
    private SOCPlayerNumbers ourNumbers;

    /**
     * a guess at how many turns it takes to build
     */

    // private SOCBuildingSpeedEstimate buildingSpeed;

    /**
     * create a copy of the player
     *
     * @param player        the player to copy
     */
    public SOCPlayer(SOCPlayer player)
    {
        int i;
        game = player.game;
        playerNumber = player.playerNumber;
        numPieces = player.numPieces.clone();
        pieces = new Vector<SOCPlayingPiece>(player.pieces);
        roads = new Vector<SOCRoad>(player.roads);
        settlements = new Vector<SOCSettlement>(player.settlements);
        cities = new Vector<SOCCity>(player.cities);
        fortress = player.fortress;
        numWarships = player.numWarships;
        longestRoadLength = player.longestRoadLength;
        lrPaths = new Vector<SOCLRPathData>(player.lrPaths);
        resources = player.resources.copy();
        resourceStats = new int[player.resourceStats.length];
        System.arraycopy(player.resourceStats, 0, resourceStats, 0, player.resourceStats.length);
        rolledResources = player.rolledResources.copy();
        devCards = new SOCDevCardSet(player.devCards);
        numKnights = player.numKnights;
        buildingVP = player.buildingVP;
        specialVP = player.specialVP;
        finalTotalVP = 0;
        playedDevCard = player.playedDevCard;
        needToDiscard = player.needToDiscard;
        needToPickGoldHexResources = player.needToPickGoldHexResources;
        boardResetAskedThisTurn = player.boardResetAskedThisTurn;
        askedSpecialBuild = player.askedSpecialBuild;
        hasSpecialBuiltThisTurn = player.hasSpecialBuiltThisTurn;
        robotFlag = player.robotFlag;
        builtInRobotFlag = player.builtInRobotFlag;
        faceId = player.faceId;
        ourNumbers = new SOCPlayerNumbers(player.ourNumbers);
        ports = new boolean[SOCBoard.WOOD_PORT + 1];

        for (i = SOCBoard.MISC_PORT; i <= SOCBoard.WOOD_PORT; i++)
        {
            ports[i] = player.ports[i];
        }

        roadNodes = new Vector<Integer>(player.roadNodes);
        // Deep copy of roadNodeGraph contents:
        roadNodeGraph = new Hashtable<Integer,int[]>((int) (player.roadNodeGraph.size() * 1.4f));
        for (Integer rnKey : player.roadNodeGraph.keySet())
        {
            final int[] rnArr = player.roadNodeGraph.get(rnKey);
            roadNodeGraph.put(rnKey, rnArr.clone());
        }

        /**
         * init legal and potential arrays
         */
        legalRoads = new HashSet<Integer>(player.legalRoads);
        legalSettlements = new HashSet<Integer>(player.legalSettlements);
        legalShips = new HashSet<Integer>(player.legalShips);
        potentialRoads = new HashSet<Integer>(player.potentialRoads);
        potentialSettlements = new HashSet<Integer>(player.potentialSettlements);
        potentialCities = new HashSet<Integer>(player.potentialCities);
        potentialShips = new HashSet<Integer>(player.potentialShips);
        addedLegalSettlement = player.addedLegalSettlement;
        if (player.legalShipsRestricted != null)
            legalShipsRestricted = new HashSet<Integer>(player.legalShipsRestricted);

        if (player.currentOffer != null)
        {
            currentOffer = new SOCTradeOffer(player.currentOffer);
        }
        else
        {
            currentOffer = null;
        }

        scenario_playerEvents_bitmask = player.scenario_playerEvents_bitmask;
        scenario_svpFromEachLandArea_bitmask = player.scenario_svpFromEachLandArea_bitmask;
        startingLandArea1 = player.startingLandArea1;
        startingLandArea2 = player.startingLandArea2;
    }

    /**
     * Create a new player for a new empty board.
     *<P>
     * Unless {@link SOCGame#hasSeaBoard},
     * the player's possible placement locations will be
     * set from {@link SOCBoard#initPlayerLegalRoads()} and
     * {@link SOCBoard#initPlayerLegalAndPotentialSettlements()}.
     *<P>
     * Once the game board is set up, be sure to call
     * {@link #setPotentialAndLegalSettlements(Collection, boolean, HashSet)}
     * to update our data.
     *
     * @param pn the player number
     * @param ga the game that the player is in
     */
    public SOCPlayer(int pn, SOCGame ga)
    {
        int i;

        game = ga;
        playerNumber = pn;
        numPieces = new int[SOCPlayingPiece.MAXPLUSONE];
        numPieces[SOCPlayingPiece.ROAD] = 15;
        numPieces[SOCPlayingPiece.SETTLEMENT] = 5;
        numPieces[SOCPlayingPiece.CITY] = 4;
        if (ga.hasSeaBoard)
            numPieces[SOCPlayingPiece.SHIP] = SHIP_COUNT;
        else
            numPieces[SOCPlayingPiece.SHIP] = 0;

        if (ga.isGameOptionSet(SOCGameOption.K_SC_PIRI))
            --numPieces[SOCPlayingPiece.SETTLEMENT];  // Pirate Fortress is a captured settlement

        pieces = new Vector<SOCPlayingPiece>(24);
        roads = new Vector<SOCRoad>(15);
        settlements = new Vector<SOCSettlement>(5);
        cities = new Vector<SOCCity>(4);
        longestRoadLength = 0;
        lrPaths = new Vector<SOCLRPathData>();
        resources = new SOCResourceSet();
        resourceStats = new int[SOCResourceConstants.UNKNOWN];
        rolledResources = new SOCResourceSet();
        devCards = new SOCDevCardSet();
        numKnights = 0;
        buildingVP = 0;
        specialVP = 0;
        playedDevCard = false;
        needToDiscard = false;
        needToPickGoldHexResources = 0;
        boardResetAskedThisTurn = false;
        askedSpecialBuild = false;
        hasSpecialBuiltThisTurn = false;
        robotFlag = false;
        builtInRobotFlag = false;
        faceId = 1;
        SOCBoard board = ga.getBoard();
        ourNumbers = new SOCPlayerNumbers(board);

        // buildingSpeed = new SOCBuildingSpeedEstimate(this);
        ports = new boolean[SOCBoard.WOOD_PORT + 1];

        for (i = SOCBoard.MISC_PORT; i <= SOCBoard.WOOD_PORT; i++)
        {
            ports[i] = false;
        }

        roadNodes = new Vector<Integer>(20);
        roadNodeGraph = new Hashtable<Integer, int[]>();

        /**
         * init legal and potential arrays.
         * no settlements yet, so no potential roads or cities.
         * If game.hasSeaBoard, these are initialized later, after board.makeNewBoard
         * and game.startGame, because the layout varies from game to game.
         */
        potentialRoads = new HashSet<Integer>();
        potentialCities = new HashSet<Integer>();
        potentialShips = new HashSet<Integer>();

        if (! game.hasSeaBoard)
        {
            legalRoads = board.initPlayerLegalRoads();
            legalSettlements = board.initPlayerLegalAndPotentialSettlements();
            legalShips = new HashSet<Integer>();  // will remain empty
            potentialSettlements = new HashSet<Integer>(legalSettlements);
        } else {
            legalRoads = new HashSet<Integer>();
            legalSettlements = new HashSet<Integer>();
            legalShips = new HashSet<Integer>();
            potentialSettlements = new HashSet<Integer>();
        }

        currentOffer = null;
    }

    /**
     * Set all nodes to not be potential settlements.
     * Called by {@link SOCGame#putPiece(SOCPlayingPiece)}
     * in state {@link SOCGame#START2A} or {@link SOCGame#START3A} after final initial settlement placement.
     * After they have placed another road, that road's
     * {@link #putPiece(SOCPlayingPiece, boolean)} call will call
     * {@link #updatePotentials(SOCPlayingPiece)}, which
     * will set potentialSettlements at the road's new end node.
     */
    public void clearPotentialSettlements()
    {
        potentialSettlements.clear();
    }

    /**
     * Update player's state as needed when any player begins their turn (before dice are rolled).
     * Called by server and client, as part of {@link SOCGame#updateAtTurn()}.
     *<P>
     * Called for each player, just before calling the current player's {@link #updateAtOurTurn()}.
     *<UL>
     *<LI> Clear {@link #getRolledResources()}
     *</UL>
     * @since 1.1.14
     */
    void updateAtTurn()
    {
        rolledResources.clear();
    }

    /**
     * Update game state as needed when this player begins their turn (before dice are rolled).
     * Called by server and client, as part of {@link SOCGame#updateAtTurn()}.
     * Called just after calling each player's {@link #updateAtTurn()}.
     *<P>
     * May be called during initial placement.
     * Is called at the end of initial placement, before the first player's first roll.
     * On the 6-player board, is called at the start of
     * the player's {@link #SPECIAL_BUILDING Special Building Phase}.
     *<UL>
     *<LI> Mark our new dev cards as old
     *<LI> Set {@link #getNeedToPickGoldHexResources()} to 0
     *<LI> Clear the "last-action bank trade" flag/list
     *     used by {@link SOCGame#canUndoBankTrade(SOCResourceSet, SOCResourceSet) game.canUndoBankTrade}
     *</UL>
     * @since 1.1.14
     */
    void updateAtOurTurn()
    {
        getDevCards().newToOld();
        lastActionBankTrade_give = null;
        lastActionBankTrade_get = null;
        if (needToPickGoldHexResources > 0)
            needToPickGoldHexResources = 0;
    }

    /**
     * set the name of the player
     *
     * @param na    the player's new name, or null.
     *           For network message safety, must not contain
     *           control characters, {@link SOCMessage#sep_char}, or {@link SOCMessage#sep2_char}.
     *           This is enforced by calling {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @throws IllegalArgumentException if a non-null name fails
     *           {@link SOCMessage#isSingleLineAndSafe(String)}.
     *           This exception was added in 1.1.07.
     */
    public void setName(String na)
        throws IllegalArgumentException
    {
        if ((na != null) && ! SOCMessage.isSingleLineAndSafe(na))
            throw new IllegalArgumentException("na");
        name = na;
    }

    /**
     * @return the name of the player
     */
    public String getName()
    {
        return name;
    }

    /**
     * Get the player's seat number in the {@link #getGame() game}.
     * @return the player id
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the game that this player is in
     */
    public SOCGame getGame()
    {
        return game;
    }

    /**
     * @return true if the player played a dev card this turn
     */
    public boolean hasPlayedDevCard()
    {
        return playedDevCard;
    }

    /**
     * set the playedDevCard flag
     *
     * @param value         the value of the flag
     */
    public void setPlayedDevCard(boolean value)
    {
        playedDevCard = value;
    }

    /**
     * @return true if the player asked to reset the board this turn
     */
    public boolean hasAskedBoardReset()
    {
        return boardResetAskedThisTurn;
    }

    /**
     * set the flag indicating if the player asked to reset the board this turn
     *
     * @param value  true to set, false to clear
     */
    public void setAskedBoardReset(boolean value)
    {
        boardResetAskedThisTurn = value;
    }

    /**
     * In 6-player mode's Special Building Phase, this player has asked to build.
     * To set or clear this flag, use {@link #setAskedSpecialBuild(boolean)}.
     *
     * @return  if the player has asked to build
     * @see #getAskSpecialBuildPieces()
     * @see #hasSpecialBuilt()
     * @since 1.1.08
     */
    public boolean hasAskedSpecialBuild()
    {
        return askedSpecialBuild;
    }

    /**
     * In 6-player mode's Special Building Phase, set or clear the flag
     * for this player asking to build.
     * Does not validate that they are currently allowed to ask;
     * use {@link SOCGame#canAskSpecialBuild(int, boolean)} for that.
     * To read this flag, use {@link #hasAskedSpecialBuild()}.
     *
     * @param set  if the player has asked to build
     * @see SOCGame#askSpecialBuild(int, boolean)
     * @since 1.1.08
     */
    public void setAskedSpecialBuild(boolean set)
    {
        askedSpecialBuild = set;
    }

    /**
     * In 6-player mode's Special Building Phase, this player has already built this turn.
     * To set or clear this flag, use {@link #setSpecialBuilt(boolean)}.
     *
     * @return  if the player has built
     * @see #hasAskedSpecialBuild()
     * @since 1.1.09
     */
    public boolean hasSpecialBuilt()
    {
        return hasSpecialBuiltThisTurn;
    }

    /**
     * In 6-player mode's Special Building Phase, set or clear the flag
     * for this player already built this turn.
     * Does not validate against current game conditions.
     * To read this flag, use {@link #hasSpecialBuilt()}.
     *
     * @param set  if the player special-built this turn
     * @since 1.1.09
     */
    public void setSpecialBuilt(boolean set)
    {
        hasSpecialBuiltThisTurn = set;
    }

    /**
     * set the "need to discard" flag
     *
     * @param value         the value of the flag
     */
    public void setNeedToDiscard(boolean value)
    {
        needToDiscard = value;
    }

    /**
     * @return true if this player needs to discard
     * @see #getNeedToPickGoldHexResources()
     */
    public boolean getNeedToDiscard()
    {
        return needToDiscard;
    }

    /**
     * Set the number of gold-hex resources this player must now pick,
     * after a dice roll or placing their 2nd initial settlement.
     * 0 unless {@link SOCGame#hasSeaBoard} and player is adjacent
     * to a {@link SOCBoardLarge#GOLD_HEX}.
     * Game state {@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE}.
     * Once the player has picked their resources, returns to 0.
     *
     * @param numRes  Number of resources to pick, or 0 for no pick
     * @since 2.0.00
     */
    public void setNeedToPickGoldHexResources(final int numRes)
    {
        needToPickGoldHexResources = numRes;
    }

    /**
     * Get the number of gold-hex resources this player must now pick,
     * after a dice roll or placing their 2nd initial settlement.
     * 0 unless {@link SOCGame#hasSeaBoard} and player is adjacent
     * to a {@link SOCBoardLarge#GOLD_HEX}.
     * Game state {@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE}.
     * Once the player has picked their resources, returns to 0.
     *
     * @return  number of resources to pick
     * @see #getNeedToDiscard()
     * @since 2.0.00
     */
    public int getNeedToPickGoldHexResources()
    {
        return needToPickGoldHexResources;
    }

    /**
     * set the robot flags.
     * @param isRobot  Is this player a robot?
     * @param isBuiltIn  Is this player the built-in robot type?
     *   Assume false if unknown, such as in SITDOWN message received at other clients.
     */
    public void setRobotFlag(boolean isRobot, boolean isBuiltIn)
    {
        robotFlag = isRobot;
        builtInRobotFlag = isBuiltIn;
    }

    /**
     * Is this player a robot AI (built-in or 3rd-party)?
     * @return the value of the robot flag
     * @see #isBuiltInRobot()
     */
    public boolean isRobot()
    {
        return robotFlag;
    }

    /**
     * Is this robot player the built-in robot (not a 3rd-party),
     * with the original AI?  False if unknown.
     * @see #isRobot()
     * @see soc.message.SOCImARobot
     * @return the value of the built-in-robot flag
     * @since 1.1.09
     */
    public boolean isBuiltInRobot()
    {
        return builtInRobotFlag;
    }

    /**
     * set the face image id
     *
     * @param id        the image id. 1 is the first human face image; 0 is the robot.
     */
    public void setFaceId(int id)
    {
        faceId = id;
    }

    /**
     * get the face image id.
     * @return  the face image id.  1 is the first human face image; 0 is the robot.
     */
    public int getFaceId()
    {
        return faceId;
    }

    /**
     * @return the numbers that this player's settlements are touching
     */
    public SOCPlayerNumbers getNumbers()
    {
        return ourNumbers;
    }

    /**
     * Get the number of one piece type not in play and available to place.
     * At the start of the game, for example, <tt>getNumPieces({@link SOCPlayingPiece#CITY})</tt> == 4.
     * On the sea board, each player starts with {@link #SHIP_COUNT} ships.
     *
     * @return the number of pieces not in play for a particular type of piece
     * @param ptype the type of piece; matches SOCPlayingPiece constants,
     *   such as {@link SOCPlayingPiece#ROAD}, {@link SOCPlayingPiece#SETTLEMENT}.
     * @see #getPieces()
     * @see #getNumWarships()
     * @throws ArrayIndexOutOfBoundsException if piece type is invalid
     */
    public int getNumPieces(int ptype)
        throws ArrayIndexOutOfBoundsException
    {
        return numPieces[ptype];
    }

    /**
     * set the amount of pieces not in play
     * for a particular type of piece
     *
     * @param ptype the type of piece; matches SOCPlayingPiece constants,
     *   such as {@link SOCPlayingPiece#ROAD}, {@link SOCPlayingPiece#SETTLEMENT}.
     * @param amt                 the amount
     * @see #setNumWarships(int)
     */
    public void setNumPieces(int ptype, int amt)
    {
        numPieces[ptype] = amt;
    }

    /**
     * For scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * the number of {@link SOCShip}s that have been converted to warships
     * to defend against the pirate fleet and attack the {@link SOCFortress}.
     *<P>
     * {@link SOCShip} has no "isWarship" field; the player's first {@code numWarships}
     * ships within {@link #getRoads()} are the warships, because those are the ships
     * heading out to sea starting at the player's settlement, placed chronologically.
     * See {@link SOCGame#isShipWarship(SOCShip)} for details.
     * @since 2.0.00
     */
    public int getNumWarships()
    {
        return numWarships;
    }

    /**
     * For scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * set the player's number of warships.  See {@link #getNumWarships()} for details.
     * @param count  New number of warships
     * @since 2.0.00
     */
    public void setNumWarships(final int count)
    {
        numWarships = count;
    }

    /**
     * Get this player's pieces on the board. Does not include {@link #getFortress()}, if any,
     * because the player doesn't control that piece.
     * @return the list of pieces in play
     * @see #getNumPieces(int)
     */
    public Vector<SOCPlayingPiece> getPieces()
    {
        return pieces;
    }

    /**
     * Get this player's roads and ships on the board.  Chronological order.
     * @return the list of roads/ships in play
     */
    public Vector<SOCRoad> getRoads()
    {
        return roads;
    }

    /**
     * Get this player's road or ship on an edge.
     * @param  edge  Edge coordinate of the road or ship
     * @return  The player's road or ship in play at this edge, or null
     * @since 2.0.00
     */
    public SOCRoad getRoadOrShip(final int edge)
    {
        for (SOCRoad roadOrShip : roads)
        {
            if (roadOrShip.getCoordinates() == edge)
                return roadOrShip;
        }

        return null;
    }

    /**
     * @return the list of settlements in play
     */
    public Vector<SOCSettlement> getSettlements()
    {
        return settlements;
    }

    /**
     * @return the list of cities in play
     */
    public Vector<SOCCity> getCities()
    {
        return cities;
    }

    /**
     * For scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * the "pirate fortress" that this player must defeat to win.
     * Null if fortress has already been defeated and converted to a settlement.
     * Null unless game has that scenario option.
     *<P>
     * There is no <tt>setFortress</tt> method; instead call
     * {@link SOCGame#putPiece(SOCPlayingPiece) game.putPiece(SOCFortress)} to set, or
     * {@link SOCGame#putPiece(SOCPlayingPiece) game.putPiece(SOCSettlement)} to clear.
     * @since 2.0.00
     */
    public SOCFortress getFortress()
    {
        return fortress;
    }

    /**
     * For some game scenarios, get how many cloth this player currently has.
     * Every 2 pieces of cloth is worth 1 VP.
     * @since 2.0.00
     */
    public int getCloth()
    {
        return numCloth;
    }

    /**
     * Set how many cloth this player currently has.
     * More cloth gives the player more VPs, see {@link #getPublicVP()}.
     * For use at client based on messages from server.
     * @param numCloth  Number of cloth
     * @since 2.0.00
     */
    public void setCloth(final int numCloth)
    {
        this.numCloth = numCloth;
    }

    /**
     * Can we move this ship, based on our trade routes
     * and settlements/cities near its current location?
     *<P>
     * Only the ship at the newer end of an open trade route can be moved.
     * So, to move a ship, one of its end nodes must be clear of this
     * player's pieces: No settlement or city, and no other adjacent ship on
     * the other side of the node.  Other players' pieces are ignored.
     * The ship must be part of an open trade route;
     * {@link SOCShip#isClosed() sh.isClosed()} must be false.
     *<P>
     * Does not check game state, only this player's pieces.
     * Trade routes can branch, so it may be that more than one ship
     * could be moved.  The game limits players to one move per turn.
     * That limit isn't checked here.
     * After the player picks the ship's requested new location,
     * {@link SOCGame#canMoveShip(int, int, int)} checks that limit,
     * the other game conditions, and calls this method to check the
     * player's piece conditions.
     *<P>
     * Once the player picks the ship's requested new location,
     * that edge will be checked with {@link #isPotentialShipMoveTo(int, int)}.
     *<P>
     * @param sh  One of our ships
     * @since 2.0.00
     */
    public boolean canMoveShip(SOCShip sh)
    {
        if (sh.isClosed())
            return false;

        final SOCBoard board = game.getBoard();
        final int shipEdge = sh.getCoordinates();
        final int[] shipNodes = board.getAdjacentNodesToEdge_arr(shipEdge);

        final boolean clearPastNode0, clearPastNode1;

        SOCPlayingPiece pp = board.settlementAtNode(shipNodes[0]);
        if ((pp != null) && (pp.getPlayerNumber() != playerNumber))
            pp = null;
        clearPastNode0 =
            (null == pp)
            && ! doesTradeRouteContinuePastNode
                   (board, true, shipEdge, -9, shipNodes[0]);

        pp = board.settlementAtNode(shipNodes[1]);
        if ((pp != null) && (pp.getPlayerNumber() != playerNumber))
            pp = null;
        clearPastNode1 =
             (null == pp)
             && ! doesTradeRouteContinuePastNode
                    (board, true, shipEdge, -9, shipNodes[1]);

        return (clearPastNode0 || clearPastNode1);
    }

    /**
     * For road/ship building, does this edge have a piece of ours adjacent to it
     * (settlement, city, or same road/ship type as {@code wantShip})?
     *
     * @param edge   Check adjacents of this edge
     * @param wantShip   True to look for ships only, false for roads only
     * @return  True if we have an adjacent settlement or city, or our route continues on an adjacent edge 
     * @since 2.0.00
     */
    private final boolean doesTradeRouteContinuePastEdge(final int edge, final boolean wantShip)
    {
        // TODO refactor: similar to canMoveShip's checks

        final SOCBoard board = game.getBoard();

        final int[] edgeNodes = board.getAdjacentNodesToEdge_arr(edge);
        for (int i = 0; i < 2; ++i)
        {
            SOCPlayingPiece sc = board.settlementAtNode(edgeNodes[i]);
            if (sc != null)
            {
                if (sc.getPlayerNumber() == playerNumber)
                    return true;
            } else {
                if (doesTradeRouteContinuePastNode
                        (board, wantShip, edge, -9, edgeNodes[i]))
                    return true;
            }
        }

        return false;
    }

    /**
     * Does this trade route (ships only, or roads only) continue past
     * an unoccupied node?
     *
     * @param board  game board
     * @param wantShip   True to look for ships only, false for roads only
     * @param rsEdge  Edge adjacent to {@code node} with a road/ship on the trade route
     * @param ignoreEdge  Edge to ignore our pieces on, or -9; used
     *                    during the check before moving one of our ships
     *                    to ignore the ship's current position (its {@code fromEdge}).
     *                    Not necessarily adjacent to {@code node} or {@code rsEdge}.
     *                    <P>
     *                    In scenario {@code _SC_PIRI}, moving a ship must not create a new
     *                    branch in the existing ship route. When not -9 in that scenario,
     *                    this method checks that by counting our ships/roads adjacent to {@code node}
     *                    besides {@code rsEdge} and {@code ignoreEdge}.
     * @param node  Node at one end of {@code rsEdge},
     *              which does not have a settlement or city;
     *              check this node's other 2 edges for roads/ships
     *              continuing the trade route
     * @return  True if a road/ship continues the route past {@code node}
     *              along one or both of the node's 2 other edges
     * @since 2.0.00
     */
    private final boolean doesTradeRouteContinuePastNode
        (final SOCBoard board, final boolean wantShip, final int rsEdge, final int ignoreEdge, final int node)
    {
        boolean routeContinues = false;

        // openEdgesCount: for _SC_PIRI when moving ship, number of non-ship edges around node;
        //   otherwise 0.  Starts at 3, and the loop has a break below 2, so it will never reach 0.
        //   Track this count because we can't create a branch in the ship route by moving one.
        //   Decrement openEdgesCount when a ship is seen.  Ignore roads.
        //   Assumes that it's OK to place 2 ships next to a coastal settlement
        //   (the road to the settlement is ignored) because the player's free initial coastal settlement
        //   has just 1 legal sea edge next to it, not 2, so the route can't branch there,
        //   so any other coastal settlement is "on the way" along the non-branching route.
        int openEdgesCount =
            ((ignoreEdge != -9) && game.isGameOptionSet(SOCGameOption.K_SC_PIRI)) ? 3 : 0;

        int[] adjEdges = board.getAdjacentEdgesToNode_arr(node);
        for (int i = 0; i < 3; ++i)
            if ((adjEdges[i] == rsEdge) || (adjEdges[i] == ignoreEdge))
                adjEdges[i] = -9;  // ignore this edge

        // Look for a road/ship of ours, adjacent to node
        for (SOCRoad road : roads)
        {
            final int edge = road.getCoordinates();
            for (int i = 0; i < 3; ++i)
            {
                if (edge == adjEdges[i])
                {
                    if (road.isRoadNotShip() == wantShip)
                        continue;  // interested in ships only, or roads only, not both types

                    routeContinues = true;
                    if (openEdgesCount != 0)
                        --openEdgesCount;

                    break;
                }
            }

            if (routeContinues && (openEdgesCount < 2))
                break;  // no need to keep looking at roads
        }

        if (openEdgesCount == 0)
            return routeContinues;
        else
            return (openEdgesCount == 2);  // If 3, nothing beyond. If 1, already 2 ships beyond, route would branch.
    }

    /**
     * Follow a trade route (a line of {@link SOCShip}s) away from a newly closed end, to
     * determine if the other end is closed or still open, and close this route if
     * necessary.
     *<P>
     * We check the route in one direction towards <tt>edgeFarNode</tt>, because we
     * assume that the other end of <tt>newShipEdge</tt>
     * has a settlement, or that it branches from an already-closed trade route.
     *<P>
     * The route and its segments may end at a settlement/city/village, a branch where 3 ships
     * meet at a node (and 2 of the 3 ships are closed), or may end "open" with no further pieces.
     *<P>
     * Settlements and cities owned by other players won't close the trade route.
     * {@link SOCVillage Villages} are present only with scenario game option {@link SOCGameOption#K_SC_CLVI}.
     * If this route becomes closed and is the player's first Cloth Trade route with a village,
     * this method sets that player event flag and fires
     * {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     *<P>
     * Valid only when {@link SOCGame#hasSeaBoard}.
     *
     * @param newShipEdge  A ship in a currently-open trade route, either newly placed
     *                  or adjacent to a newly placed settlement.
     *                  If the ship is newly placed, it should not yet be in {@link #roads}.
     *                  If the settlement is newly placed, it should not yet be in {@link #settlements}.
     * @param edgeFarNode  The unvisited node at the far end of <tt>fromEdge</tt>.
     *                  We'll examine this node and then continue to move along edges past it.
     * @return  null if open, otherwise all the newly-closed {@link SOCShip}s
     * @since 2.0.00
     * @throws IllegalStateException  if not {@link SOCGame#hasSeaBoard}
     */
    private Vector<SOCShip> checkTradeRouteFarEndClosed(final SOCShip newShipEdge, final int edgeFarNode)
        throws IllegalStateException
    {
        if (! game.hasSeaBoard)
            throw new IllegalStateException();

        List<Vector<Object>> encounteredSelf = new ArrayList<Vector<Object>>();
            // if route loops around, contains Vectors of node coords & SOCShips
            // -- see isTradeRouteFarEndClosed javadoc for details

        HashSet<Integer> alreadyVisited = new HashSet<Integer>();  // contains Integer coords as segment is built

        // Check the far end node of fromEdge
        // for a settlement/city, then for ships in each
        // of that node's directions.
        // Note that if it becomes closed, segment will contain newShipEdge.

        isTradeRouteFarEndClosed_foundVillage = null;
        Vector<SOCShip> segment = isTradeRouteFarEndClosed
            (newShipEdge, edgeFarNode, alreadyVisited, encounteredSelf);

        if (segment == null)
            return null;

        for (SOCShip sh : segment)
            sh.setClosed();

        if (isTradeRouteFarEndClosed_foundVillage != null)
        {
            final boolean gotCloth = isTradeRouteFarEndClosed_foundVillage.addTradingPlayer(this);
            final boolean flagNew =
                ! hasScenarioPlayerEvent(SOCScenarioPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);

            if (flagNew)
                setScenarioPlayerEvent(SOCScenarioPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);
            if (flagNew || gotCloth)
            {
                if (game.scenarioEventListener != null)
                    game.scenarioEventListener.playerEvent
                        (game, this, SOCScenarioPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE,
                         flagNew, isTradeRouteFarEndClosed_foundVillage);
            }
        }

        // Now that those ships are closed, re-check the segments
        // where we might have found a loop, and see if those are
        // still open or should be closed.

        if (encounteredSelf.size() > 0)
        {
            // go from the farthest, inwards
            for (int i = 0; i < encounteredSelf.size(); ++i)
            {
                Vector<Object> self = encounteredSelf.get(i);
                final int farNode = ((Integer) self.firstElement()).intValue();
                SOCShip nearestShip = (SOCShip) self.elementAt(1);
                if (nearestShip.isClosed())
                    continue;  // already closed

                Vector<SOCShip> recheck;
                List<Vector<Object>> reSelf = new ArrayList<Vector<Object>>();
                HashSet<Integer> reAlready = new HashSet<Integer>();

                // check again to see if it should be closed now
                isTradeRouteFarEndClosed_foundVillage = null;
                if (self.size() == 2)
                {
                    // just 1 ship along that segment
                    recheck = isTradeRouteFarEndClosed
                        (nearestShip, farNode, reAlready, reSelf);
                } else {
                    // 2 or more ships
                    final int nextNearEdge = ((SOCShip) self.elementAt(2)).getCoordinates();
                    recheck = isTradeRouteFarEndClosed
                        (nearestShip,
                         ((SOCBoardLarge) game.getBoard()).getNodeBetweenAdjacentEdges
                             (nearestShip.getCoordinates(), nextNearEdge),
                         reAlready, reSelf);
                }

                if (recheck == null)
                    continue;  // still not closed

                // close the re-checked segment
                segment.addAll(recheck);
                for (SOCShip sh : recheck)
                    sh.setClosed();

                if (isTradeRouteFarEndClosed_foundVillage != null)
                {
                    final boolean gotCloth = isTradeRouteFarEndClosed_foundVillage.addTradingPlayer(this);
                    final boolean flagNew =
                        ! hasScenarioPlayerEvent(SOCScenarioPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);

                    if (flagNew)
                        setScenarioPlayerEvent(SOCScenarioPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);
                    if (flagNew || gotCloth)
                    {
                        if (game.scenarioEventListener != null)
                            game.scenarioEventListener.playerEvent
                                (game, this, SOCScenarioPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE,
                                 flagNew, isTradeRouteFarEndClosed_foundVillage);
                    }
                }
            }
        }

        return segment;
    }

    /**
     * Set by {@link #isTradeRouteFarEndClosed(SOCShip, int, HashSet, List)}
     * if it finds a village at any far end.
     * Not set unless {@link SOCGame#hasSeaBoard} and {@link SOCGameOption#K_SC_CLVI} are set.
     * @since 2.0.00
     */
    private SOCVillage isTradeRouteFarEndClosed_foundVillage;

    /**
     * Recursive call for {@link #checkTradeRouteFarEndClosed(SOCShip, int)}.
     * See that method for more information.
     * This method checks one segment of the trade route going from a branch.
     * The segment may end at a settlement/city/village, another branch, or end with no further pieces.
     *<P>
     * If recursion ends at a {@link SOCVillage}, {@link #isTradeRouteFarEndClosed_foundVillage}
     * will be set to it.  If you're looking for villages, clear that field before calling this method.
     *<P>
     * Valid only when {@link SOCGame#hasSeaBoard}.
     *
     * @param edgeFirstShip  First edge along the segment; an open ship required here.
     *                  All edges, including this first edge, are checked against <tt>encounteredSelf</tt>.
     * @param edgeFarNode  The unvisited node at the far end of <tt>fromEdge</tt>.
     *                  We'll examine this node and then continue to move along edges past it.
     *                  If the "facing direction" we're moving in is called "moveDir",
     *                  then <tt>edgeFarNode</tt> =
     *                  {@link SOCBoardLarge#getAdjacentNodeToEdge(int, int)
     *                   board.getAdjacentNodeToEdge(edgeFirstShip.getCoordinates(), moveDir)}.
     * @param alreadyVisited   contains Integer edge coordinates as the segment is built;
     *               added to in this method
     * @param encounteredSelf  contains Vectors, each with a node coord Integer and SOCShips;
     *               might be added to in this method.
     *               Node is the "far end" of the segment from <tt>edgeFirstShip</tt>,
     *               just past the ship that was re-encountered.
     *               The SOCShips are ordered starting with <tt>edgeFirstShip</tt> and moving
     *               out to the node just past (farther than) the encounter ship.
     *               (That ship is not in the Vector.)
     *               The very first vector in the list is the one farthest from the original
     *               starting ship, and the following list entries will overall move closer
     *               to the start.
     * @return a closed route of {@link SOCShip} or null, from <tt>fromEdge</tt> to far end;
     *         may also add to <tt>alreadyVisited</tt> and <tt>encounteredSelf</tt>
     * @throws ClassCastException if not {@link SOCGame#hasSeaBoard}.
     * @throws IllegalArgumentException if {@link SOCShip#isClosed() edgeFirstShip.isClosed()}
     * @since 2.0.00
     */
    private Vector<SOCShip> isTradeRouteFarEndClosed
        (final SOCShip edgeFirstShip, final int edgeFarNode,
         HashSet<Integer> alreadyVisited, List<Vector<Object>> encounteredSelf)
        throws ClassCastException, IllegalArgumentException
    {
        if (edgeFirstShip.isClosed())
            throw new IllegalArgumentException();
        final SOCBoardLarge board = (SOCBoardLarge) game.getBoard();
        final boolean boardHasVillages = game.isGameOptionSet(SOCGameOption.K_SC_CLVI);
        Vector<SOCShip> segment = new Vector<SOCShip>();

        SOCShip edgeShip = edgeFirstShip;
        segment.add(edgeShip);
        int edge = edgeShip.getCoordinates();
        int node = edgeFarNode;

        boolean foundClosedEnd = false;
        SOCPlayingPiece pp = null;
        while ((edge != 0) && ! foundClosedEnd)
        {
            // Loop invariant:
            // - edge is an edge with a ship, we're currently at edge
            // - node is the "far end" of edge, next to be inspected
            // - segment's most recently added ship is the one at edge

            final Integer edgeInt = new Integer(edge);

            // have we already visited this edge?
            if (alreadyVisited.contains(edgeInt))
            {
                // Build an encounteredSelf list entry.
                Vector<Object> already = new Vector<Object>();
                already.add(Integer.valueOf(node));
                already.addAll(segment);

                encounteredSelf.add(already);

                return null;  // <--- Early return: already visited ---
            }

            alreadyVisited.add(edgeInt);

            // check the node
            pp = board.settlementAtNode(node);
            if (pp != null)
            {
                if (pp.getPlayerNumber() == playerNumber)
                    foundClosedEnd = true;

                break;  // segment doesn't continue past a settlement or city
            }
            else if (boardHasVillages)
            {
                pp = board.getVillageAtNode(node);
                if (pp != null)
                {
                    foundClosedEnd = true;

                    break;  // segment doesn't continue past a village
                }
            }
            
            // check node's other 2 adjacent edges
            // to see where the trade route goes next

            final int[] nodeEdges = board.getAdjacentEdgesToNode_arr(node);
            SOCShip nextShip1 = null, nextShip2 = null;
            for (int i = 0; i < 3; ++i)
            {
                if ((nodeEdges[i] == edge) || (nodeEdges[i] == -9))
                    continue;  // not a new direction

                SOCRoad rs = getRoadOrShip(nodeEdges[i]);
                if ((rs == null) || rs.isRoadNotShip())
                    continue;  // not a ship

                if (nextShip1 == null)
                    nextShip1 = (SOCShip) rs;
                else
                    nextShip2 = (SOCShip) rs;
            }

            if (nextShip1 == null)
                break;  // open end, won't continue past here

            // move next
            if (nextShip2 == null)
            {
                // Trade route continues in just 1 direction,
                // so follow it along

                edge = nextShip1.getCoordinates();
                node = board.getAdjacentNodeFarEndOfEdge(edge, node);
                segment.add(nextShip1);

            } else {

                // Found ships in 2 directions (a branch)

                if (nextShip2.isClosed())
                {
                    // If one ship is already closed, they both are, because they're connected.
                    // Stop here.
                    foundClosedEnd = true;
                    break;
                }

                // Recursive call to the 2 directions out from node:

                final int encounterSize = encounteredSelf.size();
                Vector<SOCShip> shipsFrom1 = isTradeRouteFarEndClosed
                    (nextShip1, board.getAdjacentNodeFarEndOfEdge(nextShip1.getCoordinates(), node),
                     alreadyVisited, encounteredSelf);
                Vector<SOCShip> shipsFrom2 = isTradeRouteFarEndClosed
                    (nextShip2, board.getAdjacentNodeFarEndOfEdge(nextShip2.getCoordinates(), node),
                     alreadyVisited, encounteredSelf);

                // Did we encounter our route while recursing?
                if (encounterSize != encounteredSelf.size())
                {
                    // Build an encounteredSelf list entry.
                    Vector<Object> already = new Vector<Object>();
                    already.add(new Integer(node));
                    already.addAll(segment);

                    encounteredSelf.add(already);
                }

                if (shipsFrom1 == null)
                {
                    // only shipsFrom2 might be closed
                    if (shipsFrom2 == null)
                        return null;  // neither one was closed

                    segment.addAll(shipsFrom2);
                    return segment;
                }
                else if (shipsFrom2 == null)
                {
                    // shipsFrom2 is null, shipsFrom1 is not null, so it's closed.

                    segment.addAll(shipsFrom1);
                    return segment;
                }

                // both were non-null (newly closed)
                // -> This shouldn't happen, because together they already
                //    form a continuous path between 2 settlements.
                segment.addAll(shipsFrom1);
                segment.addAll(shipsFrom2);
                return segment;
            }
        }

        if (! foundClosedEnd)
            return null;

        if ((pp != null) && (pp instanceof SOCVillage))
            isTradeRouteFarEndClosed_foundVillage = (SOCVillage) pp;

        return segment;
    }

    /**
     * Get the location of this player's most recent
     * settlement.  Useful during initial placement.
     * @return the coordinates of the last settlement
     * played by this player
     */
    public int getLastSettlementCoord()
    {
        return lastSettlementCoord;
    }

    /**
     * Get the location of this player's most recent
     * road or ship.  Useful during initial placement.
     * @return the coordinates of the last road/ship
     * played by this player
     */
    public int getLastRoadCoord()
    {
        return lastRoadCoord;
    }

    /**
     * Get the length of this player's longest road or trade route,
     * as calculated by the most recent call to {@link #calcLongestRoad2()}.
     * @return the longest road length / trade route length
     */
    public int getLongestRoadLength()
    {
        return longestRoadLength;
    }

    /**
     * Get our longest road paths.
     * Vector is empty if {@link SOCGameOption#K_SC_0RVP} is set.
     * @return longest road paths
     */
    public Vector<SOCLRPathData> getLRPaths()
    {
        return lrPaths;
    }

    /**
     * set the longest paths vector
     * @param vec  the vector
     */
    public void setLRPaths(Vector<SOCLRPathData> vec)
    {
        lrPaths.removeAllElements();

        for (SOCLRPathData pd : vec)
        {
            D.ebugPrintln("restoring pd for player " + playerNumber + " :" + pd);
            lrPaths.addElement(pd);
        }
    }

    /**
     * set the longest road / longest trade route length
     *
     * @param len         the length
     */
    public void setLongestRoadLength(int len)
    {
        longestRoadLength = len;
    }

    /**
     * @return the resource set
     */
    public SOCResourceSet getResources()
    {
        return resources;
    }

    /**
     * On server, get the current totals of resources received by dice rolls by this player.
     * Includes resources picked from a rolled {@link SOCBoardLarge#GOLD_HEX}.
     * For the {@link SOCScenario#K_SC_FOG Fog Scenario}, includes resources picked when building
     * a road or ship revealed gold from a {@link SOCBoardLarge#FOG_HEX}.
     *<P>
     * Please treat this as read-only.
     *<P>
     * Not currently tracked at client.
     *
     * @return array of resource counts from dice rolls;
     *   the used indexes are {@link SOCResourceConstants#CLAY} - {@link SOCResourceConstants#WOOD}.
     *   Index 0 is unused.
     * @since 1.1.09
     */
    public int[] getResourceRollStats()
    {
        return resourceStats;
    }

    /**
     * Add to this player's resources and resource-roll totals.
     *<P>
     * If {@link #hasSeaBoard}, treat {@link SOCResourceConstants#GOLD_LOCAL}
     * as the gold-hex resources they must pick, and set
     * {@link #getNeedToPickGoldHexResources()} to that amount.
     * Once the resources from gold from a dice roll are picked, the
     * game should update this player's {@link #getResourceRollStats()}.
     *<P>
     * Otherwise ignores rolled {@link SOCResourceConstants#UNKNOWN} resources.
     *
     * @param rolled The resources gained by this roll, as determined
     *     by {@link SOCGame#rollDice()}
     * @since 1.1.09
     */
    public void addRolledResources(SOCResourceSet rolled)
    {
        if (game.hasSeaBoard)
        {
            final int gold = rolled.getAmount(SOCResourceConstants.GOLD_LOCAL);
            if (gold > 0)
            {
                needToPickGoldHexResources = gold;
                rolled.setAmount(0, SOCResourceConstants.GOLD_LOCAL);
            }
        }
        rolledResources.setAmounts(rolled);
        resources.add(rolled);
        for (int rtype = SOCResourceConstants.CLAY; rtype < resourceStats.length; ++rtype)
            resourceStats[rtype] += rolled.getAmount(rtype);
    }

    /**
     * Resources gained from dice roll of the current turn.
     * Valid at server only, not at client.
     * Please treat the returned set as read-only.
     * @return the resources, if any, gained by this player from the
     *     current turn's {@link SOCGame#rollDice()}.
     * @since 2.0.00
     */
    public SOCResourceSet getRolledResources()
    {
        return rolledResources;
    }

    /**
     * @return the development card set
     */
    public SOCDevCardSet getDevCards()
    {
        return devCards;
    }
    
    /**
     * @return whether this player has any unplayed dev cards
     * 
     * @see #getDevCards()
     */
    public boolean hasUnplayedDevCards()
    {
        return (0 < devCards.getNumUnplayed());
    }

    /**
     * @return the number of knights in play
     */
    public int getNumKnights()
    {
        return numKnights;
    }

    /**
     * set the number of knights in play
     *
     * @param nk        the number of knights
     */
    public void setNumKnights(int nk)
    {
        numKnights = nk;
    }

    /**
     * increment the number of knights in play
     */
    public void incrementNumKnights()
    {
        numKnights++;
    }

    /**
     * @return true if this player has the longest road / longest trade route
     */
    public boolean hasLongestRoad()
    {
        SOCPlayer p = game.getPlayerWithLongestRoad();
        if (p == null)
            return false;
        return p.getPlayerNumber() == playerNumber;
    }

    /**
     * @return true if this player has the largest army
     */
    public boolean hasLargestArmy()
    {
        SOCPlayer p = game.getPlayerWithLargestArmy();
        if (p == null)
            return false;
        return p.getPlayerNumber() == playerNumber;
    }

    /**
     * Get the number of Special Victory Points (SVPs) awarded to this player.
     * SVPs are part of some game scenarios on the large sea board.
     * @return the number of SVPs, or 0
     * @see #getSpecialVPInfo()
     * @since 2.0.00
     */
    public int getSpecialVP()
    {
        return specialVP;
    }

    /**
     * Set the number of Special Victory Points (SVPs) awarded to this player.
     * For use at client based on messages from server.
     * @param svp the number of SVPs, or 0
     * @see #addSpecialVPInfo(int, String)
     * @since 2.0.00
     */
    public void setSpecialVP(int svp)
    {
        specialVP = svp;
    }

    /**
     * This player's number of publicly known victory points.
     * Public victory points exclude VP development cards, except at
     * end of game, when they've been announced by server.
     * Special Victory Points (SVPs) are included, if the game scenario awards them.
     * Also includes any VP from {@link #getCloth() cloth}.
     * 
     * @return the number of publicly known victory points
     * @see #getTotalVP()
     * @see #getSpecialVP()
     * @see #forceFinalVP(int)
     */
    public int getPublicVP()
    {
        if (finalTotalVP > 0)
            return finalTotalVP;
        
        int vp = buildingVP + specialVP + (numCloth / 2);

        /**
         * if we have longest road, then add 2 VP
         */
        if (hasLongestRoad())
        {
            vp += 2;
        }

        /**
         * if we have largest army, then add 2 VP
         */
        if (hasLargestArmy())
        {
            vp += 2;
        }

        return vp;
    }

    /**
     * @return the actual number of victory points (including VP cards)
     * @see #getPublicVP()
     * @see #forceFinalVP(int)
     */
    public int getTotalVP()
    {
        if (finalTotalVP > 0)
            return finalTotalVP;

        int vp = getPublicVP();
        vp += devCards.getNumVPCards();

        return vp;
    }

    /**
     * If game is over, server can push the final score for
     * each player to the client.  During play, true scores aren't
     * known, because of hidden victory-point cards.
     * getTotalVP() and getPublicVP() will report this, if set.
     * 
     * @param score Total score for the player, or 0 for no forced total.
     */
    public void forceFinalVP(int score)
    {
        if (game.getGameState() != SOCGame.OVER)
            return;  // Consider throw IllegalStateException
        
        finalTotalVP = score;
    }

    /**
     * Add details on Special Victory Points (SVP) just awarded.
     * This is called at the server (because it has the text strings) when
     * {@link SOCGame#scenarioEventListener} != null, and sent out to clients.
     * Clients call it from the network message handler.
     * @param svp  Number of SVP
     * @param desc  Description of player's action that led to the SVP
     * @see #getSpecialVPInfo()
     * @since 2.0.00
     */
    public void addSpecialVPInfo(final int svp, final String desc)
    {
        if (svpInfo == null)
            svpInfo = new ArrayList<SpecialVPInfo>();

        svpInfo.add(new SpecialVPInfo(svp, desc));
    }

    /**
     * Get the details, if known, behind this player's {@link #getSpecialVP()} total.
     * In chronological order during game play.
     * @return Info on the Special Victory Points (SVP) awarded, or null; please treat as read-only
     * @since 2.0.00
     */
    public ArrayList<SpecialVPInfo> getSpecialVPInfo()
    {
        return svpInfo;
    }

    /**
     * Gets one-time player events for scenarios on the {@link SOCGame#hasSeaBoard large sea board}.
     * As events occur during a game, each one's {@link SOCScenarioPlayerEvent#flagValue} bit is set.
     *<P>
     * Example events: {@link SOCScenarioPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA},
     * {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     * Not all player events are returned here; some can't be represented in a single flag bit.
     * 
     * @return Player events which have occurred so far this game
     * @see #hasScenarioPlayerEvent(int)
     * @since 2.0.00
     */
    public int getScenarioPlayerEvents()
    {
        return scenario_playerEvents_bitmask;
    }

    /**
     * At client, set the player's {@link #getScenarioPlayerEvents()} based on a server message.
     * @param events  Player event flags to set; all others will be cleared.
     * @since 2.0.00
     */
    public void setScenarioPlayerEvents(final int events)
    {
        scenario_playerEvents_bitmask = events;
    }

    /**
     * Does this player have a certain scenario player event flag?
     * Flag bits are set as per-player events occur during a game.
     * @param spe  Player event, such as {@link SOCScenarioPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA}
     * @return  True if event flag is set for this player
     * @see #getScenarioPlayerEvents()
     * @since 2.0.00
     */
    public final boolean hasScenarioPlayerEvent(final SOCScenarioPlayerEvent spe)
    {
        return (0 != (scenario_playerEvents_bitmask & spe.flagValue));
    }

    /**
     * Set a certain scenario player event flag.
     * Can be set once per game.
     * @param spe  Player event, such as {@link SOCScenarioPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA}
     * @throws IllegalStateException if this flag is already set.
     *             This is checked at server, not client, due to message timing:
     *             Game events happen at the server before they happen at the client.
     * @see #clearScenarioPlayerEvent(SOCScenarioPlayerEvent)
     * @since 2.0.00
     */
    private final void setScenarioPlayerEvent(final SOCScenarioPlayerEvent spe)
        throws IllegalStateException
    {
        final int bit = spe.flagValue;
        if (game.isAtServer && (0 != (scenario_playerEvents_bitmask & bit)))
            throw new IllegalStateException("Already set: 0x" + Integer.toHexString(bit));
        scenario_playerEvents_bitmask |= bit;
    }

    /**
     * Clear a certain scenario player event flag.
     * @param spe  Player event, such as {@link SOCScenarioPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA}
     * @see #setScenarioPlayerEvent(SOCScenarioPlayerEvent)
     * @since 2.0.00
     */
    private final void clearScenarioPlayerEvent(final SOCScenarioPlayerEvent spe)
    {
        scenario_playerEvents_bitmask &= (~ spe.flagValue);
    }

    /**
     * For scenarios on the {@link soc.game.SOCBoardLarge large sea board}, get
     * this player's bitmask of land areas for tracking Special Victory Points (SVP).
     * Used with scenario game option {@link SOCGameOption#K_SC_SEAC _SC_SEAC}.
     * @return land areas bitmask, or 0
     * @since 2.0.00
     */
    public int getScenarioSVPLandAreas()
    {
        return scenario_svpFromEachLandArea_bitmask;
    }

    /**
     * At client, set the player's {@link #getScenarioPlayerEvents()} based on a server message.
     * @param las  Land areas to set in player's bitmask; all others will be cleared.
     * @since 2.0.00
     */
    public void setScenarioSVPLandAreas(final int las)
    {
        scenario_svpFromEachLandArea_bitmask = las;
    }

    /**
     * For scenarios on the {@link soc.game.SOCBoardLarge large sea board}, get
     * this player's starting settlement land areas, encoded to send over the network
     * from server to client. 0 otherwise.
     * @return  Encoded starting land area numbers 1 and 2
     * @see SOCPlayerElement#STARTING_LANDAREAS
     * @since 2.0.00
     */
    public int getStartingLandAreasEncoded()
    {
        return (startingLandArea2 << 8) | startingLandArea1;
    }

    /**
     * At client, set the player's {@link #getStartingLandAreasEncoded()} based on a server message.
     * @param slas  Starting land areas to set for player
     * @since 2.0.00
     */
    public void setStartingLandAreasEncoded(final int slas)
    {
        startingLandArea1 = slas & 0xFF;
        startingLandArea2 = (slas >> 8) & 0xFF;
    }

    /**
     * @return the list of nodes that touch the roads/ships in play
     */
    public Vector<Integer> getRoadNodes()
    {
        return roadNodes;
    }

    /**
     * @return this player's latest offer, or null if none
     */
    public SOCTradeOffer getCurrentOffer()
    {
        return currentOffer;
    }

    /**
     * set the current offer for this player
     *
     * @param of        the offer, or null to clear
     */
    public void setCurrentOffer(SOCTradeOffer of)
    {
        currentOffer = of;
    }

    /**
     * Are these two adjacent nodes connected by this player's road/ship?
     * @return true if one of this player's roads or ships
     *         connects the two nodes.
     *
     * @param node1         coordinates of first node
     * @param node2         coordinates of second node
     */
    public boolean isConnectedByRoad(final int node1, final int node2)
    {
        //D.ebugPrintln("isConnectedByRoad "+Integer.toHexString(node1)+", "+Integer.toHexString(node2)+" = "+roadNodeGraph[node1][node2]);

        final int[] adjac = roadNodeGraph.get(Integer.valueOf(node1));
        if (adjac == null)
            return false;

        for (int i = 2; i >= 0; --i)
            if (node2 == adjac[i])
                return true;

        return false;
    }

    /**
     * Put a piece into play.
     * {@link #updatePotentials(SOCPlayingPiece) Update potential} piece lists.
     * For roads, update {@link #roadNodes} and {@link #roadNodeGraph}.
     * Does not update longest road; instead, {@link SOCGame#putPiece(SOCPlayingPiece)}
     * calls {@link #calcLongestRoad2()}.
     *<P>
     * <b>Note:</b> Placing a city automatically removes the settlement there
     * via {@link SOCGame#putPiece(SOCPlayingPiece)} calling
     * {@link SOCPlayer#removePiece(SOCPlayingPiece, SOCPlayingPiece)}.
     *<P>
     * Call this before calling {@link SOCBoard#putPiece(SOCPlayingPiece)}.
     *<P>
     * For some scenarios on the {@link SOCGame#hasSeaBoard large sea board}, placing
     * a settlement in a new Land Area may award the player a Special Victory Point (SVP).
     * This method will increment {@link #specialVP}
     * and set the {@link SOCScenarioPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA} flag.
     *<P>
     * For scenario option {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * call with <tt>piece</tt> = {@link SOCFortress} to set the single "pirate fortress"
     * that this player must defeat to win.  When the fortress is defeated, it is
     * converted to a settlement; call with <tt>piece</tt> = {@link SOCSettlement} at the
     * fortress location.
     * <tt>isTempPiece</tt> must be false to set or clear the fortress.
     *
     * @param piece        The piece to be put into play; coordinates are not checked for validity.
     * @param isTempPiece  Is this a temporary piece?  If so, do not call the
     *                     game's {@link SOCScenarioEventListener}.
     * @throws IllegalArgumentException  only if piece is a {@link SOCFortress}, and either
     *                     <tt>isTempPiece</tt>, or player already has a fortress set.
     */
    public void putPiece(final SOCPlayingPiece piece, final boolean isTempPiece)
        throws IllegalArgumentException
    {
        /**
         * only do this stuff if it's our piece
         */
        if (piece.getPlayerNumber() == playerNumber)
        {
            if (! (piece instanceof SOCFortress))
                pieces.addElement(piece);

            final SOCBoard board = game.getBoard();
            switch (piece.getType())
            {
            /**
             * placing a road
             */
            case SOCPlayingPiece.ROAD:
                numPieces[SOCPlayingPiece.ROAD]--;
                putPiece_roadOrShip((SOCRoad) piece, board);
                break;

            /**
             * placing a settlement
             */
            case SOCPlayingPiece.SETTLEMENT:
                {
                    final int settlementNode = piece.getCoordinates();
                    if ((fortress != null) && (fortress.getCoordinates() == settlementNode))
                    {
                        // settlement is converted from the defeated fortress,
                        // not subtracted from player's numPieces
                        fortress = null;
                        if (isTempPiece)
                            throw new IllegalArgumentException("temporary fortress settlement");
                    } else {
                        numPieces[SOCPlayingPiece.SETTLEMENT]--;
                    }
                    putPiece_settlement_checkTradeRoutes((SOCSettlement) piece, board);
                    settlements.addElement((SOCSettlement) piece);
                    lastSettlementCoord = settlementNode;
                    buildingVP++;
    
                    /**
                     * update what numbers we're touching
                     */
                    ourNumbers.updateNumbers(piece, board);
    
                    /**
                     * update our port flags
                     */
                    final int portType = board.getPortTypeFromNodeCoord(settlementNode);
                    if (portType != -1)
                        setPortFlag(portType, true);

                    if ((board instanceof SOCBoardLarge) && (null != ((SOCBoardLarge) board).getLandAreasLegalNodes()))
                    {
                        /**
                         * track starting Land Areas on large board
                         */
                        if (game.isInitialPlacement())
                        {
                            final int newSettleArea = ((SOCBoardLarge) board).getNodeLandArea(settlementNode);
                            if (newSettleArea != 0)
                            {
                                if (startingLandArea1 == 0)
                                    startingLandArea1 = newSettleArea;
                                else if ((startingLandArea2 == 0) && (newSettleArea != startingLandArea1))
                                    startingLandArea2 = newSettleArea;
                            }
                        }

                        /**
                         * do we get an SVP for reaching a new land area?
                         */
                        else
                        {
                            final int newSettleArea = ((SOCBoardLarge) board).getNodeLandArea(settlementNode);
                            if ((newSettleArea != 0)
                                && (newSettleArea != startingLandArea1) && (newSettleArea != startingLandArea2))
                            {
                                putPiece_settlement_checkScenarioSVPs
                                    ((SOCSettlement) piece, newSettleArea, isTempPiece);
                            }
                        }
                    }
                }

                break;

            /**
             * placing a city
             */
            case SOCPlayingPiece.CITY:

                /**
                 * place the city
                 */
                numPieces[SOCPlayingPiece.CITY]--;
                cities.addElement((SOCCity) piece);
                buildingVP += 2;

                /**
                 * update what numbers we're touching;
                 * a city counts as touching a number twice
                 */
                ourNumbers.updateNumbers(piece, board);

                break;

            /**
             * placing a ship
             */
            case SOCPlayingPiece.SHIP:
                numPieces[SOCPlayingPiece.SHIP]--;
                putPiece_roadOrShip((SOCShip) piece, board);
                break;

            /**
             * placing the player's pirate fortress (scenario game opt _SC_PIRI)
             */
            case SOCPlayingPiece.FORTRESS:
                if (isTempPiece)
                    throw new IllegalArgumentException("temporary fortress");
                if (fortress != null)
                    throw new IllegalArgumentException("already has fortress");
                fortress = (SOCFortress) piece;
                break;
            }
        }

        updatePotentials(piece);
    }

    /**
     * For {@link #putPiece(SOCPlayingPiece, boolean) putPiece}, update road/ship-related info,
     * {@link #roadNodes}, {@link #roadNodeGraph} and {@link #lastRoadCoord}.
     * Call only when the piece is ours.
     * Does not update {@link #potentialRoads}/{@link #potentialShips}; see {@link #updatePotentials(SOCPlayingPiece)}.
     * @param piece  The road or ship
     * @param board  The board
     * @since 2.0.00
     */
    private void putPiece_roadOrShip(SOCRoad piece, SOCBoard board)
    {
        /**
         * before adding a ship, check to see if its trade route is now closed
         */
        if (piece instanceof SOCShip)
            putPiece_roadOrShip_checkNewShipTradeRoute((SOCShip) piece, board);

        /**
         * remember it
         */
        roads.addElement(piece);
        lastRoadCoord = piece.getCoordinates();

        /**
         * add the nodes that this road or ship touches to the roadNodes list
         */
        Collection<Integer> nodes = board.getAdjacentNodesToEdge(piece.getCoordinates());
        int[] nodeCoords = new int[2];
        int i = 0;

        for (Integer node : nodes)
        {
            //D.ebugPrintln("^^^ node = "+Integer.toHexString(node.intValue()));
            nodeCoords[i] = node.intValue();
            i++;

            /**
             * only add nodes that aren't in the list
             */

            //D.ebugPrintln("(roadNodes.contains(node)) = "+(roadNodes.contains(node)));
            if (!(roadNodes.contains(node)))
            {
                roadNodes.addElement(node);
            }
        }

        /**
         * update the graph of nodes connected by roads/ships
         * by adding this road/ship
         */
        {
            final int node0 = nodeCoords[0],
                      node1 = nodeCoords[1];
            final Integer node0Int = new Integer(node0),
                          node1Int = new Integer(node1);

            // roadNodeGraph[node0][node1]
            int[] rnArr = roadNodeGraph.get(node0Int);
            if (rnArr == null)
            {
                rnArr = new int[3];
                roadNodeGraph.put(node0Int, rnArr);
                rnArr[0] = node1;
                // rnArr[1] = 0, rnArr[2] = 0 by default
            } else {
                for (int j = 0; j < 3; ++j)
                {
                    if (node1 == rnArr[j])
                        break;
                    if (0 == rnArr[j])
                    {
                        rnArr[j] = node1;
                        break;
                    }
                }
            }

            // roadNodeGraph[node1][node0]
            rnArr = roadNodeGraph.get(node1Int);
            if (rnArr == null)
            {
                rnArr = new int[3];
                roadNodeGraph.put(node1Int, rnArr);
                rnArr[0] = node0;
                // rnArr[1] = 0, rnArr[2] = 0 by default
            } else {
                for (int j = 0; j < 3; ++j)
                {
                    if (node0 == rnArr[j])
                        break;
                    if (0 == rnArr[j])
                    {
                        rnArr[j] = node0;
                        break;
                    }
                }
            }
        }

        //D.ebugPrintln("^^ roadNodeGraph["+Integer.toHexString(nodeCoords[0])+"]["+Integer.toHexString(nodeCoords[1])+"] = true");
        //D.ebugPrintln("^^ roadNodeGraph["+Integer.toHexString(nodeCoords[1])+"]["+Integer.toHexString(nodeCoords[0])+"] = true");
    }

    /**
     * Check this new ship for adjacent settlements/cities/villages, to see if its trade route
     * will be closed.  Close it if so.
     *<P>
     * If the route becomes closed and is the player's first Cloth Trade route with a {@link SOCVillage},
     * this method sets that player flag and fires {@link SOCScenarioPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     *
     * @param newShip  Our new ship being placed in {@link #putPiece(SOCPlayingPiece, boolean)};
     *                 should not yet be added to {@link #roads}
     * @param board  game board
     * @since 2.0.00
     */
    private void putPiece_roadOrShip_checkNewShipTradeRoute
        (SOCShip newShip, SOCBoard board)
    {
        final boolean boardHasVillages = game.isGameOptionSet(SOCGameOption.K_SC_CLVI);
        final int[] edgeNodes = board.getAdjacentNodesToEdge_arr
            (newShip.getCoordinates());

        for (int i = 0; i < 2; ++i)
        {
            SOCPlayingPiece pp = board.settlementAtNode(edgeNodes[i]);
            if (pp != null)
            {
                if (pp.getPlayerNumber() != playerNumber)
                    pp = null;  // other players' pieces won't close a route
            }
            else if (boardHasVillages)
            {
                pp = ((SOCBoardLarge) board).getVillageAtNode(edgeNodes[i]);
            }

            if (pp == null)
                continue;  // nothing adjacent

            // if pp is at edgeNodes[1], check from edgeNodes[0], or vice versa
            final int edgeFarNode = edgeNodes[1 - i];
            final Vector<SOCShip> closedRoute = checkTradeRouteFarEndClosed(newShip, edgeFarNode);
            if (closedRoute != null)
            {
                if (pp instanceof SOCVillage)
                {
                    final boolean gotCloth = ((SOCVillage) pp).addTradingPlayer(this);
                    final boolean flagNew =
                        ! hasScenarioPlayerEvent(SOCScenarioPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);

                    if (flagNew)
                        setScenarioPlayerEvent(SOCScenarioPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);
                    if (flagNew || gotCloth)
                    {
                        if (game.scenarioEventListener != null)
                            game.scenarioEventListener.playerEvent
                                (game, this, SOCScenarioPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE, flagNew, pp);
                    }
                }

                break;
            }
        }
    }

    /**
     * Check this new settlement for adjacent open ships, to see their its trade route
     * will be closed.  Close it if so.
     * @param newSettlement  Our new settlement being placed in {@link #putPiece(SOCPlayingPiece, boolean)};
     *                 should not yet be added to {@link #settlements}
     * @param board  game board
     * @since 2.0.00
     */
    private void putPiece_settlement_checkTradeRoutes
        (SOCSettlement newSettle, SOCBoard board)
    {
        final int[] nodeEdges = board.getAdjacentEdgesToNode_arr
            (newSettle.getCoordinates());

        for (int i = 0; i < 3; ++i)
        {
            final int edge = nodeEdges[i];
            if (edge == -9)
                continue;
            SOCRoad pp = getRoadOrShip(edge);
            if ((pp == null) || ! (pp instanceof SOCShip))
                continue;
            SOCShip sh = (SOCShip) pp;
            if (sh.isClosed())
                continue;

            final int edgeFarNode =
                ((SOCBoardLarge) board).getAdjacentNodeFarEndOfEdge
                  (edge, newSettle.getCoordinates());
            checkTradeRouteFarEndClosed
                (sh, edgeFarNode);
        }
    }

    /**
     * Does the player get a Special Victory Point (SVP) for reaching a new land area?
     * Call when a settlement has been placed in a land area different from
     * {@link #startingLandArea1} and {@link #startingLandArea2}.
     * Used with game options {@link SOCGameOption#K_SC_SANY _SC_SANY} and {@link SOCGameOption#K_SC_SEAC _SC_SEAC}.
     * @param newSettle  Newly placed settlement
     * @param newSettleArea  Land area number of new settlement's location
     * @param isTempPiece  Is this a temporary piece?  If so, do not call the
     *                     game's {@link SOCScenarioEventListener}.
     * @since 2.0.00
     */
    private final void putPiece_settlement_checkScenarioSVPs
        (final SOCSettlement newSettle, final int newSettleArea, final boolean isTempPiece)
    {
        if ((! hasScenarioPlayerEvent(SOCScenarioPlayerEvent.SVP_SETTLED_ANY_NEW_LANDAREA))
             && game.isGameOptionSet(SOCGameOption.K_SC_SANY))
        {
            setScenarioPlayerEvent(SOCScenarioPlayerEvent.SVP_SETTLED_ANY_NEW_LANDAREA);
            ++specialVP;
            newSettle.specialVP = 1;
            newSettle.specialVPEvent = SOCScenarioPlayerEvent.SVP_SETTLED_ANY_NEW_LANDAREA;
       
            if ((game.scenarioEventListener != null) && ! isTempPiece)
            {
                // Notify (server or GUI)
                game.scenarioEventListener.playerEvent
                    (game, this, SOCScenarioPlayerEvent.SVP_SETTLED_ANY_NEW_LANDAREA, true, newSettle);
            }
        }

        final int laBit = (1 << (newSettleArea - 1));
        if ((0 == (laBit & scenario_svpFromEachLandArea_bitmask)) && game.isGameOptionSet(SOCGameOption.K_SC_SEAC))
        {
            scenario_svpFromEachLandArea_bitmask |= laBit;
            specialVP += 2;
            newSettle.specialVP = 2;
            newSettle.specialVPEvent = SOCScenarioPlayerEvent.SVP_SETTLED_EACH_NEW_LANDAREA;
       
            if ((game.scenarioEventListener != null) && ! isTempPiece)
            {
                // Notify (server or GUI)
                game.scenarioEventListener.playerEvent
                    (game, this, SOCScenarioPlayerEvent.SVP_SETTLED_EACH_NEW_LANDAREA, true, newSettle);
            }
        }
    }

    /**
     * undo the putting of a piece.
     *<P>
     * Among other actions,
     * Updates the potential building lists
     * for removing settlements or cities.
     * Updates port flags, this player's dice resource numbers, etc.
     *<P>
     * Call this only after calling {@link SOCBoard#removePiece(SOCPlayingPiece)}.
     *<P>
     * If the piece is ours, calls {@link #removePiece(SOCPlayingPiece, SOCPlayingPiece) removePiece(piece, null)}.
     *<P>
     * For roads, does not update longest road; if you need to,
     * call {@link #calcLongestRoad2()} after this call.
     *<P>
     * For removing second initial settlement (state START2B),
     *   will zero the player's resource cards.
     *
     * @param piece         the piece placement to be undone.
     *
     */
    public void undoPutPiece(SOCPlayingPiece piece)
    {
        final boolean ours = (piece.getPlayerNumber() == playerNumber);
        final int pieceCoord = piece.getCoordinates();
        final Integer pieceCoordInt = new Integer(pieceCoord);

        final SOCBoard board = game.getBoard();
        switch (piece.getType())
        {
        //
        // undo a played road or ship
        //
        case SOCPlayingPiece.SHIP:  // fall through to ROAD
        case SOCPlayingPiece.ROAD:

            if (ours)
            {
                //
                // update the potential places to build roads/ships
                //
                removePiece(piece, null);
            }
            else
            {
                //
                // not our road/ship
                //
                // make it a legal space again
                //
                final boolean isCoastline = game.hasSeaBoard && ((SOCBoardLarge) board).isEdgeCoastline(pieceCoord);
                if (piece.getType() == SOCPlayingPiece.ROAD)
                {
                    legalRoads.add(pieceCoordInt);
                    if (isCoastline &&
                        ((legalShipsRestricted == null) || legalShipsRestricted.contains(pieceCoordInt)))
                        legalShips.add(pieceCoordInt);
                } else {
                    if ((legalShipsRestricted == null) || legalShipsRestricted.contains(pieceCoordInt))
                        legalShips.add(pieceCoordInt);
                    if (isCoastline)
                        legalRoads.add(pieceCoordInt);
                }

                //
                // call updatePotentials
                // on our roads/ships that are adjacent to
                // this edge
                //
                Vector<Integer> adjEdges = board.getAdjacentEdgesToEdge(pieceCoord);

                for (SOCRoad road : roads)
                {
                    for (Integer edgeObj : adjEdges)
                    {
                        final int edge = edgeObj.intValue();

                        if (road.getCoordinates() == edge)
                        {
                            updatePotentials(road);
                        }
                    }
                }
            }

            break;

        //
        // undo a played settlement
        //
        case SOCPlayingPiece.SETTLEMENT:

            if (ours)
            {
                removePiece(piece, null);
                ourNumbers.undoUpdateNumbers(piece, board);

                //
                // update our port flags
                //
                final int portType = board.getPortTypeFromNodeCoord(pieceCoord);
                if (portType != -1)
                {
                    boolean only1portOfType;
                    if (portType == SOCBoard.MISC_PORT)
                    {
                        only1portOfType = false;
                    } else {
                        // how many 2:1 ports of this type?
                        int nPort = board.getPortCoordinates(portType).size() / 2;
                        only1portOfType = (nPort < 2);
                    }
                    
                        if (only1portOfType)
                        {
                            // since only one settlement on this kind of port,
                            // we can just set the port flag to false
                            setPortFlag(portType, false);
                        }
                        else
                        {
                            //
                            // there are muliple ports, so we need to check all
                            // the settlements and cities
                            //
                            boolean havePortType = false;

                            for (SOCSettlement settlement : settlements)
                            {
                                if (board.getPortTypeFromNodeCoord(settlement.getCoordinates()) == portType)
                                {
                                    havePortType = true;
                                    break;
                                }
                            }

                            if (!havePortType)
                            {
                                for (SOCCity city : cities)
                                {
                                    if (board.getPortTypeFromNodeCoord(city.getCoordinates()) == portType)
                                    {
                                        havePortType = true;
                                        break;
                                    }
                                }
                            }

                            setPortFlag(portType, havePortType);
                        }
                }  // if (portType != -1)
            }  // if (ours)

            //
            // update settlement potentials
            //
            undoPutPieceAuxSettlement(pieceCoord);

            //
            // check adjacent nodes
            //
            Vector<Integer> adjNodesEnum = board.getAdjacentNodesToNode(pieceCoord);

            for (Integer adjNodeObj : adjNodesEnum)
            {
                final int adjNode = adjNodeObj.intValue();
                undoPutPieceAuxSettlement(adjNode);
            }

            if (ours &&
                ((game.getGameState() == SOCGame.START2B) || (game.getGameState() == SOCGame.START3B)))
            {
                resources.clear();
                // resourceStats[] is 0 already, because nothing's been rolled yet
            }

            break;

        //
        // undo a played city
        //
        case SOCPlayingPiece.CITY:

            if (ours)
            {
                removePiece(piece, null);
                potentialCities.add(pieceCoordInt);

                /**
                 * update what numbers we're touching
                 * a city counts as touching a number twice
                 */
                ourNumbers.undoUpdateNumbers(piece, board);
                ourNumbers.undoUpdateNumbers(piece, board);
            }

            break;

        }
    }

    /**
     * Auxiliary function for undoing settlement placement
     *
     * @param settlementNode  the node we want to consider
     */
    protected void undoPutPieceAuxSettlement(int settlementNode)
    {
        final Integer settleNodeInt = new Integer(settlementNode);

        //D.ebugPrintln("))))) undoPutPieceAuxSettlement : node = "+Integer.toHexString(settlementNode));
        //
        // if this node doesn't have any neighboring settlements or cities, make it legal
        //
        boolean haveNeighbor = false;
        SOCBoard board = game.getBoard();
        Vector<Integer> adjNodes = board.getAdjacentNodesToNode(settlementNode);

        for (SOCSettlement settlement : board.getSettlements())
        {
            for (Integer adjNodeObj : adjNodes)
            {
                final int adjNode = adjNodeObj.intValue();

                if (adjNode == settlement.getCoordinates())
                {
                    haveNeighbor = true;

                    //D.ebugPrintln(")))) haveNeighbor = true : node = "+Integer.toHexString(adjNode.intValue()));
                    break;
                }
            }

            if (haveNeighbor == true)
            {
                break;
            }
        }

        if (!haveNeighbor)
        {
            for (SOCCity city : board.getCities())
            {
                for (Integer adjNodeObj : adjNodes)
                {
                    final int adjNode = adjNodeObj.intValue();

                    if (adjNode == city.getCoordinates())
                    {
                        haveNeighbor = true;

                        //D.ebugPrintln(")))) haveNeighbor = true : node = "+Integer.toHexString(adjNode.intValue()));
                        break;
                    }
                }

                if (haveNeighbor == true)
                {
                    break;
                }
            }

            if (!haveNeighbor)
            {
                //D.ebugPrintln(")))) haveNeighbor = false");
                //
                // check to see if this node is on the board
                //
                if (board.isNodeOnLand(settlementNode))
                {
                    legalSettlements.add(settleNodeInt);

                    //
                    // if it's the beginning of the game, make it potential
                    //
                    //D.ebugPrintln(")))) legalSettlements["+Integer.toHexString(settlementNode)+"] = true");
                    //
                    if (game.getGameState() < SOCGame.PLAY)
                    {
                        potentialSettlements.add(settleNodeInt);

                        //D.ebugPrintln(")))) potentialSettlements["+Integer.toHexString(settlementNode)+"] = true");
                    }
                    else
                    {
                        //
                        // if it's legal and we have an adjacent road, make it potential
                        //
                        //D.ebugPrintln(")))) checking for adjacent roads");
                        boolean adjRoad = false;
                        Vector<Integer> adjEdges = board.getAdjacentEdgesToNode(settlementNode);

                        for (SOCRoad road : roads)
                        {
                            for (Integer adjEdgeObj : adjEdges)
                            {
                                final int adjEdge = adjEdgeObj.intValue();

                                if (road.getCoordinates() == adjEdge)
                                {
                                    //D.ebugPrintln("))) found adj road at "+Integer.toHexString(adjEdge.intValue()));
                                    adjRoad = true;

                                    break;
                                }
                            }

                            if (adjRoad == true)
                            {
                                break;
                            }
                        }

                        if (adjRoad)
                        {
                            potentialSettlements.add(settleNodeInt);

                            //D.ebugPrintln(")))) potentialSettlements["+Integer.toHexString(settlementNode)+"] = true");
                        }
                    }
                }
            }
        }
    }

    /**
     * remove a player's piece from the board,
     * and put it back in the player's hand.
     *<P>
     * Most callers will want to instead call {@link #undoPutPiece(SOCPlayingPiece)}
     * which calls removePiece and does more.
     *<P>
     * Don't call removePiece for a {@link SOCFortress}; see {@link #getFortress()} javadoc.
     *<P>
     *<B>Note:</b> removePiece does NOT update the potential building lists
     *           for removing settlements or cities.
     * It does update potential road lists.
     * For roads, updates {@link #roadNodes} and {@link #roadNodeGraph}.
     *
     * @param piece  Our player's piece, to be removed from the board
     * @param replacementPiece  Piece that's replacing this piece; usually null unless player is upgrading to a city.
     *          If not null, and same player as <tt>piece</tt>, the removed piece's {@link SOCPlayingPiece#specialVP}
     *          and {@link SOCPlayingPiece#specialVPEvent} are copied to <tt>replacementPiece</tt>
     *          instead of being subtracted from the player's {@link #getSpecialVP()} count.
     * @see #undoPutPiece(SOCPlayingPiece)
     */
    public void removePiece(SOCPlayingPiece piece, SOCPlayingPiece replacementPiece)
    {
        D.ebugPrintln("--- SOCPlayer.removePiece(" + piece + ")");

        final int pieceCoord = piece.getCoordinates();
        final Integer pieceCoordInt = new Integer(pieceCoord);
        final int ptype = piece.getType();

        Enumeration<SOCPlayingPiece> pEnum = pieces.elements();
        SOCBoard board = game.getBoard();

        while (pEnum.hasMoreElements())
        {
            SOCPlayingPiece p = pEnum.nextElement();

            if ((ptype == p.getType()) && (pieceCoord == p.getCoordinates()))
            {
                pieces.removeElement(p);

                if (p.specialVP != 0)
                {
                    if ((replacementPiece == null)
                        || (replacementPiece.player != piece.player))
                    {
                        removePieceUpdateSpecialVP(p);
                    } else {
                        replacementPiece.specialVP = p.specialVP;
                        replacementPiece.specialVPEvent = p.specialVPEvent;
                    }
                }

                switch (ptype)
                {
                case SOCPlayingPiece.SHIP:  // fall through to ROAD
                case SOCPlayingPiece.ROAD:
                    roads.removeElement(p);
                    numPieces[ptype]++;

                    int[] edgeNodeCoords = new int[2];  // will hold edge's adjacent nodes

                    /**
                     * remove the nodes this road/ship touches from the roadNodes list
                     */
                    {
                        Collection<Integer> nodes = board.getAdjacentNodesToEdge(pieceCoord);
                        int i = 0;

                        for (Integer node : nodes)
                        {
                            edgeNodeCoords[i] = node.intValue();
                            i++;

                            /**
                             * only remove a node if none of our roads/ships are touching it
                             */
                            Collection<Integer> adjEdges = board.getAdjacentEdgesToNode(node.intValue());
                            boolean match = false;

                            for (SOCRoad rd : roads)
                            {
                                final int rdEdge = rd.getCoordinates();

                                for (Integer adjEdgeObj : adjEdges)
                                {
                                    if (rdEdge == adjEdgeObj)
                                    {
                                        match = true;
                                        break;
                                    }
                                }

                                if (match)
                                {
                                    break;
                                }
                            }

                            if (! match)
                            {
                                roadNodes.removeElement(node);
                                potentialSettlements.remove(node);
                            }
                        }
                    }

                    /**
                     * remove this road/ship from the graph of nodes connected by roads/ships
                     */
                    {
                        final int node0 = edgeNodeCoords[0],
                                  node1 = edgeNodeCoords[1];
                        final Integer node0Int = new Integer(node0),
                                      node1Int = new Integer(node1);

                        // check roadNodeGraph[node0][node1]
                        int[] rnArr = roadNodeGraph.get(node0Int);
                        if (rnArr != null)
                        {
                            for (int j = 0; j < 3; ++j)
                                if (node1 == rnArr[j])
                                {
                                    rnArr[j] = 0;
                                    break;
                                }
                        }

                        // check roadNodeGraph[node1][node0]
                        rnArr = roadNodeGraph.get(node1Int);
                        if (rnArr != null)
                        {
                            for (int j = 0; j < 3; ++j)
                                if (node0 == rnArr[j])
                                {
                                    rnArr[j] = 0;
                                    break;
                                }
                        }
                    }

                    /**
                     * update the potential places to build roads/ships.
                     *
                     * NOTE: we're assuming that we could build it here
                     * before, so we can make it a legal spot again.
                     * On coastline, since might be both potential road and ship,
                     * look for an adjacent road, ship, settlement or city.
                     */
                    final boolean isCoastlineTransition
                        = game.hasSeaBoard
                          && ((SOCBoardLarge) board).isEdgeCoastline(pieceCoord)
                          && doesTradeRouteContinuePastEdge
                              (pieceCoord, (ptype == SOCPlayingPiece.ROAD));  // look for opposite type for transition
                    if (ptype == SOCPlayingPiece.ROAD)
                    {
                        potentialRoads.add(pieceCoordInt);
                        legalRoads.add(pieceCoordInt);
                        if (isCoastlineTransition &&
                            ((legalShipsRestricted == null) || legalShipsRestricted.contains(pieceCoordInt)))
                        {
                            potentialShips.add(pieceCoordInt);
                            legalShips.add(pieceCoordInt);
                        }
                    } else {
                        potentialShips.add(pieceCoordInt);
                        legalShips.add(pieceCoordInt);
                        if (isCoastlineTransition)
                        {
                            potentialRoads.add(pieceCoordInt);
                            legalRoads.add(pieceCoordInt);
                        }
                        // (Since we're removing a ship, skip checking legalShipsRestricted.)
                    }

                    /**
                     * check each adjacent legal edge, if there are
                     * no roads touching it, then the adjacent is no longer a
                     * potential road
                     */
                    // TODO roads/ships are not interchangeable here
                    Collection<Integer> adjEdgesEnum = board.getAdjacentEdgesToEdge(pieceCoord);

                    for (Integer adjEdge : adjEdgesEnum)
                    {
                        if (! (potentialRoads.contains(adjEdge) || potentialShips.contains(adjEdge)))
                            continue;

                        boolean isPotentialRoad = false;  // or, isPotentialShip

                        /**
                         * check each adjacent node for blocking
                         * settlements or cities
                         */
                        final int adjEdgeID = adjEdge.intValue();
                        final int[] adjNodes = board.getAdjacentNodesToEdge_arr(adjEdgeID);

                        for (int ni = 0; (ni < 2) && ! isPotentialRoad; ++ni)
                        {
                            boolean blocked = false;  // Are we blocked in this node's direction?
                            final int adjNode = adjNodes[ni];
                            final SOCPlayingPiece aPiece = board.settlementAtNode(adjNode);
                            if ((aPiece != null)
                                && (aPiece.getPlayerNumber() != playerNumber))
                            {
                                /**
                                 * we're blocked, don't bother checking adjacent edges
                                 */
                                blocked = true;
                            }

                            if (! blocked)
                            {
                                Collection<Integer> adjAdjEdgesEnum = board.getAdjacentEdgesToNode(adjNode);

                                for (Integer adjAdjEdgesObj : adjAdjEdgesEnum)
                                {
                                    final int adjAdjEdge = adjAdjEdgesObj.intValue();

                                    if (adjAdjEdge != adjEdgeID)
                                    {
                                        for (SOCRoad ourRoad : roads)
                                        {
                                            if (ourRoad.getCoordinates() == adjAdjEdge)
                                            {
                                                /**
                                                 * we're still connected
                                                 */
                                                isPotentialRoad = true;
                                                break;
                                            }
                                        }
                                    }

                                    if (isPotentialRoad)
                                        break;  // no need to keep looking at adjacent edges
                                }
                            }
                        }

                        if (ptype == SOCPlayingPiece.ROAD)
                        {
                            if (isPotentialRoad && legalRoads.contains(adjEdge))
                                potentialRoads.add(adjEdge);
                            else
                                potentialRoads.remove(adjEdge);
                        } else {
                            if (isPotentialRoad && legalShips.contains(adjEdge))
                                potentialShips.add(adjEdge);
                            else
                                potentialShips.remove(adjEdge);
                        }
                    }

                    break;

                case SOCPlayingPiece.SETTLEMENT:
                    settlements.removeElement(p);
                    numPieces[SOCPlayingPiece.SETTLEMENT]++;
                    buildingVP--;

                    break;

                case SOCPlayingPiece.CITY:
                    cities.removeElement(p);
                    numPieces[SOCPlayingPiece.CITY]++;
                    buildingVP -= 2;

                    break;
                }

                break;
            }
        }
    }

    /**
     * As part of {@link #removePiece(SOCPlayingPiece, SOCPlayingPiece) removePiece(SOCPlayingPiece, null)},
     * update player's {@link #specialVP} and related fields.
     * Not called if the removed piece is being replaced by another one (settlement upgrade to city).
     * @param p  Our piece being removed, which has {@link SOCPlayingPiece#specialVP} != 0
     * @since 2.0.00
     */
    private final void removePieceUpdateSpecialVP(final SOCPlayingPiece p)
    {
        specialVP -= p.specialVP;

        switch (p.specialVPEvent)
        {
        case SVP_SETTLED_ANY_NEW_LANDAREA:
            clearScenarioPlayerEvent(p.specialVPEvent);
            break;
        }
    }

    /**
     * When a {@link SOCBoardLarge#FOG_HEX} is revealed to be water,
     * or a land hex at the board's {@link SOCBoardLarge#isHexAtBoardMargin(int) margin},
     * update the set of edges used by {@link #isLegalShip(int)}.
     * The revealed hex's edges previously weren't part of the set,
     * because we didn't know if the fog hid land or water.
     *<P>
     * Called by {@link SOCGame#revealFogHiddenHex(int, int, int)}.
     * If the hex type isn't {@link SOCBoard#WATER_HEX}
     * and ! {@link SOCBoardLarge#isHexAtBoardMargin(int) board.isHexAtBoardMargin(hexCoord)},
     * does nothing.
     * Call only if {@link SOCGame#hasSeaBoard}.
     * @param hexCoord  Coordinate of hex to add if water
     * @since 2.0.00
     */
    void updateLegalShipsAddHex(final int hexCoord)
    {
        final SOCBoardLarge board = (SOCBoardLarge) game.getBoard();
        final int htype = board.getHexTypeFromCoord(hexCoord);
        if ((htype != SOCBoard.WATER_HEX) && ! board.isHexAtBoardMargin(hexCoord))
            return;

        // Previously not a legal ship edge, because
        // we didn't know if the fog hid land or water
        final int[] sides = board.getAdjacentEdgesToHex(hexCoord);
        for (int i = 0; i < 6; ++i)
        {
            final int edge = sides[i];
            if ((htype == SOCBoard.WATER_HEX) || board.isEdgeCoastline(edge))
            {
                final Integer edgeInt = Integer.valueOf(edge);
                if ((legalShipsRestricted == null) || legalShipsRestricted.contains(edgeInt))
                    legalShips.add(edgeInt);
            }
        }
    }

    /**
     * update the arrays that keep track of where
     * this player can play further pieces, after a
     * piece has just been played, or after another
     * player's adjacent piece has been removed.
     *<P>
     * <b>Special case:</b> In game scenario {@link SOCGameOption#K_SC_PIRI _SC_PIRI},
     * ship routes can't branch in different directions, only extend from their ends.
     * So when a ship is placed to extend a sea route, this method will remove
     * nearby potential ships which would now be side branches.
     *
     * @param piece         a piece that has just been played
     *          or our piece adjacent to another player's
     *          removed piece
     */
    public void updatePotentials(SOCPlayingPiece piece)
    {
        //D.ebugPrintln("&&& UPDATING POTENTIALS FOR "+piece);
        int tmp;
        final boolean ours;
        boolean blocked;
        final int id = piece.getCoordinates();
        final Integer idInt = new Integer(id);
        SOCBoard board = game.getBoard();

        /**
         * check if this piece is ours
         */
        ours = (piece.getPlayerNumber() == playerNumber);

        final int ptype = piece.getType();
        switch (ptype)
        {
        /**
         * a ship or road was played
         */
        case SOCPlayingPiece.SHIP: // fall through to ROAD
        case SOCPlayingPiece.ROAD:

            // remove non-potentials;
            // if not in that set, does nothing
            potentialRoads.remove(idInt);
            legalRoads.remove(idInt);

            potentialShips.remove(idInt);
            legalShips.remove(idInt);

            if (ours)
            {
                // only add potentials if it's our piece
                // and the far end isn't blocked by
                // another player.
                final int[] nodes = board.getAdjacentNodesToEdge_arr(id);

                for (int ni = 0; ni < 2; ++ni)
                {
                    final int node = nodes[ni];

                    /**
                     * check for a foreign settlement or city
                     */
                    blocked = false;

                    SOCPlayingPiece p = board.settlementAtNode(node);
                    if ((p != null) && (p.getPlayerNumber() != playerNumber))
                    {
                        blocked = true;
                    }

                    if (! blocked)
                    {
                        int[] edges = board.getAdjacentEdgesToNode_arr(node);
                        for (int i = 0; i < 3; ++i)
                        {
                            int edge = edges[i];
                            if (edge != -9)
                            {
                                final Integer edgeInt = new Integer(edge);
                                if (ptype == SOCPlayingPiece.ROAD)
                                {
                                    if (legalRoads.contains(edgeInt))
                                        potentialRoads.add(edgeInt);
                                } else {
                                    if (legalShips.contains(edgeInt))
                                        potentialShips.add(edgeInt);
                                }
                            }
                        }

                        final Integer nodeInt = new Integer(node);
                        if (legalSettlements.contains(nodeInt))
                        {
                            potentialSettlements.add(nodeInt);
                        }
                    }
                }

                // For game scenario _SC_PIRI, ship routes can't branch
                // in different directions, only extend from their ends.
                if ((ptype == SOCPlayingPiece.SHIP) && game.isGameOptionSet(SOCGameOption.K_SC_PIRI))
                {
                    // Find the end of this ship edge with a previous ship,
                    // make sure that end node has no other potential ships.
                    // Remove any potentials to prevent new branches from the old node.
                    // Check both end nodes of the new edge, in case we're joining
                    // 2 previous "segments" of ship routes from different directions.

                    for (int ni = 0; ni < 2; ++ni)
                    {
                        final int node = nodes[ni];
                        boolean foundOtherShips = false;

                        final int[] edges = board.getAdjacentEdgesToNode_arr(node);
                        for (int i = 0; i < 3; ++i)
                        {
                            final int edge = edges[i];
                            if ((edge == -9) || (edge == id))
                                continue;
                            if (null != getRoadOrShip(edge))
                            {
                                foundOtherShips = true;
                                break;
                            }
                        }

                        if (foundOtherShips)
                        {
                            for (int i = 0; i < 3; ++i)
                            {
                                final Integer edgeInt = Integer.valueOf(edges[i]);
                                if (potentialShips.contains(edgeInt))
                                    potentialShips.remove(edgeInt);
                            }
                        }
                    }
                }
            }

            break;

        /**
         * a settlement was placed
         */
        case SOCPlayingPiece.SETTLEMENT:

            // remove non-potentials:
            // no settlement at this node coordinate,
            // no settlement in its adjacent nodes.
            potentialSettlements.remove(idInt);
            legalSettlements.remove(idInt);
            int[] adjac = board.getAdjacentNodesToNode_arr(id);
            for (int i = 0; i < 3; ++i)
            {
                if (adjac[i] != -9)
                {
                    final Integer adjacNodeInt = new Integer(adjac[i]);
                    potentialSettlements.remove(adjacNodeInt);
                    legalSettlements.remove(adjacNodeInt);
                }
            }

            // if it's our piece, add potential roads/ships and city.
            // otherwise, check for cutoffs of our potential roads/ships by this piece.

            if (ours)
            {
                potentialCities.add(idInt);

                adjac = board.getAdjacentEdgesToNode_arr(id);
                for (int i = 0; i < 3; ++i)
                {
                    tmp = adjac[i];
                    if (tmp != -9)
                    {
                        final Integer tmpEdgeInt = new Integer(tmp);
                        if (legalRoads.contains(tmpEdgeInt))
                            potentialRoads.add(tmpEdgeInt);
                        if (legalShips.contains(tmpEdgeInt))
                            potentialShips.add(tmpEdgeInt);
                    }
                }
            }
            else
            {
                // see if a nearby potential road/ship has been cut off:
                // build vector of our road edge IDs placed so far.
                // for each of 3 adjacent edges to node:
                //  if we have potentialRoad(edge) or potentialShip(edge)
                //    check ourRoads vs that edge's far-end (away from node of new settlement)
                //    unless we have a road on far-end, this edge is no longer potential,
                //      because we're not getting past opponent's new settlement (on this end
                //      of the edge) to build it.

                // ourRoads contains both roads and ships.
                //  TODO may need to separate them and check twice,
                //       or differentiate far-side roads vs ships.
                Hashtable<Integer,Object> ourRoads = new Hashtable<Integer,Object>();  // TODO more efficient way of looking this up, with fewer temp objs
                Object hashDummy = new Object();   // a value is needed for hashtable
                for (SOCPlayingPiece p : this.pieces)
                {
                    if (p instanceof SOCRoad)   // roads and ships
                        ourRoads.put(new Integer(p.getCoordinates()), hashDummy);
                }

                adjac = board.getAdjacentEdgesToNode_arr(id);
                for (int i = 0; i < 3; ++i)
                {
                    tmp = adjac[i];  // edge coordinate
                    if (tmp == -9)
                        continue;
                    final Integer tmpInt = new Integer(tmp);
                    if (! (potentialRoads.contains(tmpInt)
                           || potentialShips.contains(tmpInt)))
                    {
                        continue;  // We don't have a potential road here, so
                                   // there's nothing to be potentially broken.
                    }

                    // find the far-end node coordinate
                    final int farNode;
                    {
                        final int[] enodes = board.getAdjacentNodesToEdge_arr(tmp);
                        if (enodes[0] == id)
                            farNode = enodes[1];
                        else
                            farNode = enodes[0];
                    }

                    // now find the 2 other edges past that node;
                    // we may have actual roads/ships on them already.
                    // If so, we'll still be able to get to the edge (tmp)
                    // which connects that node with the new settlement's node,
                    // from its far side.

                    final int[] farEdges = board.getAdjacentEdgesToNode_arr(farNode);
                    boolean foundOurRoad = false;
                    for (int ie = 0; ie < 3; ++ie)
                    {
                        int farEdge = farEdges[ie];
                        if ((farEdge != tmp) && ourRoads.contains(new Integer(farEdge)))
                        {
                            foundOurRoad = true;
                            break;
                        }
                    }
                    if (! foundOurRoad)
                    {
                        // the potential road is no longer connected
                        potentialRoads.remove(tmpInt);
                        potentialShips.remove(tmpInt);
                    }
                }
            }

            break;

        /**
         * a city was placed
         */
        case SOCPlayingPiece.CITY:

            // remove non-potentials
            potentialCities.remove(idInt);

            break;

        }
    }

    /**
     * Get this player's current potential settlement nodes.
     * At the start of the game (before/during initial placement), this is all legal nodes.
     * Afterwards it's mostly empty, and follows from the player's road locations.
     *<P>
     * Please make no changes, treat the returned set as read-only.
     * @return the player's set of {@link Integer} potential-settlement node coordinates
     * @see #getPotentialSettlements_arr()
     * @since 2.0.00
     */
    public HashSet<Integer> getPotentialSettlements()
    {
        return potentialSettlements;
    }

    /**
     * Get this player's current potential settlement nodes.
     * At the start of the game (before/during initial placement), this is all legal nodes.
     * Afterwards it's mostly empty, and follows from the player's road locations.
     *<P>
     * This variant returns them as an array, for ease of use when traversing all potential settlements.
     * @return the player's set of potential-settlement node coordinates,
     *     or if none, <tt>null</tt>
     * @see #getPotentialSettlements()
     * @since 2.0.00
     */
    public int[] getPotentialSettlements_arr()
    {
        int L = potentialSettlements.size();
        if (L == 0)
            return null;

        int[] pset = new int[L];
        Iterator<Integer> it = potentialSettlements.iterator();
        for (int i = 0; it.hasNext(); ++i)
            pset[i] = it.next().intValue();
        return pset;
    }

    /**
     * Set which nodes are potential settlements.
     * Called at client when joining/creating a game.
     * At server, unless {@link SOCGame#hasSeaBoard},
     * the potentials list is instead copied at start
     * of game from legalSettlements.
     *<P>
     * If our game uses the large sea board ({@link SOCGame#hasSeaBoard}),
     * and <tt>setLegalsToo</tt>, and <tt>psList</tt> is not empty,
     * then also update the player's legal settlements
     * and legal road sets, since they aren't constant
     * on that type of board; don't call this method before calling
     * {@link SOCBoardLarge#setLegalAndPotentialSettlements(Collection, int, HashSet[])},
     * or the road sets won't be complete.
     *<P>
     * Call this method before, not after, calling {@link #setRestrictedLegalShips(int[])}.
     * However, if the player already has a restricted legal ship edge list, this method won't clear it.
     *<P>
     * This method is called at the server only when <tt>game.hasSeaBoard</tt>,
     * just after makeNewBoard in {@link SOCGame#startGame()}.
     *<P>
     * Before v2.0.00, this method was called <tt>setPotentialSettlements</tt>.
     *
     * @param psList  the list of potential settlements,
     *     a {@link Vector} or {@link HashSet} of
     *     {@link Integer} node coordinates
     * @param setLegalsToo  If true, also update legal settlements/roads/ships
     *     if we're using the large board layout.  [Parameter added in v2.0.00]
     *     <P>
     *     In scenario {@code _SC_PIRI}, for efficiency, the legal ships list will remain
     *     empty until {@link #setRestrictedLegalShips(int[])} is called.
     * @param legalLandAreaNodes If non-null and <tt>setLegalsToo</tt>,
     *     all Land Areas' legal (but not currently potential) node coordinates.
     *     Index 0 is ignored; land area numbers start at 1.
     * @see #addLegalSettlement(int)
     */
    public void setPotentialAndLegalSettlements
        (Collection<Integer> psList, final boolean setLegalsToo, final HashSet<Integer>[] legalLandAreaNodes)
    {
        clearPotentialSettlements();
        potentialSettlements.addAll(psList);

        if (setLegalsToo && game.hasSeaBoard
            && ((! psList.isEmpty()) || (legalLandAreaNodes != null)) )
        {
            legalSettlements.clear();
            legalSettlements.addAll(psList);
            if (legalLandAreaNodes != null)
            {
                for (int i = 1; i < legalLandAreaNodes.length; ++i)
                    legalSettlements.addAll(legalLandAreaNodes[i]);
            }

            legalRoads = game.getBoard().initPlayerLegalRoads();
            if (! game.isGameOptionSet(SOCGameOption.K_SC_PIRI))
                legalShips = ((SOCBoardLarge) game.getBoard()).initPlayerLegalShips();
            else
                legalShips.clear();  // caller must soon call setRestrictedLegalShips
        }
    }

    /**
     * Add this node to the player's legal settlement coordinates, for future possible placement.
     * Used in some scenarios when {@link SOCGame#hasSeaBoard} to add a location
     * after calling {@link #setPotentialAndLegalSettlements(Collection, boolean, HashSet[])}.
     * This would be a lone location beyond the usual starting/legal LandAreas on the scenario's board.
     * @param node  A node coordinate to add, or 0 to do nothing
     * @since 2.0.00
     * @see #isLegalSettlement(int)
     * @see #getAddedLegalSettlement()
     */
    public void addLegalSettlement(final int node)
    {
        if (node == 0)
            return;
        legalSettlements.add(Integer.valueOf(node));
        addedLegalSettlement = node;
    }

    /**
     * Can a settlement be placed at this node?
     * Calls {@link #isPotentialSettlement(int)}.
     * Does not check {@link #getNumPieces(int) getNumPieces(SETTLEMENT)}.
     * On the large board, checks against {@link SOCBoardLarge#getPlayerExcludedLandAreas()}.
     * @param node  node coordinate
     * @return  True if can place, false otherwise
     * @see SOCGame#couldBuildSettlement(int)
     * @since 2.0.00
     */
    public boolean canPlaceSettlement(final int node)
    {
        if (! isPotentialSettlement(node))
            return false;

        if (game.hasSeaBoard)
        {
            final SOCBoardLarge board = (SOCBoardLarge) game.getBoard();
            if (board.isNodeInLandAreas
                (node, board.getPlayerExcludedLandAreas()))
                return false;
        }

        return true;
    }

    /**
     * Is this node a potential settlement?
     * True if the location is legal, currently not occupied,
     * no settlement is currently on an adjacent node,
     * and we have an adjacent road or ship.
     * Does not check {@link #getNumPieces(int) getNumPieces(SETTLEMENT)}.
     * @return true if this node is a potential settlement
     * @param node        the coordinates of a node on the board
     * @see #canPlaceSettlement(int)
     */
    public boolean isPotentialSettlement(final int node)
    {
        return potentialSettlements.contains(Integer.valueOf(node));
    }

    /**
     * Set this node to not be a potential settlement.
     * For use (by robots) when the server denies our request to build at a certain spot.
     *
     * @param node  coordinates of a node on the board
     * @see #isPotentialSettlement(int)
     * @since 1.1.09
     */
    public void clearPotentialSettlement(final int node)
    {
        potentialSettlements.remove(Integer.valueOf(node));
    }

    /**
     * Is this node to a legal settlement?
     * @return true if this edge is a legal settlement
     * @param node        the coordinates of a node on the board
     * @since 2.0.00
     * @see #getAddedLegalSettlement()
     */
    public boolean isLegalSettlement(final int node)
    {
        return legalSettlements.contains(Integer.valueOf(node));
    }

    /**
     * Get the legal-settlement location, if any, added by {@link #addLegalSettlement(int)}.
     *<P>
     * That method could be called multiple times, but only the most recently added node
     * is returned by this method.
     *
     * @return  Legal settlement node added by {@link #addLegalSettlement(int)}, or 0
     * @since 2.0.00
     */
    public int getAddedLegalSettlement()
    {
        return addedLegalSettlement;
    }

    /**
     * Is this node a potential city?
     * True if we currently have a settlement there.
     * Does not check {@link #getNumPieces(int) getNumPieces(CITY)}.
     * @return true if this node is a potential city
     * @param node        the coordinates of a node on the board
     */
    public boolean isPotentialCity(final int node)
    {
        return potentialCities.contains(Integer.valueOf(node));
    }

    /**
     * Set this node to not be a potential city.
     * For use (by robots) when the server denies our request to build at a certain spot.
     *
     * @param node  coordinates of a node on the board
     * @see #isPotentialCity(int)
     * @since 1.1.09
     */
    public void clearPotentialCity(final int node)
    {
        potentialCities.remove(Integer.valueOf(node));
    }

    /**
     * Is this edge a potential road?
     * True if the location is legal, currently not occupied,
     * and we have an adjacent road, settlement, or city.
     * Does not check {@link #getNumPieces(int) getNumPieces(ROAD)}.
     * @return true if this edge is a potential road
     * @param edge        the coordinates of an edge on the board. Accepts -1 for edge 0x00.
     */
    public boolean isPotentialRoad(int edge)
    {
        if (edge == -1)
            edge = 0x00;
        return potentialRoads.contains(Integer.valueOf(edge));
    }

    /**
     * Set this edge to not be a potential road.
     * For use (by robots) when the server denies our request to build at a certain spot.
     *
     * @param node  coordinates of a an edge on the board. Accepts -1 for edge 0x00.
     * @see #isPotentialRoad(int)
     * @since 1.1.09
     */
    public void clearPotentialRoad(int edge)
    {
        if (edge == -1)
            edge = 0x00;
        potentialRoads.remove(Integer.valueOf(edge));
    }

    /**
     * @return true if this edge is a legal road
     * @param edge        the coordinates of an edge on the board.
     *   Accepts -1 for edge 0x00; any other negative value returns false.
     */
    public boolean isLegalRoad(int edge)
    {
        if (edge == -1)
            edge = 0x00;
        else if (edge < 0)
            return false;
        return legalRoads.contains(Integer.valueOf(edge));
    }

    /**
     * Is this edge coordinate a potential place to move a ship, even if another ship
     * edge was not?  Used by {@link SOCGame#canMoveShip(int, int, int)}
     * to check the ship's requested new location.
     *<P>
     * First, {@code toEdge} must be a potential ship<B>*</B> now.
     * Then, we check to see if even without the ship at {@code fromEdge},
     * toEdge is still potential:
     * If either end node of {@code toEdge} has a settlement/city of ours,
     * or has an adjacent edge with a ship of ours
     * (except {@code fromEdge}), then {@code toEdge} is potential.
     *<P>
     * <B>*</B>In scenario {@code _SC_PIRI}, we check more carefully because
     * after ship placement, nearby potential ships are removed to prevent
     * any branching of the ship route.  This would make it impossible to
     * move the route's newest ship to its other potential direction from
     * the previous node.
     *
     * @return true if this edge is still a potential ship
     * @param toEdge  the coordinates of an edge on the board;
     *       {@link #isPotentialShip(int) isPotentialShip(toEdge)}
     *       must currently be true.
     * @param fromEdge  the ship's current edge coordinate, to
     *   ignore when determining if {@code toEdge} is still potential.
     * @see #isPotentialShip(int)
     * @since 2.0.00
     */
    public boolean isPotentialShipMoveTo(final int toEdge, final int fromEdge)
    {
        if (! potentialShips.contains(Integer.valueOf(toEdge)))
        {
            if (game.isGameOptionSet(SOCGameOption.K_SC_PIRI)
                && (null != legalShipsRestricted))
            {
                if ((getRoadOrShip(toEdge) != null)
                    || ! legalShipsRestricted.contains(Integer.valueOf(toEdge)))
                    return false;

                // Continue checks below. New edge must be adjacent to a current ship or settlement/city
                // (potentialShips would normally check that); and can't be a branch of a trade route
                // (new edge's node must have just 1 road or ship, not 2 already).
            } else {
                return false;
            }
        }

        final SOCBoard board = game.getBoard();
        final int[] edgeNodes = board.getAdjacentNodesToEdge_arr(toEdge);

        SOCPlayingPiece pp = board.settlementAtNode(edgeNodes[0]);
        if ((pp != null) && (pp.getPlayerNumber() != playerNumber))
            pp = null;
        if ((pp != null)
            || doesTradeRouteContinuePastNode
                 (board, true, toEdge, fromEdge, edgeNodes[0]))
            return true;

        pp = board.settlementAtNode(edgeNodes[1]);
        if ((pp != null) && (pp.getPlayerNumber() != playerNumber))
            pp = null;
        if ((pp != null)
            || doesTradeRouteContinuePastNode
                 (board, true, toEdge, fromEdge, edgeNodes[1]))
            return true;

        return false;
    }

    /**
     * Is this edge a potential ship?
     * True if the location is legal, currently not occupied,
     * we have an adjacent ship, settlement, or city,
     * and {@link SOCGame#hasSeaBoard},
     * Does not check {@link #getNumPieces(int) getNumPieces(SHIP)}.
     * @return true if this edge is a potential ship;
     *   if not {@link SOCGame#hasSeaBoard}, always returns false
     *   because the player has no potential ship locations.
     * @param edge  the coordinates of an edge on the board
     * @see #isPotentialShipMoveTo(int, int)
     * @see SOCGame#canPlaceShip(SOCPlayer, int)
     * @since 2.0.00
     */
    public boolean isPotentialShip(int edge)
    {
        return potentialShips.contains(Integer.valueOf(edge));
    }

    /**
     * Set this edge to not be a potential ship.
     * For use (by robots) when the server denies our request to build at a certain spot.
     *
     * @param node  coordinates of a an edge on the board
     * @see #isPotentialRoad(int)
     * @since 2.0.00
     */
    public void clearPotentialShip(int edge)
    {
        potentialShips.remove(Integer.valueOf(edge));
    }

    /**
     * Is this edge a legal ship placement?
     * @return true if this edge is a legal ship
     * @param edge        the coordinates of an edge on the board
     * @since 2.0.00
     */
    public boolean isLegalShip(final int edge)
    {
        if (edge < 0)
            return false;
        return legalShips.contains(Integer.valueOf(edge));
    }

    /**
     * A list of edges where the player can build ships,
     * if the legal sea edges for ships are restricted
     * by the game's scenario ({@link SOCGameOption#K_SC_PIRI _SC_PIRI}),
     * or {@code null} if all sea edges are legal for ships.
     * If the player has no legal ship edges, this list is empty (not null).
     *<P>
     * Please treat the returned HashSet as read-only.
     *
     * @return  Legal sea edges if they're restricted, or {@code null}
     * @since 2.0.00
     */
    public HashSet<Integer> getRestrictedLegalShips()
    {
        return legalShipsRestricted;
    }

    /**
     * Set the list of edges where the player can build ships,
     * when the legal sea edges for ships are restricted
     * by the game's scenario ({@link SOCGameOption#K_SC_PIRI _SC_PIRI}).
     * @param edgeList  List of edges, same format as one player's array from
     *   {@link soc.server.SOCBoardLargeAtServer#getLegalSeaEdges(SOCGame, int) SOCBoardLargeAtServer.getLegalSeaEdges(SOCGame, int)};
     *   or an empty array (length 0) for vacant players with no legal ship edges;
     *   or {@code null} for unrestricted ship placement.
     *   <P>
     *   If {@code edgeList} is {@code null} and the player previously had a restricted
     *   ship edge list, will call {@code SOCBoardLarge.initPlayerLegalShips()} to
     *   unrestrict the player's legal ship positions.  If the game is already in progress,
     *   this naive call will include any no-longer-legal ships due to placed pieces.
     * @since 2.0.00
     */
    public void setRestrictedLegalShips(final int[] edgeList)
    {
        if (legalShipsRestricted != null)
            legalShipsRestricted.clear();

        if (edgeList == null)
        {
            if (legalShipsRestricted != null)
            {
                legalShipsRestricted = null;
                legalShips.addAll(((SOCBoardLarge) game.getBoard()).initPlayerLegalShips());
            }
            return;
        }

        HashSet<Integer> lse = legalShipsRestricted;  // local reference for brevity
        if (lse == null)
        {
            lse = new HashSet<Integer>();
            legalShipsRestricted = lse;
        }

        for (int i = 0; i < edgeList.length; ++i)
        {
            int edge = edgeList[i];
            if (edge > 0)
            {
                lse.add(Integer.valueOf(edge));
            } else {
                // Represents a range from previous element to current.
                // Previous was added in the previous iteration.
                edge = -edge;
                final int incr  // even rows get +1 (along top/bottom of hexes); odd rows get +2 (left/right sides)
                  = (0 == (edge & 0x100)) ? 1 : 2;

                for (int ed = edgeList[i-1] + incr; ed <= edge; ed += incr)
                    lse.add(Integer.valueOf(ed));
            }
        }

        legalShips.clear();
        legalShips.addAll(lse);
    }

    /**
     * @return true if there is at least one potential road
     */
    public boolean hasPotentialRoad()
    {
        return ! potentialRoads.isEmpty();
    }

    /**
     * @return true if there is at least one potential settlement
     */
    public boolean hasPotentialSettlement()
    {
        return ! potentialSettlements.isEmpty();
    }

    /**
     * @return true if there is at least one potential city
     */
    public boolean hasPotentialCity()
    {
        return ! potentialCities.isEmpty();
    }

    /**
     * Does this player have a potential edge to place a ship on,
     * in a game using the large sea board?
     * @return true if there is at least one potential ship
     * @since 2.0.00
     */
    public boolean hasPotentialShip()
    {
        return ! potentialShips.isEmpty();
    }

    /**
     * Can this player build this piece type now, based on their pieces so far?
     * Initial placement order is Settlement, Road/Ship, Settlement, Road/Ship.
     * Once 2 settlements and 2 roads/ships have been placed, any piece type is valid.
     *<P>
     * Ignores the specific gameState (any initial state is OK).
     * Ships are allowed only when {@link SOCGame#hasSeaBoard}.
     * @param pieceType  Piece type, such as {@link SOCPlayingPiece#SETTLEMENT}
     * @since 1.1.12
     * @return true if this piece type is the next to be placed
     * @throws IllegalStateException if gameState is past initial placement (> {@link #START3B})
     */
    public boolean canBuildInitialPieceType(final int pieceType)
        throws IllegalStateException
    {
        if (game.getGameState() > SOCGame.START3B)
            throw new IllegalStateException();

        final int pieceCountMax = game.isGameOptionSet(SOCGameOption.K_SC_3IP) ? 6 : 4;
        final int pieceCount = pieces.size();
        if (pieceCount >= pieceCountMax)
            return true;

        final boolean pieceCountOdd = ((pieceCount % 2) == 1);
        final boolean ok;
        switch (pieceType)
        {
        case SOCPlayingPiece.SETTLEMENT:
            ok = ! pieceCountOdd;
            break;

        case SOCPlayingPiece.SHIP:
            if (! game.hasSeaBoard)
                return false;
            // fall through to ROAD

        case SOCPlayingPiece.ROAD:
            ok = pieceCountOdd;
            break;

        default:
            ok = false;
        }

        return ok;
    }

    /**
     * Calculates the longest road / longest trade route for this player
     *
     * @return the length of the longest road for this player
     */
    public int calcLongestRoad2()
    {
        //Date startTime = new Date();
        //
        // clear the lr paths vector so that we have an accurate
        // representation.  if someone cut our longest path in two
        // we won't catch it unless we clear the vector
        //
        //D.ebugPrintln("CLEARING PATH DATA");
        lrPaths.removeAllElements();

        /**
         * we're doing a depth first search of all possible road paths.
         * For similar code, see soc.robot.SOCRobotDM.recalcLongestRoadETAAux.
         */
        SOCBoard board = game.getBoard();
        Stack<NodeLenVis<IntPair>> pending = new Stack<NodeLenVis<IntPair>>();
        int longest = 0;

        for (Integer rn : roadNodes)
        {
            final int pathStartNodeCoord = rn.intValue();
            pending.push(new NodeLenVis<IntPair>(pathStartNodeCoord, 0, new Vector<IntPair>()));

            while (! pending.isEmpty())
            {
                NodeLenVis<IntPair> curNode = pending.pop();
                final int coord = curNode.node;
                final int len = curNode.len;
                Vector<IntPair> visited = curNode.vis;
                boolean pathEnd = false;
                final SOCPlayingPiece settlementAtNodeCoord;

                /**
                 * check for road blocks
                 */
                if (len > 0)
                {
                    settlementAtNodeCoord = board.settlementAtNode(coord);
                    if ((settlementAtNodeCoord != null)
                        && (settlementAtNodeCoord.getPlayerNumber() != playerNumber))
                    {
                        pathEnd = true;

                        //D.ebugPrintln("^^^ path end at "+Integer.toHexString(coord));
                    }
                } else {
                    settlementAtNodeCoord = null;
                }

                if (! pathEnd)
                {
                    /**
                     * Check if this road path continues to adjacent connected nodes.
                     */

                    pathEnd = true;  // may be set false in loop

                    final int[] adjacNodes = board.getAdjacentNodesToNode_arr(coord);
                    for (int ni = adjacNodes.length - 1; ni>=0; --ni)
                    {
                        final int j = adjacNodes[ni];
                        if (j == -9)
                            continue;

                        if (isConnectedByRoad(coord, j))
                        {
                            final SOCRoad roadFromNode;  // sea board: road/ship from node to j

                            if (game.hasSeaBoard)
                            {
                                // Check for road<->ship transitions,
                                // which require a settlement/city at node.
                                // If len==0, inboundRoad is null because we're just starting.

                                roadFromNode = getRoadOrShip
                                (board.getEdgeBetweenAdjacentNodes(coord, j));
                                if (len > 0)
                                {
                                    if (roadFromNode == null)  // shouldn't happen
                                        continue;

                                    if ((roadFromNode.isRoadNotShip() != curNode.inboundRoad.isRoadNotShip())
                                        && (settlementAtNodeCoord == null))
                                    {
                                        continue;  // Requires settlement/city to connect road to ship
                                    }
                                }
                            } else {
                                roadFromNode = null;
                            }

                            IntPair pair = new IntPair(coord, j);
                            boolean match = false;

                            for (IntPair vis : visited)
                            {
                                if (vis.equals(pair))
                                {
                                    match = true;
                                    break;
                                }
                            }

                            if (! match)
                            {
                                Vector<IntPair> newVis = new Vector<IntPair>(visited);
                                newVis.addElement(pair);
                                pending.push(new NodeLenVis<IntPair>(j, len + 1, newVis, roadFromNode));
                                pathEnd = false;
                            }
                        }
                    }  // foreach(adjacNodes)
                }

                if (pathEnd)
                {
                    if (len > longest)
                    {
                        longest = len;
                    }

                    //
                    // we want to store the longest path for a single set of nodes
                    // check to make sure that we don't save two paths that share a node
                    //
                    boolean intersection;
                    boolean addNewPath = true;
                    Vector<SOCLRPathData> trash = new Vector<SOCLRPathData>();

                    for (SOCLRPathData oldPathData : lrPaths)
                    {
                        //D.ebugPrintln("oldPathData = " + oldPathData);

                        Vector<IntPair> nodePairs = oldPathData.getNodePairs();
                        intersection = false;

                        for (IntPair vis : visited)
                        {
                            //D.ebugPrintln("vis = " + vis);

                            for (IntPair np : nodePairs)
                            {
                                //D.ebugPrintln("np = " + np);

                                if (np.equals(vis))
                                {
                                    //D.ebugPrintln("oldPathData.nodePairs.contains(vis)");
                                    intersection = true;

                                    break;
                                }
                            }

                            if (intersection)
                            {
                                break;
                            }
                        }

                        if (intersection)
                        {
                            //
                            // only keep the longer of the two paths
                            //
                            if (oldPathData.getLength() < len)
                            {
                                //D.ebugPrintln("REMOVING OLDPATHDATA");
                                trash.addElement(oldPathData);
                            }
                            else
                            {
                                addNewPath = false;
                                //D.ebugPrintln("NOT ADDING NEW PATH");
                            }
                        }
                    }

                    if (! trash.isEmpty())
                    {
                        for (SOCLRPathData oldPathData : trash)
                        {
                            lrPaths.removeElement(oldPathData);
                        }
                    }

                    if (addNewPath)
                    {
                        SOCLRPathData newPathData = new SOCLRPathData(pathStartNodeCoord, coord, len, visited);
                        //D.ebugPrintln("ADDING PATH: " + newPathData);
                        lrPaths.addElement(newPathData);
                    }
                }
            }
        }

        longestRoadLength = longest;

        //Date stopTime = new Date();
        //long elapsed = stopTime.getTime() - startTime.getTime();
        //System.out.println("LONGEST FOR "+name+" IS "+longest+" TIME = "+elapsed+"ms");
        return longest;
    }

    /**
     * set a port flag
     *
     * @param portType  the type of port; in range {@link SOCBoard#MISC_PORT} to {@link SOCBoard#WOOD_PORT}
     * @param value                        true or false
     */
    public void setPortFlag(int portType, boolean value)
    {
        ports[portType] = value;
    }

    /**
     * @return the port flag for a type of port
     *
     * @param portType   the type of port; in range {@link SOCBoard#MISC_PORT} to {@link SOCBoard#WOOD_PORT}
     */
    public boolean getPortFlag(int portType)
    {
        return ports[portType];
    }

    /**
     * @return the ports array
     */
    public boolean[] getPortFlags()
    {
        return ports;
    }

    /**
     * for debug prints; appends to sb or creates it, returns it
     * @since 1.1.12
     */
    public StringBuffer numpieces(StringBuffer sb)
    {
        if (sb == null)
            sb = new StringBuffer("{");
        else
            sb.append("{");
        for (int i = 0; i < numPieces.length; ++i)
        {
            if (i > 0)
                sb.append(", ");
            sb.append(numPieces[i]);
        }
        sb.append("}");
        return sb;
    }

    /**
     * set vars to null so gc can clean up
     */
    public void destroyPlayer()
    {
        game = null;
        numPieces = null;
        pieces.removeAllElements();
        pieces = null;
        roads.removeAllElements();
        roads = null;
        settlements.removeAllElements();
        settlements = null;
        cities.removeAllElements();
        cities = null;
        fortress = null;
        resources = null;
        resourceStats = null;
        devCards = null;
        ourNumbers = null;
        ports = null;
        roadNodes.removeAllElements();
        roadNodes = null;
        roadNodeGraph.clear();
        roadNodeGraph = null;
        if (legalRoads != null)
        {
            legalRoads.clear();
            legalRoads = null;
            legalSettlements.clear();
            legalSettlements = null;
            legalShips.clear();
            legalShips = null;
            if (legalShipsRestricted != null)
            {
                legalShipsRestricted.clear();
                legalShipsRestricted = null;
            }
            potentialRoads.clear();
            potentialRoads = null;
            potentialSettlements.clear();
            potentialSettlements = null;
            potentialCities.clear();
            potentialCities = null;
            potentialShips.clear();
            potentialShips = null;
        }
        currentOffer = null;
    }
    
    @Override
    public String toString()
    {
        return "Player["+playerNumber+" "+name+"]";
    }

    /**
     * Holds details of {@link SOCPlayer#getSpecialVP()}.
     * Built via {@link SOCPlayer#addSpecialVPInfo(int, String)}.
     * @author jeremy@nand.net
     * @since 2.0.00
     */
    public static class SpecialVPInfo
    {
        /** Number of special victory points */
        public final int svp;

        /** Description of the player's action that led to the SVP */
        public final String desc;

        public SpecialVPInfo(final int svp, final String desc)
        {
            this.svp = svp;
            this.desc = desc;
        }
    }

}
