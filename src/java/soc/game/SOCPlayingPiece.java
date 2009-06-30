/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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


/**
 * Playing pieces for Settlers of Catan
 */
public abstract class SOCPlayingPiece implements Serializable, Cloneable
{
    /**
     * Types of playing pieces
     */
    public static final int ROAD = 0;
    public static final int SETTLEMENT = 1;
    public static final int CITY = 2;
    public static final int MIN = 0;
    public static final int MAXPLUSONE = 3;

    /**
     * The type of this playing piece
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
    }
}
