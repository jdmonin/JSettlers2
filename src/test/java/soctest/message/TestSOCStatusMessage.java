/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2021 Jeremy D Monin <jeremy@nand.net>
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

import soc.message.SOCStatusMessage;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCStatusMessage}.
 * @since 2.5.00
 */
public class TestSOCStatusMessage
{

    /**
     * Test a few methods for status values and fallback at older client versions.
     *<UL>
     * <LI> {@link SOCStatusMessage#buildForVersion(int, int, String)}
     * <LI> {@link SOCStatusMessage#statusFallbackForVersion(int, int)}
     * <LI> {@link SOCStatusMessage#statusValidAtVersion(int, int)}
     *</UL>
     */
    @Test
    public void testStatusFallback()
    {
        final int[][] TEST_SV_VERSION_FALLBACKS =
            {
                // {status value, min version recognizing it, fallback status value}

                {SOCStatusMessage.SV_OK_DEBUG_MODE_ON, 2000, SOCStatusMessage.SV_OK},
                {SOCStatusMessage.SV_PW_REQUIRED,      1119, SOCStatusMessage.SV_PW_WRONG},
                {SOCStatusMessage.SV_ACCT_CREATED_OK_FIRST_ONE,   1120, SOCStatusMessage.SV_ACCT_CREATED_OK},
                {SOCStatusMessage.SV_GAME_CLIENT_FEATURES_NEEDED, 2000, SOCStatusMessage.SV_NEWGAME_OPTION_VALUE_TOONEW},
                {SOCStatusMessage.SV_OK_SET_NICKNAME,  1200, -1},  // throws IllegalArgumentException
                // explicitly test generic fallback to NOT_OK:
                {SOCStatusMessage.SV_NEWGAME_TOO_MANY_CREATED,    1110, SOCStatusMessage.SV_NOT_OK_GENERIC},
            };

        for (final int[] TEST : TEST_SV_VERSION_FALLBACKS)
        {
            final int sv_newcli = TEST[0], old_vers = TEST[1] - 1, sv_oldcli = TEST[2];

            assertTrue(sv_oldcli < sv_newcli);  // consistency of test case; no message needed, shouldn't fail except while updating test class

            assertTrue
                ("expected true: statusValidAtVersion(" + sv_newcli + ", " + (old_vers + 1) + ')',
                 SOCStatusMessage.statusValidAtVersion(sv_newcli, old_vers + 1));
            assertFalse
                ("expected false: statusValidAtVersion(" + sv_newcli + ", " + old_vers + ')',
                 SOCStatusMessage.statusValidAtVersion(sv_newcli, old_vers));

            if (sv_oldcli == -1)
            {
                try
                {
                    final int sv = SOCStatusMessage.statusFallbackForVersion(sv_newcli, old_vers);
                    assertNotEquals(-1, sv);  // just to use value of sv
                } catch (IllegalArgumentException e) {
                    continue;
                }
                fail("expected IllegalArgumentException from statusFallbackForVersion(" + sv_newcli + ", " + old_vers + ')');
            }

            assertEquals
                ("statusFallbackForVersion(" + sv_newcli + ", " + old_vers + ')',
                 sv_oldcli, SOCStatusMessage.statusFallbackForVersion(sv_newcli, old_vers));

            // generic test for generic fallback to NOT_OK
            if ((old_vers > 1106) && (sv_oldcli >= SOCStatusMessage.SV_NEWGAME_OPTION_UNKNOWN))
                assertEquals
                    ("statusFallbackForVersion(" + sv_newcli + ", 1106)",
                     SOCStatusMessage.SV_NOT_OK_GENERIC, SOCStatusMessage.statusFallbackForVersion(sv_newcli, 1106));

            SOCStatusMessage msg = SOCStatusMessage.buildForVersion(sv_newcli, old_vers, "x");
            assertNotNull(msg);
            assertEquals
                ("sv from buildForVersion(" + sv_newcli + ", " + old_vers + ')',
                 sv_oldcli, msg.getStatusValue());
        }
    }

}
