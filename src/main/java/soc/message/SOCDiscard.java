/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2012,2014,2016-2022 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.ResourceSet;
import soc.game.SOCGame;  // for javadocs only
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;

import java.util.StringTokenizer;


/**
 * From client, this message gives the resources that a player has chosen to discard;
 * client's response to server's {@link SOCDiscardRequest}.
 *<P>
 * If the resource total isn't correct, server v2.5.00 and newer will
 * resend {@code SOCDiscardRequest} with the required resource count.
 *<P>
 * If this is the right total amount to discard, server will respond to player
 * with {@code SOCDiscard} to confirm the details, and report only the total amount discarded
 * to the other players via {@code SOCDiscard}(UNKNOWN=total).
 * Clients older than v2.5 ({@link #VERSION_FOR_OMIT_PLAYERELEMENTS_ALWAYS_GAMESTATE})
 * are instead sent {@link SOCPlayerElement} or {@link SOCPlayerElements}(LOSE) messages and text.
 *<P>
 * Server will then send the new {@link SOCGameState}.
 * If waiting for others to discard, server then sends the game a {@link SOCGameServerText} that lists
 * who we're still waiting for. The {@link SOCGameState}({@link SOCGame#WAITING_FOR_DISCARDS WAITING_FOR_DISCARDS})
 * sent is redundant in that case, but server sends it anyway in order to regularize the message sequence
 * to make it easier for bots to understand.
 *<P>
 * Server v2.0.00 through v2.4.00 didn't send that {@code SOCGameState(WAITING_FOR_DISCARDS)},
 * to be a bit more efficient. So for compatibility, server won't send that redundant message to
 * clients older than v2.5. All client versions including v1.x.xx correctly display progress of the discards
 * without needing that {@code SOCGameState}.
 *
 * @author Robert S. Thomas
 */
public class SOCDiscard extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2500L;  // last structural change v2.5.00

    /**
     * First version (v2.5.00) where server sends this message instead of {@link SOCPlayerElement}s;
     * client uses this message's fields to update the player's resources;
     * and after a player discards, if other players still must discard,
     * server sends {@link SOCGameState}({@link SOCGame#WAITING_FOR_DISCARDS}) for clarity
     * and to mark end of message sequence, although state hasn't changed.
     *<P>
     * That redundant {@code SOCGameState} was also sent in v1.x, but not v2.0 - v2.4.
     *
     * @since 2.5.00
     */
    public static final int VERSION_FOR_OMIT_PLAYERELEMENTS_ALWAYS_GAMESTATE = 2500;

    /**
     * Name of game
     */
    private String game;

    /**
     * Optional player number; see {@link #getPlayerNumber()}
     */
    private int playerNumber = -1;

    /**
     * The set of resources being discarded
     */
    private ResourceSet resources;

    /**
     * Create a Discard message without a player number.
     *
     * @param ga  the name of the game
     * @param cl  the amount of clay being discarded
     * @param or  the amount of ore being discarded
     * @param sh  the amount of sheep being discarded
     * @param wh  the amount of wheat being discarded
     * @param wo  the amount of wood being discarded
     * @param uk  the amount of unknown resources being discarded
     * @see #SOCDiscard(String, int, int, int, int, int, int, int)
     * @see #SOCDiscard(String, int, ResourceSet)
     */
    public SOCDiscard(String ga, int cl, int or, int sh, int wh, int wo, int uk)
    {
        this(ga, -1, cl, or, sh, wh, wo, uk);
    }

    /**
     * Create a Discard message with an optional player number.
     *
     * @param ga  the name of the game
     * @param pn  Player number, or -1 if none; requires v2.5 or newer client/server
     *     ({@link #VERSION_FOR_OMIT_PLAYERELEMENTS_ALWAYS_GAMESTATE})
     * @param cl  the amount of clay being discarded
     * @param or  the amount of ore being discarded
     * @param sh  the amount of sheep being discarded
     * @param wh  the amount of wheat being discarded
     * @param wo  the amount of wood being discarded
     * @param uk  the amount of unknown resources being discarded
     * @see #SOCDiscard(String, int, int, int, int, int, int)
     * @see #SOCDiscard(String, int, ResourceSet)
     * @since 2.5.00
     */
    public SOCDiscard(String ga, int pn, int cl, int or, int sh, int wh, int wo, int uk)
    {
        messageType = DISCARD;
        game = ga;
        playerNumber = pn;
        resources = new SOCResourceSet(cl, or, sh, wh, wo, uk);
    }

    /**
     * Create a Discard message.
     *
     * @param ga  the name of the game
     * @param pn  Player number, or -1 if none; player number requires v2.5 or newer client/server
     *     ({@link #VERSION_FOR_OMIT_PLAYERELEMENTS_ALWAYS_GAMESTATE})
     * @param rs  the resources being discarded
     * @see #SOCDiscard(String, int, int, int, int, int, int)
     * @see #SOCDiscard(String, int, int, int, int, int, int, int)
     */
    public SOCDiscard(String ga, int pn, ResourceSet rs)
    {
        messageType = DISCARD;
        playerNumber = pn;
        game = ga;
        resources = rs;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * Get the optional player number discarding. Not sent from client or from server older than v2.5
     * ({@link #VERSION_FOR_OMIT_PLAYERELEMENTS_ALWAYS_GAMESTATE}).
     * @return the player number, or -1 if none in message
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the set of resources being discarded
     */
    public ResourceSet getResources()
    {
        return resources;
    }

    /**
     * DISCARD sep game sep2 ['p' playerNumber sep2] clay sep2 ore sep2 sheep sep2
     * wheat sep2 wood sep2 unknown
     *<P>
     * Player number field (v2.5+ only) was deliberately inserted near the start of the field list
     * to discourage sending it to older clients which can't understand it:
     * If that field was at the end, those clients would ignore it.
     *
     * @return the command string
     */
    public String toCmd()
    {
        return DISCARD + sep + game + sep2
            + ((playerNumber >= 0) ? "p" + playerNumber + sep2 : "")
            + resources.getAmount(SOCResourceConstants.CLAY) + sep2
            + resources.getAmount(SOCResourceConstants.ORE) + sep2
            + resources.getAmount(SOCResourceConstants.SHEEP) + sep2
            + resources.getAmount(SOCResourceConstants.WHEAT) + sep2
            + resources.getAmount(SOCResourceConstants.WOOD) + sep2
            + resources.getAmount(SOCResourceConstants.UNKNOWN);
    }

    /**
     * Parse the command String into a Discard message
     *
     * @param s   the String to parse
     * @return    a Discard message, or null if the data is garbled
     */
    public static SOCDiscard parseDataStr(String s)
    {
        String ga; // the game name
        int pn = -1;  // player number (v2.5+)
        int cl; // the amount of clay being discarded
        int or; // the amount of ore being discarded
        int sh; // the amount of sheep being discarded
        int wh; // the amount of wheat being discarded
        int wo; // the amount of wood being discarded
        int uk; // the amount of unknown resources being discarded

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            String tok = st.nextToken();
            if (tok.charAt(0) == 'p')
            {
                pn = Integer.parseInt(tok.substring(1));
                tok = st.nextToken();
            }
            cl = Integer.parseInt(tok);
            or = Integer.parseInt(st.nextToken());
            sh = Integer.parseInt(st.nextToken());
            wh = Integer.parseInt(st.nextToken());
            wo = Integer.parseInt(st.nextToken());
            uk = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCDiscard(ga, pn, cl, or, sh, wh, wo, uk);
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link SOCMessage#parseMsgStr(String)}.
     * @param message Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     * @since 2.5.00
     */
    public static String stripAttribNames(String message)
    {
        message = message.replace("resources=",  "");

        int pos = message.indexOf("|playerNum=");
        if (pos != -1)
            message = message.substring(0, pos + 2) + message.substring(pos + 11);  // "|playerNum=3" -> "|p3"

        return SOCMessage.stripAttribNames(message);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCDiscard:game=" + game
            + ((playerNumber >= 0) ? "|playerNum=" + playerNumber : "")
            + "|resources=" + resources;
    }

}
