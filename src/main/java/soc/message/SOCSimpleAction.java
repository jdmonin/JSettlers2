/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013-2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCBoardLarge;  // solely for javadocs
import soc.game.SOCGame;        // solely for javadocs
import soc.game.SOCGameOptionSet;  // solely for javadocs
import soc.game.SOCResourceConstants;  // solely for javadocs
import soc.util.SOCStringManager;  // solely for javadocs

/**
 * This generic message from the server to clients handles a simple action or event in a game, usually about
 * a client player. This is a way to add game actions and events without adding new SOCMessage subclasses.
 * It has a player number, an action type code, and two optional detail-value fields.
 * This message comes after, not before, any messages that update the game and player data for the action.
 *<P>
 * To get the optional detail value fields from a {@code SOCSimpleAction}, be sure to use {@link #getValue1()}
 * and {@link #getValue2()}, not {@link #getParam1()} and {@link #getParam2()} which would instead return the
 * player number and action type code.  {@link #getPlayerNumber()} and {@link #getActionType()} are
 * convenience methods with more intuitive names to retrieve the player number and typecode.
 *<P>
 * This message type is useful for functions that don't have a complicated set of
 * details attached, such as telling all players that someone has bought a development card,
 * or telling a bot that it's made a successful bank/port trade, or some event or condition just happened.
 * Some action types may not be about a specific player; this will be mentioned in the typecode's javadoc.
 *<P>
 * Depending on the action type code, this message may be broadcast to the entire game
 * or sent to only the affected player.  Clients should ignore action types they don't
 * know how to handle (maybe the type is newer than the client's version).
 *<P>
 * Action type codes below 1000 are for general types that different kinds of games might be able to use.<BR>
 * Gametype-specific action types start at 1000.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.19
 * @see SOCSimpleRequest
 */
public class SOCSimpleAction extends SOCMessageTemplate4i
{
    private static final long serialVersionUID = 1119L;

    /**
     * First version number (1.1.19) that has this message type.
     * Send older clients {@link SOCGameTextMsg} or other appropriate messages instead.
     */
    public static final int VERSION_FOR_SIMPLEACTION = 1119;

    /**
     * The current player has bought a development card.
     * For i18n in v2.x, this message is sent instead of a text message announcing the buy; bots can ignore it.
     *<P>
     * {@code value1} is the number of cards remaining to be bought, {@code value2} is unused.
     * Follows a {@link SOCDevCardAction} which has the card's info, bots must process that message.
     * For complete message sequence, see {@link SOCBuyDevCardRequest} javadoc.
     * @since 1.1.19
     */
    public static final int DEVCARD_BOUGHT = 1;

    /**
     * The requested resource trade with the bank/ports was successful.
     * {@code value1} and {@code value2} are unused.
     * Sent to bots only; human players see a text message sent to the entire game.
     * @deprecated  Not used in v2.0.00 or higher; bots instead receive a {@link SOCBankTrade}
     *     which has their player number.
     * @since 1.1.19
     */
    @Deprecated
    public static final int TRADE_SUCCESSFUL = 2;

    /**
     * The current player has monopolized a resource type.
     * For i18n in v2.x, this message is sent instead of a text message announcing the monopoly.
     * Clients older than {@link SOCStringManager#VERSION_FOR_I18N} are sent the text message.
     *<P>
     * {@code value1}: Total number of resources monopolized by {@code pn}; may be 0 <br>
     * {@code value2}: The monopolized resource type,
     *     such as {@link SOCResourceConstants#CLAY} or {@link SOCResourceConstants#SHEEP}
     *<P>
     * Is preceded by each affected player's {@link SOCPlayerElement}({@code GAIN}, <em>amount</em>) or ({@code SET}, 0)
     * for that resource type, which for any victim also has the {@link SOCPlayerElement#isNews()} flag set.
     *<P>
     * In v2.0.00 - 2.4.00 those {@code SOCPlayerElement}s weren't sent until after
     * SOCSimpleAction(RSRC_TYPE_MONOPOLIZED). v2.4.50 and newer send them before the action message
     * (as v1.x did before sending current player "You monopolized..." text)
     * so client's game data is updated by the time it sees RSRC_TYPE_MONOPOLIZED.
     *
     * @since 2.0.00
     */
    public static final int RSRC_TYPE_MONOPOLIZED = 3;

    /**
     * This edge coordinate on the game board has become a Special Edge, or is no longer a Special Edge.
     * Used in some game scenarios.  Applies only to games using {@link SOCBoardLarge}.
     * Client should call {@link SOCBoardLarge#setSpecialEdge(int, int)}.
     *<P>
     * {@code pn}: Unused; -1 <br>
     * {@code value1}: The edge coordinate <br>
     * {@code value2}: Its new special edge type, such as {@link SOCBoardLarge#SPECIAL_EDGE_DEV_CARD},
     *     or 0 if no longer special
     * @since 2.0.00
     */
    public static final int BOARD_EDGE_SET_SPECIAL = 4;

    /**
     * This marker "action" means the server has sent all results/game data changes from the current dice roll.
     * Clients can now take plan and take action based on fully updated game details. Server announces this
     * to game at end of its response to current player client's {@link SOCRollDice}, only if game has a certain
     * config flag set.
     *<P>
     * Can be useful for third-party bot or client development.
     * The standard client and built-in bots don't need this to be sent:
     * If the roll results require special action from the bots or human clients
     * (move robber, discard resources, etc), the game state and other messages from server
     * will prompt that.
     *<P>
     * Is sent only if the game's {@link SOCGame#clientRequestsDiceResultsFullySent} config flag is set.
     *<P>
     * A client can request this by saying it has {@link soc.util.SOCFeatureSet#CLIENT_REQUESTS_DICE_RESULTS_FULLY_SENT}
     * when it sends {@link SOCVersion} info while connecting to the server. The flag is then set for any game that
     * client joins.
     *<P>
     * {@code pn}: Unused; -1 <br>
     * {@code value1}, {@code value2}: Unused; 0
     *
     * @since 2.4.50
     */
    public static final int DICE_RESULTS_FULLY_SENT = 5;

    /**
     * This message from server announces the results of the current player's pirate fortress attack attempt:
     * Pirates' random defense strength, number of player's ships lost (win/tie/loss).
     * Sent in response to client's {@link SOCSimpleRequest#SC_PIRI_FORT_ATTACK}
     * in scenario {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.
     *<P>
     * This message is sent out <B>after</B> related messages with game data (see below), so that those
     * can be shown visually before any popup announcing the result.
     *<P>
     * {@code value1}: The pirates' defense strength (random 1 - 6) <br>
     * {@code value2}: The number of ships lost by the player: 0 if player wins, 1 if tie, 2 if pirates win
     *<P>
     * These game data update messages are sent from server before {@code SC_PIRI_FORT_ATTACK_RESULT}, in this order:
     *<UL>
     *      Messages sent if player does not win: <br>&nbsp;
     * <LI> {@link SOCRemovePiece} for each removed ship
     * <LI> {@link SOCPlayerElement}({@link SOCPlayerElement.PEType#SCENARIO_WARSHIP_COUNT SCENARIO_WARSHIP_COUNT})
     *        if any of the player's warships were removed
     *      <P>&nbsp;<P>
     *      Messages sent if player wins: <br>&nbsp;
     * <LI> {@link SOCMoveRobber} only if all players' fortresses are recaptured,
     *        which removes the pirate fleet from the board (new pirate coordinate = 0)
     * <LI> {@link SOCPieceValue} for the fortress' reduced strength;
     *        if its new strength is 0, it is recaptured by the player
     * <LI> {@link SOCPutPiece}({@code SETTLEMENT}) if the player wins for the last time
     *        and recaptures the fortress
     *</UL>
     *
     * See also {@link SOCReportRobbery} used in this scenario to announce pirate fleet attack results.
     *
     * @since 2.0.00
     */
    public static final int SC_PIRI_FORT_ATTACK_RESULT = 1001;

    /**
     * The current player has removed a trade port from the board.
     * {@code value1} is the former port's edge coordinate, {@code value2} is the port type.
     * Sent to entire game.  If the player must place the port immediately, server will soon send
     * {@link SOCGameState}({@link SOCGame#PLACING_INV_ITEM PLACING_INV_ITEM}) among other messages.
     *<P>
     * When the player wants to place the removed port, they will send {@link SOCSimpleRequest#TRADE_PORT_PLACE}
     * with their chosen location.  If the placement is allowed, the server will broadcast a similar
     * {@link SOCSimpleRequest#TRADE_PORT_PLACE} to the game; see that javadoc for details.
     *<P>
     * Used with scenario option {@link SOCGameOptionSet#K_SC_FTRI _SC_FTRI}.
     * @since 2.0.00
     */
    public static final int TRADE_PORT_REMOVED = 1002;

    // Reminder: If you add an action type, check client and server code to determine if the new type
    // should be added to methods such as:
    // - SOCGameHandler.handleSIMPLEACTION
    // - SOCPlayerClient.handleSIMPLEACTION
    // - SOCDisplaylessPlayerClient.handleSIMPLEACTION
    // - SOCRobotBrain.run case SOCMessage.SIMPLEACTION

    /**
     * Create a SOCSimpleAction message.
     *
     * @param ga  the name of the game
     * @param pn  the player acting or acted on, or -1 if this action isn't about a specific player
     * @param actType  the action type; below 1000 is general, 1000+ is specific to one kind of game
     */
    public SOCSimpleAction(final String ga, final int pn, final int actType)
    {
        this(ga, pn, actType, 0, 0);
    }

    /**
     * Create a SOCSimpleAction message with a detail value.
     *
     * @param ga  the name of the game
     * @param pn  the player acting or acted on, or -1 if this action isn't about a specific player
     * @param actType  the action type; below 1000 is general, 1000+ is specific to one kind of game
     * @param value1  Optional detail value, or 0.  Use {@link #getValue1()}, not {@link #getParam1()}, to get
     *     this value from a {@code SOCSimpleAction} message.
     */
    public SOCSimpleAction(final String ga, final int pn, final int actType, final int value1)
    {
        this(ga, pn, actType, value1, 0);
    }

    /**
     * Create a SOCSimpleAction message with 2 detail values.
     *
     * @param ga  the name of the game
     * @param pn  the player acting or acted on, or -1 if this action isn't about a specific player
     * @param actType  the action type; below 1000 is general, 1000+ is specific to one kind of game
     * @param value1  First optional detail value, or 0.  Use {@link #getValue1()}, not {@link #getParam1()}, to get
     *     this value from a {@code SOCSimpleAction} message.
     * @param value2  Second optional detail value, or 0.  Use {@link #getValue2()}, not {@link #getParam2()}, to get
     *     this value from a {@code SOCSimpleAction} message.
     */
    public SOCSimpleAction(final String ga, final int pn, final int actType, final int value1, final int value2)
    {
        super(SIMPLEACTION, ga, pn, actType, value1, value2);
    }

    /**
     * @return the player number acting or acted on, or -1 if this action isn't about a specific player
     */
    public final int getPlayerNumber()
    {
        return p1;
    }

    /**
     * @return the action type
     */
    public final int getActionType()
    {
        return p2;
    }

    /**
     * @return the action's optional {@code value1} detail field
     */
    public final int getValue1()
    {
        return p3;
    }

    /**
     * @return the action's optional {@code value2} detail field
     */
    public final int getValue2()
    {
        return p4;
    }

    /**
     * {@link SOCMessage#SIMPLEACTION SIMPLEACTION} sep game sep2 playernumber sep2 actType sep2 value1 sep2 value2
     *
     * @param ga  the name of the game
     * @param pn  the player acting or acted on, or -1 if this action isn't about a specific player
     * @param actType  the action type; below 1000 is general, 1000+ is specific to one kind of game
     * @param value1  First optional detail value, or 0
     * @param value2  Second optional detail value, or 0
     * @return the command string
     */
    public static String toCmd(final String ga, final int pn, final int actType, final int value1, final int value2)
    {
        return SOCMessageTemplate4i.toCmd(SIMPLEACTION, ga, pn, actType, value1, value2);
    }

    /**
     * Parse the command String into a SOCSimpleAction message
     *
     * @param s   the String to parse: {@link SOCMessage#SIMPLEACTION SIMPLEACTION}
     *            sep game sep2 playernumber sep2 actType sep2 value1 sep2 value2
     * @return    a SOCSimpleAction message, or {@code null} if the data is garbled
     */
    public static SOCSimpleAction parseDataStr(final String s)
    {
        final String ga; // the game name
        final int pn;    // the player number or -1
        final int at;    // action type code
        final int v1;    // optional value1
        final int v2;    // optional value2

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            at = Integer.parseInt(st.nextToken());
            v1 = Integer.parseInt(st.nextToken());
            v2 = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCSimpleAction(ga, pn, at, v1, v2);
    }

    /**
     * Minimum version where this message type is used.
     * SIMPLEACTION introduced in 1.1.19.
     * @return Version number, 1119 for JSettlers 1.1.19.
     */
    public final int getMinimumVersion() { return VERSION_FOR_SIMPLEACTION; /* == 1119 */ }

    /**
     * Build a human-readable form of the message, with this class's field names
     * instead of generic names from {@link SOCMessageTemplate4i}.
     * @return a human readable form of the message
     * @since 2.0.00
     */
    @Override
    public String toString()
    {
        return "SOCSimpleAction:game=" + game
            + "|pn=" + p1 + "|actType=" + p2
            + "|v1=" + p3 + "|v2=" + p4;
    }

}
