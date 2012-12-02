/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010,2012 Jeremy D Monin <jeremy@nand.net>
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

import java.util.StringTokenizer;
import soc.game.SOCDevCardConstants;  // for javadoc's use


/**
 * This message from the server means that a player is
 * {@link #DRAW drawing} or {@link #PLAY playing}
 * a development card; response to {@link SOCPlayDevCardRequest}.
 *<P>
 * If a robot asks to play a dev card that they can't right now,
 * the server sends that bot DEVCARD(-1, {@link #CANNOT_PLAY}, cardtype).
 *
 * @author Robert S Thomas
 */
public class SOCDevCard extends SOCMessage
    implements SOCMessageForGame
{
    /** dev card action DRAW (Buy): Add as new to player's hand */
    public static final int DRAW = 0;
    /** dev card action PLAY: remove as old from player's hand */
    public static final int PLAY = 1;
    /** dev card action ADDNEW: Add as new to player's hand */
    public static final int ADDNEW = 2;
    /** dev card action ADDOLD: Add as old to player's hand */
    public static final int ADDOLD = 3;

    /**
     * dev card action CANNOT_PLAY: The bot can't play the requested card at this time.
     * This is sent only to the requesting robot, so playerNumber is always -1 in this message.
     * @since 1.1.17
     */
    public static final int CANNOT_PLAY = 4;

    /**
     * Name of game
     */
    private String game;

    /**
     * Player number
     */
    private int playerNumber;

    /**
     * The type of development card, like {@link SOCDevCardConstants#ROADS}
     */
    private int cardType;

    /**
     * Action type
     */
    private int actionType;

    /**
     * Create a DevCard message.
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for {@link #CANNOT_PLAY}
     * @param ac  the type of action
     * @param ct  the type of card, like {@link SOCDevCardConstants#ROADS}
     */
    public SOCDevCard(String ga, int pn, int ac, int ct)
    {
        messageType = DEVCARD;
        game = ga;
        playerNumber = pn;
        actionType = ac;
        cardType = ct;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the player number, or -1 for action type {@link #CANNOT_PLAY}
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the action type, like {@link #DRAW}
     */
    public int getAction()
    {
        return actionType;
    }

    /**
     * @return the card type, like {@link SOCDevCardConstants#ROADS}
     */
    public int getCardType()
    {
        return cardType;
    }

    /**
     * DEVCARD sep game sep2 playerNumber sep2 actionType sep2 cardType
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, actionType, cardType);
    }

    /**
     * DEVCARD sep game sep2 playerNumber sep2 actionType sep2 cardType
     *
     * @param ga  the game name
     * @param pn  the player number
     * @param ac  the type of action
     * @param ct  the type of card
     * @return    the command string
     */
    public static String toCmd(String ga, int pn, int ac, int ct)
    {
        return DEVCARD + sep + ga + sep2 + pn + sep2 + ac + sep2 + ct;
    }

    /**
     * Parse the command String into a DevCard message
     *
     * @param s   the String to parse
     * @return    a DevCard message, or null of the data is garbled
     */
    public static SOCDevCard parseDataStr(String s)
    {
        String ga;
        int pn;
        int ac;
        int ct;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            ac = Integer.parseInt(st.nextToken());
            ct = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCDevCard(ga, pn, ac, ct);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCDevCard:game=" + game + "|playerNum=" + playerNumber + "|actionType=" + actionType + "|cardType=" + cardType;

        return s;
    }
}
