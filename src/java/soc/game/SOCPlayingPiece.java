/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009-2010 Jeremy D Monin <jeremy@nand.net>
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
     * Types of playing pieces: Road.
     * @see #getResourcesToBuild(int)
     */
    public static final int ROAD = 0;

    /**
     * Types of playing pieces: Settlement.
     * @see #getResourcesToBuild(int)
     */
    public static final int SETTLEMENT = 1;

    /**
     * Types of playing pieces: City.
     * @see #getResourcesToBuild(int)
     */
    public static final int CITY = 2;

    /**
     * Minimum type number of playing piece (currently Road).
     */
    public static final int MIN = 0;

    /**
     * One past the maximum type number of playing piece.
     */
    public static final int MAXPLUSONE = 3;

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
     * Which edges touch this piece on the board?
     * @return edges touching this piece, same format as {@link SOCBoard#getAdjacentEdgesToNode(int)}
     */
    public Vector getAdjacentEdges()
    {
        return board.getAdjacentEdgesToNode(coord);
    }

    /**
     * @return  the type of piece
     */
    public int getType()
    {
        return pieceType;
    }

    /**
     * @return the owner of the piece
     */
    public SOCPlayer getPlayer()
    {
        return player;
    }

    /**
     * @return the coordinates for this piece
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
        case -2:
            // fall through
        case 4:    // == SOCPossiblePiece.CARD (robots)
            // fall through
        case SOCPlayingPiece.MAXPLUSONE:
            return SOCGame.CARD_SET;
        default:
            throw new IllegalArgumentException("pieceType: " + pieceType);
        }
    }
}
