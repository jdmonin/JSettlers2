/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009-2012 Jeremy D Monin <jeremy@nand.net>
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
 *<P>
 * Unless otherwise mentioned, any {@link #getElementType()} can be sent with
 * any action ({@link #SET}, {@link #GAIN}, {@link #LOSE}).
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
     * Number of SHIP pieces; added in v2.0.00.
     * @since 2.0.00 
     */
    public static final int SHIPS = 13;

    /**
     * Number of knights in player's army; sent after a Soldier card is played.
     */
    public static final int NUMKNIGHTS = 15;

    /**
     * For the 6-player board, player element type for asking to build
     * during the {@link soc.game.SOCGame#SPECIAL_BUILDING Special Building Phase}.
     * This element is {@link #SET} to 1 or 0.
     * @since 1.1.08
     */
    public static final int ASK_SPECIAL_BUILD = 16;

    /**
     * For the {@link soc.game.SOCBoardLarge large sea board},
     * player element type for asking to choose
     * resources from the gold hex after a dice roll,
     * during the {@link soc.game.SOCGame#WAITING_FOR_PICK_GOLD_RESOURCE WAITING_FOR_PICK_GOLD_RESOURCE}
     * game state.
     * This element is {@link #SET} to 0, or to the number of resources to choose.
     * Call {@link soc.game.SOCPlayer#setNeedToPickGoldHexResources(int)}.
     * @since 2.0.00
     */
    public static final int NUM_PICK_GOLD_HEX_RESOURCES = 17;

    /**
     * For scenarios on the {@link soc.game.SOCBoardLarge large sea board},
     * the player's number of Special Victory Points (SVP).
     * This element is {@link #SET} to 0, or to the player's
     * {@link soc.game.SOCPlayer#getSpecialVP()}.
     * @since 2.0.00
     */
    public static final int SCENARIO_SVP = 18;

    /**
     * For scenarios on the {@link soc.game.SOCBoardLarge large sea board},
     * bitmask of flags related to scenario player events.
     * This element is {@link #SET} to 0, or to the player's flags
     * from {@link soc.game.SOCPlayer#getScenarioPlayerEvents()}.
     * @since 2.0.00
     */
    public static final int SCENARIO_PLAYEREVENTS_BITMASK = 19;

    /**
     * For scenarios on the {@link soc.game.SOCBoardLarge large sea board},
     * bitmask of land areas for tracking Special Victory Points (SVP).
     * This element is {@link #SET} to 0, or to the player's land areas
     * from {@link soc.game.SOCPlayer#getScenarioSVPLandAreas()}.
     * @since 2.0.00
     */
    public static final int SCENARIO_SVP_LANDAREAS_BITMASK = 20;

    /**
     * For scenarios on the {@link soc.game.SOCBoardLarge large sea board},
     * the number of cloth held by this player.
     * This element is {@link #SET} to 0, or to the player's cloth count
     * from {@link soc.game.SOCPlayer#getCloth()}.
     * After giving cloth to a player, check their total VP; 2 cloth = 1 Victory Point.
     *<P>
     * The board's "general supply" is updated with this element type
     * with {@link #getPlayerNumber()} == -1.
     * Each village's cloth count is updated with a {@link SOCPieceValue PIECEVALUE} message.
     * @since 2.0.00
     */
    public static final int SCENARIO_CLOTH_COUNT = 21;

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
     * @param pn  the player number; v2.0.00+ allows -1 for some elements (applies to board or to all players).
     *            Earlier client versions will throw an exception accessing player -1.
     *            If the element allows -1, its constant's javadoc will mention that.
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
     * Get this element's player number.
     * v2.0.00+ allows -1 for some elements (applies to board or to all players).
     * Earlier client versions will throw an exception accessing player -1.
     * If the element allows -1, its constant's javadoc will mention that.
     * @return the player number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * Get the type of action.
     * @return the action type: {@link #GAIN}, {@link #LOSE} or {@link #SET}
     */
    public int getAction()
    {
        return actionType;
    }

    /**
     * Get the element type, the part of the player's info that is changing.
     * @return the element type, such as {@link #SETTLEMENTS} or {@link #NUMKNIGHTS}
     */
    public int getElementType()
    {
        return elementType;
    }

    /**
     * Get the new value to set, or the delta to gain/lose.
     * @return the element for player to {@link #GAIN}, {@link #LOSE} or {@link #SET}
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
     * @param pn  the player number; v2.0.00+ allows -1 for some elements (applies to board or to all players).
     *            Earlier client versions will throw an exception accessing player -1.
     *            If the element allows -1, its constant's javadoc will mention that.
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
