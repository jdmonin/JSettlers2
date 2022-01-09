/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2012,2014,2017-2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.game;

import java.io.Serializable;
import java.util.List;


/**
 * Playing pieces for Settlers of Catan.
 * For the resources needed to build a piece type, see {@link #getResourcesToBuild(int)}.
 * See also soc.robot.SOCPossiblePiece.
 */
public abstract class SOCPlayingPiece implements Serializable, Cloneable
{
    private static final long serialVersionUID = 2300L;

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
     * Types of playing pieces: {@link SOCShip Ship}.
     * Used only when {@link SOCGame#hasSeaBoard}.
     * Requires client and server version 2.0.00 or newer.
     * @see #getResourcesToBuild(int)
     * @since 2.0.00
     */
    public static final int SHIP = 3;

    /**
     * Types of playing pieces: {@link SOCFortress Fortress}.
     * Used only when {@link SOCGame#hasSeaBoard} and scenario
     * option {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.
     * Requires client and server version 2.0.00 or newer.
     * New fortresses cannot be built after the game starts.
     * @since 2.0.00
     */
    public static final int FORTRESS = 4;

    /**
     * Types of playing pieces: {@link SOCVillage Village}.
     * Used only when {@link SOCGame#hasSeaBoard}.
     * Requires client and server version 2.0.00 or newer.
     * Villages belong to the game board, not to any player,
     * and new villages cannot be built after the game starts.
     * @since 2.0.00
     */
    public static final int VILLAGE = 5;

    /**
     * Minimum type number of playing piece (currently Road).
     */
    public static final int MIN = 0;

    /**
     * One past the maximum type number of playing piece.
     * MAXPLUSONE == 3 up through all v1.x.xx versions.
     * MAXPLUSONE == 6 in v2.x.xx so far.
     */
    public static final int MAXPLUSONE = 6;

    /**
     * Piece type unique technical names, for {@link #getTypeName(int)} and {@link #getType(String)}.
     * @since 2.4.00
     */
    private static final String[] PIECETYPE_NAMES
        = { "ROAD", "SETTLEMENT", "CITY", "SHIP", "FORTRESS", "VILLAGE" };

    /**
     * The type of this playing piece, within range {@link #MIN} to ({@link #MAXPLUSONE} - 1)
     */
    protected int pieceType;

    /**
     * The player who owns this piece, if any. Will be null for certain piece types
     * such as {@link SOCVillage} which belong to the board and not to players.
     * Player is from same game as {@link #board}.
     */
    protected transient SOCPlayer player;

    /**
     * Coordinates on the board for this piece. An edge or a node, depending on piece type.
     */
    protected int coord;

    /**
     * Board, for coordinate-related operations. Should be from same game as {@link #player}.
     * @since 1.1.08
     */
    protected transient SOCBoard board;

    /**
     * Special Victory Points (SVP) awarded for placing this piece, if any.
     * Used with the {@link SOCGame#hasSeaBoard large sea board} game scenarios.
     * When {@link #specialVP} != 0, the source is {@link #specialVPEvent}.
     *<P>
     * When setting or incrementing this field, should also increment/add to player's {@code specialVP} field.
     * Don't just increase player's SVP by the piece's new {@code specialVP}, because piece may have accumulated SVP
     * from multiple events which are already part of player's SVP amount.
     *<P>
     * <b>Note:</b> This is set when the piece was placed, so it's always accurate at server.
     * At client it may be 0 if the client joined the game after this piece was placed.
     * @since 2.0.00
     */
    public int specialVP;

    /**
     * If {@link #specialVP} != 0, the event for which the SVP was awarded. Otherwise <tt>null</tt>.
     *<P>
     * <b>Note:</b> This is set when the piece was placed, so it's always accurate at server.
     * At client it may be <tt>null</tt> if the client joined the game after this piece was placed.
     *<P>
     * Package access for SOCPlayer's benefit.
     * @since 2.0.00
     */
    transient SOCPlayerEvent specialVPEvent;

    /**
     * Make a new piece, which is owned by a player.
     *
     * @param ptype  the type of piece, such as {@link #SETTLEMENT}
     * @param pl  player who owns the piece
     * @param co  coordinates
     * @param pboard  board if known; otherwise will extract from <tt>pl</tt>.
     *               Board should be from same game as <tt>pl</tt>.
     * @throws IllegalArgumentException  if <tt>pl</tt> null, or board null and <tt>pl.board</tt> also null
     * @see #SOCPlayingPiece(int, int, SOCBoard)
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
     * Make a new piece, which belongs to the board and never to players.
     * @throws IllegalArgumentException  if <tt>board</tt> null
     * @see #SOCPlayingPiece(int, SOCPlayer, int, SOCBoard)
     * @since 2.0.00
     */
    protected SOCPlayingPiece(final int ptype, final int co, SOCBoard pboard)
        throws IllegalArgumentException
    {
        if (pboard == null)
            throw new IllegalArgumentException("board null");

        pieceType = ptype;
        player = null;
        coord = co;
        board = pboard;
    }

    /**
     * Which edges touch this piece's node on the board?
     * Should not be called for roads, because they aren't placed at a node.
     * @return edges touching this piece, same format as {@link SOCBoard#getAdjacentEdgesToNode(int)}
     */
    public List<Integer> getAdjacentEdges()
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
     * Get the player who owns this piece, if any.
     * Certain piece types such as {@link SOCVillage} belong to the board and not to players.
     * @return the owner of the piece
     * @see #getPlayerNumber()
     * @throws UnsupportedOperationException  if this piece type has no player and is owned by the board
     */
    public SOCPlayer getPlayer()
        throws UnsupportedOperationException
    {
        if (player != null)
            return player;
        else
            throw new UnsupportedOperationException
                ("No player for piece type " + pieceType + " at 0x" + Integer.toHexString(coord));
    }

    /**
     * Get the owner's player number.
     * @return {@link #getPlayer()}.{@link SOCPlayer#getPlayerNumber() getPlayerNumber()}
     * @throws UnsupportedOperationException  if this piece type has no player and is owned by the board
     * @since 2.0.00
     */
    public int getPlayerNumber()
        throws UnsupportedOperationException
    {
        if (player != null)
            return player.getPlayerNumber();
        else
            throw new UnsupportedOperationException
                ("No player for piece type " + pieceType + " at 0x" + Integer.toHexString(coord));
    }

    /**
     * @return the node or edge coordinate for this piece
     */
    public int getCoordinates()
    {
        return coord;
    }

    /**
     * Update piece's game info. Useful when loading from a persisted form.
     * @param pl  Player; can be null for some piece types, like {@link SOCVillage}
     * @param b   Board; not null
     * @throws IllegalArgumentException if {@code b} is {@code null}
     * @since 2.3.00
     */
    public void setGameInfo(SOCPlayer pl, SOCBoard b)
    {
        if (b == null)
            throw new IllegalArgumentException("board");

        player = pl;
        board = b;
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

        return "SOCPlayingPiece:" + clName + "|type=" + pieceType + "|player=" + player
            + "|coord=" + Integer.toHexString(coord);
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
     * @since 1.1.00
     */
    @Override
    public boolean equals(Object other)
    {
        if (other == null)
            return false;
        if (! (other instanceof SOCPlayingPiece))
            return super.equals(other);
        return ((this.pieceType == ((SOCPlayingPiece) other).pieceType)
            &&  (this.coord == ((SOCPlayingPiece) other).coord)
            &&  (this.player == ((SOCPlayingPiece) other).player));

        // board field is based on player, so no need to compare it too.
    }

    /**
     * the set of resources a player needs to build a playing piece.
     * @param pieceType The type of this playing piece, in range {@link #MIN} to ({@link #MAXPLUSONE} - 1).
     *           {@link #ROAD}, {@link #CITY}, etc.
     *           For convenience, can also pass -2 or {@link #MAXPLUSONE} for {@link SOCDevCard#COST}.
     * @return the set, such as {@link SOCSettlement#COST}
     * @throws IllegalArgumentException if <tt>pieceType</tt> is out of range, or can never be built by players
     * @since 1.1.08
     */
    public static SOCResourceSet getResourcesToBuild(final int pieceType)
        throws IllegalArgumentException
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
        case -2:  // == SOCPossiblePiece.CARD (robots)
            // fall through
        case SOCPlayingPiece.MAXPLUSONE:
            return SOCDevCard.COST;
        default:
            throw new IllegalArgumentException("pieceType: " + pieceType);
        }
    }

    /**
     * Get a piece type's unique technical name, which is similar to constant field names like {@link #CITY}.
     *
     * @param pieceType  A constant such as {@link #CITY}
     * @return Piece type's unique technical name, all-uppercase and following the pattern {@code [A-Z][A-Z0-9_]+},
     *     or {@link Integer#toString(int) Integer.toString(pieceType)} if not a recognized type constant
     * @throws IllegalArgumentException if {@code pieceType} &lt; 0
     * @see #getType(String)
     * @since 2.4.00
     */
    public static String getTypeName(final int pieceType)
        throws IllegalArgumentException
    {
        return getTypeName(pieceType, PIECETYPE_NAMES);
    }

    /**
     * For game items which have different type constants ({@link SOCPlayingPiece}, {@link SOCDevCard}, etc),
     * get an item type's unique technical name for a constant value.
     *
     * @param typeNum  A constant such as {@link SOCDevCardConstants#UNIV}
     *     or {@link SOCPlayingPiece#CITY}
     * @param allTypeNames  All known item types' unique technical names, indexed same as their int constant values.
     *     See return javadoc for expected name format.
     *     Null elements are ignored, in case your type has no constant at index 0 or some other index;
     *     if element 7 is null, will return "7" for it.
     * @return That type constant's unique technical name,
     *     all-uppercase and following the pattern {@code [A-Z][A-Z0-9_]+},
     *     or {@link Integer#toString(int) Integer.toString(typeNum)} if {@code typeNum} not a recognized type constant
     *     from {@code allTypeNames}
     * @throws IllegalArgumentException if {@code typeNum} &lt; 0
     * @see #getType(String, String[], int)
     * @since 2.4.00
     */
    public static String getTypeName(final int typeNum, final String[] allTypeNames)
        throws IllegalArgumentException
    {
        if (typeNum < 0)
            throw new IllegalArgumentException("typeNum: " + typeNum);

        String name = null;
        if (typeNum < allTypeNames.length)
            name = allTypeNames[typeNum];

        return (name != null) ? name : Integer.toString(typeNum);
    }

    /**
     * Given a piece type's unique technical name like {@code "CITY"}, return its piece type constant
     * like {@link #CITY} if recognized.
     * @param typeName Piece type's technical name, as returned by {@link #getTypeName(int)},
     *     or a numeric string like {@code "42"} for any recognized or unrecognized type's numeric value
     * @return That type's constant such as {@link #CITY} or {@link #SETTLEMENT} if recognized,
     *     or its parsed value if starts with a digit, or -1 if type string is unrecognized
     * @throws IllegalArgumentException if {@code typeName} is null or "",
     *     or doesn't start with an uppercase {@code 'A'}-{@code 'Z'} or digit {@code '0'}-{@code '9'}
     * @throws NumberFormatException if {@code typeName} starts with a digit but {@link Integer#parseInt(String)} fails
     * @see #getTypeName(int)
     * @since 2.4.00
     */
    public static int getType(final String typeName)
        throws IllegalArgumentException, NumberFormatException
    {
        return getType(typeName, PIECETYPE_NAMES, -1);
    }

    /**
     * For game items which have different type constants ({@link SOCPlayingPiece}, {@link SOCDevCard}, etc),
     * get an item type's constant value from its unique technical name returned by {@link #getType(String, String[])}.
     *
     * @param typeName Type's technical name, as returned by {@link #getTypeName(int, String[])},
     *     or a numeric string like {@code "42"} for any recognized or unrecognized type's numeric value
     * @param allTypeNames  All known item types' unique technical names, indexed same as their int constant values.
     *     See {@link #getTypeName(int, String[])} javadoc for expected name format.
     *     Null elements are ignored, in case your type has no constant at index 0 or some other index.
     * @param unknownValue  Value to return if {@code typeName} is unrecognized, because it isn't in {@code allTypeNames}
     * @return That type's constant if {@code typeName} recognized from {@code allTypeNames},
     *     or its parsed intValue if starts with a digit, or {@code unknownValue} if type string is unrecognized
     * @throws IllegalArgumentException if {@code typeName} is null or ""
     *     or doesn't start with an uppercase {@code 'A'}-{@code 'Z'} or digit {@code '0'}-{@code '9'},
     *     or if {@code AllTypeNames} is null
     * @throws NumberFormatException if {@code typeName} starts with a digit but {@link Integer#parseInt(String)} fails
     * @see #getTypeName(int, String[])
     * @since 2.4.00
     */
    public static int getType(final String typeName, final String[] allTypeNames, final int unknownValue)
        throws IllegalArgumentException, NumberFormatException
    {
        if ((typeName == null) || typeName.isEmpty())
            throw new IllegalArgumentException("typeName empty or null");
        if (allTypeNames == null)
            throw new IllegalArgumentException("allTypeNames");

        final char c0 = typeName.charAt(0);
        if ((c0 >= '0') && (c0 <= '9'))
        {
            return Integer.parseInt(typeName);
        }
        else if ((c0 >= 'A') && (c0 <= 'Z'))
        {
            // for these very small arrays, linear search is fast enough
            for (int i = 0; i < allTypeNames.length; ++i)
                if (typeName.equals(allTypeNames[i]))
                    return i;

            return unknownValue;
        } else {
            throw new IllegalArgumentException("typeName format");
        }
    }

}
