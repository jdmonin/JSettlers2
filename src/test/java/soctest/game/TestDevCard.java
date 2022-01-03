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

import soc.game.SOCDevCard;
import soc.game.SOCDevCardConstants;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCDevCard} and {@link SOCDevCardConstants}.
 *
 * @since 2.4.00
 */
public class TestDevCard
{

    /** Test expected values and min/max range of {@link SOCDevCardConstants} card type fields. */
    @Test
    public void testDevCardConstants()
    {
        assertEquals(0, SOCDevCardConstants.MIN);
        assertEquals(1, SOCDevCardConstants.MIN_KNOWN);
        assertEquals(0, SOCDevCardConstants.UNKNOWN);
        assertEquals(1, SOCDevCardConstants.ROADS);
        assertEquals(2, SOCDevCardConstants.DISC);
        assertEquals(3, SOCDevCardConstants.MONO);
        assertEquals(4, SOCDevCardConstants.CAP);
        assertEquals(5, SOCDevCardConstants.MARKET);
        assertEquals(6, SOCDevCardConstants.UNIV);
        assertEquals(7, SOCDevCardConstants.TEMP);
        assertEquals(8, SOCDevCardConstants.CHAPEL);
        assertEquals(9, SOCDevCardConstants.KNIGHT);
        assertEquals(1 + SOCDevCardConstants.KNIGHT, SOCDevCardConstants.MAXPLUSONE);
    }

    /**
     * Test {@link SOCDevCard#getCardTypeName(int)}, lightly test {@link SOCDevCard#getCardType(String)}.
     * @see #testGetCardType()
     */
    @Test
    public void testGetDevCardTypeName()
    {
        // hardcode currently known names, to ensure compat when loading a savegame across different versions
        final String[] knownNames =
            { "UNKNOWN", "ROADS", "DISC", "MONO", "CAP", "MARKET", "UNIV",
              "TEMPLE" /* not ambiguous abbreviation TEMP */, "CHAPEL", "KNIGHT"
            };

        assertEquals(SOCDevCardConstants.MAXPLUSONE, knownNames.length);
        for (int i = 0; i < knownNames.length; ++i)
        {
            final String name = SOCDevCard.getCardTypeName(i);
            assertEquals(knownNames[i], name);
            assertTrue("expected regex match for \"" + name + "\"",
                TestPlayingPiece.TYPENAME_PATTERN.matcher(name).matches());

            assertEquals(i, SOCDevCard.getCardType(knownNames[i]));
        }

        assertEquals
            (Integer.toString(SOCDevCardConstants.MAXPLUSONE),
             SOCDevCard.getCardTypeName(SOCDevCardConstants.MAXPLUSONE));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDevCardTypeName_negative()
    {
        assertEquals("-WONTREACH", SOCDevCard.getCardTypeName(-42));  // < 0: should throw exception
    }

    /**
     * The rest of the {@link SOCDevCard#getCardType(String)} tests.
     * @see #testGetDevCardTypeName()
     */
    @Test
    public void testGetCardType()
    {
        assertEquals(42, SOCDevCard.getCardType("42"));
        assertEquals(1, SOCDevCard.getCardType("1"));
        assertEquals
            (SOCDevCardConstants.MAXPLUSONE,
             SOCDevCard.getCardType(Integer.toString(SOCDevCardConstants.MAXPLUSONE)));
        assertEquals
            (100 + SOCDevCardConstants.MAXPLUSONE,
             SOCDevCard.getCardType(Integer.toString(100 + SOCDevCardConstants.MAXPLUSONE)));

        assertEquals(0, SOCDevCard.getCardType("THIS_CARDTYPE_WILL_NEVER_BE_DEFINED"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetCardType_null()
    {
        assertEquals(42, SOCDevCard.getCardType(null));  // null: should throw exception
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetCardType_empty()
    {
        assertEquals(42, SOCDevCard.getCardType(""));  // empty string: should throw exception
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetCardType_wrongCharClass_p()
    {
        assertEquals(42, SOCDevCard.getCardType("{something}"));  // punctuation: should throw exception
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetCardType_wrongCharClass_lc()
    {
        assertEquals(42, SOCDevCard.getCardType("something"));  // lowercase: should throw exception
    }

    @Test(expected=NumberFormatException.class)
    public void testGetCardType_notReallyNumeric()
    {
        assertEquals(42, SOCDevCard.getCardType("4XYZ"));  // malformed: should throw exception
    }

}
