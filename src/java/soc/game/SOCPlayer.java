/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2011 Jeremy D Monin <jeremy@nand.net>
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
 **/
package soc.game;

import soc.disableDebug.D;

import soc.message.SOCMessage;
import soc.util.IntPair;
import soc.util.NodeLenVis;

import java.io.Serializable;

import java.util.Enumeration;
import java.util.Hashtable;
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
     * the number of pieces not in play
     */
    private int[] numPieces;

    /**
     * a list of this player's pieces in play
     */
    private Vector pieces;

    /**
     * a list of this player's roads in play
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
     * The coordinates of our most recent settlement
     */
    protected int lastSettlementCoord;

    /**
     * The coordinates of our most recent road
     */
    protected int lastRoadCoord;

    /**
     * length of the longest road for this player
     */
    private int longestRoadLength;

    /**
     * list of longest paths
     */
    private Vector lrPaths;

    /**
     * how many of each resource this player has
     */
    private SOCResourceSet resources;

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
     * all of the nodes that this player's roads touch
     * this is used to calculate longest road
     */
    private Vector roadNodes;

    /**
     * a graph of what nodes are connected by this
     * player's roads
     */
    private boolean[][] roadNodeGraph;

    /**
     * a list of edges where it is legal to place a road.
     * an edge is legal if a road could eventually be
     * placed there.
     */
    private boolean[] legalRoads;

    /**
     * a list of nodes where it is legal to place a
     * settlement.        a node is legal if a settlement
     * could eventually be placed there.
     * @see #potentialSettlements
     * @see SOCBoard#nodesOnBoard
     */
    private boolean[] legalSettlements;

    /**
     * a list of edges where a road could be placed
     * on the next turn.
     * At start of the game, this is clear/empty.
     * Elements are set true when the player places adjacent settlements or roads, via
     * {@link #updatePotentials(SOCPlayingPiece)}.
     */
    private boolean[] potentialRoads;

    /**
     * a list of nodes where a settlement could be
     * placed on the next turn.
     * At start of the game, all {@link #legalSettlements} are also potential.
     * When the second settlement is placed, this is cleared,
     * and then re-set via {@link #updatePotentials(SOCPlayingPiece) updatePotentials(SOCRoad)}.
     * @see #legalSettlements
     * @see SOCBoard#nodesOnBoard
     */
    private boolean[] potentialSettlements;

    /**
     * a list of nodes where a city could be
     * placed on the next turn.
     */
    private boolean[] potentialCities;

    /**
     * a boolean array stating wheather this player is touching a
     * particular kind of port.
     * Index == port type, in range {@link SOCBoard#MISC_PORT} to {@link SOCBoard#WOOD_PORT}
     */
    private boolean[] ports;

    /**
     * this is the current trade offer that this player is making
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
        numPieces = new int[SOCPlayingPiece.MAXPLUSONE];
        numPieces[SOCPlayingPiece.ROAD] = player.numPieces[SOCPlayingPiece.ROAD];
        numPieces[SOCPlayingPiece.SETTLEMENT] = player.numPieces[SOCPlayingPiece.SETTLEMENT];
        numPieces[SOCPlayingPiece.CITY] = player.numPieces[SOCPlayingPiece.CITY];
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
        roadNodeGraph = new boolean[SOCBoard.MAXNODEPLUSONE][SOCBoard.MAXNODEPLUSONE];

        final int minNode = player.getGame().getBoard().getMinNode();
        for (i = minNode; i < SOCBoard.MAXNODEPLUSONE; i++)
        {
            System.arraycopy(player.roadNodeGraph[i], minNode,
                roadNodeGraph[i], minNode, SOCBoard.MAXNODEPLUSONE - minNode);

            // for (j = minNode; j < SOCBoard.MAXNODEPLUSONE; j++)
            //    roadNodeGraph[i][j] = player.roadNodeGraph[i][j];
        }

        /**
         * init legal and potential arrays
         */
        legalRoads = new boolean[0xEF];
        legalSettlements = new boolean[0xFF];
        potentialRoads = new boolean[0xEF];
        potentialSettlements = new boolean[0xFF];
        potentialCities = new boolean[0xFF];

        for (i = 0; i < 0xEF; i++)
        {
            legalRoads[i] = player.legalRoads[i];
            potentialRoads[i] = player.potentialRoads[i];
        }

        for (i = 0; i < 0xFF; i++)
        {
            legalSettlements[i] = player.legalSettlements[i];
            potentialSettlements[i] = player.potentialSettlements[i];
            potentialCities[i] = player.potentialCities[i];
        }

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
        int j;

        game = ga;
        playerNumber = pn;
        numPieces = new int[SOCPlayingPiece.MAXPLUSONE];
        numPieces[SOCPlayingPiece.ROAD] = 15;
        numPieces[SOCPlayingPiece.SETTLEMENT] = 5;
        numPieces[SOCPlayingPiece.CITY] = 4;
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
        ourNumbers = new SOCPlayerNumbers(board.getBoardEncodingFormat());

        // buildingSpeed = new SOCBuildingSpeedEstimate(this);
        ports = new boolean[SOCBoard.WOOD_PORT + 1];

        for (i = SOCBoard.MISC_PORT; i <= SOCBoard.WOOD_PORT; i++)
        {
            ports[i] = false;
        }

        roadNodes = new Vector(20);
        roadNodeGraph = new boolean[SOCBoard.MAXNODEPLUSONE][SOCBoard.MAXNODEPLUSONE];

        final int minNode = board.getMinNode();
        for (i = minNode; i < SOCBoard.MAXNODEPLUSONE; i++)
        {
            for (j = minNode; j < SOCBoard.MAXNODEPLUSONE; j++)
            {
                roadNodeGraph[i][j] = false;
            }
        }

        /**
         * init legal and potential arrays
         */
        potentialRoads = new boolean[0xEF];
        potentialCities = new boolean[0xFF];

        for (i = 0; i < 0xEF; i++)
        {
            potentialRoads[i] = false;
        }

        for (i = 0; i < 0xFF; i++)
        {
            potentialCities[i] = false;
        }

        legalRoads = board.initPlayerLegalRoads();
        legalSettlements = board.initPlayerLegalAndPotentialSettlements();
        potentialSettlements = new boolean[legalSettlements.length];
        System.arraycopy(legalSettlements, 0, potentialSettlements, 0, legalSettlements.length);

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
        int i;

        for (i = 0; i < 0xFF; i++)
        {
            potentialSettlements[i] = false;
        }
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
     * @return the number of pieces not in play for a particualr type of piece
     *
     * @param ptype the type of piece
     */
    public int getNumPieces(int ptype)
    {
        return numPieces[ptype];
    }

    /**
     * set the amount of pieces not in play
     * for a particular type of piece
     *
     * @param ptype         the type of piece
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
     * @return the list of roads in play
     */
    public Vector getRoads()
    {
        return roads;
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
     * @return the coordinates of the last settlement
     * played by this player
     */
    public int getLastSettlementCoord()
    {
        return lastSettlementCoord;
    }

    /**
     * @return the coordinates of the last road
     * played by this player
     */
    public int getLastRoadCoord()
    {
        return lastRoadCoord;
    }

    /**
     * @return the longest road length
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
     * set the longest road length
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
    public boolean hasUnplayedDevCards()  // hasUnplayedDevCards
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
     * @return true if this player has the longest road
     */
    public boolean hasLongestRoad()
    {
        if (game.getPlayerWithLongestRoad() == null)
        {
            return false;
        }
        else
        {
            return (game.getPlayerWithLongestRoad().getPlayerNumber() == this.getPlayerNumber());
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
            return (game.getPlayerWithLargestArmy().getPlayerNumber() == this.getPlayerNumber());
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
     * @return the list of nodes that touch the roads in play
     */
    public Vector getRoadNodes()
    {
        return roadNodes;
    }

    /**
     * @return this player's latest offer
     */
    public SOCTradeOffer getCurrentOffer()
    {
        return currentOffer;
    }

    /**
     * set the current offer for this player
     *
     * @param of        the offer
     */
    public void setCurrentOffer(SOCTradeOffer of)
    {
        currentOffer = of;
    }

    /**
     * @return true if one of this player's roads connects
     *                                 the two nodes.
     *
     * @param node1         coordinates of first node
     * @param node2         coordinates of second node
     */
    public boolean isConnectedByRoad(int node1, int node2)
    {
        //D.ebugPrintln("isConnectedByRoad "+Integer.toHexString(node1)+", "+Integer.toHexString(node2)+" = "+roadNodeGraph[node1][node2]);
        return roadNodeGraph[node1][node2];
    }

    /**
     * put a piece into play
     * note: placing a city automatically removes the settlement there
     *
     * @param piece         the piece to be put into play; coordinates are not checked for validity
     */
    public void putPiece(SOCPlayingPiece piece)
    {
        /**
         * only do this stuff if it's our piece
         */
        if (piece.getPlayer().getPlayerNumber() == this.getPlayerNumber())
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
                roads.addElement(piece);
                lastRoadCoord = piece.getCoordinates();

                /**
                 * add the nodes this road touches to the roadNodes list
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
                 * update the graph of nodes connected by roads
                 */
                roadNodeGraph[nodeCoords[0]][nodeCoords[1]] = true;
                roadNodeGraph[nodeCoords[1]][nodeCoords[0]] = true;

                //D.ebugPrintln("^^ roadNodeGraph["+Integer.toHexString(nodeCoords[0])+"]["+Integer.toHexString(nodeCoords[1])+"] = true");
                //D.ebugPrintln("^^ roadNodeGraph["+Integer.toHexString(nodeCoords[1])+"]["+Integer.toHexString(nodeCoords[0])+"] = true");
                break;

            /**
             * placing a settlement
             */
            case SOCPlayingPiece.SETTLEMENT:
                numPieces[SOCPlayingPiece.SETTLEMENT]--;
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
                 * update what numbers we're touching
                 * a city counts as touching a number twice
                 */
                ourNumbers.updateNumbers(piece, board);

                break;
            }
        }

        updatePotentials(piece);
    }

    /**
     * undo the putting of a piece.
     *<P>
     * Among other actions,
     * Updates the potential building lists
     * for removing settlements or cities.
     * Updates port flags, this player's dice resource numbers, etc.
     *<P>
     * If the piece is ours, calls {@link #removePiece(SOCPlayingPiece)}.
     *<P>
     * For removing second initial settlement (state START2B),
     *   will zero the player's resource cards. 
     *
     * @param piece         the piece placement to be undone.
     *
     */
    public void undoPutPiece(SOCPlayingPiece piece)
    {
        final boolean ours = (piece.getPlayer().getPlayerNumber() == this.getPlayerNumber());

        final SOCBoard board = game.getBoard();
        switch (piece.getType())
        {
        //
        // undo a played road
        //
        case SOCPlayingPiece.ROAD:

            if (ours)
            {
                //
                // update the potential places to build roads
                // 
                removePiece(piece);
            }
            else
            {
                //
                // not our road
                //
                // make it a legal space again
                //
                legalRoads[piece.getCoordinates()] = true;

                //
                // call updatePotentials
                // on our roads that are adjacent to 
                // this edge
                //
                Vector adjEdges = board.getAdjacentEdgesToEdge(piece.getCoordinates());
                Enumeration roadEnum = roads.elements();

                while (roadEnum.hasMoreElements())
                {
                    SOCRoad road = (SOCRoad) roadEnum.nextElement();
                    Enumeration edgeEnum = adjEdges.elements();

                    while (edgeEnum.hasMoreElements())
                    {
                        Integer edge = (Integer) edgeEnum.nextElement();

                        if (road.getCoordinates() == edge.intValue())
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
                final int portType = board.getPortTypeFromNodeCoord(piece.getCoordinates());
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
            undoPutPieceAuxSettlement(piece.getCoordinates());

            //
            // check adjacent nodes
            //
            Enumeration adjNodesEnum = board.getAdjacentNodesToNode(piece.getCoordinates()).elements();

            while (adjNodesEnum.hasMoreElements())
            {
                Integer adjNode = (Integer) adjNodesEnum.nextElement();
                undoPutPieceAuxSettlement(adjNode.intValue());
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
                potentialCities[piece.getCoordinates()] = true;

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
                Integer adjNode = (Integer) adjNodesEnum.nextElement();

                if (adjNode.intValue() == settlement.getCoordinates())
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
                    Integer adjNode = (Integer) adjNodesEnum.nextElement();

                    if (adjNode.intValue() == city.getCoordinates())
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
                if (board.isNodeOnBoard(settlementNode))
                {
                    legalSettlements[settlementNode] = true;

                    //D.ebugPrintln(")))) legalSettlements["+Integer.toHexString(settlementNode)+"] = true");
                    //
                    // if it's the beginning of the game, make it potental
                    //
                    if (game.getGameState() < SOCGame.PLAY)
                    {
                        potentialSettlements[settlementNode] = true;

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
                                Integer adjEdge = (Integer) adjEdgesEnum.nextElement();

                                if (road.getCoordinates() == adjEdge.intValue())
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
                            potentialSettlements[settlementNode] = true;

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
     * NOTE: Does NOT update the potential building lists
     *           for removing settlements or cities.
     *       DOES update potential road lists.
     *
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

            if ((piece.getType() == p.getType()) && (piece.getCoordinates() == p.getCoordinates()))
            {
                pieces.removeElement(p);

                switch (piece.getType())
                {
                case SOCPlayingPiece.ROAD:
                    roads.removeElement(p);
                    numPieces[SOCPlayingPiece.ROAD]++;

                    /**
                     * remove the nodes this road touches from the roadNodes list
                     */
                    Enumeration nodes = board.getAdjacentNodesToEdge(piece.getCoordinates()).elements();
                    int[] nodeCoords = new int[2];
                    int i = 0;

                    while (nodes.hasMoreElements())
                    {
                        Integer node = (Integer) nodes.nextElement();
                        nodeCoords[i] = node.intValue();
                        i++;

                        /**
                         * only remove nodes if none of our roads are touching it
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
                                Integer adjEdge = (Integer) adjEdgesEnum.nextElement();

                                if (adjEdge.intValue() == rd.getCoordinates())
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
                            potentialSettlements[node.intValue()] = false;
                        }
                    }

                    /**
                     * update the graph of nodes connected by roads
                     */
                    roadNodeGraph[nodeCoords[0]][nodeCoords[1]] = false;
                    roadNodeGraph[nodeCoords[1]][nodeCoords[0]] = false;

                    /**
                     * update the potential places to build roads
                     *
                     * NOTE: we're assuming that we could build a road here
                     * before, so we can make it a legal spot again
                     */
                    potentialRoads[piece.getCoordinates()] = true;
                    legalRoads[piece.getCoordinates()] = true;

                    /**
                     * check each adjacent legal edge, if there are
                     * no roads touching it, then it's no longer a
                     * potential road
                     */
                    Vector allPieces = board.getPieces();
                    Enumeration adjEdgesEnum = board.getAdjacentEdgesToEdge(piece.getCoordinates()).elements();

                    while (adjEdgesEnum.hasMoreElements())
                    {
                        Integer adjEdge = (Integer) adjEdgesEnum.nextElement();
                        final int adjEdgeID = adjEdge.intValue();

                        if (potentialRoads[adjEdgeID])
                        {
                            boolean isPotentialRoad = false;

                            /**
                             * check each adjacent node for blocking
                             * settlements or cities
                             */
                            final int[] adjNodes = SOCBoard.getAdjacentNodesToEdge_arr(adjEdgeID);

                            for (int ni = 0; (ni < 2) && ! isPotentialRoad; ++ni) 
                            {
                                boolean blocked = false;  // Are we blocked in this node's direction?
                                final int adjNode = adjNodes[ni];
                                Enumeration allPiecesEnum = allPieces.elements();

                                while (allPiecesEnum.hasMoreElements())
                                {
                                    SOCPlayingPiece aPiece = (SOCPlayingPiece) allPiecesEnum.nextElement();

                                    if ((aPiece.getCoordinates() == adjNode) && (aPiece.getPlayer().getPlayerNumber() != this.getPlayerNumber()) && ((aPiece.getType() == SOCPlayingPiece.SETTLEMENT) || (aPiece.getType() == SOCPlayingPiece.CITY)))
                                    {
                                        /**
                                         * we're blocked, don't bother checking adjacent edges
                                         */
                                        blocked = true;

                                        break;
                                    }
                                }

                                if (!blocked)
                                {
                                    Enumeration adjAdjEdgesEnum = board.getAdjacentEdgesToNode(adjNode).elements();

                                    while ((adjAdjEdgesEnum.hasMoreElements()) && (isPotentialRoad == false))
                                    {
                                        Integer adjAdjEdge = (Integer) adjAdjEdgesEnum.nextElement();

                                        if (adjAdjEdge.intValue() != adjEdgeID)
                                        {
                                            Enumeration ourRoadsEnum = roads.elements();

                                            while (ourRoadsEnum.hasMoreElements())
                                            {
                                                SOCRoad ourRoad = (SOCRoad) ourRoadsEnum.nextElement();

                                                if (ourRoad.getCoordinates() == adjAdjEdge.intValue())
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

                            potentialRoads[adjEdgeID] = isPotentialRoad;
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
     * piece has just been played.
     *
     * @param piece         a piece that has just been played
     */
    public void updatePotentials(SOCPlayingPiece piece)
    {
        //D.ebugPrintln("&&& UPDATING POTENTIALS FOR "+piece);
        int tmp;
        final boolean ours;
        boolean blocked;
        final int id = piece.getCoordinates();
        SOCBoard board = game.getBoard();
        Vector allPieces = board.getPieces();

        /**
         * check if this piece is ours
         */
        ours = (piece.getPlayer().getPlayerNumber() == this.getPlayerNumber());

        switch (piece.getType())
        {
        /**
         * a road was played
         */
        case SOCPlayingPiece.ROAD:

            // remove non-potentials
            potentialRoads[id] = false;
            legalRoads[id] = false;

            if (ours)
            {
                // only add potentials if it's our piece
                final int[] nodes = SOCBoard.getAdjacentNodesToEdge_arr(id);

                for (int ni = 0; ni < 2; ++ni)
                {
                    final int node = nodes[ni];

                    /**
                     * check for a foreign settlement or city
                     */
                    blocked = false;

                    Enumeration pEnum = allPieces.elements();

                    while (pEnum.hasMoreElements())
                    {
                        SOCPlayingPiece p = (SOCPlayingPiece) pEnum.nextElement();

                        if ((p.getCoordinates() == node) && (p.getPlayer().getPlayerNumber() != this.getPlayerNumber()) && ((p.getType() == SOCPlayingPiece.SETTLEMENT) || (p.getType() == SOCPlayingPiece.CITY)))
                        {
                            blocked = true;

                            break;
                        }
                    }

                    if (!blocked)
                    {
                        int[] edges = board.getAdjacentEdgesToNode_arr(node);
                        for (int i = 0; i < 3; ++i)
                        {
                            int edge = edges[i];
                            if ((edge != -1) && legalRoads[edge])
                                potentialRoads[edge] = true;
                        }

                        if (legalSettlements[node])
                        {
                            potentialSettlements[node] = true;
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
            potentialSettlements[id] = false;
            legalSettlements[id] = false;
            int[] adjac = board.getAdjacentNodesToNode_arr(id);
            for (int i = 0; i < 3; ++i)
            {
                if (adjac[i] != -1)
                {
                    potentialSettlements[adjac[i]] = false;
                    legalSettlements[adjac[i]] = false;
                }
            }

            // if it's our piece, add potential roads and city.
            // otherwise, check for cutoffs of our potential roads by this piece.

            if (ours)
            {
                potentialCities[id] = true;

                adjac = board.getAdjacentEdgesToNode_arr(id);
                for (int i = 0; i < 3; ++i)
                {
                    tmp = adjac[i];
                    if ((tmp != -1) && legalRoads[tmp])
                        potentialRoads[tmp] = true;
                }
            }
            else
            {
                // see if a nearby potential road has been cut off:
                // build vector of our road edge IDs placed so far.
                // for each of 3 adjacent edges to node:
                //  if we have potentialRoad(edge)
                //    check ourRoads vs that edge's far-end (away from node of new settlement)
                //    unless we have a road on far-end, this edge is no longer potential,
                //      because we're not getting past opponent's new settlement (on this end
                //      of the edge) to build it.

                Hashtable ourRoads = new Hashtable();  // TODO more efficient way of looking this up, with fewer temp objs
                Object hashDummy = new Object();   // a value is needed for hashtable
                Enumeration pEnum = (this.pieces).elements();
                while (pEnum.hasMoreElements())
                {
                    SOCPlayingPiece p = (SOCPlayingPiece) pEnum.nextElement();
                    if (p.getType() == SOCPlayingPiece.ROAD)
                        ourRoads.put(new Integer(p.getCoordinates()), hashDummy);
                }

                adjac = board.getAdjacentEdgesToNode_arr(id);
                for (int i = 0; i < 3; ++i)
                {
                    tmp = adjac[i];  // edge coordinate
                    if ((tmp == -1) || ! potentialRoads[tmp])
                    {
                        continue;  // We don't have a potential road here, so
                                   // there's nothing to be potentially broken.
                    }

                    // find the far-end node coordinate
                    final int farNode;
                    {
                        final int[] enodes = SOCBoard.getAdjacentNodesToEdge_arr(tmp);
                        if (enodes[0] == id)
                            farNode = enodes[1];
                        else
                            farNode = enodes[0];
                    }

                    // now find the 2 other edges from that node;
                    // we may have actual roads on them already.
                    // If so, we'll still be able to get to the edge (tmp)
                    // that touches the new settlement's node.

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
                        potentialRoads[tmp] = false;
                    }
                }
            }

            break;

        /**
         * a city was placed
         */
        case SOCPlayingPiece.CITY:

            // remove non-potentials
            potentialCities[id] = false;

            break;
        }
    }

    /**
     * set which nodes are potential settlements
     *
     * @param psList        the list of potential settlements
     */
    public void setPotentialSettlements(Vector psList)
    {
        clearPotentialSettlements();

        Enumeration settlementEnum = psList.elements();

        while (settlementEnum.hasMoreElements())
        {
            Integer number = (Integer) settlementEnum.nextElement();
            potentialSettlements[number.intValue()] = true;
        }
    }

    /**
     * @return true if this node is a potential settlement
     * @param node        the coordinates of a node on the board
     */
    public boolean isPotentialSettlement(int node)
    {
        return potentialSettlements[node];
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
        potentialSettlements[node] = false;
    }

    /**
     * @return true if this node is a potential city
     * @param node        the coordinates of a node on the board
     */
    public boolean isPotentialCity(int node)
    {
        return potentialCities[node];
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
        potentialSettlements[node] = false;
    }

    /**
     * @return true if this edge is a potential road
     * @param edge        the coordinates of an edge on the board. Accepts -1 for edge 0x00.
     */
    public boolean isPotentialRoad(int edge)
    {
        if (edge == -1)
            edge = 0x00;
        return potentialRoads[edge];
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
        potentialRoads[edge] = false;
    }

    /**
     * @return true if this edge is a legal road
     * @param edge        the coordinates of an edge on the board. Accepts -1 for edge 0x00.
     */
    public boolean isLegalRoad(int edge)
    {
        if (edge == -1)
            edge = 0x00;
        return legalRoads[edge];
    }

    /**
     * @return true if there is at least one potential road
     */
    public boolean hasPotentialRoad()
    {
        // TODO efficiency; maybe a count variable instead?
        for (int i = game.getBoard().getMinNode(); i <= SOCBoard.MAXNODE; i++)
        {
            if (potentialRoads[i])
            {
                return true;
            }
        }

        return false;
    }

    /**
     * @return true if there is at least one potential settlement
     */
    public boolean hasPotentialSettlement()
    {
        // TODO efficiency; maybe a count variable instead?
        for (int i = game.getBoard().getMinNode(); i <= SOCBoard.MAXNODE; i++)
        {
            if (potentialSettlements[i])
            {
                return true;
            }
        }

        return false;
    }

    /**
     * @return true if there is at least one potential city
     */
    public boolean hasPotentialCity()
    {
        // TODO efficiency; maybe a count variable instead?
        for (int i = game.getBoard().getMinNode(); i <= SOCBoard.MAXNODE; i++)
        {
            if (potentialCities[i])
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Calculates the longest road for this player
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
        D.ebugPrintln("CLEARING PATH DATA");
        lrPaths.removeAllElements();

        /**
         * we're doing a depth first search of all possible road paths
         */
        SOCBoard board = game.getBoard();
        Stack pending = new Stack();
        int longest = 0;

        for (Enumeration e = roadNodes.elements(); e.hasMoreElements();)
        {
            Integer roadNode = (Integer) e.nextElement();
            int pathStartCoord = roadNode.intValue();
            pending.push(new NodeLenVis(pathStartCoord, 0, new Vector()));

            while (!pending.isEmpty())
            {
                NodeLenVis curNode = (NodeLenVis) pending.pop();
                final int coord = curNode.node;
                final int len = curNode.len;
                Vector visited = curNode.vis;
                boolean pathEnd = false;

                /**
                 * check for road blocks
                 */
                if (len > 0)
                {
                    Enumeration pEnum = board.getPieces().elements();
    
                    while (pEnum.hasMoreElements())
                    {
                        SOCPlayingPiece p = (SOCPlayingPiece) pEnum.nextElement();
    
                        if ((p.getCoordinates() == coord)
                            && (p.getPlayer().getPlayerNumber() != playerNumber)
                            && ((p.getType() == SOCPlayingPiece.SETTLEMENT) || (p.getType() == SOCPlayingPiece.CITY)))
                        {
                            pathEnd = true;
    
                            //D.ebugPrintln("^^^ path end at "+Integer.toHexString(coord));
                            break;
                        }
                    }
                }

                if (!pathEnd)
                {
                    /**
                     * Check if this road path continues to adjacent connected nodes.
                     */

                    pathEnd = true;  // may be set false in loop

                    final int[] adjacNodes = board.getAdjacentNodesToNode_arr(coord);
                    for (int ni = adjacNodes.length - 1; ni>=0; --ni)
                    {
                        final int j = adjacNodes[ni];
                        if (j == -1)
                            continue;

                        if (board.isNodeOnBoard(j) && isConnectedByRoad(coord, j))
                        {
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

                            if (!match)
                            {
                                Vector newVis = (Vector) visited.clone();
                                newVis.addElement(pair);
                                pending.push(new NodeLenVis(j, len + 1, newVis));
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
                        D.ebugPrintln("oldPathData = " + oldPathData);

                        Vector nodePairs = oldPathData.getNodePairs();
                        intersection = false;

                        for (Enumeration ev = visited.elements();
                                ev.hasMoreElements();)
                        {
                            IntPair vis = (IntPair) ev.nextElement();
                            D.ebugPrintln("vis = " + vis);

                            for (Enumeration npev = nodePairs.elements();
                                    npev.hasMoreElements();)
                            {
                                IntPair np = (IntPair) npev.nextElement();
                                D.ebugPrintln("np = " + np);

                                if (np.equals(vis))
                                {
                                    D.ebugPrintln("oldPathData.nodePairs.contains(vis)");
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
                                D.ebugPrintln("REMOVING OLDPATHDATA");
                                trash.addElement(oldPathData);
                            }
                            else
                            {
                                addNewPath = false;
                                D.ebugPrintln("NOT ADDING NEW PATH");
                            }
                        }
                    }

                    if (!trash.isEmpty())
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
                        SOCLRPathData newPathData = new SOCLRPathData(pathStartCoord, coord, len, visited);
                        D.ebugPrintln("ADDING PATH: " + newPathData);
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

    /** TODO this constructor is unused; is it worth maintaining? is it missing fields?
     * @return a copy of this player
     */
    public SOCPlayer copy()
    {
        SOCPlayer copy = new SOCPlayer(this.getPlayerNumber(), game);
        SOCBoard board = game.getBoard();

        if (game.getGameState() >= SOCGame.START2B)
        {
            copy.clearPotentialSettlements();
        }

        /**
         * copy all of the pieces that have been played by all players
         * we need to get all pieces so that we have an accurate potential
         * building map
         */
        for (int pnum = 0; pnum < game.maxPlayers; pnum++)
        {
            if (pnum != this.getPlayerNumber())
            {
                Enumeration piecesEnum = game.getPlayer(pnum).getPieces().elements();

                while (piecesEnum.hasMoreElements())
                {
                    SOCPlayingPiece piece = (SOCPlayingPiece) piecesEnum.nextElement();
                    SOCPlayer owner = game.getPlayer(pnum);

                    switch (piece.getType())
                    {
                    case SOCPlayingPiece.ROAD:
                        copy.putPiece(new SOCRoad(owner, piece.getCoordinates(), board));

                        break;

                    case SOCPlayingPiece.SETTLEMENT:
                        copy.putPiece(new SOCSettlement(owner, piece.getCoordinates(), board));

                        break;

                    case SOCPlayingPiece.CITY:

                        /**
                         * if it's a city, put down a settlement first in order to
                         * get the proper potential settlement list and number list
                         */
                        if (piece.getType() == SOCPlayingPiece.CITY)
                        {
                            SOCSettlement temp = new SOCSettlement(owner, piece.getCoordinates(), board);
                            copy.putPiece(temp);
                            copy.removePiece(temp);
                        }

                        copy.putPiece(new SOCCity(owner, piece.getCoordinates(), board));

                        break;
                    }
                }
            }
            else
            {
                Enumeration piecesEnum = this.getPieces().elements();

                while (piecesEnum.hasMoreElements())
                {
                    SOCPlayingPiece piece = (SOCPlayingPiece) piecesEnum.nextElement();
                    SOCPlayer owner = copy;

                    switch (piece.getType())
                    {
                    case SOCPlayingPiece.ROAD:
                        copy.putPiece(new SOCRoad(owner, piece.getCoordinates(), board));

                        break;

                    case SOCPlayingPiece.SETTLEMENT:
                        copy.putPiece(new SOCSettlement(owner, piece.getCoordinates(), board));

                        break;

                    case SOCPlayingPiece.CITY:

                        /**
                         * if it's a city, put down a settlement first in order to
                         * get the proper potential settlement list and number list
                         */
                        if (piece.getType() == SOCPlayingPiece.CITY)
                        {
                            SOCSettlement temp = new SOCSettlement(owner, piece.getCoordinates(), board);
                            copy.putPiece(temp);
                            copy.removePiece(temp);
                        }

                        copy.putPiece(new SOCCity(owner, piece.getCoordinates(), board));

                        break;
                    }
                }
            }
        }

        /**
         * copy the resources
         */
        SOCResourceSet copyResources = copy.getResources();

        for (int rType = SOCResourceConstants.CLAY;
                rType <= SOCResourceConstants.UNKNOWN; rType++)
        {
            copyResources.setAmount(resources.getAmount(rType), rType);
        }
        copy.resourceStats = new int[resourceStats.length];
        System.arraycopy(resourceStats, 0, copy.resourceStats, 0, resourceStats.length);

        /**
         * copy the dev cards
         */
        SOCDevCardSet copyDevCards = getDevCards();

        for (int dcType = SOCDevCardConstants.KNIGHT;
                dcType <= SOCDevCardConstants.UNKNOWN; dcType++)
        {
            copyDevCards.setAmount(devCards.getAmount(SOCDevCardSet.OLD, dcType), SOCDevCardSet.OLD, dcType);
            copyDevCards.setAmount(devCards.getAmount(SOCDevCardSet.NEW, dcType), SOCDevCardSet.NEW, dcType);
        }

        /**
         * copy the army
         */
        copy.setNumKnights(numKnights);

        /**
         * copy port flags
         */
        for (int port = SOCBoard.MISC_PORT; port <= SOCBoard.WOOD_PORT;
                port++)
        {
            copy.setPortFlag(port, ports[port]);
        }

        /**
         * NEED TO COPY :
         *        currentOffer
         *        playedDevCard flag
         *        robotFlag
         *        faceId
         *        other recently added fields (TODO)
         */
        return copy;
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
        roadNodeGraph = null;
        legalRoads = null;
        legalSettlements = null;
        potentialRoads = null;
        potentialSettlements = null;
        potentialCities = null;
        currentOffer = null;
    }
}
