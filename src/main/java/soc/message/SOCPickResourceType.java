/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2017-2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCResourceConstants;  // for javadocs only


/**
 * This message says which resource type the current player wants to
 * pick for a game action, such as using a Monopoly development card.
 *<P>
 * Sent from client as a request.
 *<P>
 * Before v2.0.00 this class was {@code SOCMonopolyPick}.
 *
 * @see SOCPickResources
 * @author Robert S. Thomas
 */
public class SOCPickResourceType extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Name of game
     */
    private String game;

    /**
     * The chosen resource type,
     * such as {@link SOCResourceConstants#CLAY} or {@link SOCResourceConstants#SHEEP}
     */
    private int resource;

    /**
     * Create a SOCPickResourceType message.
     *
     * @param ga  the name of the game
     * @param rs  the resource type,
     *     such as {@link SOCResourceConstants#CLAY} or {@link SOCResourceConstants#SHEEP}
     */
    public SOCPickResourceType(String ga, int rs)
    {
        messageType = PICKRESOURCETYPE;
        game = ga;
        resource = rs;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * Get the player's chosen resource type.
     *<P>
     * Before v2.0.00 this method was {@code getResource()}.
     * @return the chosen resource type,
     *     such as {@link SOCResourceConstants#CLAY} or {@link SOCResourceConstants#SHEEP}
     */
    public int getResourceType()
    {
        return resource;
    }

    /**
     * PICKRESOURCETYPE sep game sep2 resourceType
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, resource);
    }

    /**
     * PICKRESOURCETYPE sep game sep2 resourceType
     *
     * @param ga  the name of the game
     * @param rs  the chosen resource type,
     *     such as {@link SOCResourceConstants#CLAY} or {@link SOCResourceConstants#SHEEP}
     * @return the command string
     */
    public static String toCmd(String ga, int rs)
    {
        return PICKRESOURCETYPE + sep + ga + sep2 + rs;
    }

    /**
     * Parse the command String into a PICKRESOURCETYPE message.
     *
     * @param s   the String to parse
     * @return    a PickResourceType message, or null if the data is garbled
     */
    public static SOCPickResourceType parseDataStr(String s)
    {
        String ga; // the game name
        int rs; // the chosen resource

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            rs = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPickResourceType(ga, rs);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCPickResourceType:game=" + game + "|resType=" + resource;
    }
}
