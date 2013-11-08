/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCBoardLarge;  // for javadocs

/**
 * This message from the server to clients announces that an edge on the game board has become a Special Edge,
 * or is no longer a Special Edge.  Used in some game scenarios.  Applies only to games using {@link SOCBoardLarge}.
 *<P>
 * Param 1: The edge coordinate <br>
 * Param 2: Its new special edge type, such as {@link SOCBoardLarge#SPECIAL_EDGE_DEV_CARD}, or 0
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCBoardSpecialEdge extends SOCMessageTemplate2i
{
    private static final long serialVersionUID = 2000L;

    /**
     * Create a SOCBoardSpecialEdge message.
     *
     * @param ga  the name of the game
     * @param edge  the edge coordinate
     * @param seType  the special edge type, such as {@link SOCBoardLarge#SPECIAL_EDGE_DEV_CARD}, or 0
     */
    public SOCBoardSpecialEdge(final String ga, final int edge, final int seType)
    {
        super(BOARDSPECIALEDGE, ga, edge, seType);
    }

    /**
     * {@link SOCMessage#BOARDSPECIALEDGE BOARDSPECIALEDGE} sep game sep2 edge sep2 seType
     *
     * @param ga  the name of the game
     * @param edge  the edge coordinate
     * @param seType  the special edge type, such as {@link SOCBoardLarge#SPECIAL_EDGE_DEV_CARD}, or 0
     * @return the command string
     */
    public static String toCmd(final String ga, final int edge, final int seType)
    {
        return SOCMessageTemplate2i.toCmd(BOARDSPECIALEDGE, ga, edge, seType);
    }

    /**
     * Parse the command String into a SOCBoardSpecialEdge message.
     *
     * @param s   the String to parse: {@link SOCMessage#BOARDSPECIALEDGE BOARDSPECIALEDGE}
     *            sep game sep2 edge sep2 seType
     * @return    a SOCBoardSpecialEdge message, or {@code null} if the data is garbled
     */
    public static SOCBoardSpecialEdge parseDataStr(final String s)
    {
        final String ga; // the game name
        final int edge, seType;  // edge coord, special edge type

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            edge = Integer.parseInt(st.nextToken());
            seType = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCBoardSpecialEdge(ga, edge, seType);
    }

    /**
     * Minimum version where this message type is used.
     * BOARDSPECIALEDGE introduced in 2.0.00.
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    public final int getMinimumVersion()
    {
        return 2000;
    }

}
