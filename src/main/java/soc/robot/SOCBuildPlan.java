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

import java.util.NoSuchElementException;

import soc.game.SOCResourceSet;

/**
 * Encapsulation for building plans of {@link SOCPossiblePiece}s.
 * Typically implemented as {@link SOCBuildPlanStack}; a third-party bot might have a different implementation.
 *<P>
 * Before v2.4.50, Building Plans were represented as {@code Stack<SOCPossiblePiece>}.
 *
 * @author kho30
 * @since 2.4.50
 */
public interface SOCBuildPlan
{
    /**
     * Reset the build plan
     */
    public void clear();

    /**
     * Is this plan currently empty?
     * @return true if nothing is currently planned
     */
    public boolean isEmpty();

    /**
     * Get the <em>i</em>th planned build item, without removing it from the plan.
     * This is typically called with an index of 0 (the first piece, equivalent to a peek),
     * however it is called with an index of 1 during the play of a Road Building card.
     *<P>
     * Note: This may be unsafe - assumes an appropriate size of build plan.  Could easily add a check for size in the
     * function call, but it's probably easier to debug for now with bad calls throwing exceptions, rather than
     * returning nulls that may be harder to figure out if they create funny behavior later.
     * JSettlers building plans used to be {@code Stack}, which throws an exception rather than returning null.
     *<P>
     * In future, non-linear build plans should be discussed as to how to implement this. For example if we
     * traverse a tree-like structure, so separate functions would need to be added to switch between branches.
     *
     * @param pieceNum  Piece number within plan, where 0 is the first to be built.
     *     Range is 0 to {@link #getPlanDepth()} - 1.
     * @return  Piece within plan
     * @throws IndexOutOfBoundsException if {@code pieceNum} is out of range
     * @see #advancePlan()
     */
    public SOCPossiblePiece getPlannedPiece(int pieceNum)
        throws IndexOutOfBoundsException;

    /**
     * Return the depth of the plan: The number of pieces to be built. Non-linear plans to be discussed in future.
     * @return Number of pieces in this plan
     * @see #getPlannedPiece(int)
     */
    public int getPlanDepth();

    /**
     * Step forward in the plan.  Equivalent to a pop in the stack implementation.
     * @return the piece at index 0 in the plan
     * @throws NoSuchElementException if {@link #isEmpty()}
     * @see #getPlannedPiece(int)
     */
    public SOCPossiblePiece advancePlan()
        throws NoSuchElementException;

    /**
     * Get the resources needed to build the first piece in this plan.
     * @return {@link #getPlannedPiece(int) getPlannedPiece(0)}{@link SOCPossiblePiece#getResourcesToBuild() .getResourcesToBuild()},
     *     or {@link SOCResourceSet#EMPTY_SET} if that's null or if {@link #isEmpty()}
     */
    public SOCResourceSet getFirstPieceResources();

    /**
     * Calculate the total resources needed to build all pieces in this plan.
     * @return Total resources, from each piece's {@link SOCPossiblePiece#getResourcesToBuild()}
     */
    public SOCResourceSet getTotalResourcesForBuildPlan();

}
