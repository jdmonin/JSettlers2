/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008 Jeremy D. Monin <jeremy@nand.net>
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

/**
 * This message from server informs all clients that voting has ended,
 * and the board reset request has been rejected.
 *<P>
 * Follows {@link SOCResetBoardRequest}, and then usually {@link SOCResetBoardVote}, messages.
 *
 * @see SOCResetBoardRequest
 * @author Jeremy D. Monin <jeremy@nand.net>
 *
 */
public class SOCResetBoardReject extends SOCMessageTemplate0
{
    /**
     * Create a SOCResetBoardReject message.
     *
     * @param ga  the name of the game
     */
    public SOCResetBoardReject(String ga)
    {
        super(RESETBOARDREJECT, ga);
    }

    /**
     * Parse the command String into a SOCResetBoardReject message
     *
     * @param s   the String to parse
     * @return    a SOCResetBoardAuth message, or null if the data is garbled
     */
    public static SOCResetBoardReject parseDataStr(String s)
    {
        // s is just the game name
        return new SOCResetBoardReject(s);
    }

    /**
     * Minimum version where this message type is used.
     * RESETBOARDREJECT introduced in 1.1.00 for reset-board feature.
     * @return Version number, 1100 for JSettlers 1.1.00.
     */
    public int getMinimumVersion()
    {
        return 1100;
    }

}
