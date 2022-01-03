/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2012-2014,2017,2020 Jeremy D Monin <jeremy@nand.net>
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
 * This message from client means that a player wants to play a development card.
 *<P>
 * If client player can play it, server will respond to all players with
 * {@link SOCDevCardAction}({@link SOCDevCardAction#PLAY PLAY}), {@link SOCSetPlayedDevCard}
 * or {@link SOCPlayerElement}({@link SOCPlayerElement.PEType#PLAYED_DEV_CARD_FLAG PLAYED_DEV_CARD_FLAG}),
 * and other messages, followed by {@link SOCGameState} if it changed.
 *<BR>
 * If playing a {@code KNIGHT} card leads to Largest Army, server announces that
 * after {@code SOCPlayerElement} before {@code SOCGameState}:
 * {@link SOCGameElements}({@link SOCGameElements.GEType#LARGEST_ARMY_PLAYER LARGEST_ARMY_PLAYER}).
 *<P>
 * If client player can't play the card, server will reply to human player with {@link SOCGameServerText}
 * or robot with {@link SOCDevCardAction}({@link SOCDevCardAction#CANNOT_PLAY CANNOT_PLAY}).
 *<P>
 * Note that in v2.0.00, the {@link #getDevCard()} value for <tt>SOCDevCardConstants.KNIGHT</tt>
 * was changed.  This class doesn't handle the translation from old clients
 * (<tt>SOCDevCardConstants.KNIGHT_FOR_VERS_1_X</tt>), the caller must do so.
 *
 * @author Robert S. Thomas
 */
public class SOCPlayDevCardRequest extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * The type of dev card
     */
    private int devCard;

    /**
     * Create a PlayDevCardRequest message.
     *
     * @param ga  the name of the game
     * @param dc  the type of dev card
     */
    public SOCPlayDevCardRequest(String ga, int dc)
    {
        messageType = PLAYDEVCARDREQUEST;
        game = ga;
        devCard = dc;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the type of dev card <BR>
     * Note that in v2.0.00, the value for <tt>SOCDevCardConstants.KNIGHT</tt>
     * was changed.  This class doesn't handle the translation from old clients
     * (<tt>SOCDevCardConstants.KNIGHT_FOR_VERS_1_X</tt>), the caller must do so.
     */
    public int getDevCard()
    {
        return devCard;
    }

    /**
     * PLAYDEVCARDREQUEST sep game sep2 devCard
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, devCard);
    }

    /**
     * PLAYDEVCARDREQUEST sep game sep2 devCard
     *
     * @param ga  the name of the game
     * @param dc  the type of dev card
     * @return the command string
     */
    public static String toCmd(String ga, int dc)
    {
        return PLAYDEVCARDREQUEST + sep + ga + sep2 + dc;
    }

    /**
     * Parse the command String into a PlayDevCardRequest message
     *
     * @param s   the String to parse
     * @return    a PlayDevCardRequest message, or null if the data is garbled
     */
    public static SOCPlayDevCardRequest parseDataStr(String s)
    {
        String ga; // the game name
        int dc; // the type of dev card

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            dc = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPlayDevCardRequest(ga, dc);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCPlayDevCardRequest:game=" + game + "|devCard=" + devCard;
    }
}
