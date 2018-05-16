/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008,2013-2014,2018 Jeremy D Monin <jeremy@nand.net>
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
 * This message from server informs the client that a game they're playing
 * has been "reset" to a new game (with same name and players, new layout),
 * and they should join at the given position.
 *<P>
 * For human players, this message replaces the {@link SOCJoinGameAuth} seen when joining a brand-new game;
 * the reset message will be followed with others which will fill in the game state.
 *<P>
 * For robots, they must discard game state and ask to re-join.
 * Robot client treats as a {@link SOCBotJoinGameRequest}: Asks to join the new game.
 *<P>
 * Follows {@link SOCResetBoardRequest} and {@link SOCResetBoardVote} messages.
 * For details of messages sent, see
 * {@link soc.server.SOCServer#resetBoardAndNotify(String, int)}.
 *
 * @see SOCResetBoardRequest
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
public class SOCResetBoardAuth extends SOCMessageTemplate2i
{
    private static final long serialVersionUID = 1100L;  // last structural change v1.1.00

    /**
     * In version 2.0.00 and above, {@link #getRejoinPlayerNumber()} can be -1.
     * See that method for details.
     * @since 2.0.00
     */
    public static final int VERSION_FOR_BLANK_PLAYERNUM = 2000;

    /**
     * Create a ResetBoardAuth message.
     *
     * @param ga  the name of the game
     * @param joinpn  the player position number at which to join, or -1:
     *     See {@link #getRejoinPlayerNumber()}
     * @param reqpn  player number who requested the reset
     */
    public SOCResetBoardAuth(String ga, int joinpn, int reqpn)
    {
        super (RESETBOARDAUTH, ga, joinpn, reqpn);
    }

    /**
     * Get the player position number (player number/seat number) at which client player should rejoin.
     * This has always been the same player number they had before the reset,
     * so it's redundant: Client v2.0.00 and newer accepts -1 instead.
     * Check client version against {@link #VERSION_FOR_BLANK_PLAYERNUM}.
     *
     * @return the player position number at which to rejoin, or -1
     */
    public int getRejoinPlayerNumber()
    {
        return p1;
    }

    /**
     * @return the number of the player who requested the board reset
     */
    public int getRequestingPlayerNumber()
    {
        return p2;
    }

    /**
     * Parse the command String into a SOCResetBoardAuth message
     *
     * @param s   the String to parse: RESETBOARDAUTH sep game sep2 playernumber sep2 requester
     * @return    a SOCResetBoardAuth message, or null if the data is garbled
     */
    public static SOCResetBoardAuth parseDataStr(String s)
    {
        String ga;   // the game name
        int joinpn;  // the player number to join at
        int reqpn;   // the requester player number

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            joinpn = Integer.parseInt(st.nextToken());
            reqpn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCResetBoardAuth(ga, joinpn, reqpn);
    }

    /**
     * Minimum version where this message type is used.
     * RESETBOARDAUTH introduced in 1.1.00 for reset-board feature.
     * @return Version number, 1100 for JSettlers 1.1.00.
     */
    public int getMinimumVersion() { return 1100; }

}
