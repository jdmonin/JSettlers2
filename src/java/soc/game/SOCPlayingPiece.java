/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009-2012 Jeremy D Monin <jeremy@nand.net>
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.game;

import java.io.Serializable;
import java.util.Vector;


/**
 * Playing pieces for Settlers of Catan.
 * For the resources needed to build a piece type, see {@link #getResourcesToBuild(int)}.
 */
public abstract class SOCPlayingPiece implements Serializable, Cloneable
{
    /**
     * Types of playing pieces: {@link SOCRoad Road}.
     * @see #getResourcesToBuild(int)
     */
    public static final int ROAD = 0;

    /**
     * Types of playing pieces: {@link SOCSettlement Settlement}.
     * @see #getResourcesToBuild(int)
     */
    public static final int SETTLEMENT = 1;

    /**
     * Types of playing pieces: {@link SOCCity City}.
     * @see #getResourcesToBuild(int)
     */
    public static final int CITY = 2;

    /**
     * Types of playing pieces: Ship.
     * Used only when {@link SOCGame#hasSeaBoard}.
     * Requires client and server verson 2.0.00 or newer.
     * @see #getResourcesToBuild(int)
     * @since 2.0.00
     */
    public static final int SHIP = 3;

    /**
     * Minimum type number of playing piece (currently Road).
     */
    public static final int MIN = 0;

    /**
     * One past the maximum type number of playing piece.
     * MAXPLUSONE == 3 up through version 1.1.13.
     * MAXPLUSONE == 4 in v2.0.00.
     */
    public static final int MAXPLUSONE = 4;

    /**
     * The type of this playing piece, within range {@link #MIN} to ({@link #MAXPLUSONE} - 1)
     */
    protected int pieceType;

    /**
     * The player who owns this piece
     */
    protected SOCPlayer player;

    /**
     * Where this piece is on the board
     */
    protected int coord;

    /**
     * Board, for coordinate-related operations. Should be from same game as {@link #player}.
     * @since 1.1.08
     */
    protected SOCBoard board;

    /**
     * Make a new piece.
     *
     * @param ptype  the type of piece, such as {@link #SETTLEMENT}
     * @param pl  player who owns the piece
     * @param co  coordinates
     * @param pboard  board if known; otherwise will extract from <tt>pl</tt>.
     *               Board should be from same game as <tt>pl</tt>.
     * @throws IllegalArgumentException  if <tt>pl</tt> null, or board null and <tt>pl.board</tt> also null
     * @since 1.1.08
     */
    protected SOCPlayingPiece(final int ptype, SOCPlayer pl, final int co, SOCBoard pboard)
        throws IllegalArgumentException
    {
        if (pl == null)
            throw new IllegalArgumentException("player null");
        pieceType = ptype;
        player = pl;
        coord = co;
        if (pboard == null)
        {
            pboard = pl.getGame().getBoard();
            if (pboard == null)
                throw new IllegalArgumentException("player has null board");
        }
        board = pboard;       
    }

    /**
     * Which edges touch this piece's node on the board?
     * Should not be called for roads, because they aren't placed at a node.
     * @return edges touching this piece, same format as {@link SOCBoard#getAdjacentEdgesToNode(int)}
     */
    public Vector getAdjacentEdges()
    {
        return board.getAdjacentEdgesToNode(coord);
    }

    /**
     * @return  the type of piece, such as {@link SOCPlayingPiece#ROAD}
     */
    public int getType()
    {
        return pieceType;
    }

    /**
     * @return the owner of the piece
     * @see #getPlayerNumber()
     */
    public SOCPlayer getPlayer()
    {
        return player;
    }

    /**
     * Get the owner's player number.
     * @return {@link #getPlayer()}.{@link SOCPlayer#getPlayerNumber() getPlayerNumber()}
     * @since 2.0.00
     */
    public int getPlayerNumber()
    {
        return player.getPlayerNumber();
    }

    /**
     * @return the node or edge coordinate for this piece
     */
    public int getCoordinates()
    {
        return coord;
    }

    /**
     * @return a human readable form of this object
     */
    public String toString()
    {
        String s = "SOCPlayingPiece:type=" + pieceType + "|player=" + player + "|coord=" + Integer.toHexString(coord);

        return s;
    }
    
    /**
     * Compare this SOCPlayingPiece to another SOCPlayingPiece, or another object.
     * Comparison method:
     * <UL>
     * <LI> If other is null, false.
     * <LI> If other is not a SOCPlayingPiece, use our super.equals to compare.
     * <LI> SOCPlayingPieces are equal if same piece type, coordinate, and player.
     * </UL>
     * 
     * @param other The object to compare with, or null.
     */
    public boolean equals(Object other)
    {
        if (other == null)
            return false;
        if (! (other instanceof SOCPlayingPiece))
            return super.equals(other);
        return ((this.pieceType == ((SOCPlayingPiece) other).pieceType)
            &&  (this.coord == ((SOCPlayingPiece) other).coord)
            &&  (this.player == ((SOCPlayingPiece) other).player));

        // board is based on player; no need to check board too.
    }

    /**
     * the set of resources a player needs to build a playing piece.
     * @param pieceType The type of this playing piece, in range {@link #MIN} to ({@link #MAXPLUSONE} - 1).
     *           {@link #ROAD}, {@link #CITY}, etc.
     *           Can also pass -2 or {@link #MAXPLUSONE} for {@link SOCGame#CARD_SET}.
     * @return the set, such as {@link SOCGame#SETTLEMENT_SET}
     * @throws IllegalArgumentException if <tt>pieceType</tt> is out of range
     * @since 1.1.08
     */
    public static SOCResourceSet getResourcesToBuild(final int pieceType)
        throws IllegalArgumentException 
    {
        switch (pieceType)
        {
        case ROAD:
            return SOCGame.ROAD_SET;
        case SETTLEMENT:
            return SOCGame.SETTLEMENT_SET;
        case CITY:
            return SOCGame.CITY_SET;
        case SHIP:
            return SOCGame.SHIP_SET;
        case -2:  // == SOCPossiblePiece.CARD (robots)
            // fall through
        case SOCPlayingPiece.MAXPLUSONE:
            return SOCGame.CARD_SET;
        default:
            throw new IllegalArgumentException("pieceType: " + pieceType);
        }
    }
}
