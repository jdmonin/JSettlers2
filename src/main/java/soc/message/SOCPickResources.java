/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2017-2018,2020 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2017-2018 Strategic Conversation (STAC Project) https://www.irit.fr/STAC/
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

import soc.game.SOCGame;  // for javadocs only
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;

import java.util.StringTokenizer;


/**
 * This message says which resources the player picked for a
 * Discovery/Year of Plenty card, or (v2.0.00+) free resources from the Gold Hex
 * or other events in Sea Board scenarios.
 *<P>
 * Also used in scenario SC_PIRI when player wins a free resource for defeating the
 * pirate fleet attack at a dice roll, as if it's a gold hex free resource.
 *
 *<H3>Sent from Client:</H3>
 *
 * For <B>Discovery/Year of Plenty,</B> this is the client response to server's
 * {@link SOCGameState GAMESTATE}({@link SOCGame#WAITING_FOR_DISCOVERY WAITING_FOR_DISCOVERY}).
 *<P>
 * For <B>Gold Hex</B> picks or other Sea Board scenarios, this is the Client response to server's
 * {@link SOCSimpleRequest}({@link SOCSimpleRequest#PROMPT_PICK_RESOURCES PROMPT_PICK_RESOURCES}).
 * (Same prompt/response pattern as {@link SOCDiscardRequest} / {@link SOCDiscard}.)
 *<P>
 * In either of those situations, if the resource count is wrong, the server will
 * resend {@code SOCSimpleRequest(PROMPT_PICK_RESOURCES)} with the required resource count.
 *<BR>
 * Otherwise, server announces a {@code SOCPickResources} to the game to give player those resources:
 * See next section for details.
 *
 *<H3>Sent from Server:</H3>
 *
 * A player has chosen their two free Discovery/Year of Plenty resources
 * in state {@link soc.game.SOCGame#WAITING_FOR_DISCOVERY}, or free Gold Hex resources.
 * Announced by server to update game data and have the client indicate that the trade has happened,
 * as if they were sent a {@code SOCGameServerText} about it, based on {@link #getReasonCode()} value.
 * When reason code is 0, print no text: Server might follow this message with a {@link SOCGameServerText}
 * to explain the reason for this resource pick.
 *<P>
 * For gold hex picks, server follows this message with a
 * {@link SOCPlayerElement}({@link SOCPlayerElement.PEType#NUM_PICK_GOLD_HEX_RESOURCES NUM_PICK_GOLD_HEX_RESOURCES}, 0)
 * to clear that player field.
 *<P>
 * When server announces to clients older than v2.4.50 ({@link #VERSION_FOR_SERVER_ANNOUNCE}),
 * it instead sends player data update {@link SOCPlayerElement} message(s) and {@link SOCGameServerText}.
 *<P>
 * Before v2.0.00 this message class was called {@code SOCDiscoveryPick}.
 *
 * @see SOCPickResourceType
 * @author Robert S. Thomas
 */
public class SOCPickResources extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Minimum server and client version number where server sends this message,
     * with player number and optional reason code ({@link #REASON_DISCOVERY}, etc): 2450 for v2.4.50.
     * @since 2.4.50
     */
    public static final int VERSION_FOR_SERVER_ANNOUNCE = 2450;

    /**
     * Generic pick reason. Client can mention as "(player) has picked (resources)."
     * @since 2.4.50
     */
    public static final int REASON_GENERIC = 1;

    /**
     * Discovery/Year of Plenty reason. Client can mention as "(player) received (resources) from the bank."
     * @since 2.4.50
     */
    public static final int REASON_DISCOVERY = 2;

    /**
     * Gold Hex pick reason. Client can mention as "(player) has picked (resources) from the gold hex."
     * @since 2.4.50
     */
    public static final int REASON_GOLD_HEX = 3;

    private static final long serialVersionUID = 2450L;  // last structural change v2.4.50

    /**
     * Name of game
     */
    private String game;

    /**
     * The set of resources picked to be gained
     */
    private SOCResourceSet resources;

    /**
     * Player number when sent from server, or 0 when sent from client:
     * See {@link #getPlayerNumber()} for details
     * @since 2.4.50
     */
    private int playerNumber;

    /**
     * Optional reason code when sent from server, or 0:
     * See {@link #getReasonCode()} for details
     * @since 2.4.50
     */
    private int reasonCode;

    /**
     * Create a Pick Resources message without a player number or reason code.
     *
     * @param ga  the name of the game
     * @param cl  the amount of clay being picked
     * @param or  the amount of ore being picked
     * @param sh  the amount of sheep being picked
     * @param wh  the amount of wheat being picked
     * @param wo  the amount of wood being picked
     * @since 2.0.00
     */
    public SOCPickResources(String ga, int cl, int or, int sh, int wh, int wo)
    {
        this(ga, new SOCResourceSet(cl, or, sh, wh, wo, 0));
    }

    /**
     * Create a PickResources message without a player number or reason code.
     *
     * @param ga  the name of the game
     * @param rs  the resources being picked.
     *     Should not contain unknown resources, those will be ignored and not sent.
     */
    public SOCPickResources(String ga, SOCResourceSet rs)
    {
        this(ga, rs, 0, 0);
    }

    /**
     * Create a PickResources message with an optional player number and reason code,
     * for sending to client v2.4.50 or newer ({@link #VERSION_FOR_SERVER_ANNOUNCE}).
     *
     * @param ga  the name of the game
     * @param rs  the resources being picked.
     *     Should not contain unknown resources, those will be ignored and not sent.
     * @param pn  player number when sent from server, or 0 when sent from client
     * @param rc  reason code ({@link #REASON_DISCOVERY}, etc), or 0 for none
     */
    public SOCPickResources(String ga, SOCResourceSet rs, int pn, int rc)
    {
        messageType = PICKRESOURCES;
        game = ga;
        resources = rs;
        playerNumber = pn;
        reasonCode = rc;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the set of picked resources
     */
    public SOCResourceSet getResources()
    {
        return resources;
    }

    /**
     * Get the player number when sent from server, or 0 when sent from client.
     *<P>
     * Ignored by clients older than {@link #VERSION_FOR_SERVER_ANNOUNCE}.
     *
     * @return  player number, or 0 if none
     * @since 2.4.50
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * Get the optional reason code when sent from server,
     * like {@link #REASON_DISCOVERY} or {@link #REASON_GOLD_HEX}, or 0.
     *<P>
     * Ignored by clients older than {@link #VERSION_FOR_SERVER_ANNOUNCE}.
     *
     * @return reason code, or 0 if none
     * @since 2.4.50
     */
    public int getReasonCode()
    {
        return reasonCode;
    }

    /**
     * Build a command string for this message:
     * PICKRESOURCES sep game sep2 clay sep2 ore sep2 sheep sep2 wheat sep2 wood [sep2 playerNumber sep2 reasonCode]
     *
     * @return the command string
     */
    public String toCmd()
    {
        StringBuilder cmd = new StringBuilder(PICKRESOURCES + sep + game);

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            cmd.append(sep2_char).append(resources.getAmount(i));
        }

        if ((playerNumber != 0) || (reasonCode != 0))
            cmd.append(sep2_char).append(playerNumber).append(sep2_char).append(reasonCode);

        return cmd.toString();
    }

    /**
     * Parse the command String into a PickResources message.
     *
     * @param s   the String to parse
     * @return    a PickResources message, or null if the data is garbled
     */
    public static SOCPickResources parseDataStr(String s)
    {
        final String ga; // the game name
        final int cl, // the amount of clay being picked
                  or, // the amount of ore being picked
                  sh, // the amount of sheep being picked
                  wh, // the amount of wheat being picked
                  wo; // the amount of wood being picked
        int pn = 0, reasonCode = 0;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            cl = Integer.parseInt(st.nextToken());
            or = Integer.parseInt(st.nextToken());
            sh = Integer.parseInt(st.nextToken());
            wh = Integer.parseInt(st.nextToken());
            wo = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens())
            {
                pn = Integer.parseInt(st.nextToken());
                reasonCode = Integer.parseInt(st.nextToken());
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPickResources
            (ga, new SOCResourceSet(cl, or, sh, wh, wo, 0), pn, reasonCode);
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link SOCMessage#parseMsgStr(String)}.
     * @param message Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     * @since 2.4.50
     */
    public static String stripAttribNames(String message)
    {
        message = message.replace("resources=",  "").replace("|unknown=0", "");

        return SOCMessage.stripAttribNames(message);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder
            ("SOCPickResources:game=" + game + "|resources=" + resources);
        if ((playerNumber != 0) || (reasonCode != 0))
            sb.append("|pn=").append(playerNumber).append("|reason=").append(reasonCode);

        return sb.toString();
    }

}
