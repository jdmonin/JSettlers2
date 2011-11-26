/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009-2011 Jeremy D Monin <jeremy@nand.net>
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.message;


/**
 * This message means that the player wants to
 * buy a development card.
 * During game state {@link soc.game.SOCGame#PLAY1 PLAY1}, this is a normal buy request.
 * When sent during other game states, and other players' turns, this is a request
 * to start the 6-player {@link soc.game.SOCGame#SPECIAL_BUILDING Special Building Phase}.
 *<P>
 * If there are no cards remaining to buy, or the player otherwise can't
 * buy a card right now (not enough resources, for example), the server will
 * send them a text response denying the buy.  For the robots' benefit,
 * robot clients will be sent a {@link SOCCancelBuildRequest CANCELBUILDREQUEST(-2)} message.
 * (-2 == soc.robot.SOCPossiblePiece.CARD)
 *
 * @author Robert S Thomas
 */
public class SOCBuyCardRequest extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of game
     */
    private String game;

    /**
     * Create a BuyCardRequest message.
     *
     * @param ga  name of game
     */
    public SOCBuyCardRequest(String ga)
    {
        messageType = BUYCARDREQUEST;
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
     * BUYCARDREQUEST sep game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game);
    }

    /**
     * BUYCARDREQUEST sep game
     *
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(String ga)
    {
        return BUYCARDREQUEST + sep + ga;
    }

    /**
     * Parse the command String into a BuyCardRequest message
     *
     * @param s   the String to parse
     * @return    a BuyCardRequest message, or null of the data is garbled
     */
    public static SOCBuyCardRequest parseDataStr(String s)
    {
        return new SOCBuyCardRequest(s);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCBuyCardRequest:game=" + game;

        return s;
    }
}
