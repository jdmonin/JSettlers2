/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2012,2014,2016-2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;

import java.util.StringTokenizer;


/**
 * This message gives the resources that a player has chosen to discard;
 * client's response to server's {@link SOCDiscardRequest}.
 * The server will report the discard's resource total to the other
 * players via {@link SOCPlayerElement} and text, but will not send
 * a {@code SOCDiscard} message to other players.
 *
 * @author Robert S. Thomas
 */
public class SOCDiscard extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * The set of resources being discarded
     */
    private SOCResourceSet resources;

    /**
     * Create a Discard message.
     *
     * @param ga  the name of the game
     * @param cl  the amount of clay being discarded
     * @param or  the amount of ore being discarded
     * @param sh  the amount of sheep being discarded
     * @param wh  the amount of wheat being discarded
     * @param wo  the amount of wood being discarded
     * @param uk  the amount of unknown resources being discarded
     */
    public SOCDiscard(String ga, int cl, int or, int sh, int wh, int wo, int uk)
    {
        messageType = DISCARD;
        game = ga;
        resources = new SOCResourceSet(cl, or, sh, wh, wo, uk);
    }

    /**
     * Create a Discard message.
     *
     * @param ga  the name of the game
     * @param rs  the resources being discarded
     */
    public SOCDiscard(String ga, int pn, SOCResourceSet rs)
    {
        messageType = DISCARD;
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
     * @return the set of resources being discarded
     */
    public SOCResourceSet getResources()
    {
        return resources;
    }

    /**
     * DISCARD sep game sep2 clay sep2 ore sep2 sheep sep2
     * wheat sep2 wood sep2 unknown
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, resources);
    }

    /**
     * DISCARD sep game sep2 clay sep2 ore sep2 sheep sep2
     * wheat sep2 wood sep2 unknown
     *
     * @param ga  the name of the game
     * @param rs  the resources being discarded
     * @return the command string
     */
    public static String toCmd(String ga, SOCResourceSet rs)
    {
        return DISCARD + sep + ga + sep2
            + rs.getAmount(SOCResourceConstants.CLAY) + sep2
            + rs.getAmount(SOCResourceConstants.ORE) + sep2
            + rs.getAmount(SOCResourceConstants.SHEEP) + sep2
            + rs.getAmount(SOCResourceConstants.WHEAT) + sep2
            + rs.getAmount(SOCResourceConstants.WOOD) + sep2
            + rs.getAmount(SOCResourceConstants.UNKNOWN);
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
            cl = Integer.parseInt(st.nextToken());
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

        return new SOCDiscard(ga, cl, or, sh, wh, wo, uk);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCDiscard:game=" + game + "|resources=" + resources;
    }

}
