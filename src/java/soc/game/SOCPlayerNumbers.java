/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
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
     * Land-hex coordinates in standard board ({@link SOCBoard#BOARD_ENCODING_ORIGINAL}).
     * hexCoords_v1 was hexCoords before version 1.1.08.
     */
    private final static int[] hexCoords_v1 = 
    {
        0x33, 0x35, 0x37, 0x53, 0x55, 0x57, 0x59, 0x73, 0x75, 0x77, 0x79, 0x7B,
        0x95, 0x97, 0x99, 0x9B, 0xB7, 0xB9, 0xBB
    };

    /**
     * Land-hex coordinates in 6-player board ({@link SOCBoard#BOARD_ENCODING_6PLAYER}).
     * @since 1.1.08.
     */
    private final static int[] hexCoords_v2 = 
    {
        0x33, 0x35, 0x37, 0x53, 0x55, 0x57, 0x59, 0x73, 0x75, 0x77, 0x79, 0x7B,
        0x95, 0x97, 0x99, 0x9B, 0xB7, 0xB9, 0xBB
    };

    /**
     * Dice roll numbers which yield this resource.
     * Uses indexes in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}.
     */
    private Vector[] numbersForResource;

    /** Resources on dice roll numbers; uses indexes 2-12  */
    private Vector[] resourcesForNumber;

    /** Hex information, by hex coordinate ID  */
    private Vector[] numberAndResourceForHex;

    /**
     * make a copy of the player numbers
     *
     * @param numbers   the player numbers to copy
     */
    public SOCPlayerNumbers(SOCPlayerNumbers numbers)
    {
        numbersForResource = new Vector[SOCResourceConstants.MAXPLUSONE - 1];

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            numbersForResource[i] = (Vector) numbers.numbersForResource[i].clone();
        }

        resourcesForNumber = new Vector[13];

        for (int i = 0; i < 13; i++)
        {
            resourcesForNumber[i] = (Vector) numbers.resourcesForNumber[i].clone();
        }

        numberAndResourceForHex = new Vector[0xBC];

        for (int i = 0; i < hexCoords_v1.length; i++)
        {
            numberAndResourceForHex[hexCoords_v1[i]] = (Vector) numbers.numberAndResourceForHex[hexCoords_v1[i]].clone();
        }
    }

    /**
     * the constructor
     */
    public SOCPlayerNumbers()
    {
        numbersForResource = new Vector[SOCResourceConstants.MAXPLUSONE - 1];

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            numbersForResource[i] = new Vector();
        }

        resourcesForNumber = new Vector[13];

        for (int i = 0; i < 13; i++)
        {
            resourcesForNumber[i] = new Vector();
        }

        numberAndResourceForHex = new Vector[0xBC];

        for (int i = 0; i < hexCoords_v1.length; i++)
        {
            numberAndResourceForHex[hexCoords_v1[i]] = new Vector();
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

        for (int i = 0; i < hexCoords_v1.length; i++)
        {
            numberAndResourceForHex[hexCoords_v1[i]].removeAllElements();
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
        Enumeration hexes = SOCBoard.getAdjacentHexesToNode(piece.getCoordinates()).elements();

        while (hexes.hasMoreElements())
        {
            Integer hex = (Integer) hexes.nextElement();
            int number = board.getNumberOnHexFromCoord(hex.intValue());
            int resource = board.getHexTypeFromCoord(hex.intValue());
            addNumberForResource(number, resource, hex.intValue());
        }
    }

    /**
     * update the numbers data
     *
     * given a node coordinate and a board, add numbers for this player to the list
     *
     * @param coord   the node coordinate
     * @param board   the game board
     */
    public void updateNumbers(int coord, SOCBoard board)
    {
        Enumeration hexes = SOCBoard.getAdjacentHexesToNode(coord).elements();

        while (hexes.hasMoreElements())
        {
            Integer hex = (Integer) hexes.nextElement();
            int number = board.getNumberOnHexFromCoord(hex.intValue());
            int resource = board.getHexTypeFromCoord(hex.intValue());
            addNumberForResource(number, resource, hex.intValue());
        }
    }

    /**
     * @return the resources for a number
     *
     * @param num  the number
     */
    public Vector getResourcesForNumber(int num)
    {
        return resourcesForNumber[num];
    }

    /**
     * @return the numbers for a resource
     *
     * @param resource  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
     */
    public Vector getNumbersForResource(int resource)
    {
        return numbersForResource[resource];
    }

    /**
     * @return the number-resource pairs for a hex
     *
     * @param hex  the hex coord
     */
    public Vector getNumberResourcePairsForHex(int hex)
    {
        return numberAndResourceForHex[hex];
    }

    /**
     * @return the numbers for a resource, taking the robber into account
     *
     * @param resource  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
     * @param robberHex the robber hex
     */
    public Vector getNumbersForResource(int resource, int robberHex)
    {
        Vector numbers = new Vector();

        for (int i = 0; i < hexCoords_v1.length; i++)
        {
            if (hexCoords_v1[i] != robberHex)
            {
                Enumeration pairsEnum = numberAndResourceForHex[hexCoords_v1[i]].elements();

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
     * @return the resources for a number taking the robber into account
     *
     * @param number  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
     * @param robberHex the robber hex
     */
    public Vector getResourcesForNumber(int number, int robberHex)
    {
        Vector resources = new Vector();

        for (int i = 0; i < hexCoords_v1.length; i++)
        {
            if (hexCoords_v1[i] != robberHex)
            {
                Enumeration pairsEnum = numberAndResourceForHex[hexCoords_v1[i]].elements();

                while (pairsEnum.hasMoreElements())
                {
                    IntPair pair = (IntPair) pairsEnum.nextElement();

                    if (pair.getA() == number)
                    {
                        resources.addElement(new Integer(pair.getB()));
                    }
                }
            }
        }

        return resources;
    }

    /**
     * set a number for a resource
     *
     * @param number    the dice-roll number
     * @param resource  the resource, in range {@link SOCResourceConstants#CLAY} to {@link SOCResourceConstants#WOOD}
     * @param hex       the hex coordinate ID
     */
    public void addNumberForResource(int number, int resource, int hex)
    {
        if ((resource >= SOCResourceConstants.CLAY) && (resource <= SOCResourceConstants.WOOD))
        {
            numbersForResource[resource].addElement(new Integer(number));

            Integer resourceInt = new Integer(resource);

            //if (!resourcesForNumber[number].contains(resourceInt)) {
            resourcesForNumber[number].addElement(resourceInt);

            //}
            numberAndResourceForHex[hex].addElement(new IntPair(number, resource));
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
        Enumeration hexes = SOCBoard.getAdjacentHexesToNode(piece.getCoordinates()).elements();

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
        Enumeration hexes = SOCBoard.getAdjacentHexesToNode(coord).elements();

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
     * return a copy of this object
     */
    public SOCPlayerNumbers copy()
    {
        SOCPlayerNumbers copy = new SOCPlayerNumbers();

        for (int i = 0; i < hexCoords_v1.length; i++)
        {
            Enumeration pairsEnum = numberAndResourceForHex[hexCoords_v1[i]].elements();

            while (pairsEnum.hasMoreElements())
            {
                IntPair pair = (IntPair) pairsEnum.nextElement();
                copy.addNumberForResource(pair.getA(), pair.getB(), hexCoords_v1[i]);
            }
        }

        return copy;
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
