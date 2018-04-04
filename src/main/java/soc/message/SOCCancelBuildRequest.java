/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007,2010-2013,2017-2018 Jeremy D Monin <jeremy@nand.net>
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
 *  This message type has five possible meanings, depending on game state and direction sent:
 *
 *<UL>
 *
 *<H3>When sent from client to server:</H3>
 *
 *<LI> During piece placement (PLACING_ROAD, PLACING_SETTLEMENT, PLACING_CITY, PLACING_SHIP, PLACING_INV_ITEM): <BR>
 *   CANCELBUILDREQUEST means the player has changed their mind about spending resources to build a piece.
 *   Server will reply by returning the player's resources and changing game state.
 *<P>
 *   The special inventory items in PLACING_INV_ITEM each have a different placement message, but if item placement
 *   can be canceled, use this common message type, with {@code pieceType} == -3 ({@link #INV_ITEM_PLACE_CANCEL}).
 *   If placement can't be canceled, server will reply with {@link SOCGameServerText}.
 *
 *<LI> While placing the second free road or ship (PLACING_FREE_ROAD2), means
 *   the player has decided to skip placing the second free road or ship,
 *   to use just one road or ship piece.  Server will reply with new game state.
 *   (This was added in v1.1.17)
 *
 *<LI> Not sent from client during other game states. Server will reply with {@link SOCGameServerText}
 *   instead.
 *
 *<H3>When sent from server to a client player:</H3>
 *
 *<LI> During game startup (START1B, START2B or START3B): <BR>
 *   The current player is undoing the placement of their initial settlement.
 *
 *<LI> During piece placement (PLACING_ROAD, PLACING_CITY, PLACING_SETTLEMENT,
 *   PLACING_FREE_ROAD1 or PLACING_FREE_ROAD2): <BR>
 *   The requesting player has sent an illegal {@link SOCPutPiece PUTPIECE} (bad building location)
 *   Humans can probably decide a better place to put their road, but robots must cancel
 *   the build request and decide on a new plan.
 *  <P>
 *   This can also be the reply if the client sends an illegal {@link SOCBuildRequest BUILDREQUEST}
 *   or {@link SOCBuyDevCardRequest BUYDEVCARDREQUEST} (no resources, not the right game state, etc.)
 *   or {@link SOCMovePiece MOVEPIECE}.
 *   In some of those cases it's sent only to robot clients, not to humans.
 *   (Humans get a textual error message, and can understand that instead.)
 *
 *<LI> Piece type -2 is sent only from server to robot client, as a way to tell robots
 *   they can't buy a development card (insufficient resources, or no cards left).
 *   (This was added in 1.1.09.)
 *   -2 == soc.robot.SOCPossiblePiece.CARD.
 *
 *</UL>
 *
 * @author Robert S. Thomas
 */
public class SOCCancelBuildRequest extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * pieceType to cancel special {@code SOCInventoryItem} placement.
     * @since 2.0.00
     */
    public static final int INV_ITEM_PLACE_CANCEL = -3;

    private static final long serialVersionUID = 2000L;

    /**
     * Name of game
     */
    private final String game;

    /**
     * The type of piece to cancel build, such as {@link soc.game.SOCPlayingPiece#CITY}
     * -2 is used from server to reject request to buy a Development Card.
     * -3 ({@link #INV_ITEM_PLACE_CANCEL}) is used from client to request canceling placement of a
     *    special SOCInventoryItem if possible.
     */
    private final int pieceType;

    /**
     * Create a CancelBuildRequest message.
     *
     * @param ga  the name of the game
     * @param pt  the type of piece to cancel build, such as {@link soc.game.SOCPlayingPiece#CITY}.
     *   -2 is used from server to reject request to buy a Development Card.
     *   -3 ({@link #INV_ITEM_PLACE_CANCEL}) is used from client to request canceling placement of a
     *      special SOCInventoryItem if possible.
     */
    public SOCCancelBuildRequest(String ga, int pt)
    {
        messageType = CANCELBUILDREQUEST;
        game = ga;
        pieceType = pt;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the type of piece to cancel build, such as {@link soc.game.SOCPlayingPiece#CITY}.
     *   -2 is used from server to reject request to buy a Development Card.
     *   -3 ({@link #INV_ITEM_PLACE_CANCEL}) is used from client to request canceling placement of a
     *      special SOCInventoryItem if possible.
     */
    public int getPieceType()
    {
        return pieceType;
    }

    /**
     * CANCELBUILDREQUEST sep game sep2 pieceType
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, pieceType);
    }

    /**
     * CANCELBUILDREQUEST sep game sep2 pieceType
     *
     * @param ga  the name of the game
     * @param pt  the type of piece to cancel build;
     *     see {@link #SOCCancelBuildRequest(String, int)} constructor for values
     * @return the command string
     */
    public static String toCmd(String ga, int pt)
    {
        return CANCELBUILDREQUEST + sep + ga + sep2 + pt;
    }

    /**
     * Parse the command String into a CancelBuildRequest message
     *
     * @param s   the String to parse
     * @return    a CancelBuildRequest message, or null if the data is garbled
     */
    public static SOCCancelBuildRequest parseDataStr(String s)
    {
        String ga; // the game name
        int pt; // the type of piece to build

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pt = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCCancelBuildRequest(ga, pt);
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.CancelBuild.Builder b
            = GameMessage.CancelBuild.newBuilder();
        if (pieceType >= 0)
            b.setPieceTypeValue(pieceType);
        else
            b.setItemTypeValue(-pieceType);
        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGaName(game).setCancelBuild(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCCancelBuildRequest:game=" + game + "|pieceType=" + pieceType;
    }

}
