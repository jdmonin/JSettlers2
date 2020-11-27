/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2017-2018 Strategic Conversation (STAC Project) https://www.irit.fr/STAC/
 * Portions of this file copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
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

import java.io.Serializable;
import java.util.EmptyStackException;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Stack;
import soc.game.SOCResourceSet;

/**
 * Stack implementation of a {@link SOCBuildPlan}.
 *<P>
 * Uses stack directly to save time - will need to change
 * to a wrapper if we ever change SOCBuildPlan to an abstract class instead of an interface.
 *
 * @author kho30
 * @since 2.4.50
 */
public class SOCBuildPlanStack extends Stack<SOCPossiblePiece>
    implements SOCBuildPlan, Serializable
{
    private static final long serialVersionUID = 2450L;  // last structural change v2.4.50

    /**
     * NB: This does not check for a legal index
     */
    public SOCPossiblePiece getPlannedPiece(int pieceNum)
        throws IndexOutOfBoundsException
    {
        return super.get(elementCount - 1 - pieceNum);
            // throws subclass ArrayIndexOutOfBoundsException
    }

    public int getPlanDepth()
    {
        return elementCount;
    }

    /**
     * NB: This does not check for a safe operation
     */
    public SOCPossiblePiece advancePlan()
        throws NoSuchElementException
    {
        try
        {
            return pop();
        } catch (EmptyStackException e) {
            throw new NoSuchElementException(e.getMessage());
        }
    }

    public SOCResourceSet getFirstPieceResources()
    {
        final SOCResourceSet rs = (isEmpty()) ? null : getPlannedPiece(0).getResourcesToBuild();

        return (rs != null) ? rs : SOCResourceSet.EMPTY_SET;
    }

    public SOCResourceSet getTotalResourcesForBuildPlan()
    {
        SOCResourceSet rs = new SOCResourceSet();

        for (Enumeration<SOCPossiblePiece> e = super.elements(); e.hasMoreElements(); )
        {
            SOCPossiblePiece pp = (SOCPossiblePiece) e.nextElement();
            rs.add(pp.getResourcesToBuild());
        }

        return rs;
    }

}
