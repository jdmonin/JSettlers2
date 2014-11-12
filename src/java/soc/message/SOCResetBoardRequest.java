/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008,2014 Jeremy D Monin <jeremy@nand.net>
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
 * This message from client to server requests a "reset board" of
 * a game being played. (New game, same name, same players, new layout).
 *<P>
 * If reset is allowed, server will respond with {@link SOCResetBoardVoteRequest}
 * or {@link SOCResetBoardAuth} and subsequent messages. For details, see 
 * {@link soc.server.SOCServer#resetBoardAndNotify(String, int)}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
public class SOCResetBoardRequest extends SOCMessageTemplate0
{
    private static final long serialVersionUID = 1100L;  // last structural change v1.1.00

    /**
     * Create a ResetBoardRequest message.
     *
     * @param ga  the name of the game
     */
    public SOCResetBoardRequest(String ga)
    {
        super (RESETBOARDREQUEST, ga);
    }

    /**
     * Parse the command String into a ResetBoardRequest message
     *
     * @param s   the String to parse
     * @return    a ResetBoardRequest message
     */
    public static SOCResetBoardRequest parseDataStr(String s)
    {
        return new SOCResetBoardRequest(s);
    }

    /**
     * Minimum version where this message type is used.
     * RESETBOARDREQUEST introduced in 1.1.00 for reset-board feature.
     * @return Version number, 1100 for JSettlers 1.1.00.
     */
    public int getMinimumVersion() { return 1100; }

}
