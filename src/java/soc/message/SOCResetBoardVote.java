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

import java.util.StringTokenizer;

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
 * @author Jeremy D. Monin <jeremy@nand.net>
 */
@SuppressWarnings("serial")
public class SOCResetBoardVote extends SOCMessageTemplate2i
{
    /**
     * Create a SOCResetBoardVoteRequest message.
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

    /**
     * Minimum version where this message type is used.
     * RESETBOARDVOTE introduced in 1.1.00 for reset-board feature.
     * @return Version number, 1100 for JSettlers 1.1.00.
     */
    public int getMinimumVersion()
    {
        return 1100;
    }

}
