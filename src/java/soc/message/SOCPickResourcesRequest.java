/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012 Jeremy D Monin <jeremy@nand.net>
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

import java.util.StringTokenizer;

/**
 * This message from server informs the client that in a game they're playing,
 * they must pick which resources they want from a gold hex.
 * Client should respond with {@link SOCPickResources}.
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCPickResourcesRequest extends SOCMessageTemplate1i
{
    private static final long serialVersionUID = 2000L;

    /**
     * Create a SOCPickResourcesRequest message.
     *
     * @param ga  the name of the game
     * @param numRes  number of resources the client should pick
     */
    public SOCPickResourcesRequest(String ga, final int numRes)
    {
        super (PICKRESOURCESREQUEST, ga, numRes);
    }

    /**
     * PICKRESOURCESREQUEST sep game sep2 numResources
     *
     * @param ga  the name of the game
     * @param numRes  number of resources the client should pick
     * @return the command string
     */
    public static String toCmd(String ga, final int numRes)
    {
        return PICKRESOURCESREQUEST + sep + ga + sep2 + numRes;
    }

    /**
     * Parse the command String into a SOCPickResourcesRequest message
     *
     * @param s   the String to parse
     * @return    a SOCPickResourcesRequest message, or null if the data is garbled
     */
    public static SOCPickResourcesRequest parseDataStr(String s)
    {
        final String ga; // the game name
        final int numRes; // the number of resources

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            numRes = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPickResourcesRequest(ga, numRes);
    }

    /**
     * Minimum version where this message type is used.
     * PICKRESOURCESREQUEST introduced in 2.0.00 for gold hexes on the large sea board.
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    public int getMinimumVersion()
    {
        return 2000;
    }

}
