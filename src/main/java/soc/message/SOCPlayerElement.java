/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2014,2017 Jeremy D Monin <jeremy@nand.net>
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
 * This message from the server conveys one part of a player's status,
 * such as their number of settlements remaining.
 *<P>
 * Unless otherwise mentioned, any {@link #getElementType()} can be sent with
 * any action ({@link #SET}, {@link #GAIN}, {@link #LOSE}).
 *
 *<H3>Message Sequence:</H3>
 *<UL>
 * <LI> For a bank trade (server response to player's {@link SOCBankTrade}),
 *   all the {@link #LOSE} messages come before the {@link #GAIN}s.
 * <LI> For trade between players ({@link SOCAcceptOffer}), the {@code LOSE}s and {@code GAIN}s
 *   are interspersed to simplify server code.
 * <LI> For dice rolls, after {@link SOCDiceResult} any clients older than v2.0.00 are sent {@link #GAIN}
 *   for each resource type gained by each player from the roll. Newer clients are instead sent
 *   {@link SOCDiceResultResources}. Afterwards the current player (any client version) is sent their currently
 *   held amounts for each resource as a group of <tt>SOCPlayerElement(pn, {@link #SET}, ...)</tt> messages.
 *</UL>
 *<P>
 * Resource loss can be expected and good (buying pieces or trading with other players)
 * or unexpected and bad (monopoly, robber, discards). v1.2.00 and newer have sound effects
 * to announce unexpected gains or losses; to help recognize this, this message type gets a
 * new flag field {@link #isNews()}. Versions older than v1.2.00 ignore the new field.
 *<P>
 * To use less overhead to send multiple similar element changes, use {@link SOCPlayerElements} instead;
 * doing so requires client version 2.0.00 or newer.
 *
 * @author Robert S Thomas
 */
public class SOCPlayerElement extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

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

    /**
     * Amount of resources of unknown type; sent in messages about opponents' resources.
     * For some loops which send resource types + unknown, this constant is assumed to be 6
     * (5 known resource types + 1).
     */
    public static final int UNKNOWN = 6;

    public static final int ROADS = 10;
    public static final int SETTLEMENTS = 11;
    public static final int CITIES = 12;

    /**
     * Number of SHIP pieces available to place; added in v2.0.00.
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
     * Player's starting land area numbers.
     * Sent only at reconnect, because these are also tracked during play at the client.
     * Sent as <tt>(landArea2 &lt;&lt; 8) | landArea1</tt>.
     * @since 2.0.00
     */
    public static final int STARTING_LANDAREAS = 21;

    /**
     * For scenario <tt>_SC_CLVI</tt> on the {@link soc.game.SOCBoardLarge large sea board},
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
    public static final int SCENARIO_CLOTH_COUNT = 22;

    /**
     * For scenario game option <tt>_SC_PIRI</tt>,
     * the player's total number of ships that have been converted to warships.
     * See SOCPlayer.getNumWarships() for details.
     * This element can be {@link #SET} or {@link #GAIN}ed.  For clarity, if the number of
     * warships decreases, send {@link #SET}, never send {@link #LOSE}.
     * {@link #GAIN} is sent only in response to a player's successful
     * {@link SOCPlayDevCardRequest} to convert a ship to a warship.
     *<P>
     * If a player is joining a game in progress, the <tt>PLAYERELEMENT(SCENARIO_WARSHIP_COUNT)</tt>
     * message is sent to their client only after sending their SOCShip piece positions.
     * @since 2.0.00
     */
    public static final int SCENARIO_WARSHIP_COUNT = 23;

    // End of element declaration list.
    // -----------------------------------------------------------

    /**
     * player element actions
     */
    public static final int SET = 100;
    public static final int GAIN = 101;
    public static final int LOSE = 102;

    /**
     * Convenience "value" for action, sent over network as {@link #SET}
     * with {@link #isNews()} flag set. Flag is ignored by clients older
     * than v1.2.00.
     * @since 1.2.00
     */
    public static final int SET_NEWS = -100;

    /**
     * Convenience "value" for action, sent over network as {@link #GAIN}
     * with {@link #isNews()} flag set. Flag is ignored by clients older
     * than v1.2.00.
     * @since 1.2.00
     */
    public static final int GAIN_NEWS = -101;

    /**
     * Convenience "value" for action, sent over network as {@link #LOSE}
     * with {@link #isNews()} flag set. Flag is ignored by clients older
     * than v1.2.00.
     * @since 1.2.00
     */
    public static final int LOSE_NEWS = -102;

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
     * Is this a notable/unexpected gain or loss? See {@link #isNews()} for details.
     * @since 1.2.00
     */
    private final boolean news;

    /**
     * Create a PlayerElement message.
     *
     * @param ga  name of the game
     * @param pn  the player number; v1.1.19 and newer allow -1 for some elements (applies to board or to all players).
     *            Earlier client versions will throw an exception accessing player -1.
     *            If the element type allows -1, its constant's javadoc will mention that.
     * @param ac  the type of action: {@link #SET}, {@link #GAIN}, or {@link #LOSE}.
     *            Do not use {@link #GAIN_NEWS}, {@link #SET_NEWS}, or {@link #LOSE_NEWS} here, call
     *            {@link #SOCPlayerElement(String, int, int, int, int, boolean)} instead.
     * @param et  the type of element, such as {@link #SETTLEMENTS}
     * @param va  the value of the element
     * @throws IllegalArgumentException if {@code ac} is {@link #GAIN_NEWS}, {@link #SET_NEWS}, or {@link #LOSE_NEWS}
     * @see #SOCPlayerElement(String, int, int, int, int, boolean)
     */
    public SOCPlayerElement(String ga, int pn, int ac, int et, int va)
        throws IllegalArgumentException
    {
        this(ga, pn, ac, et, va, false);
    }

    /**
     * Create a PlayerElement message, optionally with the {@link #isNews()} flag set.
     *
     * @param ga  name of the game
     * @param pn  the player number; v1.1.19 and newer allow -1 for some elements (applies to board or to all players).
     *            Earlier client versions will throw an exception accessing player -1.
     *            If the element type allows -1, its constant's javadoc will mention that.
     * @param ac  the type of action: {@link #SET}, {@link #GAIN}, or {@link #LOSE}.
     *            Do not use {@link #GAIN_NEWS}, {@link #SET_NEWS}, or {@link #LOSE_NEWS} here,
     *            instead set {@code isNews} parameter.
     * @param et  the type of element, such as {@link #SETTLEMENTS}
     * @param va  the value of the element
     * @param isNews  Value to give the {@link #isNews()} flag
     * @see #SOCPlayerElement(String, int, int, int, int)
     * @throws IllegalArgumentException if {@code ac} is {@link #GAIN_NEWS}, {@link #SET_NEWS} or {@link #LOSE_NEWS}
     * @since 1.2.00
     */
    public SOCPlayerElement(String ga, int pn, int ac, int et, int va, boolean isNews)
        throws IllegalArgumentException
    {
        if ((ac == GAIN_NEWS) || (ac == SET_NEWS) || (ac == LOSE_NEWS))
            throw new IllegalArgumentException("use isNews instead");

        messageType = PLAYERELEMENT;
        game = ga;
        playerNumber = pn;
        actionType = ac;
        elementType = et;
        value = va;
        news = isNews;
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
     * v1.1.19 and newer allow -1 for some elements (applies to board or to all players).
     * Earlier client versions will throw an exception accessing player -1.
     * If the element type allows -1, its constant's javadoc will mention that.
     * @return the player number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * Get the type of action.
     * @return the action type: {@link #SET}, {@link #GAIN}, or {@link #LOSE}
     */
    public int getAction()
    {
        return actionType;
    }

    /**
     * Get the element type, the type of info that is changing.
     * @return the element type, such as {@link #SETTLEMENTS} or {@link #NUMKNIGHTS}
     */
    public int getElementType()
    {
        return elementType;
    }

    /**
     * Get the new value to set, or the delta to gain/lose.
     * @return the amount to {@link #SET}, {@link #GAIN}, or {@link #LOSE}
     */
    public int getValue()
    {
        return value;
    }

    /**
     * Is this element change notably good or an unexpected bad change or loss?
     * For example, resource lost to the robber or monopoly or gained from the fog hex.
     *<P>
     * Do not set this flag if the player is expecting the gain or loss.
     * For example, if this message is server's response to their chosen resources
     * in a Discovery, Monopoly, or Gold Hex gain dialog.
     *<P>
     * If {@link #getAction()} == {@link #SET}, treat message as bad news.
     *<P>
     * This flag is ignored by clients older than v1.2.00.
     * @return  True if marked "news", false otherwise
     * @since 1.2.00
     */
    public boolean isNews()
    {
        return news;
    }

    /**
     * PLAYERELEMENT sep game sep2 playerNumber sep2 actionType sep2 elementType sep2 value
     * [sep2 isNews ("Y", or sep2 and field are omitted)]
     *
     * @return the command String
     */
    public String toCmd()
    {
        int ac = actionType;
        if (news)
            switch (ac)
            {
            case GAIN:
                ac = GAIN_NEWS;  break;
            case LOSE:
                ac = LOSE_NEWS;  break;
            case SET:
                ac = SET_NEWS;  break;
            }

        return toCmd(game, playerNumber, ac, elementType, value);
    }

    /**
     * PLAYERELEMENT sep game sep2 playerNumber sep2 actionType sep2 elementType sep2 value
     * [sep2 isNews ("Y", or sep2 and field are omitted)]
     *
     * @param ga  the game name
     * @param pn  the player number; v1.1.19 and newer allow -1 for some elements (applies to board or to all players).
     *            Earlier client versions will throw an exception accessing player -1.
     *            If the element type allows -1, its constant's javadoc will mention that.
     * @param ac  the type of action: {@link #SET}, {@link #GAIN}, or {@link #LOSE}.
     *            Use {@link #GAIN_NEWS}, {@link #SET_NEWS} or {@link #LOSE_NEWS} to set message's {@link #isNews()} flag.
     * @param et  the type of element
     * @param va  the value of the element
     * @return    the command string
     */
    public static String toCmd(String ga, int pn, int ac, int et, int va)
    {
        boolean isNews = false;
        switch (ac)
        {
        case GAIN_NEWS:
            isNews = true;  ac = GAIN;  break;
        case SET_NEWS:
            isNews = true;  ac = SET;  break;
        case LOSE_NEWS:
            isNews = true;  ac = LOSE;  break;
        default:
            // no ac change needed
        }

        return PLAYERELEMENT + sep + ga + sep2 + pn + sep2 + ac + sep2 + et + sep2 + va
            + ((isNews) ? (sep2 + 'Y') : "");
    }

    /**
     * Parse the command String into a PlayerElement message
     *
     * @param s   the String to parse
     * @return    a PlayerElement message, or null if the data is garbled
     */
    public static SOCPlayerElement parseDataStr(String s)
    {
        String ga;
        int pn;
        int ac;
        int et;
        int va;
        boolean isNews = false;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            ac = Integer.parseInt(st.nextToken());
            et = Integer.parseInt(st.nextToken());
            va = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens())
                isNews = st.nextToken().equals("Y");
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPlayerElement(ga, pn, ac, et, va, isNews);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        final String act;
        switch (actionType)
        {
        case SET:  act = "SET";  break;
        case GAIN: act = "GAIN"; break;
        case LOSE: act = "LOSE"; break;
        default:   act = Integer.toString(actionType);
        }

        String s = "SOCPlayerElement:game=" + game + "|playerNum=" + playerNumber + "|actionType=" + act
            + "|elementType=" + elementType + "|value=" + value + ((news) ? "|news=Y" : "");

        return s;
    }

}
