/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020-2025 Jeremy D Monin <jeremy@nand.net>
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

package soctest.message;

import java.util.HashMap;

import soc.message.SOCDevCardAction;
import soc.message.SOCGameElements;
import soc.message.SOCGameElements.GEType;
import soc.message.SOCInventoryItemAction;
import soc.message.SOCPlayerElement;
import soc.message.SOCPlayerElement.PEType;
import soc.message.SOCSetSpecialItem;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for string constants seen in message classes like {@link SOCDevCardAction}
 * and "int-valued" enum fields like {@link SOCGameElements.GEType}.
 * @since 2.5.00
 */
public class TestStringConstantsAndEnums
{
    /** Test {@link SOCDevCardAction#ACTION_STRINGS} and corresponding int constants. */
    @Test
    public void testDevCardAction()
    {
        final String[] ACTION_STRINGS = SOCDevCardAction.ACTION_STRINGS;
        assertEquals(7, ACTION_STRINGS.length);
        assertEquals("DRAW", ACTION_STRINGS[SOCDevCardAction.DRAW]);
        assertEquals("PLAY", ACTION_STRINGS[SOCDevCardAction.PLAY]);
        assertEquals("ADD_NEW", ACTION_STRINGS[SOCDevCardAction.ADD_NEW]);
        assertEquals("ADD_OLD", ACTION_STRINGS[SOCDevCardAction.ADD_OLD]);
        assertEquals("CANNOT_PLAY", ACTION_STRINGS[SOCDevCardAction.CANNOT_PLAY]);
        assertEquals("REMOVE_NEW", ACTION_STRINGS[SOCDevCardAction.REMOVE_NEW]);
        assertEquals("REMOVE_OLD", ACTION_STRINGS[SOCDevCardAction.REMOVE_OLD]);
    }

    /**
     * Test {@link SOCInventoryItemAction#toString()}
     * and corresponding int constants like {@link SOCInventoryItemAction#ADD_PLAYABLE}.
     * @since 2.7.00
     */
    @Test
    public void testInventoryItemAction()
    {
        assertEquals(1, SOCInventoryItemAction.BUY);
        assertEquals(2, SOCInventoryItemAction.ADD_PLAYABLE);
        assertEquals(3, SOCInventoryItemAction.ADD_OTHER);
        assertEquals(4, SOCInventoryItemAction.PLAY);
        assertEquals(5, SOCInventoryItemAction.CANNOT_PLAY);
        assertEquals(6, SOCInventoryItemAction.PLAYED);
        assertEquals(7, SOCInventoryItemAction.PLACING_EXTRA);
        assertEquals(8, SOCInventoryItemAction.REMOVE_PLAYABLE);
        assertEquals(9, SOCInventoryItemAction.REMOVE_OTHER);

        assertEquals
            ("SOCInventoryItemAction:game=ga|playerNum=3|action=BUY|itemType=-1|kept=false|isVP=true|canCancel=false",
             new SOCInventoryItemAction("ga", 3, SOCInventoryItemAction.BUY, -1, false, true, false).toString());
        assertEquals
            ("SOCInventoryItemAction:game=ga|playerNum=3|action=ADD_PLAYABLE|itemType=2|kept=false|isVP=false|canCancel=false",
             new SOCInventoryItemAction("ga", 3, SOCInventoryItemAction.ADD_PLAYABLE, 2, false, false, false).toString());
        assertEquals
            ("SOCInventoryItemAction:game=ga|playerNum=3|action=ADD_OTHER|itemType=5|kept=true|isVP=false|canCancel=true",
             new SOCInventoryItemAction("ga", 3, SOCInventoryItemAction.ADD_OTHER, 5, true, false, true).toString());
        assertEquals
            ("SOCInventoryItemAction:game=ga|playerNum=4|action=PLAY|itemType=-2|rc=0",
             new SOCInventoryItemAction("ga", 4, SOCInventoryItemAction.PLAY, -2).toString());
        assertEquals
            ("SOCInventoryItemAction:game=ga|playerNum=3|action=CANNOT_PLAY|itemType=3|rc=1",
             new SOCInventoryItemAction("ga", 3, SOCInventoryItemAction.CANNOT_PLAY, 3, 1).toString());
        assertEquals
            ("SOCInventoryItemAction:game=ga|playerNum=4|action=PLAYED|itemType=7|kept=true|isVP=false|canCancel=true",
             new SOCInventoryItemAction("ga", 4, SOCInventoryItemAction.PLAYED, 7, true, false, true).toString());
        assertEquals
            ("SOCInventoryItemAction:game=ga|playerNum=3|action=PLACING_EXTRA|itemType=-3|kept=false|isVP=false|canCancel=false",
             new SOCInventoryItemAction("ga", 3, SOCInventoryItemAction.PLACING_EXTRA, -3, false, false, false).toString());
        assertEquals
            ("SOCInventoryItemAction:game=ga|playerNum=5|action=REMOVE_PLAYABLE|itemType=-1|kept=false|isVP=true|canCancel=false",
             new SOCInventoryItemAction("ga", 5, SOCInventoryItemAction.REMOVE_PLAYABLE, -1, false, true, false).toString());
        assertEquals
            ("SOCInventoryItemAction:game=ga|playerNum=3|action=REMOVE_OTHER|itemType=7|kept=true|isVP=false|canCancel=false",
             new SOCInventoryItemAction("ga", 3, SOCInventoryItemAction.REMOVE_OTHER, 7, true, false, false).toString());
    }

    /**
     * Test the {@link SOCGameElements.GEType} enum's {@link SOCGameElements.GEType#valueOf(int) .valueOf(int)}
     * and {@link SOCGameElements.GEType#getValues(SOCGameElements.GEType[]) .getValues(GEType[])} methods
     * and {@link SOCGameElements.GEType#getValue() .getValue()} uniqueness.
     * @see #testPlayerElement()
     */
    @Test
    public void testGameElements()
    {
        assertEquals(0, GEType.UNKNOWN_TYPE.getValue());
        assertEquals(1, GEType.ROUND_COUNT.getValue());
        assertEquals(2, GEType.DEV_CARD_COUNT.getValue());  // DEV_CARD_COUNT mentioned in getValue javadoc

        assertEquals(GEType.UNKNOWN_TYPE, GEType.valueOf(0));
        assertEquals(GEType.ROUND_COUNT, GEType.valueOf(1));
        assertEquals(GEType.DEV_CARD_COUNT, GEType.valueOf(2));
        assertNull(GEType.valueOf(-42));

        // for completeness, test autogenerated valueOf(String)
        assertEquals(GEType.UNKNOWN_TYPE, GEType.valueOf("UNKNOWN_TYPE"));
        assertEquals(GEType.ROUND_COUNT, GEType.valueOf("ROUND_COUNT"));
        assertEquals(GEType.DEV_CARD_COUNT, GEType.valueOf("DEV_CARD_COUNT"));
        try
        {
            GEType.valueOf("NOT_A_DECLARED_CONSTANT");
            fail("GEType.valueOf(\"??\") should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        assertArrayEquals
            (new int[]{3, 6, 7, 8, 9},
             GEType.getValues(new GEType[]
                 {GEType.FIRST_PLAYER, GEType.LONGEST_ROAD_PLAYER, GEType.SPECIAL_BUILDING_AFTER_PLAYER,
                  GEType.SHIP_PLACED_THIS_TURN_EDGE, GEType.IS_PLACING_ROBBER_FOR_KNIGHT_CARD_FLAG}));

        // GEType uniqueness
        final HashMap<Integer, GEType> typeValues = new HashMap<>();
        for (GEType t : GEType.values())
        {
            final Integer ival = Integer.valueOf(t.getValue());
            GEType entry = typeValues.get(ival);
            if (entry != null)
                fail("GEType: dupe value " + ival + " for " + t + " and " + entry);
            else
                typeValues.put(ival, t);
        }
    }

    /**
     * Test {@link SOCPlayerElement#ACTION_STRINGS} and corresponding int constants.
     * Test the {@link SOCPlayerElement.PEType} enum's {@link SOCPlayerElement.PEType#valueOf(int) .valueOf(int)}
     * and {@link SOCPlayerElement.PEType#getValues(PEType[]) .getValues(PEType[])} methods
     * and {@link SOCPlayerElement.PEType#getValue() .getValue()} uniqueness.
     * @see #testGameElements()
     */
    @Test
    public void testPlayerElement()
    {
        final String[] ACTION_STRINGS = SOCPlayerElement.ACTION_STRINGS;
        assertEquals(3, ACTION_STRINGS.length);
        assertEquals("SET", ACTION_STRINGS[SOCPlayerElement.SET - 100]);
        assertEquals("GAIN", ACTION_STRINGS[SOCPlayerElement.GAIN - 100]);
        assertEquals("LOSE", ACTION_STRINGS[SOCPlayerElement.LOSE - 100]);

        assertEquals(0, PEType.UNKNOWN_TYPE.getValue());
        assertEquals(1, PEType.CLAY.getValue());
        assertEquals(15, PEType.NUMKNIGHTS.getValue());  // NUMKNIGHTS mentioned in getValue javadoc

        assertEquals(PEType.UNKNOWN_TYPE, PEType.valueOf(0));
        assertEquals(PEType.CLAY, PEType.valueOf(1));
        assertEquals(PEType.NUMKNIGHTS, PEType.valueOf(15));
        assertNull(PEType.valueOf(-42));

        // for completeness, test autogenerated valueOf(String)
        assertEquals(PEType.UNKNOWN_TYPE, PEType.valueOf("UNKNOWN_TYPE"));
        assertEquals(PEType.CLAY, PEType.valueOf("CLAY"));
        assertEquals(PEType.NUMKNIGHTS, PEType.valueOf("NUMKNIGHTS"));
        try
        {
            PEType.valueOf("NOT_A_DECLARED_CONSTANT");
            fail("PEType.valueOf(\"??\") should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        assertArrayEquals
            (new int[]{2, 3, 4, 5, 6, 17, 22, 23, 24, 25, 106},
             PEType.getValues(new PEType[]
                 {
                     PEType.ORE, PEType.SHEEP, PEType.WHEAT, PEType.WOOD, PEType.UNKNOWN_RESOURCE,
                     PEType.RESOURCE_COUNT, PEType.NUM_PLAYED_DEV_CARD_DISC, PEType.NUM_PLAYED_DEV_CARD_MONO,
                     PEType.NUM_PLAYED_DEV_CARD_ROADS, PEType.NUM_UNDOS_REMAINING, PEType.SCENARIO_CLOTH_COUNT
                 }));

        // PEType uniqueness
        final HashMap<Integer, PEType> typeValues = new HashMap<>();
        for (PEType t : PEType.values())
        {
            final Integer ival = Integer.valueOf(t.getValue());
            PEType entry = typeValues.get(ival);
            if (entry != null)
                fail("PEType: dupe value " + ival + " for " + t + " and " + entry);
            else
                typeValues.put(ival, t);
        }
    }

    // SOCSetLastAction uses GameAction.ActionType strings, which are tested in soctest.game.TestGameAction.

    /** Test {@link SOCSetSpecialItem#OPS_STRS} and corresponding int constants. */
    @Test
    public void testSetSpecialItem()
    {
        final String[] OPS_STRS = SOCSetSpecialItem.OPS_STRS;

        assertEquals(1 + SOCSetSpecialItem.OP_CLEAR_PICK, OPS_STRS.length);
        assertNull(OPS_STRS[0]);
        assertEquals("SET", OPS_STRS[SOCSetSpecialItem.OP_SET]);
        assertEquals("CLEAR", OPS_STRS[SOCSetSpecialItem.OP_CLEAR]);
        assertEquals("PICK", OPS_STRS[SOCSetSpecialItem.OP_PICK]);
        assertEquals("DECLINE", OPS_STRS[SOCSetSpecialItem.OP_DECLINE]);
        assertEquals("SET_PICK", OPS_STRS[SOCSetSpecialItem.OP_SET_PICK]);
        assertEquals("CLEAR_PICK", OPS_STRS[SOCSetSpecialItem.OP_CLEAR_PICK]);
    }

}
