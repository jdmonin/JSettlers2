/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013,2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCPlayingPiece;
import soc.proto.GameMessage;
import soc.proto.Message;

/**
 * This message from server announces a SOCShip removed from the board.
 * Ships are removed when the player makes an attack on their Pirate Fortress and fails to win.
 * Currently, no other piece types are removed in any scenario, but the message allows for other types
 * in case that changes in a later version.
 *<P>
 * Param 1: Player number owning the piece <br>
 * Param 2: Type of playing piece, such as {@link soc.game.SOCPlayingPiece#SHIP} <br>
 * Param 3: Coordinates of the piece to remove
 *<P>
 * (These parameters are in the same order as in {@link SOCPutPiece#toCmd(String, int, int, int)}.)
 *<P>
 * Introduced in v2.0.00 for the pirate islands scenario (_SC_PIRI).
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCRemovePiece extends SOCMessageTemplate3i
{
    private static final long serialVersionUID = 2000L;

    /**
     * Create a SOCRemovePiece message.
     *
     * @param ga  the name of the game
     * @param pn  player number owning the piece
     * @param ptype  type of playing piece, such as {@link soc.game.SOCPlayingPiece#SHIP}
     * @param co  coordinates of the piece to remove; must be >= 0
     */
    public SOCRemovePiece(final String ga, final int pn, final int ptype, final int co)
        throws IllegalArgumentException
    {
        super(REMOVEPIECE, ga, pn, ptype, co);

        if (co < 0)
            throw new IllegalArgumentException("coord < 0");
    }

    /**
     * Create a SOCRemovePiece message for this piece.
     * @param ga  the name of the game
     * @param pp  the playing piece to remove; {@link SOCPlayingPiece#getCoordinates() pp.getCoordinates()} must be >= 0
     */
    public SOCRemovePiece(final String ga, final SOCPlayingPiece pp)
        throws IllegalArgumentException
    {
        this(ga, pp.getPlayerNumber(), pp.getType(), pp.getCoordinates());
    }

    /**
     * Parse the command string into a SOCRemovePiece message.
     *
     * @param s   the String to parse; format: game sep2 pn sep2 ptype sep2 co
     * @return    a SOCRemovePiece message, or null if parsing errors
     */
    public static SOCRemovePiece parseDataStr(String s)
    {
        final String ga; // the game name
        final int pn; // player number
        final int pt; // type of piece
        final int co; // coordinates

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            pt = Integer.parseInt(st.nextToken());
            co = Integer.parseInt(st.nextToken());

            return new SOCRemovePiece(ga, pn, pt, co);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.RemovePiece.Builder b
            = GameMessage.RemovePiece.newBuilder();
        b.setTypeValue(p2)
         .setCoordinates(ProtoMessageBuildHelper.toPieceCoord(p3, p2));
        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGameName(game).setPlayerNumber(p1).setRemovePiece(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

    /**
     * Minimum version where this message type is used.
     * REMOVEPIECE introduced in 2.0.00 for the pirate islands scenario (_SC_PIRI).
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    public int getMinimumVersion() { return 2000; }

}
