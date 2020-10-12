/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2014,2017-2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCDevCard;  // for javadocs only
import soc.game.SOCDevCardConstants;  // for javadocs only
import soc.game.SOCGameOptionSet;  // for javadocs only


/**
 * This message from client means that the client player wants to buy a development card.
 *<P>
 * During game state {@link soc.game.SOCGame#PLAY1 PLAY1}, this is a normal buy request.
 * When sent during other game states, and other players' turns, this is a request
 * to start the 6-player {@link soc.game.SOCGame#SPECIAL_BUILDING Special Building Phase}.
 *<P>
 * If the player can buy a card, the server responds with:
 *<UL>
 * <LI> Announce game data to entire game:
 *  <UL>
 *   <LI> Resource cost paid: {@link SOCPlayerElements}
            (gaName, playerNumber, {@link SOCPlayerElement#LOSE}, {@link SOCDevCard#COST})
 *   <LI> New remaining card count: {@link SOCGameElements}
 *          (gaName, {@link SOCGameElements.GEType#DEV_CARD_COUNT}, remainingUnboughtCount)
 *  </UL>
 * <LI> Action announcement/display:
 *  <UL>
 *   <LI> Detail to the client: {@link SOCDevCardAction DEVCARDACTION}({@link SOCDevCardAction#DRAW},
 *          {@link SOCDevCardConstants cardTypeConstant})
 *   <LI> To all other players: {@link SOCDevCardAction DEVCARDACTION}({@link SOCDevCardAction#DRAW},
 *          {@link SOCDevCardConstants#UNKNOWN})
 *          <BR>
 *          Sends actual card type if game option {@link SOCGameOptionSet#K_PLAY_FO "PLAY_FO"}
 *          or {@link SOCGameOptionSet#K_PLAY_VPO "PLAY_VPO"} is set.
 *   <LI> Action announcement to entire game: {@link SOCSimpleAction}
 *          (gaName, playerNumber, {@link SOCSimpleAction#DEVCARD_BOUGHT}, remainingUnboughtCount, 0)
 *   <LI> New {@code gameState}, to entire game: {@link SOCGameState}.
 *        Usually unchanged; sent in case buying the card ended the game or otherwise changed its state.
 *        This is sent via {@link soc.server.SOCGameHandler#sendGameState(soc.game.SOCGame)},
 *        which may also send other messages depending on the gameState.
 *  </UL>
 *</UL>
 *
 * If there are no cards remaining to buy, or player doesn't have enough resources,
 * isn't currently their turn, or the player otherwise can't buy a card right now,
 * the server will send them a text response denying the buy. Instead of that text,
 * robot clients will be sent a {@link SOCCancelBuildRequest CANCELBUILDREQUEST(-2)} message
 * (-2 == soc.robot.SOCPossiblePiece.CARD).
 *<P>
 * Before v2.0.00 this class was {@code SOCBuyCardRequest}.
 *
 * @author Robert S Thomas
 */
public class SOCBuyDevCardRequest extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Name of game
     */
    private String game;

    /**
     * Create a BuyDevCardRequest message.
     *
     * @param ga  name of game
     */
    public SOCBuyDevCardRequest(String ga)
    {
        messageType = BUYDEVCARDREQUEST;
        game = ga;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * BUYDEVCARDREQUEST sep game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return BUYDEVCARDREQUEST + sep + game;
    }

    /**
     * Parse the command String into a BuyDevCardRequest message
     *
     * @param s   the String to parse
     * @return    a BuyDevCardRequest message, or null if the data is garbled
     */
    public static SOCBuyDevCardRequest parseDataStr(String s)
    {
        return new SOCBuyDevCardRequest(s);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCBuyDevCardRequest:game=" + game;

        return s;
    }

}
