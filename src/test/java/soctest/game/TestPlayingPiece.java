/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
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

package soctest.game;

import java.util.regex.Pattern;

import soc.game.SOCPlayingPiece;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCPlayingPiece}.
 *
 * @since 2.4.00
 */
public class TestPlayingPiece
{
    /**
     * Pattern required for unique "technical names" in {@link soc.game.SOCDevCard}, {@link SOCPlayingPiece}:
     * See {@link SOCPlayingPiece#getTypeName(int)}, {@link soc.game.SOCDevCard#getCardTypeName(int)}, etc.
     */
    static final Pattern TYPENAME_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]+$");

    /** Test {@link #TYPENAME_PATTERN}. */
    @Test
    public void testTypeNamePattern()
    {
        assertTrue(TYPENAME_PATTERN.matcher("A1").matches());
        assertTrue(TYPENAME_PATTERN.matcher("A315").matches());
        assertTrue(TYPENAME_PATTERN.matcher("A3_1_5").matches());
        assertTrue(TYPENAME_PATTERN.matcher("ANAME_UNDERSCORED").matches());
        assertFalse(TYPENAME_PATTERN.matcher("lowercase").matches());
        assertFalse(TYPENAME_PATTERN.matcher("MixedCase").matches());
        assertFalse(TYPENAME_PATTERN.matcher(" whitespace").matches());
        assertFalse(TYPENAME_PATTERN.matcher("1LEADINGDIGIT").matches());
    }

    /** Test expected values and min/max range of piece type fields. */
    @Test
    public void testPieceTypeConstants()
    {
        assertEquals(0, SOCPlayingPiece.MIN);
        assertEquals(0, SOCPlayingPiece.ROAD);
        assertEquals(1, SOCPlayingPiece.SETTLEMENT);
        assertEquals(2, SOCPlayingPiece.CITY);
        assertEquals(3, SOCPlayingPiece.SHIP);
        assertEquals(4, SOCPlayingPiece.FORTRESS);
        assertEquals(5, SOCPlayingPiece.VILLAGE);
        assertEquals(1 + SOCPlayingPiece.VILLAGE, SOCPlayingPiece.MAXPLUSONE);
    }

    // Tests for SOCPlayingPiece-specific getTypeName, getType:

    /**
     * Test {@link SOCPlayingPiece#getTypeName(int)}, lightly test {@link SOCPlayingPiece#getType(String)}.
     * @see #testGetPieceType()
     * @see #testSharedGetPieceTypeName()
     */
    @Test
    public void testGetPieceTypeName()
    {
        // hardcode currently known names, to ensure compat when loading a savegame across different versions
        final String[] knownNames =
            { "ROAD", "SETTLEMENT", "CITY", "SHIP", "FORTRESS", "VILLAGE" };

        assertEquals(SOCPlayingPiece.MAXPLUSONE, knownNames.length);
        for (int i = 0; i < knownNames.length; ++i)
        {
            final String name = SOCPlayingPiece.getTypeName(i);
            assertEquals(knownNames[i], name);
            assertTrue("expected regex match for \"" + name + "\"",
                TYPENAME_PATTERN.matcher(name).matches());

            assertEquals(i, SOCPlayingPiece.getType(knownNames[i]));
        }

        assertEquals
            (Integer.toString(SOCPlayingPiece.MAXPLUSONE),
             SOCPlayingPiece.getTypeName(SOCPlayingPiece.MAXPLUSONE));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPieceTypeName_negative()
    {
        assertEquals("-WONTREACH", SOCPlayingPiece.getTypeName(-42));  // < 0: should throw exception
    }

    /**
     * The rest of the {@link SOCPlayingPiece#getType(String)} tests.
     * @see #testGetPieceTypeName()
     * @see #testSharedGetPieceType()
     */
    @Test
    public void testGetPieceType()
    {
        assertEquals(42, SOCPlayingPiece.getType("42"));
        assertEquals(1, SOCPlayingPiece.getType("1"));
        assertEquals
            (SOCPlayingPiece.MAXPLUSONE,
             SOCPlayingPiece.getType(Integer.toString(SOCPlayingPiece.MAXPLUSONE)));
        assertEquals
            (100 + SOCPlayingPiece.MAXPLUSONE,
             SOCPlayingPiece.getType(Integer.toString(100 + SOCPlayingPiece.MAXPLUSONE)));

        assertEquals(-1, SOCPlayingPiece.getType("THIS_PIECETYPE_WILL_NEVER_BE_DEFINED"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetPieceType_null()
    {
        assertEquals(42, SOCPlayingPiece.getType(null));  // null: should throw exception
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetPieceType_empty()
    {
        assertEquals(42, SOCPlayingPiece.getType(""));  // empty string: should throw exception
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetPieceType_wrongCharClass_p()
    {
        assertEquals(42, SOCPlayingPiece.getType("{something}"));  // punctuation: should throw exception
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetPieceType_wrongCharClass_lc()
    {
        assertEquals(42, SOCPlayingPiece.getType("something"));  // lowercase: should throw exception
    }

    @Test(expected=NumberFormatException.class)
    public void testGetPieceType_notReallyNumeric()
    {
        assertEquals(42, SOCPlayingPiece.getType("4XYZ"));  // malformed: should throw exception
    }

    // Tests for the general shared getTypeName, getType:

    /**
     * Test {@link SOCPlayingPiece#getTypeName(int, String[])},
     * lightly test {@link SOCPlayingPiece#getType(String, String[], int)}.
     * @see #testGetPieceTypeName()
     * @see #testSharedGetPieceType()
     */
    @Test
    public void testSharedGetPieceTypeName()
    {
        final String[] knownNames =
            { "NAME1", "NAME_2", "ANOTHER", "ONE_MORE" };

        for (int i = 0; i < knownNames.length; ++i)
        {
            final String name = SOCPlayingPiece.getTypeName(i, knownNames);
            assertEquals(knownNames[i], name);

            assertEquals(i, SOCPlayingPiece.getType(knownNames[i], knownNames, -1));
        }
        assertEquals("42", SOCPlayingPiece.getTypeName(42, knownNames));

        final String[] namesWithNulls =
            { null, "NAME1", null, "NAME3" };

        assertEquals("0", SOCPlayingPiece.getTypeName(0, namesWithNulls));
        assertEquals("NAME1", SOCPlayingPiece.getTypeName(1, namesWithNulls));
        assertEquals("2", SOCPlayingPiece.getTypeName(2, namesWithNulls));
        assertEquals("NAME3", SOCPlayingPiece.getTypeName(3, namesWithNulls));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSharedPieceTypeName_negative()
    {
        assertEquals("-WONTREACH", SOCPlayingPiece.getTypeName(-42, new String[]{} ));  // < 0: should throw exception
    }

    /**
     * The rest of the {@link SOCPlayingPiece#getType(String, String[], int)} tests.
     * @see #testSharedGetPieceTypeName()
     * @see #testGetPieceType()
     */
    @Test
    public void testSharedGetPieceType()
    {
        final String[] knownNames =
            { "NAME1", "NAME_2", "ANOTHER", "ONE_MORE" };

        assertEquals(42, SOCPlayingPiece.getType("42", knownNames, 0));
        assertEquals(1, SOCPlayingPiece.getType("1", knownNames, 0));
        assertEquals(142, SOCPlayingPiece.getType("142", knownNames, 0));

        assertEquals(2, SOCPlayingPiece.getType("THIS_TYPE_WILL_NEVER_BE_DEFINED", knownNames, 2));
        assertEquals(-1, SOCPlayingPiece.getType("THIS_TYPE_WILL_NEVER_BE_DEFINED", knownNames, -1));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSharedGetPieceType_null()
    {
        assertEquals(42, SOCPlayingPiece.getType(null, new String[]{}, 0));  // null: should throw exception
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSharedGetPieceType_empty()
    {
        assertEquals(42, SOCPlayingPiece.getType("", new String[]{}, 0));  // empty string: should throw exception
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSharedGetPieceType_wrongCharClass_p()
    {
        assertEquals(42, SOCPlayingPiece.getType("{something}", new String[]{}, 0));  // punctuation: should throw exception
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSharedGetPieceType_wrongCharClass_lc()
    {
        assertEquals(42, SOCPlayingPiece.getType("something", new String[]{}, 0));  // lowercase: should throw exception
    }

    @Test(expected=NumberFormatException.class)
    public void testSharedGetPieceType_notReallyNumeric()
    {
        assertEquals(42, SOCPlayingPiece.getType("4XYZ", new String[]{}, 0));  // malformed: should throw exception
    }

}
