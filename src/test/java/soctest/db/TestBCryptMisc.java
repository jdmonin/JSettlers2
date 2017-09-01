/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
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
package soctest.db;

import java.security.SecureRandom;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.server.database.BCrypt;
import soc.server.database.SOCDBHelper;

/**
 * A few tests for {@link BCrypt}-related items; not the {@code jBCrypt} unit tests.
 * Running these tests doesn't require a database or jdbc driver JAR.
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestBCryptMisc
{
    private static final int TEST_MAXLEN = 1 + SOCDBHelper.PW_MAX_LEN_SCHEME_BCRYPT,
        TEST_MINLEN = SOCDBHelper.PW_MAX_LEN_SCHEME_NONE;

    /** Long password, length {@link #TEST_MAXLEN} + 1 */
    private static StringBuilder sbLongPW;

    /**
     * For self-testing, 0 or the nonzero length at which to truncate {@link #sbLongPW}
     * in {@link #bcryptAtLen(int, SecureRandom)}. If negative, {@link #bcryptAtLen(int, String)}
     * returns the truncated password without {@code BCrypt}ing it.
     */
    private static int selftest_truncateAt = 0;

    @BeforeClass
    public static void setup()
    {
        sbLongPW = new StringBuilder(TEST_MAXLEN + 1);
        for (int i = 0; i <= TEST_MAXLEN; ++i)
            sbLongPW.append((char) (i + '!'));  // start with lowest non-space ascii char

        // TODO consider testing with unicode chars which are longer in UTF-8
    }

    /** For {@link #testBCryptMaxLen()}, encrypt a password of length {@code L} from {@link #sbLongPW}. */
    private static String bcryptAtLen(int L, final String salt)
    {
        if (selftest_truncateAt != 0)
        {
            if (selftest_truncateAt < 0)
            {
                if (L > -selftest_truncateAt)
                    L = -selftest_truncateAt;
                return sbLongPW.substring(0, L);
            } else if (L > selftest_truncateAt) {
                L = selftest_truncateAt;
            }
        }

        return BCrypt.hashpw
            (sbLongPW.substring(0, L), salt);
    }

    /**
     * Test {@link BCrypt} at various password lengths to find its max length without password truncation, if any.
     * @return Max without truncation, or 0 if no truncation at {@link #TEST_MAXLEN}
     * @throws IllegalStateException if truncation at {@link #TEST_MINLEN}
     */
    private final static int findBCryptMaxLen()
        throws IllegalStateException
    {
        // constant salt for testing only
        final String salt = BCrypt.gensalt(SOCDBHelper.BCRYPT_MIN_WORK_FACTOR);

        // test at length max, at max - 1: if different, then OK, no truncation
        int max = TEST_MAXLEN, min = max - 1;
        String vmax = bcryptAtLen(max, salt), vmin = bcryptAtLen(min, salt);
        if (! vmin.equals(vmax))
            return 0;

        // bisect a range of values for length

        min = TEST_MINLEN;
        vmin = bcryptAtLen(min, salt);
        if (vmin.equals(vmax))
            throw new IllegalStateException("truncation at TEST_MINLEN");

        int mid = (min + max) / 2;
        do
        {
            // assert: min is a short enough length that the password isn't truncated at min;
            // max is long enough that the password gets truncated

            String vmid = bcryptAtLen(mid, salt);
            if (vmid.equals(vmax))
            {
                // still identical: search lower
                max = mid;
            } else {
                // ok at mid: search higher
                min = mid;
                vmin = vmid;
            }
            mid = (min + max) / 2;
        } while ((mid != min) && (mid != max));

        return mid;
    }

    /** Test {@link BCrypt} max password length against {@link SOCDBHelper#PW_MAX_LEN_SCHEME_BCRYPT} */
    @Test
    public final void testBCryptMaxLen()
    {
        selftest_truncateAt = 0;
        assertEquals(0, findBCryptMaxLen());
    }

    /** Self-test {@link #findBCryptMaxLen()} search with high truncation length */
    @Test
    public final void testSelfS_TruncatedHigh()
    {
        selftest_truncateAt = -(TEST_MAXLEN - 1);
        assertEquals(-(selftest_truncateAt) - 1, findBCryptMaxLen());
    }

    /** Self-test {@link #findBCryptMaxLen()} search with low truncation length */
    @Test
    public final void testSelfS_TruncatedLow()
    {
        selftest_truncateAt = -(TEST_MINLEN + 11);
        assertEquals(-(selftest_truncateAt) - 1, findBCryptMaxLen());
    }

    /** Test bcrypt results in {@link #findBCryptMaxLen()} with high truncation length */
    @Test
    public final void testSelfB_TruncatedHigh()
    {
        selftest_truncateAt = TEST_MAXLEN - 1;
        assertEquals(selftest_truncateAt - 1, findBCryptMaxLen());
    }

    /** Test bcrypt results in {@link #findBCryptMaxLen()} with low truncation length */
    @Test
    public final void testSelfB_TruncatedLow()
    {
        selftest_truncateAt = TEST_MINLEN + 11;
        assertEquals(selftest_truncateAt - 1, findBCryptMaxLen());
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.db.TestBCryptMisc");
    }

}
