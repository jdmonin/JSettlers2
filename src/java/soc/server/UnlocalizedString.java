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
package soc.server;

import soc.util.SOCStringManager;  // for javadocs only

/**
 * Marker for an I18N string to be localized and sent to game members: Key and optional parameters.
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @see SOCGameHandler#sendGamePendingMessages(soc.game.SOCGame, boolean)
 */
public final class UnlocalizedString
{
    /** Message localization key, to retrieve text with {@link SOCStringManager#get(String)} */
    public final String key;

    /** Optional parameters to use when localizing {@link #key}'s string, or null */
    public final Object[] params;

    /**
     * Create an UnlocalizedString with no parameters.
     *
     * @param k  Message localization key, to retrieve text with {@link SOCStringManager#get(String)}
     * @throws IllegalArgumentException if {@code k} is null
     */
    public UnlocalizedString(final String k)
        throws IllegalArgumentException
    {
        this(k, (Object[]) null);
    }

    /**
     * Create an UnlocalizedString with optional parameters.
     *
     * @param k  Message localization key, to retrieve text with {@link SOCStringManager#get(String)}
     * @param p  Parameters to use when localizing {@code k}'s string
     * @throws IllegalArgumentException if {@code k} is null or {@code p} is a 0-length array
     */
    public UnlocalizedString(final String k, final Object ... p)
        throws IllegalArgumentException
    {
        if ((k == null) || ((p != null) && (p.length == 0)))
            throw new IllegalArgumentException();

        key = k;
        params = p;
    }

}
