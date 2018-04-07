/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2011-2013,2017-2018 Jeremy D Monin <jeremy@nand.net>
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
 * Client player is asking to move, or server is announcing a move of,
 * a piece on the board to a new location. Currently, ships are the
 * only piece type that can be moved.
 *
 *<H3>From requesting client:</H3>
 * Requests moving a piece that's already on the board to a new location.
 * The server will announce to all players with {@link SOCMovePiece} if piece can be moved,
 * or reply to the requester with {@link SOCCancelBuildRequest}.
 *
 *<H3>From server to all game members:</H3>
 * Announces a player is moving a piece that's already on the board to a new location.
 * This is a response to all player clients, following a player's {@link SOCMovePiece} request.
 * If pieceType == ship, the client should also print a line of text such as "* Joe moved a ship."
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 * @see SOCMoveRobber
 */
public class SOCMovePiece extends SOCMessageTemplate4i
{
    private static final long serialVersionUID = 2000L;

    /**
     * Create a SOCMovePiece message.
     *
     * @param ga  the name of the game
     * @param pn  the player number; ignored if sent from client
     * @param ptype  piece type, such as {@link soc.game.SOCPlayingPiece#SHIP}; must be >= 0
     * @param fromCoord  move piece from this coordinate; must be >= 0
     * @param toCoord  move piece to this coordinate; must be >= 0
     * @throws IllegalArgumentException if {@code ptype} &lt; 0, {@code fromCoord} &lt; 0, or {@code toCoord} &lt; 0
     */
    public SOCMovePiece(String ga, final int pn, final int ptype, final int fromCoord, final int toCoord)
        throws IllegalArgumentException
    {
        super(MOVEPIECE, ga, pn, ptype, fromCoord, toCoord);

        if (ptype < 0)
            throw new IllegalArgumentException("pt: " + ptype);
        if (fromCoord < 0)
            throw new IllegalArgumentException("fromCoord < 0");
        if (toCoord < 0)
            throw new IllegalArgumentException("toCoord < 0");
    }

    /**
     * @return the player number from server, or any value sent from client (not used by server)
     */
    public int getPlayerNumber()
    {
        return p1;
    }

    /**
     * @return the piece type, such as {@link soc.game.SOCPlayingPiece#ROAD}
     */
    public int getPieceType()
    {
        return p2;
    }

    /**
     * @return the coordinate to move the piece from; is >= 0
     */
    public int getFromCoord()
    {
        return p3;
    }

    /**
     * @return the coordinate to move the piece to; is >= 0
     */
    public int getToCoord()
    {
        return p4;
    }

    /**
     * MOVEPIECE sep game sep2 playernumber sep2 ptype sep2 fromCoord sep2 toCoord
     *
     * @param ga  the name of the game
     * @param pn  the player number; ignored if sent from client
     * @param ptype  piece type, such as {@link soc.game.SOCPlayingPiece#SHIP}; must be >= 0
     * @param fromCoord  move piece from this coordinate; must be >= 0
     * @param toCoord  move piece to this coordinate; must be >= 0
     * @return the command string
     * @throws IllegalArgumentException if {@code ptype} &lt; 0, {@code fromCoord} &lt; 0, or {@code toCoord} &lt; 0
     */
    public static String toCmd(String ga, int pn, int ptype, int fromCoord, int toCoord)
        throws IllegalArgumentException
    {
        if (ptype < 0)
            throw new IllegalArgumentException("pt: " + ptype);
        if (fromCoord < 0)
            throw new IllegalArgumentException("fromCoord < 0");
        if (toCoord < 0)
            throw new IllegalArgumentException("toCoord < 0");

        return MOVEPIECE + sep + ga + sep2 + pn + sep2
            + ptype + sep2 + fromCoord + sep2 + toCoord;
    }

    /**
     * Parse the command String into a SOCMovePiece message
     *
     * @param s   the String to parse: MOVEPIECE sep game sep2 playernumber sep2 ptype sep2 fromCoord sep2 toCoord
     * @return    a SOCMovePiece message, or null if the data is garbled
     */
    public static SOCMovePiece parseDataStr(String s)
    {
        String ga; // the game name
        int pn;    // the player number
        int pc;    // piece type
        int fc;    // 'from' coordinate
        int tc;    // 'to' coordinate

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            pc = Integer.parseInt(st.nextToken());
            fc = Integer.parseInt(st.nextToken());
            tc = Integer.parseInt(st.nextToken());

            return new SOCMovePiece(ga, pn, pc, fc, tc);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Minimum version where this message type is used.
     * MOVEPIECE introduced in 2.0.00.
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    public int getMinimumVersion() { return 2000; }

}
