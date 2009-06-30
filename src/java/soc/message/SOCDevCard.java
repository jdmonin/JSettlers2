/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
import soc.game.SOCDevCardConstants;  // for javadoc's use


/**
 * This message means that a player is drawing or playing
 * a development card
 *
 * @author Robert S Thomas
 */
public class SOCDevCard extends SOCMessage
{
    /** dev card action DRAW: Add as new to player's hand */
    public static final int DRAW = 0;
    /** dev card action PLAY: remove as old from player's hand */
    public static final int PLAY = 1;
    /** dev card action ADDNEW: Add as new to player's hand */
    public static final int ADDNEW = 2;
    /** dev card action ADDOLD: Add as old to player's hand */
    public static final int ADDOLD = 3;

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
     * @param pn  the player number
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
     * @return the player number
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
