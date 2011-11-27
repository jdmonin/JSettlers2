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

import java.util.StringTokenizer;


/**
 * This message conveys one part of the player's status, such as their number of
 * settlements remaining.
 *
 * @author Robert S Thomas
 */
public class SOCPlayerElement extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * player element types.  CLAY has same value
     * as {@link soc.game.SOCResourceConstants#CLAY};
     * ORE, SHEEP, WHEAT and WOOD also match SOCResourceConstants.
     */
    public static final int CLAY = 1;
    public static final int ORE = 2;
    public static final int SHEEP = 3;
    public static final int WHEAT = 4;
    public static final int WOOD = 5;
    public static final int UNKNOWN = 6;
    public static final int ROADS = 10;
    public static final int SETTLEMENTS = 11;
    public static final int CITIES = 12;
    /**
     * Number of SHIP pieces; added in 1.2.00.
     * @since 1.2.00 
     */
    public static final int SHIPS = 13;
    public static final int NUMKNIGHTS = 15;

    /**
     * For the 6-player board, player element type for asking to build
     * during the {@link SOCGame#SPECIAL_BUILDING Special Building Phase}.
     * This element is {@link #SET} to 1 or 0.
     * @since 1.1.08
     */
    public static final int ASK_SPECIAL_BUILD = 16;

    /**
     * player element actions
     */
    public static final int SET = 100;
    public static final int GAIN = 101;
    public static final int LOSE = 102;

    /**
     * Name of game
     */
    private String game;

    /**
     * Player number
     */
    private int playerNumber;

    /**
     * Player element type, such as {@link #SETTLEMENTS}
     */
    private int elementType;

    /**
     * Action type: {@link #SET}, {@link #GAIN}, or {@link #LOSE}
     */
    private int actionType;

    /**
     * Element value
     */
    private int value;

    /**
     * Create a PlayerElement message.
     *
     * @param ga  name of the game
     * @param pn  the player number
     * @param ac  the type of action: {@link #SET}, {@link #GAIN}, or {@link #LOSE}
     * @param et  the type of element, such as {@link #SETTLEMENTS}
     * @param va  the value of the element
     */
    public SOCPlayerElement(String ga, int pn, int ac, int et, int va)
    {
        messageType = PLAYERELEMENT;
        game = ga;
        playerNumber = pn;
        actionType = ac;
        elementType = et;
        value = va;
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
     * @return the action type
     */
    public int getAction()
    {
        return actionType;
    }

    /**
     * @return the element type
     */
    public int getElementType()
    {
        return elementType;
    }

    /**
     * @return the element value
     */
    public int getValue()
    {
        return value;
    }

    /**
     * PLAYERELEMENT sep game sep2 playerNumber sep2 actionType sep2 elementType sep2 value
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, actionType, elementType, value);
    }

    /**
     * PLAYERELEMENT sep game sep2 playerNumber sep2 actionType sep2 elementType sep2 value
     *
     * @param ga  the game name
     * @param pn  the player number
     * @param ac  the type of action
     * @param et  the type of element
     * @param va  the value of the element
     * @return    the command string
     */
    public static String toCmd(String ga, int pn, int ac, int et, int va)
    {
        return PLAYERELEMENT + sep + ga + sep2 + pn + sep2 + ac + sep2 + et + sep2 + va;
    }

    /**
     * Parse the command String into a PlayerElement message
     *
     * @param s   the String to parse
     * @return    a PlayerElement message, or null of the data is garbled
     */
    public static SOCPlayerElement parseDataStr(String s)
    {
        String ga;
        int pn;
        int ac;
        int et;
        int va;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            ac = Integer.parseInt(st.nextToken());
            et = Integer.parseInt(st.nextToken());
            va = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPlayerElement(ga, pn, ac, et, va);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCPlayerElement:game=" + game + "|playerNum=" + playerNumber + "|actionType=" + actionType + "|elementType=" + elementType + "|value=" + value;

        return s;
    }
}
