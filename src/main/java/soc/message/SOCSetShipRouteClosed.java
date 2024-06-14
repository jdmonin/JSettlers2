/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2022,2024 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;

import java.util.List;

import soc.game.SOCBoardLarge;  // javadocs only
import soc.game.SOCShip;  // javadocs only


/**
 * This game data message from server reopens or closes a shipping trade route.
 * Typically this is sent (to reopen) near start of a sequence to undo placing or moving a ship
 * (before sending {@link SOCUndoPutPiece}).
 * Closing a ship route is handled at client's game logic while the pieces are placed,
 * not by sending a message from the server.
 *<P>
 * Because the open/closed flag is element 0 of the sent parameters, client should call
 * <tt>{@link SOCBoardLarge#setShipsClosed(boolean, int[], int) board.setShipsClosed}
 * ({@link #isClosed()}, {@link SOCMessageTemplateMi#getParams() getParams()}, 1)</tt>.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.7.00
 */
public class SOCSetShipRouteClosed extends SOCMessageTemplateMi
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2700L;  // v2.7.00

    /**
     * Version number (2.7.00) where this message type was introduced.
     */
    public static final int MIN_VERSION = 2700;

    /**
     * Create a SetShipRouteClosed message.
     *
     * @param gaName  Name of the game
     * @param setClosed  Is this message to close the route, or reopen it?
     * @param edges  Each ship's edge coordinate; not null or empty
     * @throws NullPointerException if {@code edges} null or empty
     */
    public SOCSetShipRouteClosed(final String gaName, final boolean setClosed, final int[] edges)
        throws NullPointerException
    {
        super(SETSHIPROUTECLOSED, gaName, new int[1 + edges.length]);
        final int L = edges.length;
        if (L == 0)
            throw new NullPointerException("length 0");

        pa[0] = (setClosed) ? 1 : 0;
        System.arraycopy(edges, 0, pa, 1, L);
    }

    /**
     * Minimum version where this message type is used.
     * BOTGAMEDATACHECK was introduced in v2.7.00 ({@link #MIN_VERSION}).
     * @return Version number, 2700 for JSettlers 2.7.00
     */
    @Override
    public final int getMinimumVersion() { return MIN_VERSION; }

    /**
     * Is this message to close the route, or reopen it?
     * @return value to pass to {@link SOCShip#setClosed(boolean)}
     */
    public boolean isClosed()
    {
        return (pa[0] != 0);
    }

    /**
     * Parse the command String list into a SOCSetShipRouteClosed message.
     *
     * @param pa   the parameters; length 3 or more required.
     *     Built by constructor at server. <pre>
     * pa[0] = gameName
     * pa[1] = setClosed
     * pa[2] = edges[0]
     * pa[3] = edges[1]
     * pa[4] = edges[2]
     * ...</pre>
     * @return    a SOCBotGameDataCheck message, or null if parsing errors
     */
    public static SOCSetShipRouteClosed parseDataStr(final List<String> pa)
    {
        if (pa == null)
            return null;
        final int L = pa.size();
        if (L < 3)
            return null;

        try
        {
            final String gaName = pa.get(0);
            final boolean setClosed = (Integer.parseInt(pa.get(1)) != 0);

            int[] edges = new int[L - 2];
            for (int i = 2; i < L; ++i)
                edges[i - 2] = Integer.parseInt(pa.get(i));

            return new SOCSetShipRouteClosed(gaName, setClosed, edges);
        } catch (Exception e) {
            return null;
        }
    }

}
