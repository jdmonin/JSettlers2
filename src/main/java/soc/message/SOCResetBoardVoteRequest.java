/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008,2014,2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.proto.GameMessage;
import soc.proto.Message;

/**
 * This message from server informs the client that in a game they're playing,
 * another player requests a "board reset" (new game with same name and players, new layout),
 * and they should vote yes or no.
 * This won't be sent to robots: robots are assumed to vote yes and go along.
 *
 * @see SOCResetBoardRequest
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
public class SOCResetBoardVoteRequest extends SOCMessageTemplate1i
{
    private static final long serialVersionUID = 1100L;  // last structural change v1.1.00

    /**
     * Create a SOCResetBoardVoteRequest message.
     *
     * @param ga  the name of the game
     * @param reqpn  player number who requested the reset
     */
    public SOCResetBoardVoteRequest(String ga, int reqpn)
    {
        super (RESETBOARDVOTEREQUEST, ga, reqpn);
    }

    /**
     * @return the player number of the player who requested the board reset
     */
    public int getRequestingPlayer()
    {
        return p1;
    }

    /**
     * RESETBOARDVOTEREQUEST sep game sep2 playernumber
     *
     * @param ga  the name of the game
     * @param reqpn  player number who requested the reset
     * @return the command string
     */
    public static String toCmd(String ga, int reqpn)
    {
        return RESETBOARDVOTEREQUEST + sep + ga + sep2 + reqpn;
    }

    /**
     * Parse the command String into a SOCResetBoardVoteRequest message
     *
     * @param s   the String to parse
     * @return    a SOCResetBoardVoteRequest message, or null if the data is garbled
     */
    public static SOCResetBoardVoteRequest parseDataStr(String s)
    {
        String ga; // the game name
        int reqpn; // the requester player number

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            reqpn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCResetBoardVoteRequest(ga, reqpn);
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.ResetBoardVote.Builder b
            = GameMessage.ResetBoardVote.newBuilder();
        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGameName(game).setResetBoardVotePrompt(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

    /**
     * Minimum version where this message type is used.
     * RESETBOARDVOTEREQUEST introduced in 1.1.00 for reset-board feature.
     * @return Version number, 1100 for JSettlers 1.1.00.
     */
    public int getMinimumVersion() { return 1100; }

}
