/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009,2011-2012 Jeremy D Monin <jeremy@nand.net>
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.game;

import soc.util.IntPair;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;


/**
 * This class tracks what dice-roll numbers a player's pieces are touching
 *
 * @author Robert S. Thomas
 */
public class SOCPlayerNumbers
{
    /**
     * Dice roll numbers which yield this resource.
     * Uses indexes in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}.
     * Each element contains {@link Integer}s for the dice numbers.
     *<P>
     * {@link SOCBoardLarge#GOLD_HEX} is handled by adding the dice number to
     * all resource types in {@link #addNumberForResource(int, int, int)}.
     */
    private Vector<Integer>[] numbersForResource;

    /**
     * Resources on dice roll numbers; uses indexes 2-12.
     * Each element contains {@link Integer}s for the resource(s),
     * in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}.
     * If the number yields more than one of that resource type
     * (a city, or multiple pieces on the hex), there will be
     * more than one Integer here with that resource type.
     *<P>
     * {@link SOCBoardLarge#GOLD_HEX} is handled by adding all resource types to
     * the dice number in {@link #addNumberForResource(int, int, int)}.
     */
    private Vector<Integer>[] resourcesForNumber;

    /**
     * Hex dice-roll resource information, by land-hex coordinate ID.
     * Key = Integer hexCoord; value = Vector.
     * Each hex coordinate's vector contains 0 or more {@link IntPair}(diceNum, resource).
     * If {@link #hasSeaBoard}, the resource type may be {@link SOCBoardLarge#GOLD_HEX}.
     *<P>
     * Until {@link #addNumberForResource(int, int, int)} or
     * {@link #updateNumbers(int, SOCBoard)} is called,
     * any land hex's entry in this hashtable may be null.
     */
    private Hashtable<Integer, Vector<IntPair>> numberAndResourceForHex;

    /**
     * Reference to either {@link SOCBoard#HEXCOORDS_LAND_V1} or {@link SOCBoard#HEXCOORDS_LAND_V2}.
     * Hex coordinates for each land hex on the board, via {@link SOCBoard#getLandHexCoords()}.
     * In {@link SOCBoard#BOARD_ENCODING_LARGE}, if the game hasn't yet called
     * {@link SOCBoard#makeNewBoard(Hashtable)}, this may be <tt>null</tt>.
     * @since 1.1.08
     */
    private int[] landHexCoords;

    /**
     * Is this game played on the {@link SOCBoardLarge} large board / sea board?
     * If true, the board's {@link SOCBoard#getBoardEncodingFormat()}
     * must be {@link SOCBoard#BOARD_ENCODING_LARGE}.
     *<P>
     * When <tt>hasSeaBoard</tt>, {@link SOCBoardLarge#GOLD_HEX} is tracked.
     * When false, it's ignored because the same numeric value in the previous
     * encoding formats is water ({@link SOCBoard#MISC_PORT_HEX}).
     * @since 2.0.00
     */
    public final boolean hasSeaBoard;

    /**
     * make a copy of the player numbers
     *
     * @param numbers   the player numbers to copy
     */
    public SOCPlayerNumbers(SOCPlayerNumbers numbers)
    {
        hasSeaBoard = numbers.hasSeaBoard;
        landHexCoords = numbers.landHexCoords;

        numbersForResource = new Vector[SOCResourceConstants.MAXPLUSONE - 1];

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; i++)
        {
            numbersForResource[i] = new Vector<Integer>(numbers.numbersForResource[i]);
        }

        resourcesForNumber = new Vector[13];  // dice roll totals 2 to 12

        for (int i = 0; i < 13; i++)
        {
            resourcesForNumber[i] = new Vector<Integer>(numbers.resourcesForNumber[i]);
        }

        // deep copy, not shallow copy:
        numberAndResourceForHex = new Hashtable<Integer, Vector<IntPair>>((int) (numbers.numberAndResourceForHex.size() * 1.4f));
        for (Enumeration<Integer> hexes = numbers.numberAndResourceForHex.keys(); hexes.hasMoreElements(); )
        {
            Integer hex = hexes.nextElement();
            numberAndResourceForHex.put(hex, new Vector<IntPair>(numbers.numberAndResourceForHex.get(hex)));
        }
    }

    /**
     * the constructor for a player's dice-resource numbers.
     *<P>
     * If using {@link SOCBoard#BOARD_ENCODING_LARGE}, and this is the start of
     * a game that hasn't yet created the layout:  The land hex coordinates
     * will need to be updated later when the board layout is created and sent;
     * call {@link #setLandHexCoordinates(int[])} at that time.
     *
     * @param board  The game board; used only for 
     *         {@link SOCBoard#getBoardEncodingFormat()}
     *         and {@link SOCBoard#getLandHexCoords()}.
     * @throws IllegalArgumentException  If <tt>boardEncodingFormat</tt> value is unknown to this class
     */
    public SOCPlayerNumbers(SOCBoard board)
        throws IllegalArgumentException
    {
        final int boardEncodingFormat = board.getBoardEncodingFormat();
        if ((boardEncodingFormat < SOCBoard.BOARD_ENCODING_ORIGINAL)
            || (boardEncodingFormat > SOCBoard.BOARD_ENCODING_LARGE))
        {
            throw new IllegalArgumentException("boardEncodingFormat: " + boardEncodingFormat);
        }

        hasSeaBoard = (boardEncodingFormat == SOCBoard.BOARD_ENCODING_LARGE);
        landHexCoords = board.getLandHexCoords();
        //   landHexCoords might be null for BOARD_ENCODING_LARGE
        //   if the layout isn't yet created in SOCBoardLarge.makeNewBoard.

        numbersForResource = new Vector[SOCResourceConstants.MAXPLUSONE - 1];

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            numbersForResource[i] = new Vector<Integer>();
        }

        resourcesForNumber = new Vector[13];  // dice roll totals 2 to 12

        for (int i = 0; i < 13; i++)
        {
            resourcesForNumber[i] = new Vector<Integer>();
        }

        numberAndResourceForHex = new Hashtable<Integer, Vector<IntPair>>();

        //    Initially empty, until addNumberForResource is called.
        //    So, skip this loop:
        // for (int i = 0; i < landHexCoords.length; i++)
        // {
        //    numberAndResourceForHex.put(new Integer(landHexCoords[i]), new Vector());
        // }
    }

    /**
     * clear all of the data
     */
    public void clear()
    {
        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD; i++)
        {
            numbersForResource[i].removeAllElements();
        }

        for (int i = 0; i < 13; i++)
        {
            resourcesForNumber[i].removeAllElements();
        }

        numberAndResourceForHex.clear();
    }

    /**
     * Set the land hex coordinates, once the board layout is known.
     * @param landHex  Array of hex coordinates for all land hexes
     * @since 2.0.00
     */
    public void setLandHexCoordinates(final int[] landHex)
    {
        int L = landHex.length;
        landHexCoords = new int[L];
        System.arraycopy(landHex, 0, landHexCoords, 0, L);
    }

    /**
     * Update the numbers data, based on placing a settlement or upgrading to a city at a node.
     *<P>
     * Given a piece and a board, add numbers for this player to the list:
     * Calls {@link #addNumberForResource(int, int, int)} for each dice number and resource
     * on the piece node's adjacent hexes.
     *
     * @param piece   the playing piece, used only for its node coordinate
     * @param board   the game board
     */
    public void updateNumbers(SOCPlayingPiece piece, SOCBoard board)
    {
        updateNumbers(piece.getCoordinates(), board);
    }

    /**
     * update the numbers data, based on placing a settlement or upgrading to a city at a node.
     *<P>
     * Given a node coordinate and a board, add numbers for this player to the list:
     * Calls {@link #addNumberForResource(int, int, int)} for each dice number and resource
     * on the node's adjacent hexes.
     *
     * @param nodeCoord   the node coordinate
     * @param board   the game board
     *
     * @see #updateNumbersAndProbability(int, SOCBoard, int[], StringBuffer)
     */
    public void updateNumbers(final int nodeCoord, SOCBoard board)
    {
        Vector<Integer> hexes = board.getAdjacentHexesToNode(nodeCoord);

        for (Integer hexInt : hexes)
        {
            final int hex = hexInt.intValue();
            final int number = board.getNumberOnHexFromCoord(hex);
            final int resource = board.getHexTypeFromCoord(hex);
            addNumberForResource(number, resource, hex);
        }
    }

    /**
     * Update the numbers data, based on placing a settlement or upgrading to a city at a node,
     * and total the probability for those dice numbers.
     *<P>
     * Given a node coordinate and a board, add numbers for this player to the list:
     * Calls {@link #addNumberForResource(int, int, int)} for each dice number and resource
     * on the node's adjacent hexes.
     * Hexes are ignored if their {@link SOCBoard#getNumberOnHexFromCoord(int)} &lt 1.
     *
     * @param nodeCoord   the settlement or city's node coordinate
     * @param board   the game board
     * @param numProb  probability factor for each dice number,
     *           as integers between 0 and 100 (percentage).
     *           <tt>numProb[i]</tt> is the percentage chance of rolling <tt>i</tt>.
     * @param sb  if not null, a StringBuffer to append each adjacent dice number into for debugging
     * @return Total probability, based on <tt>numProb</tt> for each adjacent dice number
     * @since 2.0.00
     * @see #updateNumbers(int, SOCBoard)
     */
    public int updateNumbersAndProbability
        (final int nodeCoord, SOCBoard board, final int[] numProb, final StringBuffer sb)
    {
        int probTotal = 0;
        Vector<Integer> hexes = board.getAdjacentHexesToNode(nodeCoord);

        for (Integer hexInt : hexes)
        {
            final int hex = hexInt.intValue();
            final int number = board.getNumberOnHexFromCoord(hex);
            if (number > 0)
            {
                final int resource = board.getHexTypeFromCoord(hex);
                addNumberForResource(number, resource, hex);
                probTotal += numProb[number];
            }

            if (sb != null)
            {
                sb.append(number);
                sb.append(' ');
            }
        }

        return probTotal;
    }

    /**
     * Get this player's resources gained when a dice number is rolled.
     *<P>
     * {@link SOCBoardLarge#GOLD_HEX} is handled by adding all resource types
     * to the dice number, if {@link #hasSeaBoard}.
     * So, gold hex numbers will have 5 resources in the Vector
     * (10 for cities on gold).
     *
     * @param diceNum  the dice number, 2-12
     * @return the resources for a number; contains {@link Integer}s for the resource(s),
     * in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}.
     *   If the number yields more than one of that resource type
     *   (a city, or multiple pieces on the hex), there will be
     *   more than one Integer here with that resource type.
     * @see #getResourcesForNumber(int, int)
     */
    public Vector<Integer> getResourcesForNumber(final int diceNum)
    {
        return resourcesForNumber[diceNum];
    }

    /**
     * Get the numbers for a resource type.
     *<P>
     * {@link SOCBoardLarge#GOLD_HEX} is handled by adding the dice number to
     * all resource types, if {@link #hasSeaBoard}.
     *
     * @return the numbers for a resource, as {@link Integer}s
     *
     * @param resource  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
     * @see #getNumbersForResource(int, int)
     */
    public Vector<Integer> getNumbersForResource(int resource)
    {
        return numbersForResource[resource];
    }

    /**
     * @return the number-resource pairs for a hex;
     *  a Vector of 0 or more {@link IntPair}(diceNum, resource).
     *  May be null if hexCoord has no resources for us, or is not a valid land hex.
     *  If the number yields more than one of that resource type
     *  (a city, or multiple pieces on the hex), there will be
     *  more than one Integer here with that resource type.
     *  If {@link #hasSeaBoard}, a resource type may be {@link SOCBoardLarge#GOLD_HEX}.
     *
     * @param hexCoord  the hex coord
     * @see #hasNoResourcesForHex(int)
     */
    public Vector<IntPair> getNumberResourcePairsForHex(final int hexCoord)
    {
        return numberAndResourceForHex.get(Integer.valueOf(hexCoord));
    }

    /**
     * Do we receive no resources at all from this hex on any dice rolls?
     * @param hexCoord  the hex coordinate
     * @return  True if {@link #getNumberResourcePairsForHex(int)} is empty;
     *        False if we do receive resources from this hex
     * @since 2.0.00
     */
    public boolean hasNoResourcesForHex(final int hexCoord)
    {
        Vector<IntPair> v = numberAndResourceForHex.get(Integer.valueOf(hexCoord));
        if (v == null)
            return true;
        else
            return v.isEmpty();
    }

    /**
     * Get the numbers for a resource type.
     *<P>
     * {@link SOCBoardLarge#GOLD_HEX} is handled by adding the dice number to
     * all resource types, if {@link #hasSeaBoard}.
     *
     * @return the dice numbers for a resource (as {@link Integer}s), taking the robber into account;
     *     if this resource is on two 8s (for example), there will be two {@link Integer}(8) in the
     *     returned vector.
     *
     * @param resource  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
     * @param robberHex the robber hex
     * @see #getNumbersForResource(int)
     */
    public Vector<Integer> getNumbersForResource(int resource, int robberHex)
    {
        Vector<Integer> numbers = new Vector<Integer>();

        if (landHexCoords == null)
            return numbers;

        for (int i = 0; i < landHexCoords.length; i++)
        {
            if (landHexCoords[i] == robberHex)
                continue;

            Vector<IntPair> pairs = numberAndResourceForHex.get(new Integer(landHexCoords[i]));
            if (pairs == null)
                continue;

            for (IntPair pair : pairs)
            {
                final int res = pair.getB();
                if ((res == resource) || (hasSeaBoard && (res == SOCBoardLarge.GOLD_HEX)))
                {
                    numbers.addElement(Integer.valueOf(pair.getA()));
                }
            }
        }

        return numbers;
    }

    /**
     * Get this player's resources gained when a dice number is rolled.
     *<P>
     * {@link SOCBoardLarge#GOLD_HEX} is handled by adding all resource types
     * to the dice number, if {@link #hasSeaBoard}.
     * So, gold hex numbers will have 5 resources in the Vector
     * (10 for cities on gold).
     *
     * @return the resources for a dice number, taking the robber into account;
     *   Integers in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}.
     *   If the number yields more than one of that resource type
     *   (a city, or multiple pieces on the hex), there will be
     *   more than one Integer here with that resource type.
     *
     * @param diceNum  the dice roll, 2 - 12 
     * @param robberHex the robber hex coordinate
     * @see #getResourcesForNumber(int)
     */
    public Vector<Integer> getResourcesForNumber(final int diceNum, final int robberHex)
    {
        Vector<Integer> resources = new Vector<Integer>();

        if (landHexCoords == null)
            return resources;

        for (int i = 0; i < landHexCoords.length; i++)
        {
            if (landHexCoords[i] == robberHex)
                continue;

            Vector<IntPair> pairs = numberAndResourceForHex.get(Integer.valueOf(landHexCoords[i]));
            if (pairs == null)
                continue;

            for (IntPair pair : pairs)
            {
                if (pair.getA() == diceNum)
                {
                    final int res = pair.getB();
                    if (hasSeaBoard && (res == SOCBoardLarge.GOLD_HEX))
                    {
                        for (int r = SOCResourceConstants.CLAY; r <= SOCResourceConstants.WOOD; ++r)
                            resources.addElement(new Integer(r));
                    } else {
                        resources.addElement(new Integer(res));
                    }
                }
            }
        }

        return resources;
    }

    /**
     * add a number to the list of dice numbers for a resource
     *
     * @param diceNum    the dice-roll number
     * @param resource  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD};
     *                   resources outside this range are ignored.
     *                   If {@link #hasSeaBoard}, can be {@link SOCBoardLarge#GOLD_HEX}
     *                   as returned from {@link SOCBoardLarge#getHexTypeFromCoord(int)}.
     * @param hex       the hex coordinate ID
     */
    public void addNumberForResource(final int diceNum, final int resource, final int hex)
    {
        if ((resource >= SOCResourceConstants.CLAY) && (resource <= SOCResourceConstants.WOOD))
        {
            numbersForResource[resource].addElement(new Integer(diceNum));

            Integer resourceInt = new Integer(resource);

            //if (!resourcesForNumber[number].contains(resourceInt)) {
            resourcesForNumber[diceNum].addElement(resourceInt);

            //}
        }
        else
        {
            if (! (hasSeaBoard && (resource == SOCBoardLarge.GOLD_HEX)))
            {
                return;  // <--- Ignore all other resource/hex types ---
            }

            // GOLD_HEX: Add all 5 resource types
            final Integer diceNumInt = new Integer(diceNum);
            for (int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; ++res)
            {
                numbersForResource[res].addElement(diceNumInt);
                resourcesForNumber[diceNum].addElement(new Integer(res));
            }

            // GOLD_HEX is okay in numberAndResourceForHex.
        }

        final Integer hexInt = new Integer(hex);
        Vector<IntPair> pairs = numberAndResourceForHex.get(hexInt);
        if (pairs == null)
        {
            pairs = new Vector<IntPair>();
            numberAndResourceForHex.put(hexInt, pairs);
        }
        pairs.addElement(new IntPair(diceNum, resource));
    }

    /**
     * undo the updating of the numbers data
     *
     * given a piece and a board, remove numbers for this player from the list
     *
     * @param piece   the playing piece
     * @param board   the game board
     */
    public void undoUpdateNumbers(SOCPlayingPiece piece, SOCBoard board)
    {
        undoUpdateNumbers(piece.getCoordinates(), board);
    }

    /**
     * undo the updating of the numbers data
     *
     * given a node coordinate and a board, remove numbers for this player from the list
     *
     * @param coord   the node coordinate
     * @param board   the game board
     */
    public void undoUpdateNumbers(final int coord, SOCBoard board)
    {
        Vector<Integer> hexes = board.getAdjacentHexesToNode(coord);

        for (Integer hexInt : hexes)
        {
            final int hex = hexInt.intValue();
            final int number = board.getNumberOnHexFromCoord(hex);
            final int resource = board.getHexTypeFromCoord(hex);
            undoAddNumberForResource(number, resource, hex);
        }
    }

    /**
     * remove a number for a resource
     * do this when you take back a piece
     *
     * @param number    the dice-roll number
     * @param resource  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD},
     *                    from {@link SOCBoard#getHexTypeFromCoord(int)}.
     *                    If {@link #hasSeaBoard}, can be {@link SOCBoardLarge#GOLD_HEX}.
     * @param hex       the hex coordinate ID
     */
    public void undoAddNumberForResource(int number, int resource, int hex)
    {
        if ((resource >= SOCResourceConstants.CLAY) && (resource <= SOCResourceConstants.WOOD))
        {
            for (Integer num : numbersForResource[resource])
            {
                if (num.intValue() == number)
                {
                    numbersForResource[resource].removeElement(num);
                    break;
                }
            }

            for (Integer resourceInt : resourcesForNumber[number])
            {
                if (resourceInt.intValue() == resource)
                {
                    resourcesForNumber[number].removeElement(resourceInt);
                    break;
                }
            }
        }
        else
        {
            if (! (hasSeaBoard && (resource == SOCBoardLarge.GOLD_HEX)))
            {
                return;  // <--- Ignore all other resource/hex types ---
            }

            // GOLD_HEX: Remove all 5 resource types.
            // To simplify multiple removal, use Iterator not Enumeration.

            for (int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; ++res)
            {
                Iterator<Integer> numIter = numbersForResource[res].iterator();    
                while (numIter.hasNext())
                {
                    final int num = numIter.next().intValue();  
                    if (num == number)
                    {
                        numIter.remove();
                        break;
                    }
                }
            }

            boolean[] removed = new boolean[SOCResourceConstants.UNKNOWN];  // range CLAY to WOOD
            Iterator<Integer> resIter = resourcesForNumber[number].iterator();
            while (resIter.hasNext())
            {
                final int res = resIter.next().intValue();
                if (! removed[res])
                {
                    resIter.remove();
                    removed[res] = true;
                }
            }

            // GOLD_HEX will be in numberAndResourceForHex.
        }

        Vector<IntPair> pairs = numberAndResourceForHex.get(Integer.valueOf(hex));
        if (pairs != null)
        {
            for (IntPair numAndResource : pairs)
            {
                if ((numAndResource.getA() == number) && (numAndResource.getB() == resource))
                {
                    pairs.removeElement(numAndResource);
                    break;
                }
            }
        }
    }

    /**
     * return true if this player is touching the requested number
     *
     * @param number  the dice-roll number
     * @return true if the player has the number
     */
    public boolean hasNumber(int number)
    {
        return !resourcesForNumber[number].isEmpty();
    }

    /**
     * return a human readable form of this object
     */
    @Override
    public String toString()
    {
        String str = "SOCPN:";

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            str += (i + ":");

            for (Integer num : numbersForResource[i])
            {
                str += (num + ",");
            }

            str += "|";
        }

        return str;
    }
}
