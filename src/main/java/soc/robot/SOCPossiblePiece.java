/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2011-2015,2017 Jeremy D Monin <jeremy@nand.net>
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
package soc.robot;

import soc.game.*;
import soc.message.SOCSetSpecialItem;  // strictly for javadocs

import java.util.Vector;


/**
 * Pieces that a player might build, or action (buy card) a player might take.
 * Used by {@link SOCRobotDM} for tracking and planning moves.
 *<P>
 * Also tracks threats (opponents' possible pieces) to each of our player's
 * possible pieces. Examples of threats are opponent roads on the same edge
 * as this road, settlements or cities that split a road, etc.
 *<P>
 * Although it's not a board piece type, {@link SOCPossibleCard} is a type here
 * because the player could buy them as part of a building plan.
 *
 * @author Robert S. Thomas
 */
public abstract class SOCPossiblePiece
{
    /**
     * Type constant for a possible road. Same value as {@link SOCPlayingPiece#ROAD}.
     */
    public static final int ROAD = 0;

    /**
     * Type constant for a possible settlement. Same value as {@link SOCPlayingPiece#SETTLEMENT}.
     */
    public static final int SETTLEMENT = 1;

    /**
     * Type constant for a possible city. Same value as {@link SOCPlayingPiece#CITY}.
     */
    public static final int CITY = 2;

    /**
     * Ship, for large sea board.
     * Same value as {@link SOCPlayingPiece#SHIP}.
     * @since 2.0.00
     */
    public static final int SHIP = 3;

    /**
     * Type constant for a possible card.
     * {@link #getCoordinates()} field is not used.
     * CARD is -2, was 4 before v2.0.00.
     */
    public static final int CARD = -2;

    /**
     * Type constant for {@link SOCSetSpecialItem#OP_PICK} requests, subclass {@link SOCPossiblePickSpecialItem}.
     * {@link #getCoordinates()} field is not used.
     * @since 2.0.00
     */
    public static final int PICK_SPECIAL = -3;

    /** MIN is -3 for {@link #PICK_SPECIAL}, but nothing currently uses -1. {@link #ROAD} is 0. */
    public static final int MIN = -3;
    public static final int MAXPLUSONE = 4;

    /**
     * The type of this playing piece; a constant
     *    such as {@link SOCPossiblePiece#ROAD}, {@link SOCPossiblePiece#CITY}, etc.
     *    The constant types are the same as in {@link SOCPlayingPiece#getResourcesToBuild(int)}.
     */
    protected int pieceType;

    /**
     * The player who owns this piece
     */
    protected SOCPlayer player;

    /**
     * Where this piece is on the board.
     * Some piece types such as {@link #CARD} do not use this field, it will be 0 if unused.
     * In some board layouts 0 is a valid coordinate; check the piece type to determine if unused.
     */
    protected int coord;

    /**
     * this is how soon we estimate we can build
     * this piece measured in turns (ETA)
     */
    protected int eta;

    /**
     * this is a flag used for updating
     */
    protected boolean updated;

    /**
     * this is a score used for deciding what to build next
     */
    protected float score;

    /**
     * this is the piece that we need to beat to build this one
     */
    protected Vector<SOCPossiblePiece> biggestThreats;

    /**
     * pieces that threaten this piece
     */
    protected Vector<SOCPossiblePiece> threats;

    /**
     * this flag is used for threat updating
     */
    protected boolean threatUpdatedFlag;

    /**
     * this flag is used for expansion
     */
    protected boolean hasBeenExpanded;

    /**
     * Construct a SOCPossiblePiece.
     * @param pt  Piece type: {@link #ROAD}, {@link #CARD}, etc.
     *     The type constants are the same as in {@link SOCPlayingPiece#getResourcesToBuild(int)}.
     * @param pl  The owner of this piece
     * @param coord  The coordinates for this piece, if any.
     *     Some piece types such as {@link #CARD} do not use this field, they call with {@code coord} == 0.
     * @since 2.0.00
     */
    protected SOCPossiblePiece(final int pt, final SOCPlayer pl, final int coord)
    {
        pieceType = pt;
        player = pl;
        this.coord = coord;
    }

    /**
     * @return  the type of piece; a constant
     *    such as {@link SOCPossiblePiece#ROAD}, {@link SOCPossiblePiece#CITY}, etc.
     *    The type constants are the same as in {@link SOCPlayingPiece#getResourcesToBuild(int)}.
     * @see #getResourcesToBuild()
     */
    public int getType()
    {
        return pieceType;
    }

    /**
     * @return the owner of this piece
     */
    public SOCPlayer getPlayer()
    {
        return player;
    }

    /**
     * Get this piece's coordinates on the board, if any.
     * Some piece types such as {@link #CARD} do not use this field.
     * @return the coordinates for this piece, or 0 if unused.
     *      In some board layouts 0 is a valid coordinate;
     *      check the piece type to determine if unused.
     */
    public int getCoordinates()
    {
        return coord;
    }

    /**
     * @return the ETA for this piece
     */
    public int getETA()
    {
        return eta;
    }

    /**
     * update the ETA for this piece
     *
     * @param e  the new ETA
     */
    public void setETA(int e)
    {
        eta = e;
        updated = true;
    }

    /**
     * @return the value of the ETA update flag
     */
    public boolean isETAUpdated()
    {
        return updated;
    }

    /**
     * clear the update flag
     */
    public void clearUpdateFlag()
    {
        updated = false;
    }

    /**
     * reset the score
     */
    public void resetScore()
    {
        score = 0;
    }

    /**
     * add to score, from {@link SOCRobotDM#getETABonus(int, int, float)}
     *
     * @param amt  the amount to add
     */
    public void addToScore(float amt)
    {
        score += amt;
    }

    /**
     * subtract from  score
     *
     * @param amt  the amount to subtract
     */
    public void subtractFromScore(float amt)
    {
        score -= amt;
    }

    /**
     * @return the ETA bonus score
     * @see SOCRobotDM#getETABonus(int, int, float)
     */
    public float getScore()
    {
        return score;
    }

    /**
     * reset the biggest threat
     */
    public void clearBiggestThreats()
    {
        biggestThreats.removeAllElements();
    }

    /**
     * set the biggest threat
     *
     * @param bt  the threat
     */
    public void addBiggestThreat(SOCPossiblePiece bt)
    {
        biggestThreats.addElement(bt);
    }

    /**
     * @return the biggest threat
     */
    public Vector<SOCPossiblePiece> getBiggestThreats()
    {
        return biggestThreats;
    }

    /**
     * Get the list of opponents' possible pieces that threaten this possible piece.
     * @return the list of threats
     */
    public Vector<SOCPossiblePiece> getThreats()
    {
        return threats;
    }

    /**
     * add a threat to the list, if not already there
     *
     * @param piece  Opponent's possible piece to add to this possible piece's threat list
     */
    public void addThreat(SOCPossiblePiece piece)
    {
        if (!threats.contains(piece))
        {
            threats.addElement(piece);
        }
    }

    /**
     * @return the status of the threatUpdatedFlag
     */
    public boolean isThreatUpdated()
    {
        return threatUpdatedFlag;
    }

    /**
     * clear the list of threats
     */
    public void clearThreats()
    {
        if (threatUpdatedFlag)
        {
            threats.removeAllElements();
            threatUpdatedFlag = false;
        }
    }

    /**
     * mark this piece as having been threat updated
     */
    public void threatUpdated()
    {
        threatUpdatedFlag = true;
    }

    /**
     * @return the status of the hasBeenExpanded flag
     */
    public boolean hasBeenExpanded()
    {
        return hasBeenExpanded;
    }

    /**
     * set hasBeenExpanded to false
     */
    public void resetExpandedFlag()
    {
        hasBeenExpanded = false;
    }

    /**
     * set hasBeenExpanded to true
     */
    public void setExpandedFlag()
    {
        hasBeenExpanded = true;
    }

    /**
     * Based on piece type ({@link #getType()}), the resources
     * a player needs to build or buy this possible piece.
     *<P>
     * Unlike {@link SOCPlayingPiece#getResourcesToBuild(int)}, this method handles
     * non-piece types which the bot may plan to build, such as {@link #PICK_SPECIAL}.
     *
     * @return  Set of resources, or {@code null} if no cost or if piece type unknown
     * @since 2.0.00
     */
    public SOCResourceSet getResourcesToBuild()
    {
        switch (pieceType)
        {
        case ROAD:
            return SOCRoad.COST;

        case SETTLEMENT:
            return SOCSettlement.COST;

        case CITY:
            return SOCCity.COST;

        case SHIP:
            return SOCShip.COST;

        case SOCPlayingPiece.MAXPLUSONE:
            // fall through
        case CARD:
            return SOCGame.CARD_SET;

        case PICK_SPECIAL:
            return ((SOCPossiblePickSpecialItem) this).cost;

        default:
            System.err.println
                ("SOCPossiblePiece.getResourcesToBuild: Unknown piece type " + pieceType);
            return null;
        }
    }

    /**
     * @return a human readable form of this object
     */
    @Override
    public String toString()
    {
        String clName;
        {
            clName = getClass().getName();
            int dot = clName.lastIndexOf(".");
            if (dot > 0)
                clName = clName.substring(dot + 1);
        }

        return "SOCPossiblePiece:" + clName + "|type=" + pieceType + "|player=" + player
            + "|coord=" + Integer.toHexString(coord);
    }

}
