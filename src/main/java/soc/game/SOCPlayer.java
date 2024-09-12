/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2023 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017-2018 Strategic Conversation (STAC Project) https://www.irit.fr/STAC/
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
import soc.server.savegame.SavedGameModel;  // for javadocs only
import soc.util.IntPair;
import soc.util.NodeLenVis;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;


/**
 * A class for holding and manipulating player data.
 * The player exists within one {@link SOCGame}, not persistent between games like SOCPlayerClient or SOCClientData.
 * See {@link SavedGameModel.PlayerInfo} for the player data saved to {@code .game.json} savegame files.
 *<P>
 * At the start of each player's turn, {@link SOCGame#updateAtTurn()} will call {@link SOCPlayer#updateAtTurn()},
 * then call the current player's {@link #updateAtOurTurn()}.
 *<P>
 * The player's hand holds resource cards, unplayed building pieces, and their {@link SOCInventory} of development cards
 * and sometimes scenario-specific items.
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
 * If the board layout changes from game to game, as with {@link SOCBoardLarge} /
 * {@link SOCBoard#BOARD_ENCODING_LARGE}, use these methods to update the player's board data
 * after {@link SOCBoard#makeNewBoard(SOCGameOptionSet)}, in this order:
 *<UL>
 * <LI> {@link #getNumbers()}.{@link SOCPlayerNumbers#setLandHexCoordinates(int[]) setLandHexCoordinates(int[])}
 * <LI> {@link #setPotentialAndLegalSettlements(Collection, boolean, HashSet[])}
 * <LI> Optionally, {@link #setRestrictedLegalShips(int[])}
 *</UL>
 *<P>
 * On the {@link SOCBoardLarge large sea board}, our list of the player's roads also
 * contains their ships. Some method names like {@link #isConnectedByRoad(int, int)}
 * also group roads and ships together. They are otherwise treated separately.
 *<P>
 * Some fields are for use at the server only, and are null at the client:
 * {@link #pendingMessagesOut}, etc: Check field javadocs and {@link SOCGame#isAtServer}.
 *<P>
 * To get the {@code Connection} to a SOCPlayer's client, use
 * {@code SOCServer.getConnection(player.{@link #getName()}).
 *
 * @author Robert S Thomas
 */
public class SOCPlayer implements SOCDevCardConstants, Serializable, Cloneable
{
    /** Last field change was v2.7.00 (2700) */
    private static final long serialVersionUID = 2700L;

    /**
     * Number of {@link SOCRoad}s a player can build (15).
     * @see #getNumPieces(int)
     * @since 2.0.00
     */
    public static final int ROAD_COUNT = 15;

    /**
     * Number of {@link SOCSettlement}s a player can build (5).
     * @see #getNumPieces(int)
     * @since 2.0.00
     */
    public static final int SETTLEMENT_COUNT = 5;

    /**
     * Number of {@link SOCCity}s a player can build (4).
     * @see #getNumPieces(int)
     * @since 2.0.00
     */
    public static final int CITY_COUNT = 4;

    /**
     * Number of {@link SOCShip}s a player can build (15) if {@link SOCGame#hasSeaBoard}.
     * @see #getNumPieces(int)
     * @since 2.0.00
     */
    public static final int SHIP_COUNT = 15;

    /**
     * First/default/lowest-numbered human face icon number (1) for {@link #setFaceId(int)}.
     * @since 2.4.00
     */
    public static final int FIRST_HUMAN_FACE_ID = 1;

    /**
     * If a robot player's turn must be ended this many times,
     * consider it "stubborn" and give it less time to act on its own
     * in future turns. Default is 2.
     * @see #isStubbornRobot()
     * @since 2.0.00
     */
    public static int STUBBORN_ROBOT_FORCE_END_TURN_THRESHOLD = 2;

    /**
     * Number of trade types tracked for stats; is also length of trade stats
     * subarrays returned by {@link #getResourceTradeStats()}: currently 8.
     * @since 2.6.00
     */
    public static final int TRADE_STATS_ARRAY_LEN = 8;

    /**
     * Index within {@link #getResourceTradeStats()} for 4:1 bank trades.
     * @since 2.6.00
     */
    public static final int TRADE_STATS_INDEX_BANK = 6;

    /**
     * Index within {@link #getResourceTradeStats()} for total of all player trades.
     * @since 2.6.00
     */
    public static final int TRADE_STATS_INDEX_PLAYER_ALL = 7;

    /**
     * the name of the player
     */
    private String name;

    /**
     * The integer id for this player (0 to n-1).
     */
    private int playerNumber;

    /**
     * The game that this player is in; not null until {@link #destroyPlayer()} is called.
     */
    private SOCGame game;

    /**
     * The number of pieces not in play, available to build.
     * Indexes are SOCPlayingPiece constants:
     * {@link SOCPlayingPiece#ROAD},
     * {@link SOCPlayingPiece#SETTLEMENT},
     * {@link SOCPlayingPiece#CITY},
     * {@link SOCPlayingPiece#SHIP}.
     * Initially {@link #ROAD_COUNT}, {@link #SETTLEMENT_COUNT}, etc.
     */
    private int[] numPieces;

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI},
     * the number of {@link SOCShip}s that are converted to warships.
     * See {@link #getNumWarships()} for details.
     * @since 2.0.00
     */
    private int numWarships;

    /**
     * a list of this player's pieces in play
     * (does not include any {@link #fortress}).
     * @see #getPieces()
     */
    private Vector<SOCPlayingPiece> pieces;

    /**
     * a list of this player's roads and ships in play.
     * Although roads and ships are kept together here,
     * in {@link #numPieces}[] they're counted separately.
     *<P>
     * Before v2.0.00 this field was {@code roads}.
     * @see #getRoadOrShip(int)
     * @see #roadNodes
     */
    private Vector<SOCRoutePiece> roadsAndShips;

    /**
     * a list of this player's settlements in play
     */
    private Vector<SOCSettlement> settlements;

    /**
     * a list of this player's cities in play
     */
    private Vector<SOCCity> cities;

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI},
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
     * Player's {@link SOCSpecialItem}s, if any, by type.
     * See getter/setter javadocs for details on type keys and
     * rationale for lack of synchronization.
     * ArrayList is used to guarantee we can store null items.
     * @since 2.0.00
     */
    private HashMap<String, ArrayList<SOCSpecialItem>> spItems;

    /**
     * The node coordinate of our most recent settlement placement.
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
     * List of longest road / longest trade-route paths.
     * Is empty (not null) if {@link SOCGameOptionSet#K_SC_0RVP} is set.
     */
    private final Vector<SOCLRPathData> lrPaths;

    /**
     * how many of each resource this player has
     * @see #resourceStats
     * @see #tradeStatsGive
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
     * For use at server, this player's count of forced end turns this game.
     * Useful for keeping track of buggy/slow ("stubborn") robots.
     * Is incremented by {@link #addForcedEndTurn()} and reset to 0 by {@link #setName(String)}.
     * @see #isStubbornRobot()
     * @since 2.0.00
     */
    int forcedEndTurnCount;

    /**
     * Total count of how many of each known resource the player has received this game
     * from dice rolls.
     * The used indexes are {@link SOCResourceConstants#CLAY} - {@link SOCResourceConstants#WOOD},
     * and also (in v2.0.00+) {@link SOCResourceConstants#GOLD_LOCAL}.
     * Tracked at server, and usually at recent clients.
     * See {@link #getResourceRollStats()} for details.
     * @see #tradeStatsGive
     * @since 1.1.09
     */
    private int[] resourceStats;

    /**
     * Stats for resources {@link #tradeStatsGive given} and {@link #tradeStatsGet received} in trades this game.
     * Always tracked at server, and usually at recent clients. See {@link #getResourceTradeStats()} for details.
     * @see #resourceStats
     * @since 2.6.00
     */
    private SOCResourceSet[] tradeStatsGive, tradeStatsGet;

    /**
     * The {@link SOCDevCard development card}s this player holds,
     * along with occasional scenario-specific items.
     */
    private SOCInventory inventory;

    /**
     * how many knights this player has in play
     */
    private int numKnights;

    /**
     * Development cards played this game by this player, or null if none, at server:
     * See {@link #getDevCardsPlayed()} for details.
     * @since 2.5.00
     */
    private List<Integer> devCardsPlayed;

    /**
     * How many Road Building cards ({@link SOCDevCardConstants#ROADS}) this player has played.
     * @see #getDevCardsPlayed()
     * @see #updateDevCardsPlayed(int, boolean)
     * @since 2.5.00
     */
    public int numRBCards = 0;

    /**
     * How many Discovery/Year of Plenty cards ({@link SOCDevCardConstants#DISC}) this player has played.
     * @see #getDevCardsPlayed()
     * @see #updateDevCardsPlayed(int, boolean)
     * @since 2.5.00
     */
    public int numDISCCards = 0;

    /**
     * How many Monopoly cards ({@link SOCDevCardConstants#MONO}) this player has played.
     * @see #getDevCardsPlayed()
     * @see #updateDevCardsPlayed(int, boolean)
     * @since 2.5.00
     */
    public int numMONOCards = 0;

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
     * {@link SOCGame#gameEventListener} != null, and sent out to clients.
     * @see #addSpecialVPInfo(int, String)
     * @since 2.0.00
     */
    private ArrayList<SpecialVPInfo> svpInfo;

    /**
     * the final total score (pushed from server at end of game),
     * or 0 if no score has been forced.
     *
     * @see #forceFinalVP(int)
     * @since 1.1.00
     */
    private int finalTotalVP;

    /**
     * This player's number of undos remaining in the game,
     * which starts at game option {@code "UBL"}'s value,
     * or 0 if not using that option.
     * @since 2.7.00
     */
    private int undosRemaining;

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
     * When setting this field, also increment {@link #resourceStats}
     * [{@link SOCResourceConstants#GOLD_LOCAL GOLD_LOCAL}].
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
     * @see #roadsAndShips
     * @see #roadNodeGraph
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
     * If {@link SOCGame#hasSeaBoard}, empty until {@link SOCBoard#makeNewBoard(SOCGameOptionSet)}
     * and {@link SOCGame#startGame()}, because the board layout and legal settlements
     * vary from game to game.
     */
    private HashSet<Integer> legalRoads;

    /**
     * The set of nodes where it's legal to place a settlement;
     * see {@link #getLegalSettlements()} for details.
     *
     * @see #potentialSettlements
     * @see SOCBoard#nodesOnLand
     */
    private HashSet<Integer> legalSettlements;

    /**
     * The most recently added node from {@link #addLegalSettlement(int, boolean)}, or 0.
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
     * by the game's scenario ({@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}),
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
     * If {@link HashSet#contains(Object) potentialSettlements.contains(Integer.valueOf(nodeCoord))},
     * then this is a potential settlement.
     * @see #legalSettlements
     * @see #setPotentialAndLegalSettlements(Collection, boolean, HashSet[])
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
     * True if board has fog hexes, {@link #potentialSettlements} has some nodes on
     * the fog hexes, and game hasn't completed {@link SOCGame#isInitialPlacement()} yet.
     * @since 2.0.00
     */
    private boolean hasPotentialSettlesInitInFog;

    /**
     * Flags tracking which trade port types this player has; see {@link #getPortFlags()}.
     */
    private boolean[] ports;

    /**
     * this is the current trade offer that this player is making, or null if none
     * @see #currentOfferTimeMillis
     */
    private SOCTradeOffer currentOffer;

    /**
     * time when {@link #currentOffer} was last updated; see {@link #getCurrentOfferTime()}.
     * @since 2.5.00
     */
    private long currentOfferTimeMillis;

    /**
     * this is true if the player played a development card this turn
     */
    private boolean playedDevCard;

    /**
     * this is true if the player asked to reset the board this turn
     * @since 1.1.00
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
     * As events occur during a game, each one's {@link SOCPlayerEvent#flagValue} bit is set here.
     *<P>
     * Example events: {@link SOCPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA},
     * {@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     *<P>
     * Bits are kept here, not in separate boolean fields, to keep them together and send over the network.
     * Not all player events are here; some can't be represented in a single flag bit,
     * such as {@link #scenario_svpFromEachLandArea_bitmask}.
     *
     * @see #getPlayerEvents()
     * @since 2.0.00
     */
    private int playerEvents_bitmask;

    /**
     * For some scenarios on the {@link SOCGame#hasSeaBoard large sea board},
     * bitmask: true if the player has been given a Special Victory Point for placing
     * a settlement in a given new land area.
     * The bit value is (1 &lt;&lt; (landAreaNumber - 1)).
     *
     * @see SOCPlayerEvent#SVP_SETTLED_EACH_NEW_LANDAREA
     * @see #playerEvents_bitmask
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
     * Which face image this player is using.
     * See {@link #getFaceId()} for details.
     */
    private int faceId;

    /**
     * the numbers that our settlements are touching
     */
    private SOCPlayerNumbers ourNumbers;

    /**
     * Flag for whether the player has been asked and then re-asked to discard resources this turn.
     * For details see {@link #hasAskedDiscardTwiceThisTurn()}.
     * @since 2.5.00
     */
    private boolean askedDiscardTwiceThisTurn;

    /**
     * a guess at how many turns it takes to build
     */

    // private SOCBuildingSpeedEstimate buildingSpeed;

    /**
     * For games at server, a convenient queue to hold any outbound {@code SOCMessage}s to this player's client
     * during game action callbacks. Public access for use by server classes.
     * See {@link SOCGame#pendingMessagesOut} for more details.
     * If a message contains text field(s) or is dependent on the client version, localize or resolve it
     * before adding to this queue.
     *<P>
     * For pending messages to send to entire game, see {@link SOCGame#pendingMessagesOut}.
     *<P>
     * To send and clear this queue's contents, call {@code SOCGameHandler.sendGamePendingMessages(SOCGame, boolean)}.
     *<P>
     * <B>Note:</B> Only a few of the server message-handling methods check this queue:
     * See {@link SOCGame#pendingMessagesOut}.
     *<P>
     * <B>Locking:</B> Not thread-safe, because all of a game's message handling
     * is done within a single thread.
     *<P>
     * Because this queue is server-only, it's null until {@link SOCGame#startGame()}.
     * This field is also not copied by the {@link #SOCPlayer(SOCPlayer, String)} constructor.
     *
     * @since 2.0.00
     */
    public transient List<Object> pendingMessagesOut;

    /**
     * create a copy of the player
     *
     * @param player  the player to copy
     * @param newName  new name to give copy of player, or {@code null} to copy current name.
     *     Useful for {@link soc.robot.SOCPlayerTracker} dummy players in debug prints.
     *     Note that {@link #toString()} prints the name and the copied {@link #getPlayerNumber()}.
     * @throws IllegalArgumentException if {@code player.getGame()} is null; shouldn't occur
     *     unless {@link #destroyPlayer()} has been called
     * @throws IllegalStateException if player's dev cards can't be cloned (internal error); should not possibly occur
     */
    public SOCPlayer(final SOCPlayer player, final String newName)
        throws IllegalArgumentException, IllegalStateException
    {
        if (player.game == null)
            throw new IllegalArgumentException("game");

        game = player.game;
        name = (newName != null) ? newName : player.name;
        playerNumber = player.playerNumber;
        numPieces = player.numPieces.clone();
        pieces = new Vector<SOCPlayingPiece>(player.pieces);
        roadsAndShips = new Vector<SOCRoutePiece>(player.roadsAndShips);
        settlements = new Vector<SOCSettlement>(player.settlements);
        cities = new Vector<SOCCity>(player.cities);
        spItems = new HashMap<String, ArrayList<SOCSpecialItem>>();
        if (! player.spItems.isEmpty())
        {
            // deep copy
            for (final String optKey : player.spItems.keySet())
            {
                final ArrayList<SOCSpecialItem> old = player.spItems.get(optKey);
                if (old.isEmpty())
                    continue;

                ArrayList<SOCSpecialItem> anew = new ArrayList<SOCSpecialItem>();
                final int L = old.size();
                try
                {
                    for (int i = 0; i < L; ++i)
                    {
                        SOCSpecialItem itm = old.get(i);
                        anew.add((itm != null) ? itm.clone() : null);
                    }
                } catch (CloneNotSupportedException e) {}  // Should not occur: SOCSpecialItem implements Cloneable

                spItems.put(optKey, anew);
            }
        }
        fortress = player.fortress;
        numWarships = player.numWarships;
        longestRoadLength = player.longestRoadLength;
        lrPaths = new Vector<SOCLRPathData>(player.lrPaths);
        resources = player.resources.copy();
        resourceStats = new int[player.resourceStats.length];
        System.arraycopy(player.resourceStats, 0, resourceStats, 0, player.resourceStats.length);
        tradeStatsGive = new SOCResourceSet[player.tradeStatsGive.length];
        tradeStatsGet = new SOCResourceSet[player.tradeStatsGive.length];
        for (int i = 0; i < player.tradeStatsGive.length; ++i)
        {
            tradeStatsGive[i] = new SOCResourceSet(player.tradeStatsGive[i]);
            tradeStatsGet[i] = new SOCResourceSet(player.tradeStatsGet[i]);
        }
        rolledResources = player.rolledResources.copy();
        try
        {
            inventory = new SOCInventory(player.inventory);
        }
        catch (CloneNotSupportedException e)
        {
            throw new IllegalStateException("Internal error, cards should be cloneable", e);
        }
        numKnights = player.numKnights;
        buildingVP = player.buildingVP;
        specialVP = player.specialVP;
        finalTotalVP = 0;
        undosRemaining = player.undosRemaining;
        numRBCards = player.numRBCards;
        numDISCCards = player.numDISCCards;
        numMONOCards = player.numMONOCards;
        if (player.devCardsPlayed != null)
            devCardsPlayed = new ArrayList<>(player.devCardsPlayed);
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

        for (int i = SOCBoard.MISC_PORT; i <= SOCBoard.WOOD_PORT; i++)
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

        playerEvents_bitmask = player.playerEvents_bitmask;
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
     * {@link SOCBoard#initPlayerLegalSettlements()}.
     *<P>
     * Once the game board is set up, be sure to call
     * {@link #setPotentialAndLegalSettlements(Collection, boolean, HashSet[])}
     * to update our data.
     *
     * @param pn the player number
     * @param ga the game that the player is in
     * @throws IllegalArgumentException if {@code ga} is null
     */
    public SOCPlayer(int pn, SOCGame ga)
        throws IllegalArgumentException
    {
        if (ga == null)
            throw new IllegalArgumentException("game");

        game = ga;
        playerNumber = pn;
        numPieces = new int[SOCPlayingPiece.MAXPLUSONE];
        numPieces[SOCPlayingPiece.ROAD] = ROAD_COUNT;
        numPieces[SOCPlayingPiece.SETTLEMENT] = SETTLEMENT_COUNT;
        numPieces[SOCPlayingPiece.CITY] = CITY_COUNT;
        if (ga.hasSeaBoard)
            numPieces[SOCPlayingPiece.SHIP] = SHIP_COUNT;
        else
            numPieces[SOCPlayingPiece.SHIP] = 0;

        if (ga.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
            --numPieces[SOCPlayingPiece.SETTLEMENT];  // Pirate Fortress is a captured settlement

        pieces = new Vector<SOCPlayingPiece>(ROAD_COUNT + SETTLEMENT_COUNT + CITY_COUNT);
        roadsAndShips = new Vector<SOCRoutePiece>(ROAD_COUNT);
        settlements = new Vector<SOCSettlement>(SETTLEMENT_COUNT);
        cities = new Vector<SOCCity>(CITY_COUNT);
        spItems = new HashMap<String, ArrayList<SOCSpecialItem>>();
        longestRoadLength = 0;
        lrPaths = new Vector<SOCLRPathData>();
        resources = new SOCResourceSet();
        resourceStats = new int[1 + SOCResourceConstants.GOLD_LOCAL];
        tradeStatsGive = new SOCResourceSet[TRADE_STATS_ARRAY_LEN];
        tradeStatsGet = new SOCResourceSet[TRADE_STATS_ARRAY_LEN];
        for (int i = 0; i < TRADE_STATS_ARRAY_LEN; ++i)
        {
            tradeStatsGive[i] = new SOCResourceSet();
            tradeStatsGet[i] = new SOCResourceSet();
        }
        rolledResources = new SOCResourceSet();
        inventory = new SOCInventory();
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
        faceId = FIRST_HUMAN_FACE_ID;
        SOCBoard board = ga.getBoard();
        ourNumbers = new SOCPlayerNumbers(board);

        // buildingSpeed = new SOCBuildingSpeedEstimate(this);
        ports = new boolean[SOCBoard.WOOD_PORT + 1];

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
            legalSettlements = board.initPlayerLegalSettlements();
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
     * At start of normal game play, set all nodes to not be potential settlements.
     * Called by {@code SOCGame.updateAtGameFirstTurn()}
     * in state {@link SOCGame#START2A} or {@link SOCGame#START3A} after final initial settlement placement.
     *<P>
     * Once they have placed another road, that road's
     * {@link #putPiece(SOCPlayingPiece, boolean)} call will call
     * {@link #updatePotentials(SOCPlayingPiece)}, which
     * will set potentialSettlements at the road's new end node.
     *<P>
     * Also clears the {@link #hasPotentialSettlementsInitialInFog()} flag.
     */
    public void clearPotentialSettlements()
    {
        potentialSettlements.clear();
        hasPotentialSettlesInitInFog = false;
    }

    /**
     * Update player's state as needed when any player begins their turn (before dice are rolled).
     * Called by server and client, as part of {@link SOCGame#updateAtTurn()}.
     *<P>
     * Called for each player, just before calling the current player's {@link #updateAtOurTurn()}.
     *<UL>
     * <LI> Clear {@link #getRolledResources()}
     * <LI> Clear {@link #getCurrentOffer()} in v2.4.00 and newer.
     *      In earlier versions this was cleared only at client, when server sent
     *      a {@code SOCClearOffer} message while ending previous player's turn.
     * <LI> Clear {@link #hasAskedDiscardTwiceThisTurn()} flag
     *</UL>
     * @since 1.1.14
     */
    void updateAtTurn()
    {
        rolledResources.clear();
        setCurrentOffer(null);
        askedDiscardTwiceThisTurn = false;
    }

    /**
     * Update game state as needed when this player begins their turn (before dice are rolled).
     * Called by server and client, as part of {@link SOCGame#updateAtTurn()}.
     * Called just after calling each player's {@link #updateAtTurn()}.
     *<P>
     * May be called during initial placement.
     * Is called at the end of initial placement, before the first player's first roll.
     * On the 6-player board, is called at the start of
     * the player's {@link SOCGame#SPECIAL_BUILDING Special Building Phase}.
     *<UL>
     *<LI> Mark our new dev cards as old
     *<LI> Clear our {@link #hasPlayedDevCard()} flag (in v2.5.00 and newer)
     *<LI> Set {@link #getNeedToPickGoldHexResources()} to 0
     *<LI> Clear the "last-action bank trade" flag/list
     *     used by {@link SOCGame#canUndoBankTrade(SOCResourceSet, SOCResourceSet) game.canUndoBankTrade}
     *</UL>
     * @since 1.1.14
     */
    void updateAtOurTurn()
    {
        inventory.newToOld();
        playedDevCard = false;
        if (needToPickGoldHexResources > 0)
            needToPickGoldHexResources = 0;
    }

    /**
     * Set the name of the player.
     *<P>
     * Also resets the player's forced-end-turn count to 0,
     * because this is called from {@link SOCGame#addPlayer(String, int)}
     * when a player has been replaced with someone else.
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
        forcedEndTurnCount = 0;
    }

    /**
     * @return the name of the player; may be {@code null} if this Player is an unoccupied seat in the game
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
     * @see #setPlayedDevCard(boolean)
     * @see #getDevCardsPlayed()
     */
    public boolean hasPlayedDevCard()
    {
        return playedDevCard;
    }

    /**
     * Set or clear the {@link #hasPlayedDevCard()} flag.
     *
     * @param value  new value of the flag
     * @see #updateDevCardsPlayed(int, boolean)
     */
    public void setPlayedDevCard(boolean value)
    {
        playedDevCard = value;
    }

    /**
     * Update player's {@link #getDevCardsPlayed()} list, and stats for Discovery/Year of Plenty, Monopoly,
     * or Road Building dev cards when such a card is played or canceled:
     * Increment or decrement {@link #numDISCCards}, {@link #numMONOCards}, or {@link #numRBCards}.
     *
     * @param ctype  Any development card type such as {@link SOCDevCardConstants#ROADS},
     *     {@link SOCDevCardConstants#UNIV}, or {@link SOCDevCardConstants#UNKNOWN}.
     *     Out-of-range values are allowed, not rejected with an Exception,
     *     for compatibility if range is expanded in a later version.
     * @param isCancel  If true, the card just played is being canceled and returned to hand.
     *     Decrement instead of increment its counter field.
     * @see #setPlayedDevCard(boolean)
     * @see SOCGame#playDiscovery()
     * @see SOCGame#playKnight()
     * @see SOCGame#playMonopoly()
     * @see SOCGame#playRoadBuilding()
     * @since 2.5.00
     */
    public synchronized void updateDevCardsPlayed(final int ctype, final boolean isCancel)
    {
        switch (ctype)
        {
        case SOCDevCardConstants.DISC:
            if (isCancel)
                --numDISCCards;
            else
                ++numDISCCards;
            break;

        case SOCDevCardConstants.MONO:
            if (isCancel)
                --numMONOCards;
            else
                ++numMONOCards;
            break;

        case SOCDevCardConstants.ROADS:
            if (isCancel)
                --numRBCards;
            else
                ++numRBCards;
            break;
        }

        if (devCardsPlayed == null)
            devCardsPlayed = new ArrayList<>();
        if (isCancel)
        {
            // remove most recent
            for (int i = devCardsPlayed.size() - 1; i >= 0; --i)
            {
                if (devCardsPlayed.get(i) == ctype)
                {
                    devCardsPlayed.remove(i);
                    break;
                }
            }
        } else {
            devCardsPlayed.add(Integer.valueOf(ctype));
        }
    }

    /**
     * Get the chronological list of development cards played by player so far this game, or null if none, at server.
     * Elements are card type constants: {@link SOCDevCardConstants#ROADS}, {@link SOCDevCardConstants#UNIV}, etc.,
     * but can be any int value (for upwards compatibility).
     *<P>
     * {@link #updateDevCardsPlayed(int, boolean)} adds to or removes from this list.
     *<P>
     * At client, may be incomplete or null: Updated during game play, but not sent from server as client joins mid-game.
     *
     * @return copy of list of dev cards played, or {@code null} if none
     * @since 2.5.00
     */
    public List<Integer> getDevCardsPlayed()
    {
        if (devCardsPlayed == null)
            return null;

        return new ArrayList<Integer>(devCardsPlayed);
    }

    /**
     * @return true if the player asked to reset the board this turn
     * @since 1.1.00
     */
    public boolean hasAskedBoardReset()
    {
        return boardResetAskedThisTurn;
    }

    /**
     * set the flag indicating if the player asked to reset the board this turn
     *
     * @param value  true to set, false to clear
     * @since 1.1.00
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
     * @see #setSpecialBuilt(boolean)
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
     * @see #setAskedSpecialBuild(boolean)
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
     * Does this player need to discard?
     * If so, see {@link #getCountToDiscard()} for how many to discard.
     *
     * @return true if this player needs to discard
     * @see #getNeedToPickGoldHexResources()
     */
    public boolean getNeedToDiscard()
    {
        return needToDiscard;
    }

    /**
     * For {@link #getNeedToDiscard()}, get how many resources must be discarded.
     * The amount is half, rounded down:
     * <tt>{@link #getResources()}.{@link SOCResourceSet#getTotal() getTotal()} / 2</tt>.
     * @return how many resources to discard. Always returns half of player's total,
     *     even when {@link #getNeedToDiscard()} is false.
     * @since 2.3.00
     */
    public int getCountToDiscard()
    {
        return resources.getTotal() / 2;
    }

    /**
     * Set the number of gold-hex resources this player must now pick,
     * after a dice roll or placing their 2nd initial settlement.
     * See {@link #getNeedToPickGoldHexResources()} for details.
     *<P>
     * If {@code numRes} &gt; current {@link #getNeedToPickGoldHexResources()},
     * increments stat {@link #getResourceRollStats()}[{@link SOCResourceConstants#GOLD_LOCAL GOLD_LOCAL}]
     * by that delta amount.
     *
     * @param numRes  Number of resources to pick, or 0 for no pick/pick has been completed
     * @since 2.0.00
     */
    public void setNeedToPickGoldHexResources(final int numRes)
    {
        final int d = numRes - needToPickGoldHexResources;

        needToPickGoldHexResources = numRes;
        if (d > 0)
            resourceStats[SOCResourceConstants.GOLD_LOCAL] += d;
    }

    /**
     * Get the number of gold-hex resources this player must now pick,
     * after a dice roll or placing their 2nd initial settlement.
     * 0 unless {@link SOCGame#hasSeaBoard} and player is adjacent
     * to a {@link SOCBoardLarge#GOLD_HEX}.
     * Game state should be {@link SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE}.
     * Once the player has picked their resources, returns to 0.
     *
     * @return  number of resources to pick, or 0 for no pick
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
     * @see #isStubbornRobot()
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
     * Is this robot player "stubborn": Slow or buggy enough that their turn has been forced to end several times?
     * That counter is incremented by {@link #addForcedEndTurn()} and reset by {@link #setName(String)}.
     * @return true if {@link #isRobot()} and forced-end-turn count &gt;= {@link #STUBBORN_ROBOT_FORCE_END_TURN_THRESHOLD}
     * @since 2.0.00
     */
    public boolean isStubbornRobot()
    {
        return robotFlag && (forcedEndTurnCount >= STUBBORN_ROBOT_FORCE_END_TURN_THRESHOLD);
    }

    /**
     * Increment the forced-end-turn count that's checked by {@link #isStubbornRobot()}.
     * Also includes forced discards / gold-hex resource picks when not current player.
     *<P>
     * This method is not named {@code forceEndTurn()} because all turn-forcing actions are done by server code; see
     * {@link soc.server.SOCGameHandler#endGameTurnOrForce(SOCGame, int, String, soc.server.genericServer.Connection, boolean)}.
     * @since 2.0.00
     */
    public void addForcedEndTurn()
    {
        forcedEndTurnCount++;
    }

    /**
     * set the face image id
     *
     * @param id  the image id. {@link #FIRST_HUMAN_FACE_ID} and higher are human face images; 0 and -1 are the robot.
     */
    public void setFaceId(int id)
    {
        faceId = id;
    }

    /**
     * get the face image id.
     * @return the face image id. {@link #FIRST_HUMAN_FACE_ID} and higher are human face images; 0 and -1 are the robot.
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
     * Flag for whether the player has been asked and then re-asked to discard resources this turn.
     * Helps prevent endless loops of server discard request + bot client's wrong-amount discard action
     * if a bot is buggy or its resource amounts are wrong.
     *<P>
     * Is set by {@link #setAskedDiscardTwiceThisTurn()}, cleared by {@link #updateAtTurn()}.
     *
     * @return true if flag is set
     * @since 2.5.00
     */
    public boolean hasAskedDiscardTwiceThisTurn()
    {
        return askedDiscardTwiceThisTurn;
    }

    /**
     * Set the {@link #hasAskedDiscardTwiceThisTurn()} flag; see that method for details.
     * @since 2.5.00
     */
    public void setAskedDiscardTwiceThisTurn()
    {
        askedDiscardTwiceThisTurn = true;
    }

    /**
     * Get the number of one piece type available to place (not already in play).
     * At the start of the game, for example, <tt>getNumPieces({@link SOCPlayingPiece#CITY})</tt> == {@link #CITY_COUNT}.
     * On the sea board, each player also starts with {@link #SHIP_COUNT} ships.
     *
     * @return the number of pieces available for a particular piece type
     * @param ptype the type of piece; a SOCPlayingPiece constant
     *   like {@link SOCPlayingPiece#ROAD} or {@link SOCPlayingPiece#SETTLEMENT}.
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
     * Set the amount of pieces available to place (not already in play)
     * for a particular piece type.
     *
     * @param ptype the type of piece; a SOCPlayingPiece constant
     *   like {@link SOCPlayingPiece#ROAD} or {@link SOCPlayingPiece#SETTLEMENT}.
     * @param amt                 the amount
     * @see #setNumWarships(int)
     */
    public void setNumPieces(int ptype, int amt)
    {
        numPieces[ptype] = amt;
    }

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI},
     * the number of {@link SOCShip}s that have been converted to warships
     * to defend against the pirate fleet and attack the {@link SOCFortress}.
     *<P>
     * {@link SOCShip} has no "isWarship" field; the player's first {@code numWarships}
     * ships within {@link #getRoadsAndShips()} are the warships, because those are the ships
     * heading out to sea starting at the player's settlement, placed chronologically.
     * See {@link SOCGame#isShipWarship(SOCShip)} for details.
     * @since 2.0.00
     */
    public int getNumWarships()
    {
        return numWarships;
    }

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI},
     * set the player's number of warships.  See {@link #getNumWarships()} for details.
     * @param count  New number of warships
     * @since 2.0.00
     */
    public void setNumWarships(final int count)
    {
        numWarships = count;
    }

    /**
     * Get this player's pieces on the board. Pieces here are in order of placement,
     * except when a ship has been moved on the board (removed, then re-added at the end of this list).
     * Does not include {@link #getFortress()}, if any,
     * because the player doesn't control that piece.
     * @return the list of pieces in play
     * @see #getNumPieces(int)
     * @see #getRoadsAndShips()
     */
    public Vector<SOCPlayingPiece> getPieces()
    {
        return pieces;
    }

    /**
     * Get this player's roads and ships on the board.  Chronological order.
     * Note that if a ship is moved on the board, it may go to the end of this list.
     *<P>
     * Before v2.0.00 this method was {@code getRoads}.
     *
     * @return the list of roads/ships in play
     * @see #getRoadOrShip(int)
     * @see #getMostRecentShip()
     * @see #getPieces()
     */
    public Vector<SOCRoutePiece> getRoadsAndShips()
    {
        return roadsAndShips;
    }

    /**
     * Get this player's road or ship on an edge.
     *<P>
     * Before v2.0.00 this method was {@code getRoad}.
     *
     * @param  edge  Edge coordinate of the road or ship
     * @return  The player's road or ship in play at this edge, or null
     * @see SOCBoard#roadOrShipAtEdge(int)
     * @see #getMostRecentShip()
     * @see #hasRoadOrShipAtEdge(int)
     * @since 2.0.00
     */
    public SOCRoutePiece getRoadOrShip(final int edge)
    {
        for (SOCRoutePiece roadOrShip : roadsAndShips)
            if (roadOrShip.getCoordinates() == edge)
                return roadOrShip;

        return null;
    }

    /**
     * Get this player's most recently placed ship, if any.
     * @return Most recent ship from {@link #getRoadsAndShips()}, or {@code null}
     *    if that list contains no {@link SOCShip}s
     * @see #getRoadsAndShips()
     * @since 2.0.00
     */
    public SOCShip getMostRecentShip()
    {
        for (int i = roadsAndShips.size() - 1; i >= 0; --i)
        {
            SOCRoutePiece rs = roadsAndShips.get(i);
            if (rs instanceof SOCShip)
                return (SOCShip) rs;
        }

        return null;
    }

    /**
     * @return the list of settlements in play
     * @see #getSettlementOrCityAtNode(int)
     */
    public Vector<SOCSettlement> getSettlements()
    {
        return settlements;
    }

    /**
     * Get this player's settlement or city at a node.
     *
     * @param  node  Node coordinate to check for a settlement or city
     * @return  The player's settlement or city in play at this node, or null
     * @see SOCBoard#settlementAtNode(int)
     * @see #hasSettlementOrCityAtNode(int)
     * @since 2.4.00
     */
    public SOCPlayingPiece getSettlementOrCityAtNode(final int node)
    {
        for (SOCSettlement sett : settlements)
            if (sett.getCoordinates() == node)
                return sett;

        for (SOCCity city : cities)
            if (city.getCoordinates() == node)
                return city;

        return null;
    }

    /**
     * @return the list of cities in play
     * @see #getSettlementOrCityAtNode(int)
     */
    public Vector<SOCCity> getCities()
    {
        return cities;
    }

    /**
     * Get a list of all special items of a given type held by the player.
     * Only some scenarios and expansions use Special Items.
     *<P>
     * <B>Locks:</B> This getter is not synchronized: It's assumed that the structure of Special Item lists
     * is set up at game creation time, and not often changed.  If a specific item type or access pattern
     * requires synchronization, do so outside this class and document the details.
     *
     * @param typeKey  Special item type.  Typically a {@link SOCGameOption} keyname; see the {@link SOCSpecialItem}
     *     class javadoc for details.
     * @return  List of all special items of that type, or {@code null} if none; will never return an empty list.
     *     Some list items may be {@code null} depending on the list structure created by the scenario or expansion.
     * @since 2.0.00
     * @see SOCGame#getSpecialItems(String)
     * @see SOCGame#getSpecialItemTypes()
     */
    public ArrayList<SOCSpecialItem> getSpecialItems(final String typeKey)
    {
        final ArrayList<SOCSpecialItem> ret = spItems.get(typeKey);
        if ((ret == null) || ret.isEmpty())
            return null;

        return ret;
    }

    /**
     * Get a special item of a given type, by index within the list of all items of that type held by the player.
     * Only some scenarios and expansions use Special Items.
     *<P>
     * <B>Locks:</B> This getter is not synchronized: It's assumed that the structure of Special Item lists
     * is set up at game creation time, and not often changed.  If a specific item type or access pattern
     * requires synchronization, do so outside this class and document the details.
     *
     * @param typeKey  Special item type.  Typically a {@link SOCGameOption} keyname; see the {@link SOCSpecialItem}
     *     class javadoc for details.
     * @param idx  Index within the list of special items of that type; must be within the list's current size
     * @return  The special item, or {@code null} if none of that type, or if that index is {@code null} within the list
     *     or is beyond the size of the list
     * @since 2.0.00
     * @see SOCGame#getSpecialItem(String, int)
     * @see SOCGame#getSpecialItem(String, int, int, int)
     * @see SOCSpecialItem#playerPickItem(String, SOCGame, SOCPlayer, int, int)
     * @see SOCSpecialItem#playerSetItem(String, SOCGame, SOCPlayer, int, int, boolean)
     */
    public SOCSpecialItem getSpecialItem(final String typeKey, final int idx)
    {
        final ArrayList<SOCSpecialItem> li = spItems.get(typeKey);
        if (li == null)
            return null;

        try
        {
            return li.get(idx);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Add or replace a special item in the player's list of items of that type.
     * Only some scenarios and expansions use Special Items.
     * @param typeKey  Special item type.  Typically a {@link SOCGameOption} keyname; see the {@link SOCSpecialItem}
     *     class javadoc for details.  If no list with this key exists, it will be created here.
     * @param idx  Index within the list of special items of that type; if this is past the list's current size,
     *     {@code null} elements will be inserted as needed until {@code idx} is a valid index
     *     If {@code idx} is within the list, the current element at that index will be replaced.
     * @param itm  Item object to set within the list.
     *     Method does not set or change {@link SOCSpecialItem#getPlayer() itm.getPlayer()}.
     * @return  The item previously at this index, or {@code null} if none
     * @throws IndexOutOfBoundsException  if {@code idx} &lt; 0
     * @see SOCGame#setSpecialItem(String, int, SOCSpecialItem)
     */
    public SOCSpecialItem setSpecialItem(final String typeKey, final int idx, final SOCSpecialItem itm)
        throws IndexOutOfBoundsException
    {
        ArrayList<SOCSpecialItem> li = spItems.get(typeKey);
        if (li == null)
        {
            li = new ArrayList<SOCSpecialItem>();
            spItems.put(typeKey, li);
        }

        final int L = li.size();
        if (idx < L)
        {
            return li.set(idx, itm);
        } else {
            for (int n = idx - L; n > 0; --n)  // if idx == L, n is 0, no nulls are needed
                li.add(null);

            li.add(itm);
            return null;
        }
    }

    /**
     * For scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI},
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
            ((ignoreEdge != -9) && game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI)) ? 3 : 0;

        int[] adjEdges = board.getAdjacentEdgesToNode_arr(node);
        for (int i = 0; i < 3; ++i)
            if ((adjEdges[i] == rsEdge) || (adjEdges[i] == ignoreEdge))
                adjEdges[i] = -9;  // ignore this edge

        // Look for a road/ship of ours, adjacent to node
        for (SOCRoutePiece rs : roadsAndShips)
        {
            final int edge = rs.getCoordinates();
            for (int i = 0; i < 3; ++i)
            {
                if (edge == adjEdges[i])
                {
                    if (rs.isRoadNotShip() == wantShip)
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
     * necessary. Calls {@link #isTradeRouteFarEndClosed(SOCShip, int, HashSet, List)}.
     *<P>
     * We check the route in one direction towards <tt>edgeFarNode</tt>, because we
     * assume that the other end of <tt>newShipEdge</tt>
     * has a settlement, or that it branches from an already-closed trade route.
     *<P>
     * The route and its segments may end at a settlement/city/village, a branch where 3 ships
     * meet at a node (and 2 of the 3 ships are closed), or may end "open" with no further pieces.
     *<P>
     * Settlements and cities owned by other players won't close the trade route.
     * {@link SOCVillage Villages} are present only with scenario game option {@link SOCGameOptionSet#K_SC_CLVI}.
     * If this route becomes closed and is the player's first Cloth Trade route with a village,
     * this method sets that player event flag and fires
     * {@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     *<P>
     * Valid only when {@link SOCGame#hasSeaBoard}.
     *
     * @param newShipEdge  A ship in a currently-open trade route, either newly placed
     *                  or adjacent to a newly placed settlement.
     *                  If the ship is newly placed, it should not yet be in {@link #roadsAndShips}.
     *                  If the settlement is newly placed, it should not yet be in {@link #settlements}.
     * @param edgeFarNode  The unvisited node at the far end of <tt>fromEdge</tt>.
     *                  We'll examine this node and then continue to move along edges past it.
     * @return  null if open, otherwise all the newly-closed {@link SOCShip}s
     * @throws IllegalStateException  if not {@link SOCGame#hasSeaBoard}
     * @throws IllegalArgumentException if {@link SOCShip#isClosed() newShipEdge.isClosed()} is already true
     * @since 2.0.00
     */
    private List<SOCShip> checkTradeRouteFarEndClosed(final SOCShip newShipEdge, final int edgeFarNode)
        throws IllegalStateException, IllegalArgumentException
    {
        if (! game.hasSeaBoard)
            throw new IllegalStateException();

        List<ArrayList<Object>> encounteredSelf = new ArrayList<ArrayList<Object>>();
            // if route loops around, contains ArrayLists of node coords & SOCShips
            // -- see isTradeRouteFarEndClosed javadoc for details

        HashSet<Integer> alreadyVisited = new HashSet<Integer>();  // contains Integer coords as segment is built

        // Check the far end node of fromEdge
        // for a settlement/city, then for ships in each
        // of that node's directions.
        // Note that if it becomes closed, segment will contain newShipEdge.

        isTradeRouteFarEndClosed_foundVillage = null;
        List<SOCShip> segment = isTradeRouteFarEndClosed
            (newShipEdge, edgeFarNode, alreadyVisited, encounteredSelf);

        if (segment == null)
            return null;

        for (SOCShip sh : segment)
            sh.setClosed();

        if (isTradeRouteFarEndClosed_foundVillage != null)
        {
            final boolean gotCloth = isTradeRouteFarEndClosed_foundVillage.addTradingPlayer(this);
            final boolean flagNew =
                ! hasPlayerEvent(SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);

            if (flagNew)
                setPlayerEvent(SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);
            if (flagNew || gotCloth)
            {
                if (game.gameEventListener != null)
                    game.gameEventListener.playerEvent
                        (game, this, SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE,
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
                ArrayList<Object> self = encounteredSelf.get(i);
                final int farNode = ((Integer) self.get(0)).intValue();
                SOCShip nearestShip = (SOCShip) self.get(1);
                if (nearestShip.isClosed())
                    continue;  // already closed

                List<SOCShip> recheck;
                List<ArrayList<Object>> reSelf = new ArrayList<ArrayList<Object>>();
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
                    final int nextNearEdge = ((SOCShip) self.get(2)).getCoordinates();
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
                        ! hasPlayerEvent(SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);

                    if (flagNew)
                        setPlayerEvent(SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);
                    if (flagNew || gotCloth)
                    {
                        if (game.gameEventListener != null)
                            game.gameEventListener.playerEvent
                                (game, this, SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE,
                                 flagNew, isTradeRouteFarEndClosed_foundVillage);
                    }
                }
            }
        }

        return segment;
    }

    /**
     * Set by {@link #isTradeRouteFarEndClosed(SOCShip, int, HashSet, List)}
     * if it finds a {@link SOCVillage} at any far end.
     * Not set unless both {@link SOCGame#hasSeaBoard} and {@link SOCGameOptionSet#K_SC_CLVI} are set.
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
     * @param encounteredSelf  contains ArrayLists, each with a node coord Integer and SOCShips;
     *               might be added to in this method.
     *               Node is the "far end" of the segment from <tt>edgeFirstShip</tt>,
     *               just past the ship that was re-encountered.
     *               The SOCShips are ordered starting with <tt>edgeFirstShip</tt> and moving
     *               out to the node just past (farther than) the encounter ship.
     *               (That ship is not in the ArrayList.)
     *               The very first ArrayList in the list is the one farthest from the original
     *               starting ship, and the following list entries will overall move closer
     *               to the start.
     * @return a closed route of {@link SOCShip} or null, ordered from <tt>fromEdge</tt> to far end;
     *         may also add to <tt>alreadyVisited</tt> and <tt>encounteredSelf</tt>
     * @throws ClassCastException if not {@link SOCGame#hasSeaBoard}
     * @throws IllegalArgumentException if {@link SOCShip#isClosed() edgeFirstShip.isClosed()}
     * @since 2.0.00
     */
    private List<SOCShip> isTradeRouteFarEndClosed
        (final SOCShip edgeFirstShip, final int edgeFarNode,
         HashSet<Integer> alreadyVisited, List<ArrayList<Object>> encounteredSelf)
        throws ClassCastException, IllegalArgumentException
    {
        if (edgeFirstShip.isClosed())
            throw new IllegalArgumentException
                ("closed(0x" + Integer.toHexString(edgeFirstShip.getCoordinates()) + ')');

        final SOCBoardLarge board = (SOCBoardLarge) game.getBoard();
        final boolean boardHasVillages = game.isGameOptionSet(SOCGameOptionSet.K_SC_CLVI);
        List<SOCShip> segment = new ArrayList<SOCShip>();

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

            final Integer edgeInt = Integer.valueOf(edge);

            // have we already visited this edge?
            if (alreadyVisited.contains(edgeInt))
            {
                // Build an encounteredSelf list entry.
                ArrayList<Object> already = new ArrayList<Object>();
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

                SOCRoutePiece rs = getRoadOrShip(nodeEdges[i]);
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
                List<SOCShip> shipsFrom1 = isTradeRouteFarEndClosed
                    (nextShip1, board.getAdjacentNodeFarEndOfEdge(nextShip1.getCoordinates(), node),
                     alreadyVisited, encounteredSelf);
                List<SOCShip> shipsFrom2 = isTradeRouteFarEndClosed
                    (nextShip2, board.getAdjacentNodeFarEndOfEdge(nextShip2.getCoordinates(), node),
                     alreadyVisited, encounteredSelf);

                // Did we encounter our route while recursing?
                if (encounterSize != encounteredSelf.size())
                {
                    // Build an encounteredSelf list entry.
                    ArrayList<Object> already = new ArrayList<Object>();
                    already.add(Integer.valueOf(node));
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

        if (pp instanceof SOCVillage)
            isTradeRouteFarEndClosed_foundVillage = (SOCVillage) pp;

        return segment;
    }

    /**
     * Get the location of this player's most recent
     * settlement.  Useful during initial placement.
     * @return the coordinates of the last settlement
     *     played by this player, or 0 if none yet
     */
    public int getLastSettlementCoord()
    {
        return lastSettlementCoord;
    }

    /**
     * Get the location of this player's most recent
     * road or ship.  Useful during initial placement.
     * @return the coordinates of the last road/ship played by this player
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
     * Is empty (not null) if {@link SOCGameOptionSet#K_SC_0RVP} is set.
     * @return longest road paths
     */
    public Vector<SOCLRPathData> getLRPaths()
    {
        return lrPaths;
    }

    /**
     * set the Longest Paths list.
     * @param vec  the list of Longest Paths to use
     */
    public void setLRPaths(List<SOCLRPathData> lrList)
    {
        lrPaths.clear();

        for (SOCLRPathData pd : lrList)
        {
            if (D.ebugOn)
                D.ebugPrintlnINFO("restoring pd for player " + playerNumber + " :" + pd);
            lrPaths.add(pd);
        }
    }

    /**
     * Set the node coordinate of this player's most recent Settlement placement.
     * Used at client when joining a game in progress.
     * @param node  Node coordinate, or 0 if none yet
     * @since 2.0.00
     */
    public void setLastSettlementCoord(final int node)
    {
        lastSettlementCoord = node;
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
     * Get the resources held in the player's hand.
     * @return reference to the player's resource set; not a read-only copy
     * @see #getRolledResources()
     * @see #getResourceTradeStats()
     */
    public SOCResourceSet getResources()
    {
        return resources;
    }

    /**
     * Get the current totals of resources received by dice rolls by this player.
     * Each resource type's total includes resources picked from a rolled {@link SOCBoardLarge#GOLD_HEX}.
     * For the {@link SOCScenario#K_SC_FOG Fog Scenario}, includes resources picked when building
     * a road or ship revealed gold from a {@link SOCBoardLarge#FOG_HEX}.
     *<P>
     * Please treat this as read-only.
     *<P>
     * Tracked at server, and at client v2.7.00 and newer when game's server is v2.0.00 and newer.
     *
     * @return array of resource counts from dice rolls;
     *   the used indexes are {@link SOCResourceConstants#CLAY} - {@link SOCResourceConstants#WOOD}.
     *   Index 0 is unused.
     *   In v2.0.00 and newer, index {@link SOCResourceConstants#GOLD_LOCAL} tracks how many
     *   resource picks the player has received from gold hexes.
     * @see #getRolledResources()
     * @see #addRolledResources(SOCResourceSet)
     * @see #getResourceTradeStats()
     * @since 1.1.09
     */
    public int[] getResourceRollStats()
    {
        return resourceStats;
    }

    /**
     * On server, set this player's {@link #getResourceRollStats()}. Useful for reloading a saved game snapshot.
     * @param stats  Resource roll stats to copy into player data; see {@link #getResourceRollStats()} for format.
     *     If longer than required length, extra elements are ignored.
     * @throws IllegalArgumentException if {@code stats} is null,
     *     or length &lt; 1 + {@link SOCResourceConstants#GOLD_LOCAL}
     * @see #setResourceTradeStats(ResourceSet[][])
     * @since 2.3.00
     */
    public void setResourceRollStats(final int[] stats)
        throws IllegalArgumentException
    {
        if ((stats == null) || (stats.length < resourceStats.length))
            throw new IllegalArgumentException("stats.length < " + resourceStats.length);

        System.arraycopy(stats, 0, resourceStats, 0, resourceStats.length);
    }

    /**
     * Add to this player's resources and resource-roll totals.
     * Sets {@link #getRolledResources()}, updates {@link #getResourceRollStats()}.
     *<P>
     * At server, if {@link SOCGame#hasSeaBoard} treat {@link SOCResourceConstants#GOLD_LOCAL}
     * as the gold-hex resources they must pick, and set
     * {@link #getNeedToPickGoldHexResources()} to that amount.
     * This method updates {@link #getResourceRollStats()}[{@link SOCResourceConstants#GOLD_LOCAL GOLD_LOCAL}].
     * Once the resources from gold from a dice roll are picked, the
     * game should update this player's {@link #getResourceRollStats()}.
     *<P>
     * Otherwise ignores rolled {@link SOCResourceConstants#UNKNOWN} resources.
     *<P>
     * Before v2.7.00, this was called only at server. Call at client is done in handler for the
     * {@link soc.message.SOCDiceResultResources SOCDiceResultResources} message which is sent from
     * server v2.0.00 and newer.
     *
     * @param rolled The resources gained by this roll, as determined
     *     by {@link SOCGame#rollDice()} or message from server
     * @see #getRolledResources()
     * @since 1.1.09
     */
    public void addRolledResources(SOCResourceSet rolled)
    {
        if (game.hasSeaBoard)
        {
            final int gold = rolled.getAmount(SOCResourceConstants.GOLD_LOCAL);
            if (gold > 0)
            {
                needToPickGoldHexResources += gold;
                resourceStats[SOCResourceConstants.GOLD_LOCAL] += gold;
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
     * Valid at server only; known at client only if server sends the proper message type
     * (server v2.0.00 and newer do so).
     * Please treat the returned set as read-only.
     * See {@link SOCGame#rollDice()} for details on what is and isn't included here.
     * @return the resources, if any, gained by this player from the
     *     current turn's {@link SOCGame#rollDice()}.
     * @see #addRolledResources(SOCResourceSet)
     * @see #getResources()
     * @see #getResourceRollStats()
     * @since 2.0.00
     */
    public SOCResourceSet getRolledResources()
    {
        return rolledResources;
    }

    /**
     * Get stats for resource totals given and received in trades this game.
     * Updated by {@link #makeTrade(ResourceSet, ResourceSet)}
     * and {@link #makeBankTrade(ResourceSet, ResourceSet)}.
     *<P>
     * Tracked at server, and at client v2.7.00 and newer when game's server is v2.5.00 and newer;
     * not tracked by bots whose {@link soc.util.SOCRobotParameters#getTradeFlag()} is 0.
     *<P>
     * The stats are returned as an array indexed as {@code [give=0/get=1][trType]},
     * where the {@code trType} subarray indexes are:
     *<UL>
     * <LI> 0: 3:1 port: {@link SOCBoard#MISC_PORT}
     * <LI> 1: 2:1 port from {@link SOCResourceConstants#CLAY}: {@link SOCBoard#CLAY_PORT}
     * <LI> 2: 2:1 port from {@link SOCResourceConstants#ORE}
     * <LI> 3: 2:1 port from {@link SOCResourceConstants#SHEEP
     * <LI> 4: 2:1 port from {@link SOCResourceConstants#WHEAT}
     * <LI> 5: 2:1 port from {@link SOCResourceConstants#WOOD}: {@link SOCBoard#WOOD_PORT}
     * <LI> 6: 4:1 (bank trades): {@link #TRADE_STATS_INDEX_BANK}
     * <LI> 7: Total of all player trades: {@link #TRADE_STATS_INDEX_PLAYER_ALL}
     *</UL>
     * (Highest index is {@link #TRADE_STATS_ARRAY_LEN} - 1).
     * Please treat the returned array as read-only or make a copy;
     * it's by-reference and contents will change with future trades.
     * If player hasn't traded anything, the returned resource sets will be empty (not null).
     *
     * @return the resources given/received by this player during all trades in the game
     * @see #getResourceRollStats()
     * @see #setResourceTradeStats(ResourceSet[][])
     * @since 2.6.00
     */
    public SOCResourceSet[][] getResourceTradeStats()
    {
        return new SOCResourceSet[][]{ tradeStatsGive, tradeStatsGet };
    }

    /**
     * Set this player's {@link #getResourceTradeStats()}. Useful for reloading a saved game snapshot
     * and when joining a game in progress.
     * Copies contents of {@code stats} into player's stats, instead of copying a reference.
     * @param stats Stats to copy into player's data; see {@link #getResourceTradeStats()} for format; not null.
     *     Elements in can be null; will treat as {@link SOCResourceSet#EMPTY_SET}.
     *     If subarrays' length is shorter than {@link #TRADE_STATS_ARRAY_LEN}, will pad with empty resource sets.
     *     If longer, extra elements are ignored.
     * @throws IllegalArgumentException if {@code stats} is null or {@code stats.length} != 2
     * @see #setResourceRollStats(int[])
     * @since 2.6.00
     */
    public void setResourceTradeStats(ResourceSet[][] stats)
    {
        if ((stats == null) || (stats.length != 2))
            throw new IllegalArgumentException("stats");

        int n = stats[0].length;
        if (n > TRADE_STATS_ARRAY_LEN)
            n = TRADE_STATS_ARRAY_LEN;
        else if (n < TRADE_STATS_ARRAY_LEN)
        {
            for (int ttype = n; ttype < TRADE_STATS_ARRAY_LEN; ++ttype)
            {
                tradeStatsGive[ttype].clear();
                tradeStatsGet[ttype].clear();
            }
        }

        for (int ttype = 0; ttype < n; ++ttype)
        {
            ResourceSet gave = stats[0][ttype], got = stats[1][ttype];
            if (gave != null)
                tradeStatsGive[ttype].setAmounts(gave);
            else
                tradeStatsGive[ttype].clear();
            if (got != null)
                tradeStatsGet[ttype].setAmounts(got);
            else
                tradeStatsGet[ttype].clear();
        }
    }

    /**
     * Get the player's inventory of {@link SOCDevCard}s and other occasional items.
     *<P>
     * Before v2.0.00, this method was {@code getDevCards()}.
     * @return the inventory (development card set)
     */
    public SOCInventory getInventory()
    {
        return inventory;
    }

    /**
     * @return whether this player has any unplayed dev cards in their Inventory
     *
     * @see #getInventory()
     * @since 1.1.00
     */
    public boolean hasUnplayedDevCards()
    {
        return (0 < inventory.getNumUnplayed());
    }

    /**
     * Get the player's army size (their number of knights in play).
     * @return the number of knights in play
     */
    public int getNumKnights()
    {
        return numKnights;
    }

    /**
     * set the player's army size (their number of knights in play).
     *
     * @param nk        the number of knights
     */
    public void setNumKnights(int nk)
    {
        numKnights = nk;
    }

    /**
     * increment the player's army size (their number of knights in play).
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
     * This player's number of undos remaining in the game,
     * which at start of game is game option {@code "UBL"}'s value,
     * or 0 if not using that option.
     *<P>
     * Is 0 before game starts; initialized in {@link SOCGame#updateAtBoardLayout()}.
     *
     * @return  Number of undos remaining, or 0 if none
     * @see #decrementUndosRemaining()
     * @since 2.7.00
     */
    public int getUndosRemaining()
    {
        return undosRemaining;
    }

    /**
     * Set the player's {@link #getUndosRemaining()}.
     * Game ignores this field if game option {@code "UBL"} not set.
     * @param newRemain Number of undos remaining, or 0 if none
     * @see #decrementUndosRemaining()
     * @since 2.7.00
     */
    public void setUndosRemaining(final int newRemain)
    {
        undosRemaining = newRemain;
    }

    /**
     * Reduce the player's {@link #getUndosRemaining()} by 1.
     * Game ignores this field if game option {@code "UBL"} not set.
     * @throws IllegalStateException if undosRemaining is already &lt;= 0
     * @see #setUndosRemaining(int)
     * @since 2.7.00
     */
    public void decrementUndosRemaining()
        throws IllegalStateException
    {
        if (undosRemaining <= 0)
            throw new IllegalStateException("undosRemaining");

        --undosRemaining;
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
     * Get this player's number of publicly known victory points:
     * Buildings, longest/largest bonus, Special VP, etc.
     * Public victory points exclude VP development cards, except at
     * end of game, when they've been announced by server.
     * Special Victory Points (SVPs) are included, if the game scenario awards them.
     * Also includes any VP from {@link #getCloth() cloth}.
     *<P>
     * After end of game at server, {@code getPublicVP()} might still be &lt; {@link #getTotalVP()}
     * because {@link #forceFinalVP(int)} is called only at clients.
     *
     * @return the number of publicly known victory points,
     *     or "final" VP if {@link #forceFinalVP(int)} was called
     * @see #getTotalVP()
     * @see #getSpecialVP()
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
     * Get this player's total VP: Buildings, longest/largest bonus, Special VP, VP cards/items.
     * At client, for other players is same as {@link #getPublicVP()} until VP cards/items are revealed.
     *
     * @return the actual number of victory points ({@link #getPublicVP()} + VP cards/items),
     *     or "final" VP if {@link #forceFinalVP(int)} was called
     * @see #getPublicVP()
     */
    public int getTotalVP()
    {
        if (finalTotalVP > 0)
            return finalTotalVP;

        int vp = getPublicVP();
        vp += inventory.getNumVPItems();

        return vp;
    }

    /**
     * If game is over, server can push the final score for
     * each player to the client.  During play, true scores aren't
     * known, because of hidden victory-point cards.
     * getTotalVP() and getPublicVP() will report this, if set.
     *
     * @param score Total score for the player, or 0 for no forced total.
     * @since 1.1.00
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
     * {@link SOCGame#gameEventListener} != null, and sent out to clients.
     * Clients then call it from the network message handler.
     * @param svp  Number of SVP
     * @param desc  Description of player's action that led to the SVP.
     *     At the server this is an I18N string key, at the client it's
     *     localized text sent from the server.
     * @see #getSpecialVPInfo()
     * @see #setSpecialVPInfo(ArrayList)
     * @since 2.0.00
     */
    public void addSpecialVPInfo(final int svp, final String desc)
    {
        if (svpInfo == null)
            svpInfo = new ArrayList<>();

        svpInfo.add(new SpecialVPInfo(svp, desc));
    }

    /**
     * Get the details, if known, behind this player's {@link #getSpecialVP()} total.
     * In chronological order during game play.
     * @return Info on the Special Victory Points (SVP) awarded, or null; please treat as read-only
     * @see #addSpecialVPInfo(int, String)
     * @since 2.0.00
     */
    public ArrayList<SpecialVPInfo> getSpecialVPInfo()
    {
        return svpInfo;
    }

    /**
     * Set or clear player's SVP details. Replaces any previous info.
     * See {@link #getSpecialVPInfo()} for more about {@link SpecialVPInfo}.
     * Useful for reloading a saved game snapshot.
     * @param info  New info for this player, or {@code null} to clear.
     *     An empty list will be stored as {@code null}.
     * @since 2.4.00
     */
    public void setSpecialVPInfo(ArrayList<SpecialVPInfo> info)
    {
        if ((info != null) && info.isEmpty())
            info = null;

        svpInfo = info;
    }

    /**
     * Gets one-time player events for scenarios on the {@link SOCGame#hasSeaBoard large sea board}.
     * As events occur during a game, each one's {@link SOCPlayerEvent#flagValue} bit is set.
     *<P>
     * Example events: {@link SOCPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA},
     * {@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     * Not all player events are returned here; some can't be represented in a single flag bit.
     *
     * @return Player events which have occurred so far this game
     * @see #hasPlayerEvent(SOCPlayerEvent)
     * @since 2.0.00
     */
    public int getPlayerEvents()
    {
        return playerEvents_bitmask;
    }

    /**
     * At client, set the player's {@link #getPlayerEvents()} based on a server message.
     * @param events  Player event flags to set; all others will be cleared.
     * @since 2.0.00
     */
    public void setPlayerEvents(final int events)
    {
        playerEvents_bitmask = events;
    }

    /**
     * Does this player have a certain scenario player event flag?
     * Flag bits are set as per-player events occur during a game.
     * @param spe  Player event, such as {@link SOCPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA}
     * @return  True if event flag is set for this player
     * @see #getPlayerEvents()
     * @since 2.0.00
     */
    public final boolean hasPlayerEvent(final SOCPlayerEvent spe)
    {
        return (0 != (playerEvents_bitmask & spe.flagValue));
    }

    /**
     * Set a certain scenario player event flag.
     * Can be set once per game.
     * @param spe  Player event, such as {@link SOCPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA}
     * @throws IllegalStateException if this flag is already set.
     *             This is checked at server, not client, due to message timing:
     *             Game events happen at the server before they happen at the client.
     * @see #clearPlayerEvent(SOCPlayerEvent)
     * @since 2.0.00
     */
    private final void setPlayerEvent(final SOCPlayerEvent spe)
        throws IllegalStateException
    {
        final int bit = spe.flagValue;
        if (game.isAtServer && (0 != (playerEvents_bitmask & bit)))
            throw new IllegalStateException("Already set: 0x" + Integer.toHexString(bit));
        playerEvents_bitmask |= bit;
    }

    /**
     * Clear a certain scenario player event flag.
     * @param spe  Player event, such as {@link SOCPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA}
     * @see #setPlayerEvent(SOCPlayerEvent)
     * @since 2.0.00
     */
    private final void clearPlayerEvent(final SOCPlayerEvent spe)
    {
        playerEvents_bitmask &= (~ spe.flagValue);
    }

    /**
     * For scenarios on the {@link soc.game.SOCBoardLarge large sea board}, get
     * this player's bitmask of land areas for tracking Special Victory Points (SVP).
     * Used with scenario game option {@link SOCGameOptionSet#K_SC_SEAC _SC_SEAC}.
     * @return land areas bitmask, or 0
     * @since 2.0.00
     */
    public int getScenarioSVPLandAreas()
    {
        return scenario_svpFromEachLandArea_bitmask;
    }

    /**
     * At client, set the player's {@link #getPlayerEvents()} based on a server message.
     * @param las  Land areas bitmask value to set for player, from {@link #getScenarioSVPLandAreas()}:
     *     completely replaces previously set bits
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
     * @return  Encoded starting land area numbers 1 and 2:
     *     <tt>(landArea2 &lt;&lt; 8) | landArea1</tt>
     * @see soc.message.SOCPlayerElement.PEType#STARTING_LANDAREAS
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
     * @see #getCurrentOfferTime()
     */
    public SOCTradeOffer getCurrentOffer()
    {
        return currentOffer;
    }

    /**
     * Set or clear the current offer made by this player.
     * Also updates {@link #getCurrentOfferTime()}.
     *
     * @param offer   the offer, or {@code null} to clear.
     *     Doesn't validate that {@link SOCTradeOffer#getFrom() offer.getFrom()}
     *     is this player; server must do so.
     */
    public void setCurrentOffer(final SOCTradeOffer offer)
    {
        currentOffer = offer;
        currentOfferTimeMillis = System.currentTimeMillis();
    }

    /**
     * Get the time at which this player's current offer was made or cleared:
     * Time of last call to {@link #setCurrentOffer(SOCTradeOffer)}
     * with any offer or {@code null}, in same format as {@link System#currentTimeMillis()}.
     * @return  time of most recent call to {@code setCurrentOffer(..)},
     *     or 0 if never called during game
     * @see #getCurrentOffer()
     * @since 2.5.00
     */
    public long getCurrentOfferTime()
    {
        return currentOfferTimeMillis;
    }

    /**
     * Update this player's data when completing a trade with another player.
     * Assumes trade is valid.
     * Also updates {@link #getResourceTradeStats()}[{@link #TRADE_STATS_INDEX_PLAYER_ALL}].
     *<P>
     * At client, calls {@link SOCResourceSet#subtract(ResourceSet, boolean) resources.subtract(give, true)}
     * to use unknown resources if needed since client has incomplete info about other players.
     *<P>
     * Called by {@link SOCGame#makeTrade(int, int)}.
     * @param give  Resources given by this player to the other player; not {@code null}
     * @param get  Reosurces received by this player from the other player; not {@code null}
     * @since 2.6.00
     */
    public void makeTrade(final ResourceSet give, final ResourceSet get)
    {
        resources.add(get);
        resources.subtract(give, ! game.isAtServer);

        tradeStatsGive[TRADE_STATS_INDEX_PLAYER_ALL].add(give);
        tradeStatsGet[TRADE_STATS_INDEX_PLAYER_ALL].add(get);
    }

    /**
     * Update this player's data when performing a bank trade, or undoing the last bank trade.
     * Assumes trade is valid.
     * Also updates {@link #getResourceTradeStats()}.
     *<P>
     * At client, calls {@link SOCResourceSet#subtract(ResourceSet, boolean) resources.subtract(give, true)}
     * to use unknown resources if needed since client has incomplete info about other players.
     *<P>
     * Called by {@link SOCGame#makeBankTrade(SOCResourceSet, SOCResourceSet)} at server, and directly by client.
     *
     * @param  give  What the player will give to the bank; not {@code null}
     * @param  get   What the player wants from the bank; not {@code null}
     * @since 2.6.00
     */
    public void makeBankTrade(final ResourceSet give, final ResourceSet get)
    {
        resources.subtract(give, ! game.isAtServer);
        resources.add(get);

        /*
         * Determine trade type & update player's bank/port trade stats.
         * If get > give, is undo: Swap give/get, subtract instead of add.
         * Look for ports first; remember that 2 sheep, 2 wheat -> 1 clay, 1 wood is a valid trade
         * if player has the sheep port & wheat port, and should get credited in stats to both of those ports.
         */
        final boolean notUndo = (give.getTotal() > get.getTotal());
        final SOCResourceSet gaveCopy = new SOCResourceSet(notUndo ? give : get),
            gotCopy = new SOCResourceSet(notUndo ? get : give);
        final boolean has_3_1_port = ports[SOCBoard.MISC_PORT];
        for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype)
        {
            int amt = gaveCopy.getAmount(rtype);
            if (amt == 0)
                continue;

            final int statIndex, gotAmt;
            if (ports[rtype])
            {
                // 2:1
                statIndex = rtype;
                gotAmt = amt / 2;
            } else if (has_3_1_port) {
                // 3:1
                statIndex = SOCBoard.MISC_PORT;
                gotAmt = amt / 3;
            } else {
                continue;
            }
            gaveCopy.subtract(amt);
            ResourceSet gotForType = gotCopy.subtract(gotAmt);
            if (notUndo)
            {
                tradeStatsGive[statIndex].add(amt, rtype);
                tradeStatsGet[statIndex].add(gotForType);
            } else {
                tradeStatsGive[statIndex].subtract(amt, rtype);
                tradeStatsGet[statIndex].subtract(gotForType);
            }
        }

        if (gaveCopy.getTotal() > 0)
        {
            // 4:1
            if (notUndo)
            {
                tradeStatsGive[TRADE_STATS_INDEX_BANK].add(gaveCopy);
                tradeStatsGet[TRADE_STATS_INDEX_BANK].add(gotCopy);
            } else {
                tradeStatsGive[TRADE_STATS_INDEX_BANK].subtract(gaveCopy);
                tradeStatsGet[TRADE_STATS_INDEX_BANK].subtract(gotCopy);
            }
        }
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
     * During {@link SOCGame#isDebugFreePlacement()}, check whether this ship placement would
     * run into any scenario-specific conditions not currently handled by Free Placement code
     * at game, server, or client. Server should reject such a placement request.
     *<P>
     * Doesn't re-check the standard conditions in {@link SOCGame#canPlaceShip(SOCPlayer, int)}.
     * Currently allows any placement, except:
     *<UL>
     * <LI> {@link SOCGameOptionSet#K_SC_FTRI SC_FTRI}: If not current player,
     *      can't Free Place at an edge which would pick up a free port for placement:
     *      {@link SOCBoardLarge#canRemovePort(int)}
     *</UL>
     *
     * @param shipEdge  Edge to check for placing a ship in Free Placement mode
     * @return true if this player's ship could be placed there. Defaults to true.
     * @since 2.3.00
     */
    public boolean canPlaceShip_debugFreePlace
        (final int shipEdge)
    {
        // checks similar conditions to putPiece_roadOrShip_checkNewShipTradeRouteAndSpecialEdges(..)

        if ((! game.hasSeaBoard) || (playerNumber == game.getCurrentPlayerNumber()))
            return true;

        if (game.isGameOptionSet(SOCGameOptionSet.K_SC_FTRI)
            && ((SOCBoardLarge) game.getBoard()).canRemovePort(shipEdge))
            return false;

        return true;
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
     * {@link SOCPlayer#removePiece(SOCPlayingPiece, SOCPlayingPiece, boolean)}.
     *<P>
     * Call this before calling {@link SOCBoard#putPiece(SOCPlayingPiece)}.
     *<P>
     * For some scenarios on the {@link SOCGame#hasSeaBoard large sea board}, placing
     * a settlement in a new Land Area may award the player Special Victory Points (SVP).
     * If so, this method will update {@link #getSpecialVP()} and
     * {@link #getPlayerEvents()} / {@link #getScenarioSVPLandAreas()}
     * and fire a {@link SOCPlayerEvent#SVP_SETTLED_ANY_NEW_LANDAREA} or
     * {@link SOCPlayerEvent#SVP_SETTLED_EACH_NEW_LANDAREA}.
     *<P>
     * For scenario option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI},
     * call with <tt>piece</tt> = {@link SOCFortress} to set the single "pirate fortress"
     * that this player must defeat to win.  When the fortress is defeated, it is
     * converted to a settlement; call with <tt>piece</tt> = {@link SOCSettlement} at the
     * fortress location.
     * <tt>isTempPiece</tt> must be false to set or clear the fortress.
     *
     * @param piece        The piece to be put into play; coordinates are not checked for validity.
     * @param isTempPiece  Is this a temporary piece?  If so, do not call the
     *                     game's {@link SOCGameEventListener}.
     * @return any side-effects of placing our own piece at server, or null (never an empty list)
     * @throws IllegalArgumentException  only if piece is a {@link SOCFortress}, and either
     *                     <tt>isTempPiece</tt>, or player already has a fortress set.
     */
    public List<GameAction.Effect> putPiece(final SOCPlayingPiece piece, final boolean isTempPiece)
        throws IllegalArgumentException
    {
        List<GameAction.Effect> effects = null;

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
                effects = putPiece_roadOrShip((SOCRoutePiece) piece, board, isTempPiece);
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

                    {
                        List<SOCShip> newlyClosed =
                            putPiece_settlement_checkTradeRoutes((SOCSettlement) piece, board);
                        if ((newlyClosed != null) && game.isAtServer)
                        {
                            if (effects == null)
                                effects = new ArrayList<>();

                            final int L = newlyClosed.size();
                            int[] edges = new int[L];
                            for (int i = 0; i < L; ++i)
                                edges[i] = newlyClosed.get(i).getCoordinates();

                            effects.add(new GameAction.Effect(GameAction.EffectType.CLOSE_SHIP_ROUTE, edges));
                        }
                    }

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
                         * do we get any SVP for reaching a new land area?
                         */
                        else
                        {
                            final int newSettleArea = ((SOCBoardLarge) board).getNodeLandArea(settlementNode);
                            if ((newSettleArea != 0)
                                && (newSettleArea != startingLandArea1) && (newSettleArea != startingLandArea2))
                            {
                                List<GameAction.Effect> ef = putPiece_settlement_checkScenarioSVPs
                                    ((SOCSettlement) piece, newSettleArea, isTempPiece);
                                if (ef != null)
                                {
                                    if (effects != null)
                                        effects.addAll(ef);
                                    else
                                        effects = ef;
                                }
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
                effects = putPiece_roadOrShip((SOCShip) piece, board, isTempPiece);
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

        return effects;
    }

    /**
     * For {@link #putPiece(SOCPlayingPiece, boolean) putPiece}, update road/ship-related info,
     * {@link #roadNodes}, {@link #roadNodeGraph} and {@link #lastRoadCoord}.
     * Call only when the piece is ours.
     * Does not update {@link #potentialRoads}/{@link #potentialShips}; see {@link #updatePotentials(SOCPlayingPiece)}.
     * @param piece  The road or ship
     * @param board  The board
     * @param isTempPiece  Is this a temporary piece?  If so, do not check special edges or "gift" ports
     *     or close a Ship Route
     * @return any side-effects of placing our own piece at server, or null (never an empty list)
     * @since 2.0.00
     */
    private List<GameAction.Effect> putPiece_roadOrShip
        (final SOCRoutePiece piece, final SOCBoard board, final boolean isTempPiece)
    {
        List<GameAction.Effect> effects = null;

        /**
         * before adding a non-temporary ship, check to see if its trade route is now closed,
         * or if it's reached a Special Edge or an _SC_FTRI "gift" trade port.
         */
        if ((piece instanceof SOCShip) && ! isTempPiece)
            effects = putPiece_roadOrShip_checkNewShipTradeRouteAndSpecialEdges
                ((SOCShip) piece, (SOCBoardLarge) board);

        /**
         * remember it
         */
        roadsAndShips.addElement(piece);
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
            if (! roadNodes.contains(node))
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
            final Integer node0Int = Integer.valueOf(node0),
                          node1Int = Integer.valueOf(node1);

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

        return effects;
    }

    /**
     * Check this new ship for adjacent settlements/cities/villages, to see if its trade route
     * will be closed.  Close it if so.
     *<P>
     * If the route becomes closed and is the player's first Cloth Trade route with a {@link SOCVillage},
     * this method sets that player flag and fires {@link SOCPlayerEvent#CLOTH_TRADE_ESTABLISHED_VILLAGE}.
     *<P>
     * If the board layout has Special Edges, check if the new ship has reached one, and if so
     * reward the player and fire an event like {@link SOCPlayerEvent#SVP_REACHED_SPECIAL_EDGE}
     * or {@link SOCPlayerEvent#DEV_CARD_REACHED_SPECIAL_EDGE}.
     *<P>
     * In scenario {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI}, checks for a "gift" trade port at new ship edge. If found,
     * calls {@link SOCGame#removePort(SOCPlayer, int)} and fires {@link SOCPlayerEvent#REMOVED_TRADE_PORT}.
     *
     *<H5>Temporary Pieces:</H5>
     * Do not call this method for temporary ships. Those shouldn't fire events for special edges or "gift" ports.
     * Their trade route probably contains pieces from a real player and a temporary dummy player,
     * and closing the route would call the real player's {@link SOCShip#setClosed()}.
     *
     *<H5>Saving and Loading Game:</H5>
     * When reloading a game at the server, its pieces' fields (including {@link SOCShip#isClosed()})
     * already have been calculated during gameplay before game was saved, and then reloaded as part of the game model.
     * So if game state is {@link SOCGame#LOADING} and we're at the server, won't re-check them here
     * or throw exceptions for already-closed ship routes.
     *
     * @param newShip  Our new ship being placed in {@link #putPiece(SOCPlayingPiece, boolean)};
     *                 should not yet be added to {@link #roadsAndShips}
     * @param board  game board
     * @return any side-effects of placing our own piece at server, or null (never an empty list)
     * @throws IllegalArgumentException if {@link SOCShip#isClosed() newShip.isClosed()} is already true
     * @since 2.0.00
     */
    private List<GameAction.Effect> putPiece_roadOrShip_checkNewShipTradeRouteAndSpecialEdges
        (final SOCShip newShip, final SOCBoardLarge board)
        throws IllegalArgumentException
    {
        if (game.isAtServer && (game.getGameState() == SOCGame.LOADING))
            return null;  // <--- Early return ---

        final boolean boardHasVillages = game.isGameOptionSet(SOCGameOptionSet.K_SC_CLVI);
        final int edge = newShip.getCoordinates();
        final int[] edgeNodes = board.getAdjacentNodesToEdge_arr(edge);
        List<GameAction.Effect> effects = null;
        List<SOCShip> allNewlyClosed = null;  // only at server, for effects

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
                pp = board.getVillageAtNode(edgeNodes[i]);
            }

            if (pp == null)
                continue;  // nothing adjacent

            // if pp is at edgeNodes[1], check from edgeNodes[0], or vice versa
            final int edgeFarNode = edgeNodes[1 - i];
            final List<SOCShip> closedRoute = checkTradeRouteFarEndClosed(newShip, edgeFarNode);
            if (closedRoute != null)
            {
                if (game.isAtServer)
                {
                    if (allNewlyClosed != null)
                        allNewlyClosed.addAll(closedRoute);
                    else
                        allNewlyClosed = closedRoute;
                }

                if (pp instanceof SOCVillage)
                {
                    final boolean gotCloth = ((SOCVillage) pp).addTradingPlayer(this);
                    final boolean flagNew =
                        ! hasPlayerEvent(SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);

                    if (flagNew)
                        setPlayerEvent(SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE);
                    if (flagNew || gotCloth)
                    {
                        if (game.gameEventListener != null)
                            game.gameEventListener.playerEvent
                                (game, this, SOCPlayerEvent.CLOTH_TRADE_ESTABLISHED_VILLAGE, flagNew, pp);
                    }
                }

                break;
            }
        }

        if ((allNewlyClosed != null) && game.isAtServer)
        {
            final int L = allNewlyClosed.size();
            int[] edges = new int[L];
            for (int i = 0; i < L; ++i)
                edges[i] = allNewlyClosed.get(i).getCoordinates();

            if (effects == null)
                effects = new ArrayList<>();
            effects.add(new GameAction.Effect(GameAction.EffectType.CLOSE_SHIP_ROUTE, edges));
        }

        final int seType = board.getSpecialEdgeType(edge);
        if (seType != 0)
        {
            switch (seType)
            {
            case SOCBoardLarge.SPECIAL_EDGE_DEV_CARD:
                {
                    board.setSpecialEdge(edge, 0);
                    final int cardtype;
                    if (game.isAtServer)
                    {
                        // Dev cards were set aside at start of game; get one now
                        Integer ctypeObj = board.drawItemFromStack();
                        cardtype = (ctypeObj != null) ? ctypeObj : SOCDevCardConstants.KNIGHT;
                        newShip.player.getInventory().addDevCard(1, SOCInventory.NEW, cardtype);
                    } else {
                        cardtype = SOCDevCardConstants.UNKNOWN;
                    }

                    if (game.gameEventListener != null)
                        game.gameEventListener.playerEvent
                            (game, newShip.player, SOCPlayerEvent.DEV_CARD_REACHED_SPECIAL_EDGE,
                             false, new soc.util.IntPair(edge, cardtype));
                }
                break;

            case SOCBoardLarge.SPECIAL_EDGE_SVP:
                {
                    board.setSpecialEdge(edge, 0);

                    ++specialVP;
                    ++newShip.specialVP;
                    if (newShip.specialVP == 1)
                        newShip.specialVPEvent = SOCPlayerEvent.SVP_REACHED_SPECIAL_EDGE;

                    if (game.gameEventListener != null)
                        game.gameEventListener.playerEvent
                            (game, newShip.player, SOCPlayerEvent.SVP_REACHED_SPECIAL_EDGE,
                             false, edge);
                }
                break;

            default:
                System.err.println("L2549: warning: No handler for reaching SEType " + seType);
            }
        }

        /**
         * _SC_FTRI: Is ship placed at a "gift" port that can be
         * removed from the board for placement elsewhere?
         */
        if (game.isGameOptionSet(SOCGameOptionSet.K_SC_FTRI) && board.canRemovePort(edge))
        {
            game.removePort(this, edge);  // updates game state, fires SOCPlayerEvent.REMOVED_TRADE_PORT
        }

        return effects;
    }

    /**
     * Check this new settlement for adjacent open ships, to see their its trade route
     * will be closed.  Close it if so.
     * @param newSettle  Our new settlement being placed in {@link #putPiece(SOCPlayingPiece, boolean)};
     *            should not yet be added to {@link #settlements}
     * @param board  game board
     * @return  all the newly-closed {@link SOCShip}s, or null if open
     * @since 2.0.00
     */
    private List<SOCShip> putPiece_settlement_checkTradeRoutes
        (SOCSettlement newSettle, SOCBoard board)
    {
        List<SOCShip> newlyClosed = null;
        final int[] nodeEdges = board.getAdjacentEdgesToNode_arr
            (newSettle.getCoordinates());

        for (int i = 0; i < 3; ++i)
        {
            final int edge = nodeEdges[i];
            if (edge == -9)
                continue;
            SOCRoutePiece pp = getRoadOrShip(edge);
            if (! (pp instanceof SOCShip))
                continue;
            SOCShip sh = (SOCShip) pp;
            if (sh.isClosed())
                continue;

            final int edgeFarNode =
                ((SOCBoardLarge) board).getAdjacentNodeFarEndOfEdge
                  (edge, newSettle.getCoordinates());
            List<SOCShip> segment =
                checkTradeRouteFarEndClosed(sh, edgeFarNode);

            if (segment != null)
                if (newlyClosed != null)
                    newlyClosed.addAll(segment);
                else
                    newlyClosed = segment;
        }

        return newlyClosed;
    }

    /**
     * Does the player get a Special Victory Point (SVP) for reaching a new land area?
     * Call when a settlement has been placed in a land area different from
     * {@link #startingLandArea1} and {@link #startingLandArea2}.
     * If player gets Special Victory Points because of game option
     * {@link SOCGameOptionSet#K_SC_SANY _SC_SANY} or {@link SOCGameOptionSet#K_SC_SEAC _SC_SEAC},
     * will update fields and fire a {@link SOCPlayerEvent} as described in
     * {@link #putPiece(SOCPlayingPiece, boolean)}.
     *
     * @param newSettle  Newly placed settlement
     * @param newSettleArea  Land area number of new settlement's location
     * @param isTempPiece  Is this a temporary piece?  If so, do not call the
     *            game's {@link SOCGameEventListener}.
     * @return any {@link GameAction.Effect}s from setting those SVPs if at server and not {@code isTempPiece},
     *     otherwise null
     * @since 2.0.00
     */
    private final List<GameAction.Effect> putPiece_settlement_checkScenarioSVPs
        (final SOCSettlement newSettle, final int newSettleArea, final boolean isTempPiece)
    {
        List<GameAction.Effect> effects = null;

        if ((! hasPlayerEvent(SOCPlayerEvent.SVP_SETTLED_ANY_NEW_LANDAREA))
             && game.isGameOptionSet(SOCGameOptionSet.K_SC_SANY))
        {
            final int prevSVP = specialVP, prevEvents = getPlayerEvents();

            setPlayerEvent(SOCPlayerEvent.SVP_SETTLED_ANY_NEW_LANDAREA);
            ++specialVP;
            newSettle.specialVP = 1;
            newSettle.specialVPEvent = SOCPlayerEvent.SVP_SETTLED_ANY_NEW_LANDAREA;

            if (! isTempPiece)
            {
                if (game.gameEventListener != null)
                    game.gameEventListener.playerEvent
                        (game, this, SOCPlayerEvent.SVP_SETTLED_ANY_NEW_LANDAREA, true, newSettle);

                if (game.isAtServer)
                {
                    if (effects == null)
                        effects = new ArrayList<>();
                    effects.add(new GameAction.Effect
                        (GameAction.EffectType.PLAYER_GAIN_SVP,
                         new int[]{ prevSVP, 1, prevEvents, SOCPlayerEvent.SVP_SETTLED_ANY_NEW_LANDAREA.flagValue }));
                }
            }
        }

        final int laBit = (1 << (newSettleArea - 1));
        if ((0 == (laBit & scenario_svpFromEachLandArea_bitmask)) && game.isGameOptionSet(SOCGameOptionSet.K_SC_SEAC))
        {
            final int prevSVP = specialVP, prevLAs = scenario_svpFromEachLandArea_bitmask;

            scenario_svpFromEachLandArea_bitmask |= laBit;
            specialVP += 2;
            newSettle.specialVP = 2;
            newSettle.specialVPEvent = SOCPlayerEvent.SVP_SETTLED_EACH_NEW_LANDAREA;

            if (! isTempPiece)
            {
                if (game.gameEventListener != null)
                    game.gameEventListener.playerEvent
                        (game, this, SOCPlayerEvent.SVP_SETTLED_EACH_NEW_LANDAREA, true, newSettle);

                if (game.isAtServer)
                {
                    if (effects == null)
                        effects = new ArrayList<>();
                    effects.add(new GameAction.Effect
                        (GameAction.EffectType.PLAYER_GAIN_SETTLED_LANDAREA,
                         new int[]{ prevSVP, prevLAs, scenario_svpFromEachLandArea_bitmask }));
                }
            }
        }

        return effects;
    }

    /**
     * Undo the putting of a piece; backwards-compatibility shim which calls newer form
     * {@link #undoPutPiece(SOCPlayingPiece, boolean) undoPutPiece(piece, false)}.
     * See that method's javadoc for details.
     */
    public void undoPutPiece(final SOCPlayingPiece piece)
    {
        undoPutPiece(piece, false);
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
     * If the piece is ours, calls
     * {@link #removePiece(SOCPlayingPiece, SOCPlayingPiece, boolean) removePiece(piece, null, isMoveOrReplacement)}.
     *<P>
     * For roads, does not update longest road; if you need to,
     * call {@link #calcLongestRoad2()} after this call.
     *<P>
     * For cities, doesn't add a settlement at its location.
     *<P>
     * For removing second initial settlement (state START2B),
     *   will zero the player's resource cards.
     *
     * @param piece         the piece placement to be undone.
     * @param isMoveOrReplacement  Is the piece really being moved to a new location, or replaced with another?
     *            If so, don't remove its {@link SOCPlayingPiece#specialVP} from player.
     * @since 2.3.00
     */
    public void undoPutPiece(final SOCPlayingPiece piece, final boolean isMoveOrReplacement)
    {
        final boolean ours = (piece.getPlayerNumber() == playerNumber);
        final int pieceCoord = piece.getCoordinates();
        final Integer pieceCoordInt = Integer.valueOf(pieceCoord);

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
                removePiece(piece, null, isMoveOrReplacement);
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
                List<Integer> adjEdges = board.getAdjacentEdgesToEdge(pieceCoord);

                for (SOCRoutePiece rs : roadsAndShips)
                {
                    for (Integer edgeObj : adjEdges)
                    {
                        final int edge = edgeObj.intValue();

                        if (rs.getCoordinates() == edge)
                            updatePotentials(rs);
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
                removePiece(piece, null, isMoveOrReplacement);
                ourNumbers.undoUpdateNumbers(piece, board);

                //
                // update our port flags
                //
                final int portType = board.getPortTypeFromNodeCoord(pieceCoord);
                if (portType != -1)
                    updatePortFlagsAfterRemove(portType, false);

            }  // if (ours)

            //
            // update settlement potentials
            //
            undoPutPieceAuxSettlement(pieceCoord);

            //
            // check adjacent nodes
            //
            for (final int adjNode : board.getAdjacentNodesToNode(pieceCoord))
            {
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
                removePiece(piece, null, isMoveOrReplacement);
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
        final Integer settleNodeInt = Integer.valueOf(settlementNode);

        //D.ebugPrintln("))))) undoPutPieceAuxSettlement : node = "+Integer.toHexString(settlementNode));
        //
        // if this node doesn't have any neighboring settlements or cities, make it legal
        //
        boolean haveNeighbor = false;
        SOCBoard board = game.getBoard();
        final List<Integer> adjNodes = board.getAdjacentNodesToNode(settlementNode);

        for (SOCSettlement settlement : board.getSettlements())
        {
            for (final int adjNode : adjNodes)
            {
                if (adjNode == settlement.getCoordinates())
                {
                    haveNeighbor = true;

                    //D.ebugPrintln(")))) haveNeighbor = true : node = "+Integer.toHexString(adjNode.intValue()));
                    break;
                }
            }

            if (haveNeighbor)
            {
                break;
            }
        }

        if (! haveNeighbor)
        {
            for (SOCCity city : board.getCities())
            {
                for (final int adjNode : adjNodes)
                {
                    if (adjNode == city.getCoordinates())
                    {
                        haveNeighbor = true;

                        //D.ebugPrintln(")))) haveNeighbor = true : node = "+Integer.toHexString(adjNode.intValue()));
                        break;
                    }
                }

                if (haveNeighbor)
                {
                    break;
                }
            }

            if (! haveNeighbor)
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
                    if (game.getGameState() < SOCGame.ROLL_OR_CARD)
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
                        final List<Integer> adjEdges = board.getAdjacentEdgesToNode(settlementNode);

                        for (SOCRoutePiece rs : roadsAndShips)
                        {
                            for (final int adjEdge : adjEdges)
                            {
                                if (rs.getCoordinates() == adjEdge)
                                {
                                    //D.ebugPrintln("))) found adj road at "+Integer.toHexString(adjEdge.intValue()));
                                    adjRoad = true;

                                    break;
                                }
                            }

                            if (adjRoad)
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
     * Remove a player's piece from the board, and put it back in the player's hand;
     * backwards-compatibility shim which calls newer form
     * {@link #removePiece(SOCPlayingPiece, SOCPlayingPiece, boolean) removePiece(piece, replacementPiece, false)}.
     * See that method's javadoc for details.
     */
    public void removePiece
        (final SOCPlayingPiece piece, final SOCPlayingPiece replacementPiece)
    {
        removePiece(piece, replacementPiece, false);
    }

    /**
     * remove a player's piece from the board,
     * and put it back in the player's hand.
     *<P>
     * Most callers will want to instead call {@link #undoPutPiece(SOCPlayingPiece, boolean)}
     * which calls removePiece and does more.
     *<P>
     * Removing a city doesn't add a settlement at its location.
     *<P>
     * Don't call removePiece for a {@link SOCFortress}; see {@link #getFortress()} javadoc.
     *<P>
     *<B>Note:</b> removePiece does NOT update the potential building lists
     *           for removing settlements or cities.
     * It does update potential road lists.
     * For roads, updates {@link #roadNodes} and {@link #roadNodeGraph}.
     *<P>
     * If a ship is removed in scenario {@code _SC_PIRI}, makes sure our {@link #getNumWarships()}
     * is never more than the number of ships on the board.
     *
     * @param piece  Our player's piece, to be removed from the board
     * @param replacementPiece  Piece that's replacing this piece; usually null unless player is upgrading to a city.
     *          If not null, and same player as <tt>piece</tt>, the removed piece's {@link SOCPlayingPiece#specialVP}
     *          and {@link SOCPlayingPiece#specialVPEvent} are copied to <tt>replacementPiece</tt>
     *          instead of being subtracted from the player's {@link #getSpecialVP()} count.
     * @param isMoveOrReplacement  Is the piece really being moved to a new location, or replaced with another?
     *            If so, don't remove its {@link SOCPlayingPiece#specialVP} from player.
     *            If {@code replacementPiece != null}, this field is ignored and can be {@code false}.
     * @since 2.3.00
     */
    public void removePiece
        (final SOCPlayingPiece piece, final SOCPlayingPiece replacementPiece, final boolean isMoveOrReplacement)
    {
        D.ebugPrintlnINFO("--- SOCPlayer.removePiece(" + piece + ")");

        final int pieceCoord = piece.getCoordinates();
        final Integer pieceCoordInt = Integer.valueOf(pieceCoord);
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
                        if (! isMoveOrReplacement)
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
                    roadsAndShips.removeElement(p);
                    numPieces[ptype]++;

                    if (ptype == SOCPlayingPiece.SHIP)
                    {
                        final int shipsPlaced = SHIP_COUNT - numPieces[ptype];
                        if (numWarships > shipsPlaced)
                            numWarships = shipsPlaced;
                    }

                    int[] edgeNodeCoords = new int[2];  // will hold edge's adjacent nodes

                    /**
                     * remove the nodes this road/ship touches from the roadNodes list
                     */
                    {
                        Collection<Integer> nodes = board.getAdjacentNodesToEdge(pieceCoord);
                        int i = 0;

                        for (final Integer nodeInt : nodes)
                        {
                            edgeNodeCoords[i] = nodeInt.intValue();
                            i++;

                            /**
                             * only remove a node if none of our roads/ships are touching it
                             */
                            final Collection<Integer> adjEdges = board.getAdjacentEdgesToNode(nodeInt.intValue());
                            boolean match = false;

                            for (SOCRoutePiece rs : roadsAndShips)
                            {
                                final int rdEdge = rs.getCoordinates();

                                for (final int adjEdge : adjEdges)
                                {
                                    if (rdEdge == adjEdge)
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
                                roadNodes.removeElement(nodeInt);
                                potentialSettlements.remove(nodeInt);
                            }
                        }
                    }

                    /**
                     * remove this road/ship from the graph of nodes connected by roads/ships
                     */
                    {
                        final int node0 = edgeNodeCoords[0],
                                  node1 = edgeNodeCoords[1];
                        final Integer node0Int = Integer.valueOf(node0),
                                      node1Int = Integer.valueOf(node1);

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
                    Collection<Integer> adjEdges = board.getAdjacentEdgesToEdge(pieceCoord);

                    for (Integer adjEdge : adjEdges)
                    {
                        if (! (potentialRoads.contains(adjEdge) || potentialShips.contains(adjEdge)))
                            continue;

                        /**
                         * if we have a settlement or city between adjEdge and removed piece's edge,
                         * adjEdge remains potential because it's adjacent to that settlement/city.
                         */
                        final int nodeBetween = board.getNodeBetweenAdjacentEdges(adjEdge, pieceCoord);
                        final SOCPlayingPiece settleBetween = board.settlementAtNode(nodeBetween);
                        if ((settleBetween != null) && (settleBetween.getPlayerNumber() == playerNumber))
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
                            final SOCPlayingPiece aPiece =
                                (adjNode == nodeBetween) ? settleBetween : board.settlementAtNode(adjNode);
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
                                for (final int adjAdjEdge : board.getAdjacentEdgesToNode(adjNode))
                                {
                                    if (adjAdjEdge != adjEdgeID)
                                    {
                                        for (SOCRoutePiece ourRS : roadsAndShips)
                                        {
                                            if (ourRS.getCoordinates() == adjAdjEdge)
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
     * As part of
     * {@link #removePiece(SOCPlayingPiece, SOCPlayingPiece, boolean) removePiece(SOCPlayingPiece, null, false)},
     * update player's {@link #specialVP} and related fields.
     *<P>
     * Not called if the removed piece is being moved (a ship) or replaced by another one (settlement upgrade to city).
     *<P>
     * Does nothing in game state {@link SOCGame#UNDOING_ACTION}, because in that state the SVPs are removed
     * by other code as part of undoing the piece placement and its {@link GameAction.Effect}s.
     *
     * @param p  Our piece being removed, which has {@link SOCPlayingPiece#specialVP} != 0
     * @since 2.0.00
     */
    private final void removePieceUpdateSpecialVP(final SOCPlayingPiece p)
    {
        if (game.getGameState() == SOCGame.UNDOING_ACTION)
            return;

        specialVP -= p.specialVP;

        switch (p.specialVPEvent)
        {
        case SVP_SETTLED_ANY_NEW_LANDAREA:
            clearPlayerEvent(p.specialVPEvent);
            break;

        default:
            break;  // Suppress warning; not all enum values need a handler here
        }
    }

    /**
     * After removing a player's piece at a port, or removing the port,
     * check to see if we still have a port of that type.
     * @param portType  The type of port removed (in range {@link SOCBoard#MISC_PORT MISC_PORT}
     *            to {@link SOCBoard#WOOD_PORT WOOD_PORT})
     * @param removedPort  If true, the port was removed, not our piece there; affects port-counting logic
     * @since 2.0.00
     */
    void updatePortFlagsAfterRemove(final int portType, final boolean removedPort)
    {
        final SOCBoard board = game.getBoard();
        final int nPort = board.getPortCoordinates(portType).size() / 2;
        final boolean wasOurSolePortOfType = (removedPort) ? (nPort == 0) : (nPort <= 1);

        if (wasOurSolePortOfType)
        {
            // since we have no other settlement on this kind of port,
            // we can just set the port flag to false
            setPortFlag(portType, false);
        }
        else
        {
            //
            // there are multiple ports, so we need to check all
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

            if (! havePortType)
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
        for (final int edge : board.getAdjacentEdgesToHex_arr(hexCoord))
        {
            if ((htype == SOCBoard.WATER_HEX) || board.isEdgeCoastline(edge))
            {
                final Integer edgeInt = Integer.valueOf(edge);
                if ((legalShipsRestricted == null) || legalShipsRestricted.contains(edgeInt))
                    legalShips.add(edgeInt);
            }
        }
    }

    /**
     * When a {@link SOCBoardLarge#FOG_HEX} is revealed to be water,
     * update this player's sets of potential and legal nodes and edges
     * around that hex.
     *<P>
     * The revealed hex's nodes and edges previously were part of the set,
     * because we didn't know if the fog hid land or water and assumed land.
     * <P>
     * Called by {@link SOCGame#revealFogHiddenHex(int, int, int)} when hex type is {@link SOCBoard#WATER_HEX}
     * and {@link SOCBoardLarge#revealFogHiddenHex(int, int, int)} has indicated some legal edges/nodes may
     * have been removed from the board's sets. Call only if {@link SOCGame#hasSeaBoard}.
     * @param hexCoord  Coordinate of revealed water hex
     * @since 2.0.00
     */
    void updatePotentialsAndLegalsAroundRevealedHex(final int hexCoord)
    {
        final SOCBoardLarge board = (SOCBoardLarge) game.getBoard();

        for (final Integer edgeObj : board.getAdjacentEdgesToHex(hexCoord))
            if (legalRoads.contains(edgeObj) && ! board.isEdgeLegalRoad(edgeObj))
            {
                legalRoads.remove(edgeObj);
                potentialRoads.remove(edgeObj);
            }

        for (final Integer nodeObj : board.getAdjacentNodesToHex(hexCoord))
            if (legalSettlements.contains(nodeObj) && ! board.isNodeOnLand(nodeObj))
            {
                legalSettlements.remove(nodeObj);
                potentialSettlements.remove(nodeObj);
            }
    }

    /**
     * update the arrays that keep track of where
     * this player can play further pieces, after a
     * piece has just been played, or after another
     * player's adjacent piece has been removed.
     *<P>
     * <b>Special case:</b> In game scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI},
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
        final Integer idInt = Integer.valueOf(id);
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
                                final Integer edgeInt = Integer.valueOf(edge);
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

                        final Integer nodeInt = Integer.valueOf(node);
                        if (legalSettlements.contains(nodeInt))
                        {
                            potentialSettlements.add(nodeInt);
                        }
                    }
                }

                // For game scenario _SC_PIRI, ship routes can't branch
                // in different directions, only extend from their ends.
                if ((ptype == SOCPlayingPiece.SHIP) && game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
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
                            if (getRoadOrShip(edge) instanceof SOCShip)  // adjacent roads aren't a branch
                            {
                                foundOtherShips = true;
                                break;
                            }
                        }

                        if (foundOtherShips)
                            for (int i = 0; i < 3; ++i)
                                potentialShips.remove(Integer.valueOf(edges[i]));
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
                    final Integer adjacNodeInt = Integer.valueOf(adjac[i]);
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
                        final Integer tmpEdgeInt = Integer.valueOf(tmp);
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
                // build the set of our road/ship edges placed so far.
                // for each of 3 adjacent edges to node:
                //  if we have potentialRoad(edge) or potentialShip(edge)
                //    check ourRoads vs that edge's far-end (away from node of new settlement)
                //    unless we have a road on far-end, this edge is no longer potential,
                //      because we're not getting past opponent's new settlement (on this end
                //      of the edge) to build it.

                // ourRoads contains both roads and ships.
                //  TODO may need to separate them and check twice,
                //       or differentiate far-side roads vs ships.
                HashSet<Integer> ourRoads = new HashSet<Integer>();  // TODO more efficient way of looking this up, with fewer temp objs
                for (SOCPlayingPiece p : this.pieces)
                {
                    if (p instanceof SOCRoutePiece)   // roads and ships
                        ourRoads.add(Integer.valueOf(p.getCoordinates()));
                }

                adjac = board.getAdjacentEdgesToNode_arr(id);
                for (int i = 0; i < 3; ++i)
                {
                    tmp = adjac[i];  // edge coordinate
                    if (tmp == -9)
                        continue;
                    final Integer tmpInt = Integer.valueOf(tmp);
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
                    // from tmp edge's far node.

                    final int[] farEdges = board.getAdjacentEdgesToNode_arr(farNode);
                    boolean foundOurRoad = false;
                    for (int ie = 0; ie < 3; ++ie)
                    {
                        int farEdge = farEdges[ie];
                        if ((farEdge != tmp) && ourRoads.contains(Integer.valueOf(farEdge)))
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
     * At the start of the game (before/during initial placement), all legal nodes
     * are potential; see {@link #getLegalSettlements()} for initialization.
     * During regular gameplay it's mostly empty, and based on player's road and ship locations.
     *<P>
     * Please make no changes, treat the returned set as read-only.
     * @return the player's set of potential-settlement node coordinates.
     *     Not {@code null} unless {@link #destroyPlayer()} has been called.
     * @see #getPotentialSettlements_arr()
     * @see #hasPotentialSettlement()
     * @see #hasPotentialSettlementsInitialInFog()
     * @since 2.0.00
     */
    public HashSet<Integer> getPotentialSettlements()
    {
        return potentialSettlements;
    }

    /**
     * Get this player's current potential settlement nodes.
     * At the start of the game (before/during initial placement), all legal nodes
     * are potential; see {@link #getLegalSettlements()} for initialization.
     * During regular gameplay it's mostly empty, and based on player's road and ship locations.
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
     * Called at client when joining or starting a game,
     * when game's Potential Settlements message is received.
     * Called at server if {@link SOCGame#hasSeaBoard},
     * just after makeNewBoard in {@link SOCGame#startGame()};
     * if not called, server copies the potentials list
     * at start of game from legalSettlements.
     *<P>
     * If player's game uses the large sea board ({@link SOCGame#hasSeaBoard}),
     * and {@code setLegalsToo}: Will also update the player's legal settlements
     * and legal road sets, since they aren't constant
     * on that type of board; will use {@code legalLandAreaNodes} if not null,
     * otherwise {@code psList}. Don't call this method before calling
     * {@link SOCBoardLarge#setLegalSettlements(Collection, int, HashSet[])},
     * or the road sets won't be complete.
     *<P>
     * Call this method before, not after, calling {@link #setRestrictedLegalShips(int[])}.
     * However, if the player already has a restricted legal ship edge list, this method won't clear it.
     *<P>
     * Before v2.0.00 this method was called {@code setPotentialSettlements}.
     *
     * @param psList  the list of potential settlements,
     *     a {@link Vector} or {@link HashSet} of {@link Integer} node coordinates; not null, but can be empty
     * @param setLegalsToo  For the large board layout:
     *     If true, also update legal settlements/roads/ships from {@code legalLandAreaNodes}.
     *     <P>
     *     In scenario {@code _SC_PIRI}, for efficiency the legal ships list will remain
     *     empty until {@link #setRestrictedLegalShips(int[])} is called.
     * @param legalLandAreaNodes If non-null and {@code setLegalsToo},
     *     all Land Areas' legal (but not currently potential) node coordinates.
     *     Index 0 is ignored; land area numbers start at 1.
     *     If {@code setLegalsToo} but this is null, will use
     *     {@link SOCBoardLarge#getLegalSettlements()} instead.
     * @throws NullPointerException if {@code psList} is null
     * @see #addLegalSettlement(int, boolean)
     */
    public void setPotentialAndLegalSettlements
        (Collection<Integer> psList, final boolean setLegalsToo, final HashSet<Integer>[] legalLandAreaNodes)
        throws NullPointerException
    {
        clearPotentialSettlements();
        potentialSettlements.addAll(psList);

        hasPotentialSettlesInitInFog = false;
        if (game.hasSeaBoard && (! psList.isEmpty()) && (game.getGameState() < SOCGame.ROLL_OR_CARD))
        {
            final SOCBoardLarge board = (SOCBoardLarge) game.getBoard();
            final HashSet<Integer> fogNodes = new HashSet<Integer>();
            for (int hex : board.getFogHiddenHexes().keySet())
                fogNodes.addAll(board.getAdjacentNodesToHex(hex));

            fogNodes.retainAll(psList);  // intersection of sets: fog nodes & potential settlements

            hasPotentialSettlesInitInFog = ! fogNodes.isEmpty();
        }

        if (setLegalsToo && game.hasSeaBoard)
        {
            legalSettlements.clear();

            final SOCBoardLarge board = (SOCBoardLarge) game.getBoard();

            if (legalLandAreaNodes != null)
                for (int i = 1; i < legalLandAreaNodes.length; ++i)
                    legalSettlements.addAll(legalLandAreaNodes[i]);
            else
                legalSettlements.addAll(board.getLegalSettlements());

            legalRoads = game.getBoard().initPlayerLegalRoads();
            if (! (board.getLandHexCoordsSet().isEmpty()))
            {
                if (! game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
                    legalShips = board.initPlayerLegalShips();
                else
                    legalShips.clear();  // SC_PIRI: caller must soon call setRestrictedLegalShips
            }
        }
    }

    /**
     * During initial placement, are any of this player's {@link #getPotentialSettlements()}
     * nodes on a {@link SOCBoardLarge#FOG_HEX}?
     * @return true only if board has fog hexes, our potentialSettlements have some nodes on
     *     the fog hexes, and game hasn't completed {@link SOCGame#isInitialPlacement()} yet
     * @since 2.0.00
     */
    public boolean hasPotentialSettlementsInitialInFog()
    {
        return hasPotentialSettlesInitInFog;
    }

    /**
     * The set of nodes where it's legal to place a settlement. A node is legal if a settlement
     * can ever be placed there. Placing a settlement will clear its node and adjacent nodes.
     *<P>
     * Set members are node coordinate {@link Integer}s: If
     * {@link Set#contains(Object) legalSettlements.contains}({@link Integer#valueOf(int) Integer.valueOf(nodeCoord))},
     * {@code nodeCoord} is a legal settlement.
     *<P>
     * If not {@link SOCGame#hasSeaBoard}: Initialized in constructor
     * from {@link SOCBoard#initPlayerLegalSettlements()}.
     *<P>
     * If {@link SOCGame#hasSeaBoard}: Empty at server until {@link SOCBoardLarge#makeNewBoard(SOCGameOptionSet)}
     * and {@link SOCGame#startGame()}, because the board layout and legal settlements vary
     * from game to game.  Empty at client until
     * {@link #setPotentialAndLegalSettlements(Collection, boolean, HashSet[])} is called.
     *
     * @return The player's set of legal-settlement node coordinates; please treat as read-only.
     *     Not {@code null} unless {@link #destroyPlayer()} has been called.
     * @see #getPotentialSettlements()
     * @since 2.5.00
     */
    public Set<Integer> getLegalSettlements()
    {
        return legalSettlements;
    }

    /**
     * Add this node to the player's legal settlement coordinates, for future possible placement.
     * Used in some scenarios when {@link SOCGame#hasSeaBoard} to add a location
     * after calling {@link #setPotentialAndLegalSettlements(Collection, boolean, HashSet[])}.
     * This would be a lone location beyond the usual starting/legal LandAreas on the scenario's board.
     * @param node  A node coordinate to add, or 0 to do nothing
     * @param checkAdjacents  If true, check adjacent nodes before adding.
     *      If {@code node} is adjacent to a settlement, it won't be added and {@link #getAddedLegalSettlement()}
     *      won't be updated to {@code node}.
     * @since 2.0.00
     * @see #isLegalSettlement(int)
     * @see #getAddedLegalSettlement()
     */
    public void addLegalSettlement(final int node, final boolean checkAdjacents)
    {
        if (node == 0)
            return;

        if (checkAdjacents)
        {
            final SOCBoard board = game.getBoard();
            final int[] adjacNodes = board.getAdjacentNodesToNode_arr(node);

            for (int i = 0; i < 3; ++i)
                if ((adjacNodes[i] != -9) && (null != board.settlementAtNode(adjacNodes[i])))
                    return;  // <--- Early return: adjacent settlement/city found ---
        }

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
     * Get the legal-settlement location, if any, added by {@link #addLegalSettlement(int, boolean)}.
     *<P>
     * That method could be called multiple times, but only the most recently added node
     * is returned by this method.
     *
     * @return  Legal settlement node added by most recent call to {@link #addLegalSettlement(int, boolean)}, or 0
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
     * @param edge  coordinates of an edge on the board. Accepts -1 for edge 0x00.
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
     * Is this edge coordinate a potential place this player can move a ship,
     * even if its move-from location becomes unoccupied?
     * Used by {@link SOCGame#canMoveShip(int, int, int)}
     * to check the ship's requested new location.
     *<P>
     * First, {@code toEdge} must be a potential ship<B>*</B> now.
     * Then, we check to see if even without the ship at {@code fromEdge},
     * toEdge is still potential:
     * If either end node of {@code toEdge} has a settlement/city of ours,
     * or has an adjacent edge with a ship of ours
     * (except {@code fromEdge}), then {@code toEdge} is potential.
     *<P>
     * Does not check pirate ship position or other requirements;
     * see {@link SOCGame#canMoveShip(int, int, int)} for that.
     *<P>
     * <B>*</B> In scenario {@code _SC_PIRI}, we check more carefully because
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
            if (game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI)
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
     * @param edge  coordinates of an edge on the board
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
     * @see #getRestrictedLegalShips()
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
     * by the game's scenario ({@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}),
     * or {@code null} if all sea edges are legal for ships.
     * If the player has no legal ship edges, this list is empty (not null).
     *<P>
     * Please treat the returned HashSet as read-only.
     *
     * @return  Legal sea edges if they're restricted, or {@code null}
     * @see #isLegalShip(int)
     * @since 2.0.00
     */
    public HashSet<Integer> getRestrictedLegalShips()
    {
        return legalShipsRestricted;
    }

    /**
     * Set the list of edges where the player can build ships,
     * when the legal sea edges for ships are restricted
     * by the game's scenario ({@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}).
     * @param edgeList  List of edges, same format as one player's array from
     *   {@link soc.server.SOCBoardAtServer#getLegalSeaEdges(SOCGame) SOCBoardAtServer.getLegalSeaEdges(SOCGame)};
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
     * Does this player have at least one potential road?
     * @return true if there is at least one potential road
     * @see #hasTwoPotentialRoads()
     */
    public boolean hasPotentialRoad()
    {
        return ! potentialRoads.isEmpty();
    }

    /**
     * Does this player have at least 2 potential roads (useful for Road Building),
     * or have 1 current potential plus another that becomes potential after placement there?
     * @return true if player has 2 such roads
     * @since 2.5.00
     */
    public boolean hasTwoPotentialRoads()
    {
        final int S = potentialRoads.size();
        if (S == 0)
            return false;
        if (S > 1)
            return true;

        // currently 1 potential road; see if placing there opens up another one

        Integer[] edges = potentialRoads.toArray(new Integer[1]);
        if ((edges == null) || (edges.length == 0))
            return false;  // unlikely
        SOCRoad tmpRoad = new SOCRoad(this, edges[0], null);
        game.putTempPiece(tmpRoad);
        final boolean hasAnother = ! potentialRoads.isEmpty();
        game.undoPutTempPiece(tmpRoad);

        return hasAnother;
    }

    /**
     * Does this player have at least one potential settlement?
     * @return true if there is at least one potential settlement
     * @see #getPotentialSettlements()
     */
    public boolean hasPotentialSettlement()
    {
        return ! potentialSettlements.isEmpty();
    }

    /**
     * Does this player have at least one potential city?
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
     * @throws IllegalStateException if gameState is past initial placement (> {@link SOCGame#START3B})
     */
    @SuppressWarnings("fallthrough")
    public boolean canBuildInitialPieceType(final int pieceType)
        throws IllegalStateException
    {
        if (game.getGameState() > SOCGame.START3B)
            throw new IllegalStateException();

        final int pieceCountMax = game.isGameOptionSet(SOCGameOptionSet.K_SC_3IP) ? 6 : 4;
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
         * Both methods rely on a stack holding NodeLenVis (pop to curNode in loop);
         * they differ in actual element type within the stack because they are
         * gathering slightly different results (length or a stack of edges).
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
                            final SOCRoutePiece rsFromNode;  // sea board: road/ship from node to j

                            if (game.hasSeaBoard)
                            {
                                // Check for road<->ship transitions,
                                // which require a settlement/city at node.
                                // If len==0, inboundRoad is null because we're just starting.

                                rsFromNode =
                                    getRoadOrShip(board.getEdgeBetweenAdjacentNodes(coord, j));
                                if (len > 0)
                                {
                                    if (rsFromNode == null)  // shouldn't happen
                                        continue;

                                    if ((rsFromNode.isRoadNotShip() != curNode.inboundRS.isRoadNotShip())
                                        && (settlementAtNodeCoord == null))
                                    {
                                        continue;  // Requires settlement/city to connect road to ship
                                            // (if settlementAtNodeCoord not null, its ownership was already checked)
                                    }
                                }
                            } else {
                                rsFromNode = null;
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
                                pending.push(new NodeLenVis<IntPair>(j, len + 1, newVis, rsFromNode));
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
     * For scenario option {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI},
     * calculate if the player has any coastal settlement or city where
     * they can place a moved "gift" port without it being adjacent to another port,
     * and the edges where it could be placed next to such settlements or cities.
     * @param  all  Return all such edges (for placement browsing), or just one (to see if they have any)?
     * @return  One or all potential coastal edge locations to place a moved port, or {@code null} if none
     * @since 2.0.00
     * @see SOCGame#canPlacePort(SOCPlayer, int)
     */
    public List<Integer> getPortMovePotentialLocations(final boolean all)
    {
        if (! game.hasSeaBoard)
            return null;  // <--- Early return: Not a board where this can happen ---

        // For each of player's coastal settles/cities:
        // - Check if its own node already touches a port
        // - Find its coastal edges
        // - If a coastal edge's other node touches a port, that port is on the edge or an adjacent edge

        final SOCBoardLarge board = (SOCBoardLarge) game.getBoard();

        ArrayList<Integer> coastalNodes = new ArrayList<Integer>();
        for (SOCPlayingPiece piece : settlements)
        {
            final int node = piece.getCoordinates();
            if (board.isNodeCoastline(node) && (-1 == board.getPortTypeFromNodeCoord(node)))
                coastalNodes.add(node);
        }
        for (SOCPlayingPiece piece : cities)
        {
            final int node = piece.getCoordinates();
            if (board.isNodeCoastline(node) && (-1 == board.getPortTypeFromNodeCoord(node)))
                coastalNodes.add(node);
        }

        if (coastalNodes.isEmpty())
            return null;  // <--- Early return: No coastal settlements or cities without ports ---

        ArrayList<Integer> potentialEdges = new ArrayList<Integer>();
        for (int node : coastalNodes)
        {
            for (int edge : board.getAdjacentEdgesToNode_coastal(node))
            {
                // Since each port touches 2 nodes, we can check the coastal edge's
                // other node to find a port on that edge or its adjacent edges.

                if (-1 == board.getPortTypeFromNodeCoord(board.getAdjacentNodeFarEndOfEdge(edge, node)))
                {
                    potentialEdges.add(edge);
                    if (! all)
                        return potentialEdges;  // <--- Early return: Caller doesn't want full list ---
                }
            }
        }

        return (potentialEdges.isEmpty()) ? null : potentialEdges;
    }

    /**
     * Set or clear a port-type flag used for bank trades.
     *
     * @param portType  the type of port; in range {@link SOCBoard#MISC_PORT} to {@link SOCBoard#WOOD_PORT}
     * @param value     true or false
     */
    public void setPortFlag(int portType, boolean value)
    {
        ports[portType] = value;
    }

    /**
     * For bank trades, is this player currently at any port of a given type?
     * @return the port flag for a type of port
     *
     * @param portType  the type of port; in range {@link SOCBoard#MISC_PORT} to {@link SOCBoard#WOOD_PORT}
     */
    public boolean getPortFlag(int portType)
    {
        return ports[portType];
    }

    /**
     * Get the port flags array, which tracks which trade port types this player has.
     * Array index == port type, in range {@link SOCBoard#MISC_PORT} to {@link SOCBoard#WOOD_PORT}.
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
     * Checks if this player has a settlement or a city at specified coordinate
     * @param node  the board node coordinate to check
     * @return true if we have a settlement or city at {@code node}
     * @see #hasSettlementAtNode(int)
     * @see #hasCityAtNode(int)
     * @see #getSettlementOrCityAtNode(int)
     * @since 2.5.00
     */
    public boolean hasSettlementOrCityAtNode(final int node)
    {
        return (null != getSettlementOrCityAtNode(node));
    }

    /**
     * Checks if this player has a settlement or a city at specified coordinate
     * @param node  the board node coordinate to check
     * @return true if we have a settlement at {@code node}
     * @see #hasSettlementOrCityAtNode(int)
     * @since 2.5.00
     */
    public boolean hasSettlementAtNode(final int node)
    {
        for (SOCSettlement p : settlements)
            if (p.getCoordinates() == node)
                return true;

        return false;
    }

    /**
     * Checks if this player has a settlement or a city at specified coordinate
     * @param node  the board node coordinate to check
     * @return true if we have a city at {@code node}
     * @see #hasSettlementOrCityAtNode(int)
     * @since 2.5.00
     */
    public boolean hasCityAtNode(final int node)
    {
        for (SOCCity p : cities)
            if (p.getCoordinates() == node)
                return true;

        return false;
    }

    /**
     * Checks if this player has a road or ship at specified edge coordinate.
     * @param edge  the board edge coordinate to check
     * @return true if we have a road or ship at {@code edge}
     * @see #getRoadOrShip(int)
     * @since 2.5.00
     */
    public boolean hasRoadOrShipAtEdge(final int edge)
    {
        return (null != getRoadOrShip(edge));
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
        roadsAndShips.removeAllElements();
        roadsAndShips = null;
        settlements.removeAllElements();
        settlements = null;
        cities.removeAllElements();
        cities = null;
        spItems.clear();
        spItems = null;
        fortress = null;
        resources = null;
        resourceStats = null;
        tradeStatsGive = null;
        tradeStatsGet = null;
        inventory = null;
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

    /**
     * Player as string for debugging: {@code "Player["} + playerNumber + {@code " "} + playerName + {@code "]"}
     */
    @Override
    public String toString()
    {
        return "Player[" + playerNumber + ' ' + name + ']';
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

        /**
         * Description of the player's action that led to the SVP.
         * At the server this is an I18N string key, at the client it's
         * localized text sent from the server.
         */
        public final String desc;

        public SpecialVPInfo(final int svp, final String desc)
        {
            this.svp = svp;
            this.desc = desc;
        }
    }

}
