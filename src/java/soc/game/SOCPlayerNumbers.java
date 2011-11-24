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
     * Hex dice-roll resource information, by hex coordinate ID.
     * Each hex coordinate's vector contains 0 or more {@link IntPair}(diceNum, resource).
     */
    private Vector[] numberAndResourceForHex;

    /**
     * Reference to either {@link SOCBoard#HEXCOORDS_LAND_V1} or {@link SOCBoard#HEXCOORDS_LAND_V2}.
     * Hex coordinates for each land hex on the board.
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

        numberAndResourceForHex = new Vector[0xBC];

        for (int i = 0; i < landHexCoords.length; i++)
        {
            numberAndResourceForHex[landHexCoords[i]] = (Vector) numbers.numberAndResourceForHex[landHexCoords[i]].clone();
        }
    }

    /**
     * the constructor
     * @param boardEncodingFormat  The board's coordinate encoding format, from {@link SOCBoard#getBoardEncodingFormat()}
     * @throws IllegalArgumentException  If <tt>boardEncodingFormat</tt> value is unknown to this class
     */
    public SOCPlayerNumbers(final int boardEncodingFormat)
        throws IllegalArgumentException
    {
        switch (boardEncodingFormat)
        {
        case SOCBoard.BOARD_ENCODING_ORIGINAL:
            landHexCoords = SOCBoard.HEXCOORDS_LAND_V1;  break;
        case SOCBoard.BOARD_ENCODING_6PLAYER:
            landHexCoords = SOCBoard.HEXCOORDS_LAND_V2;  break;
        default:
            throw new IllegalArgumentException("boardEncodingFormat: " + boardEncodingFormat);
        }

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

        numberAndResourceForHex = new Vector[0xBC];

        for (int i = 0; i < landHexCoords.length; i++)
        {
            numberAndResourceForHex[landHexCoords[i]] = new Vector();
        }
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

        for (int i = 0; i < landHexCoords.length; i++)
        {
            numberAndResourceForHex[landHexCoords[i]].removeAllElements();
        }
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
        Enumeration hexes = board.getAdjacentHexesToNode(piece.getCoordinates()).elements();

        while (hexes.hasMoreElements())
        {
            Integer hex = (Integer) hexes.nextElement();
            int number = board.getNumberOnHexFromCoord(hex.intValue());
            int resource = board.getHexTypeFromCoord(hex.intValue());
            addNumberForResource(number, resource, hex.intValue());
        }
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
            final int hexCoord = ((Integer) hexes.nextElement()).intValue();
            int number = board.getNumberOnHexFromCoord(hexCoord);
            int resource = board.getHexTypeFromCoord(hexCoord);
            addNumberForResource(number, resource, hexCoord);
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
     *
     * @param hex  the hex coord
     */
    public Vector getNumberResourcePairsForHex(int hex)
    {
        return numberAndResourceForHex[hex];
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

        for (int i = 0; i < landHexCoords.length; i++)
        {
            if (landHexCoords[i] != robberHex)
            {
                Enumeration pairsEnum = numberAndResourceForHex[landHexCoords[i]].elements();

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

        for (int i = 0; i < landHexCoords.length; i++)
        {
            if (landHexCoords[i] != robberHex)
            {
                Enumeration pairsEnum = numberAndResourceForHex[landHexCoords[i]].elements();

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
            numberAndResourceForHex[hex].addElement(new IntPair(diceNum, resource));
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
        Enumeration hexes = board.getAdjacentHexesToNode(piece.getCoordinates()).elements();

        while (hexes.hasMoreElements())
        {
            Integer hex = (Integer) hexes.nextElement();
            int number = board.getNumberOnHexFromCoord(hex.intValue());
            int resource = board.getHexTypeFromCoord(hex.intValue());
            undoAddNumberForResource(number, resource, hex.intValue());
        }
    }

    /**
     * undo the updating of the numbers data
     *
     * given a node coordinate and a board, remove numbers for this player from the list
     *
     * @param coord   the node coordinate
     * @param board   the game board
     */
    public void undoUpdateNumbers(int coord, SOCBoard board)
    {
        Enumeration hexes = board.getAdjacentHexesToNode(coord).elements();

        while (hexes.hasMoreElements())
        {
            Integer hex = (Integer) hexes.nextElement();
            int number = board.getNumberOnHexFromCoord(hex.intValue());
            int resource = board.getHexTypeFromCoord(hex.intValue());
            undoAddNumberForResource(number, resource, hex.intValue());
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

            Enumeration numAndResourceEnum = numberAndResourceForHex[hex].elements();

            while (numAndResourceEnum.hasMoreElements())
            {
                IntPair numAndResource = (IntPair) numAndResourceEnum.nextElement();

                if ((numAndResource.getA() == number) && (numAndResource.getB() == resource))
                {
                    numberAndResourceForHex[hex].removeElement(numAndResource);

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
