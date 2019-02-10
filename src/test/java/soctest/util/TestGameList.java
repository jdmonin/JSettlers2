/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
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

package soctest.util;

import soc.util.SOCGameList;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCGameList}.
 */
public class TestGameList
{
    /** Tests for {@link SOCGameList#REGEX_ALL_DIGITS_OR_PUNCT} */
    @Test
    public void testRegexAllDigitsOrPunct()
    {
        assertFalse(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("").matches());
        assertTrue(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("1").matches());
        assertFalse(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("1a").matches());
        assertFalse(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("a1").matches());
        assertTrue(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("123").matches());
        assertTrue(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("๔").matches());  // thai
        // see also https://www.fileformat.info/info/unicode/category/Nd/list.htm

        assertTrue(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher(":").matches());
        assertTrue(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("'").matches());
        assertTrue(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("\"").matches());
        assertFalse(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher(":a").matches());
        assertFalse(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("a:").matches());
        assertTrue(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("1:23").matches());
        assertTrue(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("^~").matches());    // is only in \p{Punct}
        assertTrue(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("«123»").matches());  // fr
        assertTrue(SOCGameList.REGEX_ALL_DIGITS_OR_PUNCT.matcher("。").matches());  // jp
    }

}
