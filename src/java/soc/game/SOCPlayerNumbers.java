/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009,2011 Jeremy D Monin <jeremy@nand.net>
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
     */
    private Vector[] numbersForResource;

    /**
     * Resources on dice roll numbers; uses indexes 2-12.
     * Each element contains {@link Integer}s for the resource(s),
     * in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}.
     */
    private Vector[] resourcesForNumber;

    /**
     * Hex dice-roll resource information, by land-hex coordinate ID.
     * Key = Integer hexCoord; value = Vector.
     * Each hex coordinate's vector contains 0 or more {@link IntPair}(diceNum, resource).
     * Until {@link #addNumberForResource(int, int, int)} is called,
     * any land hex's entry in this hashtable may be null.
     */
    private Hashtable numberAndResourceForHex;

    /**
     * Reference to either {@link SOCBoard#HEXCOORDS_LAND_V1} or {@link SOCBoard#HEXCOORDS_LAND_V2}.
     * Hex coordinates for each land hex on the board, via {@link SOCBoard#getLandHexCoords()}.
     * In {@link SOCBoard#BOARD_ENCODING_LARGE}, if the game hasn't yet called
     * {@link SOCBoard#makeNewBoard(Hashtable)}, this may be <tt>null</tt>.
     * @since 1.1.08
     */
    private int[] landHexCoords;

    /**
     * make a copy of the player numbers
     *
     * @param numbers   the player numbers to copy
     */
    public SOCPlayerNumbers(SOCPlayerNumbers numbers)
    {
        landHexCoords = numbers.landHexCoords;

        numbersForResource = new Vector[SOCResourceConstants.MAXPLUSONE - 1];

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            numbersForResource[i] = (Vector) numbers.numbersForResource[i].clone();
        }

        resourcesForNumber = new Vector[13];  // dice roll totals 2 to 12

        for (int i = 0; i < 13; i++)
        {
            resourcesForNumber[i] = (Vector) numbers.resourcesForNumber[i].clone();
        }

        // deep copy, not shallow copy:
        numberAndResourceForHex = new Hashtable((int) (numbers.numberAndResourceForHex.size() * 1.4f));
        for (Enumeration hexes = numbers.numberAndResourceForHex.keys();
             hexes.hasMoreElements(); )
        {
            Object hex = hexes.nextElement();
            numberAndResourceForHex.put
                (hex, ((Vector) numbers.numberAndResourceForHex.get(hex)).clone());
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

        landHexCoords = board.getLandHexCoords();
        //   landHexCoords might be null for BOARD_ENCODING_LARGE
        //   if the layout isn't yet created in SOCBoardLarge.makeNewBoard.

        numbersForResource = new Vector[SOCResourceConstants.MAXPLUSONE - 1];

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            numbersForResource[i] = new Vector();
        }

        resourcesForNumber = new Vector[13];  // dice roll totals 2 to 12

        for (int i = 0; i < 13; i++)
        {
            resourcesForNumber[i] = new Vector();
        }

        numberAndResourceForHex = new Hashtable();

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
        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
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
     * @since 1.2.00
     */
    public void setLandHexCoordinates(final int[] landHex)
    {
        int L = landHex.length;
        landHexCoords = new int[L];
        System.arraycopy(landHex, 0, landHexCoords, 0, L);
    }

    /**
     * update the numbers data
     *
     * given a piece and a board, add numbers for this player to the list
     *
     * @param piece   the playing piece
     * @param board   the game board
     */
    public void updateNumbers(SOCPlayingPiece piece, SOCBoard board)
    {
        updateNumbers(piece.getCoordinates(), board);
    }

    /**
     * update the numbers data, based on placing a settlement or upgrading at a node.
     *
     * given a node coordinate and a board, add numbers for this player to the list
     *
     * @param nodeCoord   the node coordinate
     * @param board   the game board
     */
    public void updateNumbers(final int nodeCoord, SOCBoard board)
    {
        Enumeration hexes = board.getAdjacentHexesToNode(nodeCoord).elements();

        while (hexes.hasMoreElements())
        {
            final int hex = ((Integer) hexes.nextElement()).intValue();
            final int number = board.getNumberOnHexFromCoord(hex);
            final int resource = board.getHexTypeFromCoord(hex);
            addNumberForResource(number, resource, hex);
        }
    }

    /**
     * Get this player's resources gained when a dice number is rolled.
     * @param diceNum  the dice number, 2-12
     * @return the resources for a number; contains {@link Integer}s for the resource(s),
     * in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}.
     */
    public Vector getResourcesForNumber(final int diceNum)
    {
        return resourcesForNumber[diceNum];
    }

    /**
     * @return the numbers for a resource, as {@link Integer}s
     *
     * @param resource  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
     */
    public Vector getNumbersForResource(int resource)
    {
        return numbersForResource[resource];
    }

    /**
     * @return the number-resource pairs for a hex;
     *  a Vector of 0 or more {@link IntPair}(diceNum, resource).
     *  May be null if hexCoord has no resources for us, or is not a valid land hex.
     *
     * @param hexCoord  the hex coord
     * @see #hasNoResourcesForHex(int)
     */
    public Vector getNumberResourcePairsForHex(final int hexCoord)
    {
        return (Vector) numberAndResourceForHex.get(new Integer(hexCoord));
    }

    /**
     * Do we receive no resources at all from this hex on any dice rolls?
     * @param hexCoord  the hex coordinate
     * @return  True if {@link #getNumberResourcePairsForHex(int)} is empty;
     *        False if we do receive resources from this hex
     * @since 1.2.00
     */
    public boolean hasNoResourcesForHex(final int hexCoord)
    {
        Vector v = (Vector) numberAndResourceForHex.get(new Integer(hexCoord));
        if (v == null)
            return true;
        else
            return v.isEmpty();
    }

    /**
     * @return the dice numbers for a resource (as {@link Integer}s), taking the robber into account
     *
     * @param resource  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
     * @param robberHex the robber hex
     */
    public Vector getNumbersForResource(int resource, int robberHex)
    {
        Vector numbers = new Vector();

        if (landHexCoords == null)
            return numbers;

        for (int i = 0; i < landHexCoords.length; i++)
        {
            if (landHexCoords[i] != robberHex)
            {
                Vector pairs = (Vector) numberAndResourceForHex.get(new Integer(landHexCoords[i]));
                if (pairs == null)
                    continue;

                Enumeration pairsEnum = pairs.elements();
                while (pairsEnum.hasMoreElements())
                {
                    IntPair pair = (IntPair) pairsEnum.nextElement();
                    if (pair.getB() == resource)
                    {
                        numbers.addElement(new Integer(pair.getA()));
                    }
                }
            }
        }

        return numbers;
    }

    /**
     * @return the resources for a dice number, taking the robber into account;
     *   Integers in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
     *
     * @param diceNum  the dice roll, 2 - 12 
     * @param robberHex the robber hex coordinate
     */
    public Vector getResourcesForNumber(final int diceNum, final int robberHex)
    {
        Vector resources = new Vector();

        if (landHexCoords == null)
            return resources;

        for (int i = 0; i < landHexCoords.length; i++)
        {
            if (landHexCoords[i] != robberHex)
            {
                Vector pairs = (Vector) numberAndResourceForHex.get(new Integer(landHexCoords[i]));
                if (pairs == null)
                    continue;

                Enumeration pairsEnum = pairs.elements();
                while (pairsEnum.hasMoreElements())
                {
                    IntPair pair = (IntPair) pairsEnum.nextElement();
                    if (pair.getA() == diceNum)
                    {
                        resources.addElement(new Integer(pair.getB()));
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
     * @param resource  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
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

            final IntPair newPair = new IntPair(diceNum, resource);
            final Integer hexInt = new Integer(hex);
            Vector pairs = (Vector) numberAndResourceForHex.get(hexInt);
            if (pairs == null)
            {
                pairs = new Vector();
                numberAndResourceForHex.put(hexInt, pairs);
            }
            pairs.addElement(newPair);
        }
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
        Enumeration hexes = board.getAdjacentHexesToNode(coord).elements();

        while (hexes.hasMoreElements())
        {
            final int hex = ((Integer) hexes.nextElement()).intValue();
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
     * @param resource  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
     * @param hex       the hex coordinate ID
     */
    public void undoAddNumberForResource(int number, int resource, int hex)
    {
        if ((resource >= SOCResourceConstants.CLAY) && (resource <= SOCResourceConstants.WOOD))
        {
            Enumeration numEnum = numbersForResource[resource].elements();

            while (numEnum.hasMoreElements())
            {
                Integer num = (Integer) numEnum.nextElement();

                if (num.intValue() == number)
                {
                    numbersForResource[resource].removeElement(num);

                    break;
                }
            }

            Enumeration resourceEnum = resourcesForNumber[number].elements();

            while (resourceEnum.hasMoreElements())
            {
                Integer resourceInt = (Integer) resourceEnum.nextElement();

                if (resourceInt.intValue() == resource)
                {
                    resourcesForNumber[number].removeElement(resourceInt);

                    break;
                }
            }

            Vector pairs = (Vector) numberAndResourceForHex.get(new Integer(hex));
            if (pairs != null)
            {
                Enumeration numAndResourceEnum = pairs.elements();
    
                while (numAndResourceEnum.hasMoreElements())
                {
                    IntPair numAndResource = (IntPair) numAndResourceEnum.nextElement();
    
                    if ((numAndResource.getA() == number) && (numAndResource.getB() == resource))
                    {
                        pairs.removeElement(numAndResource);
                        break;
                    }
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
    public String toString()
    {
        String str = "SOCPN:";

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            str += (i + ":");

            Enumeration nums = numbersForResource[i].elements();

            while (nums.hasMoreElements())
            {
                Integer num = (Integer) nums.nextElement();
                str += (num + ",");
            }

            str += "|";
        }

        return str;
    }
}
