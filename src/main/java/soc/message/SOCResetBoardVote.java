/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008-2009,2014,2018 Jeremy D Monin <jeremy@nand.net>
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
 * This bi-directional message gives the client's vote on a "board reset",
 * which was requested by another player in that game.
 *<UL>
 * <LI> This message to server is in response to a {@link SOCResetBoardRequest}
 *      sent earlier this turn to all non-robot clients. (Robots' vote is always Yes.)
 * <LI> Followed by (from server, to all clients) {@link SOCResetBoardVote} with the same data,
 *      informing all players of this client's vote.
 * <LI> Once voting is complete, server sends to all either a {@link SOCResetBoardAuth} or
 *      {@link SOCResetBoardReject} message.
 *</UL>
 * For details of messages sent, see
 * {@link soc.server.SOCServer#resetBoardAndNotify(String, int)}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
public class SOCResetBoardVote extends SOCMessageTemplate2i
{
    private static final long serialVersionUID = 1100L;  // last structural change v1.1.00

    /**
     * Create a SOCResetBoardVote message.
     *
     * @param ga  the name of the game
     * @param pn  the player position who voted (used when sending to other clients)
     * @param pyes  did they vote yes
     */
    public SOCResetBoardVote(String ga, int pn, boolean pyes)
    {
        super(RESETBOARDVOTE, ga, pn, pyes ? 1 : 0);
    }

    /**
     * @return the voter's player number
     */
    public int getPlayerNumber()
    {
        return p1;
    }

    /**
     * @return if true, the vote is Yes
     */
    public boolean getPlayerVote()
    {
        return (p2 != 0);
    }

    /**
     * RESETBOARDVOTE sep game sep2 playernumber sep2 yesno [Yes is 1, No is 0]
     *
     * @param ga  the name of the game
     * @param pn  the voter's player number
     * @param pyes if the vote was yes
     * @return the command string
     */
    public static String toCmd(String ga, int pn, boolean pyes)
    {
        return RESETBOARDVOTE + sep + ga + sep2 + pn + sep2
            + (pyes ? "1" : "0");
    }

    /**
     * Parse the command String into a SOCResetBoardVote message
     *
     * @param s   the String to parse: RESETBOARDVOTE sep game sep2 playernumber sep2 yesno [1 or 0]
     * @return    a SOCResetBoardVote message, or null if the data is garbled
     */
    public static SOCResetBoardVote parseDataStr(String s)
    {
        String ga; // the game name
        int pn;    // the voter's player number
        int vy;    // vote, 1 or 0

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            vy = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCResetBoardVote(ga, pn, vy != 0);
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.ResetBoardVote.Builder b
            = GameMessage.ResetBoardVote.newBuilder().setIsYes(getPlayerVote());
        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGameName(game).setPlayerNumber(getPlayerNumber()).setResetBoardVote(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

    /**
     * Minimum version where this message type is used.
     * RESETBOARDVOTE introduced in 1.1.00 for reset-board feature.
     * @return Version number, 1100 for JSettlers 1.1.00.
     */
    public int getMinimumVersion() { return 1100; }

}
