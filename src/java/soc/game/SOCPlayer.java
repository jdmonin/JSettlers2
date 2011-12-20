/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2011 Jeremy D Monin <jeremy@nand.net>
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
import java.util.List;
import java.util.Stack;
import java.util.Vector;


/**
 * A class for holding and manipulating player data.
 * The player exists within one SOCGame, not persistent between games like SOCPlayerClient or SOCClientData.
 *<P>
 * For more information about the "legal" and "potential" road/settlement/city terms,
 * see page 61 of Robert S Thomas' dissertation.  Briefly:
 * "Legal" locations are where pieces can be placed, according to the game rules.
 * "Potential" locations are where pieces can be placed <em>soon</em>, based on the
 * current state of the game board.  For example, every legal settlement location is
 * also a potential settlement during initial placement (game state {@link SOCGame#START1A START1A}
 * through {@link SOCGame#START2A START2A}.  Once the player's second settlement is placed,
 * all potential settlement locations are cleared.  Only when they build 2 connected road
 * segments, will another potential settlement location be set.
 *<P>
 * If the board layout changes from game to game, as with {@link SOCLargeBoard} /
 * {@link SOCBoard#BOARD_ENCODING_LARGE}, use these methods to update the player's board data
 * after {@link SOCBoard#makeNewBoard(Hashtable)}, in this order:
 *<UL>
 * <LI> {@link #getPlayerNumber()}.{@link SOCPlayerNumbers#setLandHexCoordinates(int[]) setLandHexCoordinates(int[])}
 * <LI> {@link #setPotentialSettlements(Collection, boolean)}
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
     * {@link SOCPlayingPiece#CITY}.
     */
    private int[] numPieces;

    /**
     * a list of this player's pieces in play
     */
    private Vector pieces;

    /**
     * a list of this player's roads and ships in play
     */
    private Vector roads;

    /**
     * a list of this player's settlements in play
     */
    private Vector settlements;

    /**
     * a list of this player's cities in play
     */
    private Vector cities;

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
    private Vector lrPaths;

    /**
     * how many of each resource this player has
     */
    private SOCResourceSet resources;

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
     * the final total score (pushed from server at end of game),
     * or 0 if no score has been forced.
     * 
     * @see #forceFinalVP(int)
     */
    private int finalTotalVP;

    /**
     * this flag is true if the player needs to discard
     */
    private boolean needToDiscard;

    /**
     * all of the nodes that this player's roads and ships touch;
     * this is used to calculate longest road / longest trade route.
     */
    private Vector roadNodes;

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
    private Hashtable roadNodeGraph;

    /**
     * a list of edges where it is legal to place a road.
     * an edge is legal if a road could eventually be
     * placed there.
     */
    private HashSet legalRoads;

    /**
     * a set of nodes where it is legal to place a
     * settlement. A node is legal if a settlement
     * can ever be placed there.
     *<P>
     * Key = node coordinate, as {@link Integer}.
     * If {@link HashSet#contains(Object) legalSettlements.contains(new Integer(nodeCoord))},
     * then this is a legal settlement.
     * @see #potentialSettlements
     * @see SOCBoard#nodesOnLand
     */
    private HashSet legalSettlements;

    /**
     * a list of edges where it is legal to place a ship.
     * an edge is legal if a ship could eventually be
     * placed there.
     *<P>
     * If the game doesn't use the large sea board (<tt>! {@link SOCGame#hasSeaBoard}</tt>),
     * this set is empty but non-null.
     * @since 1.2.00
     */
    private HashSet legalShips;

    /**
     * a set of edges where a road could be placed
     * on the next turn.
     * At start of the game, this is clear/empty.
     * Elements are set true when the player places adjacent settlements or roads, via
     * {@link #updatePotentials(SOCPlayingPiece)}.
     */
    private HashSet potentialRoads;

    /**
     * a set of nodes where a settlement could be
     * placed on the next turn.
     * At start of the game, all {@link #legalSettlements} are also potential.
     * When the second settlement is placed, <tt>potentialSettlements</tt> is cleared,
     * and then re-set via {@link #updatePotentials(SOCPlayingPiece) updatePotentials(SOCRoad)}.
     *<P>
     * Key = node coordinate, as {@link Integer}.
     * If {@link HashSet#contains(Object) potentialSettlements.contains(new Integer(nodeCoord))},
     * then this is a potential settlement.
     * @see #legalSettlements
     * @see SOCBoard#nodesOnLand
     */
    private HashSet potentialSettlements;

    /**
     * a set of nodes where a city could be
     * placed on the next turn.
     * At start of the game, this is clear/empty because the player has no settlements yet.
     * Elements are set true when the player places settlements, via
     * {@link #updatePotentials(SOCPlayingPiece)}.
     * Unlike other piece types, there is no "<tt>legalCities</tt>" set,
     * because we use {@link #legalSettlements} before placing a settlement,
     * and settlements can always become cities.
     */
    private HashSet potentialCities;

    /**
     * a set of edges where a ship could be placed
     * on the next turn.
     * At start of the game, this is clear/empty.
     * Elements are set true when the player places adjacent settlements or ships, via
     * {@link #updatePotentials(SOCPlayingPiece)}.
     *<P>
     * If the game doesn't use the large sea board (<tt>! {@link SOCGame#hasSeaBoard}</tt>),
     * this set is empty but non-null.
     * @since 1.2.00
     */
    private HashSet potentialShips;

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
        numPieces = (int[]) player.numPieces.clone();
        pieces = (Vector) player.pieces.clone();
        roads = (Vector) player.roads.clone();
        settlements = (Vector) player.settlements.clone();
        cities = (Vector) player.cities.clone();
        longestRoadLength = player.longestRoadLength;
        lrPaths = (Vector) player.lrPaths.clone();
        resources = player.resources.copy();
        resourceStats = new int[player.resourceStats.length];
        System.arraycopy(player.resourceStats, 0, resourceStats, 0, player.resourceStats.length);
        devCards = new SOCDevCardSet(player.devCards);
        numKnights = player.numKnights;
        buildingVP = player.buildingVP;
        finalTotalVP = 0;
        playedDevCard = player.playedDevCard;
        needToDiscard = player.needToDiscard;
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

        roadNodes = (Vector) player.roadNodes.clone();
        // Deep copy of roadNodeGraph contents:
        roadNodeGraph = new Hashtable((int) (player.roadNodeGraph.size() * 1.4f));
        for (Enumeration rnodes = player.roadNodeGraph.keys(); rnodes.hasMoreElements(); )
        {
            Object rnKey = rnodes.nextElement();
            final int[] rnArr = (int[]) player.roadNodeGraph.get(rnKey);
            roadNodeGraph.put(rnKey, (int[]) rnArr.clone());
        }

        /**
         * init legal and potential arrays
         */
        legalRoads = (HashSet) (player.legalRoads.clone());
        legalSettlements = (HashSet) (player.legalSettlements.clone());
        legalShips = (HashSet) (player.legalShips.clone());
        potentialRoads = (HashSet) (player.potentialRoads.clone());
        potentialSettlements = (HashSet) (player.potentialSettlements.clone());
        potentialCities = (HashSet) (player.potentialCities.clone());
        potentialShips = (HashSet) (player.potentialShips.clone());

        if (player.currentOffer != null)
        {
            currentOffer = new SOCTradeOffer(player.currentOffer);
        }
        else
        {
            currentOffer = null;
        }
    }

    /**
     * create a new player
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
            numPieces[SOCPlayingPiece.SHIP] = 15;
        else
            numPieces[SOCPlayingPiece.SHIP] = 0;
        pieces = new Vector(24);
        roads = new Vector(15);
        settlements = new Vector(5);
        cities = new Vector(4);
        longestRoadLength = 0;
        lrPaths = new Vector();
        resources = new SOCResourceSet();
        resourceStats = new int[SOCResourceConstants.UNKNOWN];
        devCards = new SOCDevCardSet();
        numKnights = 0;
        buildingVP = 0;
        playedDevCard = false;
        needToDiscard = false;
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

        roadNodes = new Vector(20);
        roadNodeGraph = new Hashtable();

        /**
         * init legal and potential arrays.
         * no settlements yet, so no potential roads or cities.
         */
        potentialRoads = new HashSet();
        potentialCities = new HashSet();
        potentialShips = new HashSet();

        legalRoads = board.initPlayerLegalRoads();
        legalSettlements = board.initPlayerLegalAndPotentialSettlements();
        if (game.hasSeaBoard)
            legalShips = ((SOCBoardLarge) board).initPlayerLegalShips();
        else
            legalShips = new HashSet();  // will remain empty

        potentialSettlements = (HashSet) (legalSettlements.clone());

        currentOffer = null;
    }

    /**
     * Set all nodes to not be potential settlements.
     * Called by {@link SOCGame#putPiece(SOCPlayingPiece)}
     * in state {@link SOCGame#START2A} after 2nd settlement placement.
     * After they have placed another road, that road's
     * {@link #putPiece(SOCPlayingPiece)} call will call
     * {@link #updatePotentials(SOCPlayingPiece)}, which
     * will set potentialSettlements at the road's new end node.
     */
    public void clearPotentialSettlements()
    {
        potentialSettlements.clear();
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
     */
    public boolean getNeedToDiscard()
    {
        return needToDiscard;
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
     * @return the number of pieces not in play for a particular type of piece
     *
     * @param ptype the type of piece; matches SOCPlayingPiece constants,
     *   such as {@link SOCPlayingPiece#ROAD}, {@link SOCPlayingPiece#SETTLEMENT}.

     */
    public int getNumPieces(int ptype)
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
     */
    public void setNumPieces(int ptype, int amt)
    {
        numPieces[ptype] = amt;
    }

    /**
     * @return the list of pieces in play
     */
    public Vector getPieces()
    {
        return pieces;
    }

    /**
     * @return the list of roads/ships in play
     */
    public Vector getRoads()
    {
        return roads;
    }

    /**
     * Get this player's road or ship on an edge.
     * @param  edge  Edge coordinate of the road or ship
     * @return  The player's road or ship in play at this edge, or null
     * @since 1.2.00
     */
    public SOCRoad getRoadOrShip(final int edge)
    {
        Enumeration roadEnum = roads.elements();
        while (roadEnum.hasMoreElements())
        {
            SOCRoad roadOrShip = (SOCRoad) roadEnum.nextElement();
            if (roadOrShip.getCoordinates() == edge)
                return roadOrShip;
        }

        return null;
    }

    /**
     * @return the list of settlements in play
     */
    public Vector getSettlements()
    {
        return settlements;
    }

    /**
     * @return the list of cities in play
     */
    public Vector getCities()
    {
        return cities;
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
     * that edge will be checked with {@link #isPotentialShip(int, int)}.
     *<P>
     * @param sh  One of our ships
     * @since 1.2.00
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
                   (board, shipEdge, -9, shipNodes[0]);

        pp = board.settlementAtNode(shipNodes[1]);
        if ((pp != null) && (pp.getPlayerNumber() != playerNumber))
            pp = null;
        clearPastNode1 =
             (null == pp)
             && ! doesTradeRouteContinuePastNode
                    (board, shipEdge, -9, shipNodes[1]);

        return (clearPastNode0 || clearPastNode1);
    }

    /**
     * Does this trade route (ships only) continue past
     * an unoccupied node?
     *
     * @param board  game board
     * @param shipEdge  Edge with a ship on the trade route
     * @param ignoreEdge  Edge to ignore our pieces on, or -9; used
     *                    during the check before moving one of our ships.
     * @param node  Node at one end of <tt>shipEdge</tt>,
     *              which does not have a settlement or city;
     *              check this node's other 2 edges for ships
     *              continuing the trade route
     * @return  True if a ship continues the route past <tt>node</tt>
     *              along one or both of the node's 2 other edges
     * @since 1.2.00
     */
    private final boolean doesTradeRouteContinuePastNode
        (final SOCBoard board, final int shipEdge, final int ignoreEdge, final int node)
    {
        boolean routeContinues = false;

        int[] adjEdges = board.getAdjacentEdgesToNode_arr(node);
        for (int i = 0; i < 3; ++i)
            if ((adjEdges[i] == shipEdge) || (adjEdges[i] == ignoreEdge))
                adjEdges[i] = -9;  // ignore this edge

        // Look for a ship of ours, adjacent to node
        Enumeration roadEnum = roads.elements();
        while (roadEnum.hasMoreElements() && ! routeContinues)
        {
            SOCRoad road = (SOCRoad) roadEnum.nextElement();
            final int edge = road.getCoordinates();
            for (int i = 0; i < 3; ++i)
            {
                if (edge == adjEdges[i])
                {
                    if (road.isRoadNotShip())
                        continue;  // interested in ships only

                    routeContinues = true;
                    break;
                }
            }
        }

        return routeContinues;
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
     * The route and its segments may end at a settlement/city, a branch where 3 ships
     * meet at a node (and 2 of the 3 ships are closed), or may end "open" with no further pieces.
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
     * @since 1.2.00
     * @throws IllegalStateException  if not {@link SOCGame#hasSeaBoard}
     */
    private Vector checkTradeRouteFarEndClosed
        (final SOCShip newShipEdge, final int edgeFarNode)
        throws IllegalStateException
    {
        if (! game.hasSeaBoard)
            throw new IllegalStateException();

        ArrayList encounteredSelf = new ArrayList();  // if route loops around, contains Vectors of node coords & SOCShips
                                                      // -- see isTradeRouteFarEndClosed javadoc for details
        HashSet alreadyVisited = new HashSet();  // contains Integer coords as segment is built

        // Check the far end node of fromEdge
        // for a settlement/city, then for ships in each
        // of that node's directions.
        // Note that if it becomes closed, segment will contain newShipEdge.

        Vector segment = isTradeRouteFarEndClosed
            (newShipEdge, edgeFarNode, alreadyVisited, encounteredSelf);

        if (segment == null)
            return null;

        for (Enumeration se = segment.elements(); se.hasMoreElements(); )
            ((SOCShip) se.nextElement()).setClosed();

        // Now that those ships are closed, re-check the segments
        // where we might have found a loop, and see if those are
        // still open or should be closed.

        if (encounteredSelf.size() > 0)
        {
            // go from the farthest, inwards
            for (int i = 0; i < encounteredSelf.size(); ++i)
            {
                Vector self = (Vector) encounteredSelf.get(i);
                final int farNode = ((Integer) self.firstElement()).intValue();
                SOCShip nearestShip = (SOCShip) self.elementAt(1);
                if (nearestShip.isClosed())
                    continue;  // already closed

                Vector recheck;
                ArrayList reSelf = new ArrayList();
                HashSet reAlready = new HashSet();

                // check again to see if it should be closed now
                if (self.size() == 2)
                {
                    // just 1 ship along that segment
                    recheck = isTradeRouteFarEndClosed
                        (nearestShip, farNode, reAlready, reSelf);
                } else {
                    // 2 or more ships
                    final int nextNearEdge =
                        ((SOCShip) self.elementAt(2)).getCoordinates();
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
                for (Enumeration se = recheck.elements(); se.hasMoreElements(); )
                    ((SOCShip) se.nextElement()).setClosed();
            }
        }

        return segment;
    }

    /**
     * Recursive call for {@link #isTradeRouteFarEndClosed(int, int)}.
     * Check one segment of the trade route going from a branch.
     * The segment may end at a settlement/city, another branch, or end with no further pieces.
     * Valid only when {@link SOCGame#hasSeaBoard}.
     * See that method for more information.
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
     * @since 1.2.00
     */
    private Vector isTradeRouteFarEndClosed
        (final SOCShip edgeFirstShip, final int edgeFarNode,
         HashSet alreadyVisited, List encounteredSelf)
        throws ClassCastException, IllegalArgumentException
    {
        if (edgeFirstShip.isClosed())
            throw new IllegalArgumentException();
        final SOCBoardLarge board = (SOCBoardLarge) game.getBoard();
        Vector segment = new Vector();  // will contain ships

        SOCShip edgeShip = edgeFirstShip;
        segment.add(edgeShip);
        int edge = edgeShip.getCoordinates();
        int node = edgeFarNode;

        boolean foundClosedEnd = false;
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
                Vector already = new Vector();
                already.add(new Integer(node));
                already.addAll(segment);

                encounteredSelf.add(already);

                return null;  // <--- Early return: already visited ---
            }

            alreadyVisited.add(edgeInt);

            // check the node
            SOCPlayingPiece pp = board.settlementAtNode(node);
            if (pp != null)
            {
                if (pp.getPlayerNumber() == playerNumber)
                    foundClosedEnd = true;

                break;  // won't continue past here

            } else {

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
                        // If one ship is already closed, they both are.
                        // Stop here.
                        foundClosedEnd = true;
                        break;
                    }

                    // Recursive call to the 2 directions out from node:

                    final int encounterSize = encounteredSelf.size();
                    Vector shipsFrom1 = isTradeRouteFarEndClosed
                        (nextShip1, board.getAdjacentNodeFarEndOfEdge(nextShip1.getCoordinates(), node),
                         alreadyVisited, encounteredSelf);
                    Vector shipsFrom2 = isTradeRouteFarEndClosed
                        (nextShip2, board.getAdjacentNodeFarEndOfEdge(nextShip2.getCoordinates(), node),
                            alreadyVisited, encounteredSelf);

                    // Did we encounter our route while recursing?
                    if (encounterSize != encounteredSelf.size())
                    {
                        // Build an encounteredSelf list entry.
                        Vector already = new Vector();
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

        }

        if (! foundClosedEnd)
            return null;

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
     * @return longest road paths
     */
    public Vector getLRPaths()
    {
        return lrPaths;
    }

    /**
     * set the longest paths vector
     * @param vec  the vector
     */
    public void setLRPaths(Vector vec)
    {
        lrPaths.removeAllElements();

        Enumeration pathEnum = vec.elements();

        while (pathEnum.hasMoreElements())
        {
            SOCLRPathData pd = (SOCLRPathData) pathEnum.nextElement();
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
     * @param rolled The resources gained by this roll, as from
     *     {@link SOCGame#getResourcesGainedFromRoll(SOCPlayer, int)}
     * @since 1.1.09
     */
    public void addRolledResources(SOCResourceSet rolled)
    {
        resources.add(rolled);
        for (int rtype = SOCResourceConstants.CLAY; rtype < resourceStats.length; ++rtype)
            resourceStats[rtype] += rolled.getAmount(rtype);
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
        if (game.getPlayerWithLongestRoad() == null)
        {
            return false;
        }
        else
        {
            return (game.getPlayerWithLongestRoad().getPlayerNumber() == playerNumber);
        }
    }

    /**
     * @return true if this player has the largest army
     */
    public boolean hasLargestArmy()
    {
        if (game.getPlayerWithLargestArmy() == null)
        {
            return false;
        }
        else
        {
            return (game.getPlayerWithLargestArmy().getPlayerNumber() == playerNumber);
        }
    }

    /**
     * This player's number of publicly known victory points.
     * Public victory points exclude VP development cards, except at
     * end of game, when they've been announced by server.
     *  
     * @return the number of publicly known victory points
     * @see #forceFinalVP(int)
     */
    public int getPublicVP()
    {
        if (finalTotalVP > 0)
            return finalTotalVP;
        
        int vp = buildingVP;

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
     * @return the list of nodes that touch the roads/ships in play
     */
    public Vector getRoadNodes()
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

        final int[] adjac = (int[]) roadNodeGraph.get(new Integer(node1));
        if (adjac == null)
            return false;

        for (int i = 2; i >= 0; --i)
            if (node2 == adjac[i])
                return true;

        return false;
    }

    /**
     * Put a piece into play.
     * Update potential piece lists.
     * For roads, update {@link #roadNodes} and {@link #roadNodeGraph}.
     * Does not update longest road; instead, {@link SOCGame#putPiece(SOCPlayingPiece)}
     * calls {@link #calcLongestRoad2()}.
     *<P>
     * <b>Note:</b> Placing a city automatically removes the settlement there
     *<P>
     * Call this before calling {@link SOCBoard#putPiece(SOCPlayingPiece)}.
     *
     * @param piece         the piece to be put into play; coordinates are not checked for validity
     */
    public void putPiece(SOCPlayingPiece piece)
    {
        /**
         * only do this stuff if it's our piece
         */
        if (piece.getPlayerNumber() == playerNumber)
        {
            pieces.addElement(piece);

            SOCBoard board = game.getBoard();
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
                numPieces[SOCPlayingPiece.SETTLEMENT]--;
                putPiece_settlement_checkTradeRoutes
                    ((SOCSettlement) piece, board);
                settlements.addElement(piece);
                lastSettlementCoord = piece.getCoordinates();
                buildingVP++;

                /**
                 * update what numbers we're touching
                 */
                ourNumbers.updateNumbers(piece, board);

                /**
                 * update our port flags
                 */
                int portType = board.getPortTypeFromNodeCoord(piece.getCoordinates());
                if (portType != -1)
                    setPortFlag(portType, true);

                break;

            /**
             * placing a city
             */
            case SOCPlayingPiece.CITY:

                /**
                 * place the city
                 */
                numPieces[SOCPlayingPiece.CITY]--;
                cities.addElement(piece);
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
            }
        }

        updatePotentials(piece);
    }

    /**
     * For {@link #putPiece(SOCPlayingPiece)}, update road/ship-related info,
     * {@link #roadNodes}, {@link #roadNodeGraph} and {@link #lastRoadCoord}.
     * Call only when the piece is ours.
     * @param piece  The road or ship
     * @param board  The board
     * @since 1.2.00
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
        Enumeration nodes = board.getAdjacentNodesToEdge(piece.getCoordinates()).elements();
        int[] nodeCoords = new int[2];
        int i = 0;

        while (nodes.hasMoreElements())
        {
            Integer node = (Integer) nodes.nextElement();

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
            int[] rnArr = (int[]) roadNodeGraph.get(node0Int);
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
            rnArr = (int[]) roadNodeGraph.get(node1Int);
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
     * Check this new ship for adjacent settlements/cities, to see if its trade route
     * will be closed.  Close it if so.
     * @param newShip  Our new ship being placed in {@link #putPiece(SOCPlayingPiece)};
     *                 should not yet be added to {@link #roads}
     * @param board  game board
     * @since 1.2.00
     */
    private void putPiece_roadOrShip_checkNewShipTradeRoute
        (SOCShip newShip, SOCBoard board)
    {
        final int[] edgeNodes = board.getAdjacentNodesToEdge_arr
            (newShip.getCoordinates());

        for (int i = 0; i < 2; ++i)
        {
            SOCPlayingPiece pp = board.settlementAtNode(edgeNodes[i]);
            if ((pp != null) && (pp.getPlayerNumber() != playerNumber))
                pp = null;
            if (pp == null)
                continue;

            // if pp is at edgeNodes[1], check from edgeNodes[0], or vice versa
            final int edgeFarNode = 1 - i;
            final Vector closedRoute = checkTradeRouteFarEndClosed
                (newShip, edgeFarNode);
            if (closedRoute != null)
                break;
        }
    }

    /**
     * Check this new settlement for adjacent open ships, to see their its trade route
     * will be closed.  Close it if so.
     * @param newSettlement  Our new settlement being placed in {@link #putPiece(SOCPlayingPiece)};
     *                 should not yet be added to {@link #settlements}
     * @param board  game board
     * @since 1.2.00
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
     * undo the putting of a piece.
     *<P>
     * Among other actions,
     * Updates the potential building lists
     * for removing settlements or cities.
     * Updates port flags, this player's dice resource numbers, etc.
     *<P>
     * Call this only after calling {@link SOCBoard#removePiece(SOCPlayingPiece)}.
     *<P>
     * If the piece is ours, calls {@link #removePiece(SOCPlayingPiece)}.
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
                removePiece(piece);
            }
            else
            {
                //
                // not our road/ship
                //
                // make it a legal space again
                //
                final boolean isCoastline = game.hasSeaBoard
                    && ((SOCBoardLarge) board).isEdgeCoastline(pieceCoord);
                if (piece.getType() == SOCPlayingPiece.ROAD)
                {
                    legalRoads.add(pieceCoordInt);
                    if (isCoastline)
                        legalShips.add(pieceCoordInt);
                } else {
                    legalShips.add(pieceCoordInt);
                    if (isCoastline)
                        legalRoads.add(pieceCoordInt);
                }

                //
                // call updatePotentials
                // on our roads/ships that are adjacent to 
                // this edge
                //
                Vector adjEdges = board.getAdjacentEdgesToEdge(pieceCoord);
                Enumeration roadEnum = roads.elements();

                while (roadEnum.hasMoreElements())
                {
                    SOCRoad road = (SOCRoad) roadEnum.nextElement();
                    Enumeration edgeEnum = adjEdges.elements();

                    while (edgeEnum.hasMoreElements())
                    {
                        final int edge = ((Integer) edgeEnum.nextElement()).intValue();

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
                removePiece(piece);
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
                            Enumeration settlementEnum = settlements.elements();

                            while (settlementEnum.hasMoreElements())
                            {
                                SOCSettlement settlement = (SOCSettlement) settlementEnum.nextElement();
                                if (board.getPortTypeFromNodeCoord(settlement.getCoordinates()) == portType)
                                {
                                    havePortType = true;
                                    break;
                                }
                            }

                            if (!havePortType)
                            {
                                Enumeration cityEnum = cities.elements();
                                while (cityEnum.hasMoreElements())
                                {
                                    SOCCity city = (SOCCity) cityEnum.nextElement();
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
            Enumeration adjNodesEnum = board.getAdjacentNodesToNode(pieceCoord).elements();

            while (adjNodesEnum.hasMoreElements())
            {
                final int adjNode = ((Integer) adjNodesEnum.nextElement()).intValue();
                undoPutPieceAuxSettlement(adjNode);
            }

            if (ours && (game.getGameState() == SOCGame.START2B))
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
                removePiece(piece);
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
        Vector adjNodes = board.getAdjacentNodesToNode(settlementNode);
        Enumeration settlementsEnum = board.getSettlements().elements();

        while (settlementsEnum.hasMoreElements())
        {
            SOCSettlement settlement = (SOCSettlement) settlementsEnum.nextElement();
            Enumeration adjNodesEnum = adjNodes.elements();

            while (adjNodesEnum.hasMoreElements())
            {
                final int adjNode = ((Integer) adjNodesEnum.nextElement()).intValue();

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
            Enumeration citiesEnum = board.getCities().elements();

            while (citiesEnum.hasMoreElements())
            {
                SOCCity city = (SOCCity) citiesEnum.nextElement();
                Enumeration adjNodesEnum = adjNodes.elements();

                while (adjNodesEnum.hasMoreElements())
                {
                    final int adjNode = ((Integer) adjNodesEnum.nextElement()).intValue();

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
                    // if it's the beginning of the game, make it potental
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
                        Vector adjEdges = board.getAdjacentEdgesToNode(settlementNode);
                        Enumeration roadsEnum = roads.elements();

                        while (roadsEnum.hasMoreElements())
                        {
                            SOCRoad road = (SOCRoad) roadsEnum.nextElement();
                            Enumeration adjEdgesEnum = adjEdges.elements();

                            while (adjEdgesEnum.hasMoreElements())
                            {
                                final int adjEdge = ((Integer) adjEdgesEnum.nextElement()).intValue();

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
     *<B>Note:</b> removePiece does NOT update the potential building lists
     *           for removing settlements or cities.
     * It does update potential road lists.
     * For roads, updates {@link #roadNodes} and {@link #roadNodeGraph}.
     *
     * @param piece  Our player's piece, to be removed from the board
     * @see #undoPutPiece(SOCPlayingPiece)
     */
    public void removePiece(SOCPlayingPiece piece)
    {
        D.ebugPrintln("--- SOCPlayer.removePiece(" + piece + ")");

        Enumeration pEnum = pieces.elements();
        SOCBoard board = game.getBoard();

        while (pEnum.hasMoreElements())
        {
            SOCPlayingPiece p = (SOCPlayingPiece) pEnum.nextElement();

            final int pieceCoord = piece.getCoordinates();
            final Integer pieceCoordInt = new Integer(pieceCoord);
            final int ptype = piece.getType();
            if ((ptype == p.getType()) && (pieceCoord == p.getCoordinates()))
            {
                pieces.removeElement(p);

                switch (ptype)
                {
                case SOCPlayingPiece.SHIP:  // fall through to ROAD
                case SOCPlayingPiece.ROAD:
                    roads.removeElement(p);
                    numPieces[ptype]++;

                    /**
                     * remove the nodes this road/ship touches from the roadNodes list
                     */
                    Enumeration nodes = board.getAdjacentNodesToEdge(pieceCoord).elements();
                    int[] nodeCoords = new int[2];
                    int i = 0;

                    while (nodes.hasMoreElements())
                    {
                        final Integer node = (Integer) nodes.nextElement();
                        nodeCoords[i] = node.intValue();
                        i++;

                        /**
                         * only remove a node if none of our roads/ships are touching it
                         */
                        Enumeration roadsEnum = roads.elements();
                        Vector adjEdges = board.getAdjacentEdgesToNode(node.intValue());
                        boolean match = false;

                        while (roadsEnum.hasMoreElements())
                        {
                            SOCRoad rd = (SOCRoad) roadsEnum.nextElement();
                            Enumeration adjEdgesEnum = adjEdges.elements();

                            while (adjEdgesEnum.hasMoreElements())
                            {
                                final int adjEdge = ((Integer) adjEdgesEnum.nextElement()).intValue();

                                if (adjEdge == rd.getCoordinates())
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

                        if (!match)
                        {
                            roadNodes.removeElement(node);
                            potentialSettlements.remove(node);
                        }
                    }

                    /**
                     * remove this road/ship from the graph of nodes connected by roads/ships
                     */
                    {
                        final int node0 = nodeCoords[0],
                                  node1 = nodeCoords[1];
                        final Integer node0Int = new Integer(node0),
                                      node1Int = new Integer(node1);

                        // roadNodeGraph[node0][node1]
                        int[] rnArr = (int[]) roadNodeGraph.get(node0Int);
                        if (rnArr != null)
                        {
                            for (int j = 0; j < 3; ++j)
                                if (node1 == rnArr[j])
                                {
                                    rnArr[j] = 0;
                                    break;
                                }
                        }

                        // roadNodeGraph[node1][node0]
                        rnArr = (int[]) roadNodeGraph.get(node1Int);
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
                     */
                    final boolean isCoastline = game.hasSeaBoard
                        && ((SOCBoardLarge) board).isEdgeCoastline(pieceCoord);
                    if (ptype == SOCPlayingPiece.ROAD)
                    {
                        potentialRoads.add(pieceCoordInt);
                        legalRoads.add(pieceCoordInt);
                        if (isCoastline)
                        {
                            potentialShips.add(pieceCoordInt);
                            legalShips.add(pieceCoordInt);
                        }
                    } else {
                        potentialShips.add(pieceCoordInt);
                        legalShips.add(pieceCoordInt);
                        if (isCoastline)
                        {
                            potentialRoads.add(pieceCoordInt);
                            legalRoads.add(pieceCoordInt);
                        }
                    }

                    /**
                     * check each adjacent legal edge, if there are
                     * no roads touching it, then it's no longer a
                     * potential road
                     */
                    // TODO roads/ships are not interchangeable here
                    Enumeration adjEdgesEnum = board.getAdjacentEdgesToEdge(pieceCoord).elements();

                    while (adjEdgesEnum.hasMoreElements())
                    {
                        Integer adjEdge = (Integer) adjEdgesEnum.nextElement();
                        final int adjEdgeID = adjEdge.intValue();

                        if (potentialRoads.contains(adjEdge))
                        {
                            boolean isPotentialRoad = false;  // or, isPotentialShip

                            /**
                             * check each adjacent node for blocking
                             * settlements or cities
                             */
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

                                if (!blocked)
                                {
                                    Enumeration adjAdjEdgesEnum = board.getAdjacentEdgesToNode(adjNode).elements();

                                    while ((adjAdjEdgesEnum.hasMoreElements()) && ! isPotentialRoad)
                                    {
                                        final int adjAdjEdge = ((Integer) adjAdjEdgesEnum.nextElement()).intValue();

                                        if (adjAdjEdge != adjEdgeID)
                                        {
                                            Enumeration ourRoadsEnum = roads.elements();

                                            while (ourRoadsEnum.hasMoreElements())
                                            {
                                                SOCRoad ourRoad = (SOCRoad) ourRoadsEnum.nextElement();

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
                                    }
                                }
                            }

                            if (ptype == SOCPlayingPiece.ROAD)
                            {
                                if (isPotentialRoad)
                                    potentialRoads.add(adjEdge);
                                else
                                    potentialRoads.remove(adjEdge);
                            } else {
                                if (isPotentialRoad)
                                    potentialShips.add(adjEdge);
                                else
                                    potentialShips.remove(adjEdge);                                
                            }
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
     * update the arrays that keep track of where
     * this player can play further pieces, after a
     * piece has just been played, or after another
     * player's adjacent piece has been removed.
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
                Hashtable ourRoads = new Hashtable();  // TODO more efficient way of looking this up, with fewer temp objs
                Object hashDummy = new Object();   // a value is needed for hashtable
                Enumeration pEnum = (this.pieces).elements();
                while (pEnum.hasMoreElements())
                {
                    SOCPlayingPiece p = (SOCPlayingPiece) pEnum.nextElement();
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
     * @since 1.2.00
     */
    public HashSet getPotentialSettlements()
    {
        return potentialSettlements;
    }

    /**
     * Set which nodes are potential settlements.
     * Called at client when joining/creating a game.
     * At server, the list is instead copied at start
     * of game from legalSettlements.
     *
     * @param psList  the list of potential settlements,
     *     as {@link Integer} node coordinates
     * @see #setPotentialSettlements(Collection, boolean)
     */
    public void setPotentialSettlements(Vector psList)
    {
        setPotentialSettlements(psList, false);
    }

    /**
     * Set which nodes are potential settlements.
     * Called at client when joining/creating a game.
     * At server, the list is instead copied at start
     * of game from legalSettlements.
     *<P>
     * If our game uses the large sea board ({@link SOCGame#hasSeaBoard}),
     * and <tt>setLegalsToo</tt>, and <tt>psList</tt> is not empty,
     * then also update the player's legal settlements
     * and legal road sets, since they aren't constant
     * on that type of board.
     *<P>
     * This method is called at the server only when <tt>game.hasSeaBoard</tt>,
     * just after makeNewBoard in {@link SOCGame#startGame()}.
     *
     * @param psList  the list of potential settlements,
     *     a {@link Vector} or {@link HashSet} of
     *     {@link Integer} node coordinates
     * @param setLegalsToo  If true, also update legal settlements/
     *     roads if we're using the large board layout
     * @see #setPotentialSettlements(Vector)
     * @since 1.2.00
     */
    public void setPotentialSettlements(Collection psList, final boolean setLegalsToo)
    {
        clearPotentialSettlements();
        potentialSettlements.addAll(psList);

        if (setLegalsToo && game.hasSeaBoard && ! psList.isEmpty())
        {
            legalSettlements.clear();
            legalSettlements.addAll(psList);
            legalRoads = game.getBoard().initPlayerLegalRoads();
            legalShips = ((SOCBoardLarge) game.getBoard()).initPlayerLegalShips();
        }
    }

    /**
     * @return true if this node is a potential settlement
     * @param node        the coordinates of a node on the board
     */
    public boolean isPotentialSettlement(final int node)
    {
        return potentialSettlements.contains(new Integer(node));
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
        potentialSettlements.remove(new Integer(node));
    }

    /**
     * Is this node to a legal settlement?
     * @return true if this edge is a legal settlement
     * @param node        the coordinates of a node on the board
     * @since 1.2.00
     */
    public boolean isLegalSettlement(final int node)
    {
        return legalSettlements.contains(new Integer(node));
    }

    /**
     * @return true if this node is a potential city
     * @param node        the coordinates of a node on the board
     */
    public boolean isPotentialCity(final int node)
    {
        return potentialCities.contains(new Integer(node));
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
        potentialCities.remove(new Integer(node));
    }

    /**
     * @return true if this edge is a potential road
     * @param edge        the coordinates of an edge on the board. Accepts -1 for edge 0x00.
     */
    public boolean isPotentialRoad(int edge)
    {
        if (edge == -1)
            edge = 0x00;
        return potentialRoads.contains(new Integer(edge));
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
        potentialRoads.remove(new Integer(edge));
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
        return legalRoads.contains(new Integer(edge));
    }

    /**
     * Is this edge coordinate a potential ship, even if another ship
     * edge was not?  Used by {@link SOCGame#canMoveShip(int, int, int)}
     * to check the ship's requested new location.
     *<P>
     * First, <tt>edge</tt> must be a potential ship now.
     * Then, we check to see if even without the ship at <tt>ignoreEdge</tt>,
     * edge is still potential:
     * If either end node of <tt>edge</tt> has a settlement/city of ours,
     * or has an adjacent edge with a ship of ours
     * (except <tt>ignoreEdge</tt>), then <tt>edge</tt> is potential.
     *
     * @return true if this edge is still a potential ship
     * @param edge  the coordinates of an edge on the board;
     *       {@link #isPotentialShip(int) isPotentialShip(edge)}
     *       must currently be true.
     * @param ignoreShipEdge  the coordinates of another ship edge, to
     *   ignore when determining if <tt>edge</tt> is still potential.
     * @see #isPotentialShip(int)
     * @since 1.2.00
     */
    public boolean isPotentialShip(final int edge, final int ignoreShipEdge)
    {
        if (! potentialShips.contains(new Integer(edge)))
            return false;

        final SOCBoard board = game.getBoard();
        final int[] edgeNodes = board.getAdjacentNodesToEdge_arr(edge);

        SOCPlayingPiece pp = board.settlementAtNode(edgeNodes[0]);
        if ((pp != null) && (pp.getPlayerNumber() != playerNumber))
            pp = null;
        if ((pp != null)
            || doesTradeRouteContinuePastNode
                 (board, edge, ignoreShipEdge, edgeNodes[0]))
            return true;

        pp = board.settlementAtNode(edgeNodes[1]);
        if ((pp != null) && (pp.getPlayerNumber() != playerNumber))
            pp = null;
        if ((pp != null)
            || doesTradeRouteContinuePastNode
                 (board, edge, ignoreShipEdge, edgeNodes[1]))
            return true;

        return false;
    }

    /**
     * @return true if this edge is a potential ship;
     *   if not {@link SOCGame#hasSeaBoard}, always returns false
     *   because the player has no potential ship locations.
     * @param edge  the coordinates of an edge on the board
     * @see #isPotentialShip(int, int)
     * @since 1.2.00
     */
    public boolean isPotentialShip(int edge)
    {
        return potentialShips.contains(new Integer(edge));
    }

    /**
     * Set this edge to not be a potential ship.
     * For use (by robots) when the server denies our request to build at a certain spot.
     *
     * @param node  coordinates of a an edge on the board
     * @see #isPotentialRoad(int)
     * @since 1.2.00
     */
    public void clearPotentialShip(int edge)
    {
        potentialShips.remove(new Integer(edge));
    }

    /**
     * Is this edge a legal ship placement?
     * @return true if this edge is a legal ship
     * @param edge        the coordinates of an edge on the board
     * @since 1.2.00
     */
    public boolean isLegalShip(final int edge)
    {
        if (edge < 0)
            return false;
        return legalShips.contains(new Integer(edge));
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
     * @since 1.2.00
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
     * @throws IllegalStateException if gameState is past initial placement (> {@link #START2B})
     */
    public boolean canBuildInitialPieceType(final int pieceType)
        throws IllegalStateException
    {
        if (game.getGameState() > SOCGame.START2B)
            throw new IllegalStateException();

        final int pieceCount = pieces.size();
        if (pieceCount >= 4)
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
        Stack pending = new Stack();
        int longest = 0;

        for (Enumeration e = roadNodes.elements(); e.hasMoreElements();)
        {
            final int pathStartNodeCoord = ((Integer) e.nextElement()).intValue();
            pending.push(new NodeLenVis(pathStartNodeCoord, 0, new Vector()));

            while (! pending.isEmpty())
            {
                NodeLenVis curNode = (NodeLenVis) pending.pop();
                final int coord = curNode.node;
                final int len = curNode.len;
                Vector visited = curNode.vis;
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

                            for (Enumeration ev = visited.elements();
                                    ev.hasMoreElements();)
                            {
                                IntPair vis = (IntPair) ev.nextElement();

                                if (vis.equals(pair))
                                {
                                    match = true;
                                    break;
                                }
                            }

                            if (! match)
                            {
                                Vector newVis = (Vector) visited.clone();
                                newVis.addElement(pair);
                                pending.push(new NodeLenVis(j, len + 1, newVis, roadFromNode));
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
                    Vector trash = new Vector();

                    for (Enumeration pdEnum = lrPaths.elements();
                            pdEnum.hasMoreElements();)
                    {
                        SOCLRPathData oldPathData = (SOCLRPathData) pdEnum.nextElement();
                        //D.ebugPrintln("oldPathData = " + oldPathData);

                        Vector nodePairs = oldPathData.getNodePairs();
                        intersection = false;

                        for (Enumeration ev = visited.elements();
                                ev.hasMoreElements();)
                        {
                            IntPair vis = (IntPair) ev.nextElement();
                            //D.ebugPrintln("vis = " + vis);

                            for (Enumeration npev = nodePairs.elements();
                                    npev.hasMoreElements();)
                            {
                                IntPair np = (IntPair) npev.nextElement();
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
                        for (Enumeration trashEnum = trash.elements();
                                trashEnum.hasMoreElements();)
                        {
                            SOCLRPathData oldPathData = (SOCLRPathData) trashEnum.nextElement();
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
            potentialRoads.clear();
            potentialRoads = null;
            potentialSettlements.clear();
            potentialSettlements = null;
            potentialCities.clear();
            potentialCities = null;
        }
        currentOffer = null;
    }
}
