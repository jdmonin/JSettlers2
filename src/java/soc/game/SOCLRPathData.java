/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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

import java.util.Vector;

import soc.util.IntPair;

/**
 * DOCUMENT ME!
 *
 * @author $author$
 */
public class SOCLRPathData
{
    private int beginningCoord;
    private int endCoord;
    private int length;
    private Vector<IntPair> nodePairs;

    /**
     * Creates a new SOCLRPathData object.
     *
     * @param start DOCUMENT ME!
     * @param end DOCUMENT ME!
     * @param len DOCUMENT ME!
     * @param pairs DOCUMENT ME!
     */
    public SOCLRPathData(int start, int end, int len, Vector<IntPair> pairs)
    {
        beginningCoord = start;
        endCoord = end;
        length = len;
        nodePairs = pairs;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public int getBeginning()
    {
        return beginningCoord;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public int getEnd()
    {
        return endCoord;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public Vector<IntPair> getNodePairs()
    {
        return nodePairs;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public int getLength()
    {
        return length;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    @Override
    public String toString()
    {
        String s = "SOCLRPathData:";
        s += ("bc=" + Integer.toHexString(beginningCoord) + "|");
        s += ("ec=" + Integer.toHexString(endCoord) + "|");
        s += ("len=" + length + "|");
        s += ("pairs=" + nodePairs);

        return s;
    }
}
