/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2024 Jeremy D Monin <jeremy@nand.net>
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

package soctest.robot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;

import soc.game.ResourceSet;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.robot.SOCBuildPlan;
import soc.robot.SOCBuildPlanStack;
import soc.robot.SOCPossiblePickSpecialItem;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCPossibleRoad;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link SOCBuildPlan} using its {@link SOCBuildPlanStack} implementation.
 * If another implementation is written, it should also be tested here.
 * @since 2.7.00
 */
public class TestBuildPlan
{
    private final SOCGame ga = new SOCGame("game-TestBuildPlan");
    private final SOCPlayer pl2 = new SOCPlayer(2, ga);
    private final SOCPossibleRoad nr1 = new SOCPossibleRoad(pl2, 0x14, null);
    private final SOCPossibleRoad nr2 = new SOCPossibleRoad(pl2, 0x13, new ArrayList<>(Arrays.asList(nr1)));
    private final SOCResourceSet SI_COST = new SOCResourceSet(2, 0, 0, 1, 1, 0);
    private final SOCPossiblePickSpecialItem siNoCost = new SOCPossiblePickSpecialItem(pl2, "TestSINoCost", 11, 22, 33, null),
        siWithCost = new SOCPossiblePickSpecialItem(pl2, "TestSIWithCost", 11, 22, 33, SI_COST);

    /**
     * Test {@link SOCBuildPlan#getTotalResourcesForBuildPlan()}
     * and {@link SOCBuildPlan#getFirstPieceResources()}.
     */
    @Test
    public void testGetResources()
    {
        final SOCBuildPlanStack bp = new SOCBuildPlanStack();

        assertTrue(bp.isEmpty());
        ResourceSet rs = bp.getFirstPieceResources();
        assertNotNull("getFirstPieceResources should not be null", rs);
        assertEquals(SOCResourceSet.EMPTY_SET, rs);
        rs = bp.getTotalResourcesForBuildPlan();
        assertNotNull("getTotalResourcesForBuildPlan should not be null", rs);
        assertEquals(SOCResourceSet.EMPTY_SET, rs);

        bp.push(nr2);
        bp.push(nr1);
        assertEquals(2, bp.getPlanDepth());

        rs = bp.getFirstPieceResources();
        assertNotNull(rs);
        assertEquals(SOCRoad.COST, rs);
        rs = bp.getTotalResourcesForBuildPlan();
        assertNotNull(rs);
        assertEquals(rs, new SOCResourceSet(2, 0, 0, 0, 2, 0));

        bp.clear();
        bp.push(siNoCost);
        assertEquals(1, bp.getPlanDepth());

        rs = bp.getFirstPieceResources();
        assertNotNull("piece cost null, but getFirstPieceResources should not be null", rs);
        assertEquals(SOCResourceSet.EMPTY_SET, rs);
        rs = bp.getTotalResourcesForBuildPlan();
        assertNotNull("piece cost null, but getTotalResourcesForBuildPlan should not be null", rs);
        assertEquals(SOCResourceSet.EMPTY_SET, rs);

        bp.push(siWithCost);
        rs = bp.getFirstPieceResources();
        assertNotNull(rs);
        assertEquals(SI_COST, rs);
        rs = bp.getTotalResourcesForBuildPlan();
        assertNotNull(rs);
        assertEquals(SI_COST, rs);

        bp.push(nr1);
        rs = bp.getFirstPieceResources();
        assertEquals(SOCRoad.COST, rs);
        rs = bp.getTotalResourcesForBuildPlan();
        assertNotNull(rs);
        assertEquals(rs, new SOCResourceSet(3, 0, 0, 1, 2, 0));
    }

    /**
     * Test behavior when adding or removing pieces and when empty:
     * {@link SOCBuildPlan#advancePlan()}, {@link SOCBuildPlan#clear()},
     * {@link SOCBuildPlanStack#push(SOCPossiblePiece)}, {@link SOCBuildPlan#isEmpty()},
     * {@link SOCBuildPlan#getPlanDepth()}, {@link SOCBuildPlan#getPlannedPiece(int)}.
     */
    @Test
    public void testAddRemoveEmpty()
    {
        final SOCBuildPlanStack bp = new SOCBuildPlanStack();

        assertTrue(bp.isEmpty());
        assertEquals(0, bp.getPlanDepth());
        try {
            bp.advancePlan();
            fail("advancePlan when empty should throw excep.");
        } catch (NoSuchElementException e) {}
        try {
            bp.getPlannedPiece(0);
            fail("getPlannedPiece when empty should throw excep.");
        } catch (IndexOutOfBoundsException e) {}
        try {
            bp.getFirstPiece();
            fail("getFirstPiece when empty should throw excep.");
        } catch (IndexOutOfBoundsException e) {}

        bp.clear();  // should not fail if empty
        assertTrue(bp.isEmpty());
        assertEquals(0, bp.getPlanDepth());

        bp.push(nr2);
        assertFalse(bp.isEmpty());
        assertEquals(1, bp.getPlanDepth());
        assertTrue(nr2 == bp.getFirstPiece());     // same reference, not just .equals()
        assertTrue(nr2 == bp.getPlannedPiece(0));  // same reference
        try {
            bp.getPlannedPiece(-1);
            fail("getPlannedPiece(-1) should throw excep.");
        } catch (IndexOutOfBoundsException e) {}
        try {
            bp.getPlannedPiece(1);
            fail("getPlannedPiece out of range should throw excep.");
        } catch (IndexOutOfBoundsException e) {}

        bp.push(nr1);
        assertEquals(2, bp.getPlanDepth());
        assertTrue(nr1 == bp.getFirstPiece());
        assertTrue(nr1 == bp.getPlannedPiece(0));
        assertTrue(nr2 == bp.getPlannedPiece(1));

        assertTrue(nr1 == bp.advancePlan());
        assertEquals(1, bp.getPlanDepth());
        assertTrue(nr2 == bp.getFirstPiece());
        assertTrue(nr2 == bp.getPlannedPiece(0));

        // can add different piece after advancing
        bp.push(siNoCost);
        assertEquals(2, bp.getPlanDepth());
        assertTrue(siNoCost == bp.getFirstPiece());
        assertTrue(siNoCost == bp.getPlannedPiece(0));
        assertTrue(nr2 == bp.getPlannedPiece(1));

        assertTrue(siNoCost == bp.advancePlan());
        assertEquals(1, bp.getPlanDepth());
        assertTrue(nr2 == bp.getFirstPiece());
        assertTrue(nr2 == bp.getPlannedPiece(0));

        assertTrue(nr2 == bp.advancePlan());
        assertTrue(bp.isEmpty());
    }

}
