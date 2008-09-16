/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008 Jeremy D. Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
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
 * @author Jeremy D. Monin <jeremy@nand.net>
 */
public class SOCResetBoardRequest extends SOCMessageTemplate0
{
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
    public int getMinimumVersion()
    {
        return 1100;
    }

}
