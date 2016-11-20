/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014 Jeremy D Monin <jeremy@nand.net>
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
 * This message says which resources the player picked
 * for a Discovery card
 *
 * @author Robert S. Thomas
 */
public class SOCDiscoveryPick extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * The chosen resources
     */
    private SOCResourceSet resources;

    /**
     * Create a DiscoveryPick message.
     *
     * @param ga   the name of the game
     * @param rs   the chosen resources
     */
    public SOCDiscoveryPick(String ga, SOCResourceSet rs)
    {
        messageType = DISCOVERYPICK;
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
     * @return the chosen resources
     */
    public SOCResourceSet getResources()
    {
        return resources;
    }

    /**
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, resources);
    }

    /**
     * @return the command string
     *
     * @param ga  the name of the game
     * @param rs   the chosen resources
     */
    public static String toCmd(String ga, SOCResourceSet rs)
    {
        String cmd = DISCOVERYPICK + sep + ga;

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            cmd += (sep2 + rs.getAmount(i));
        }

        return cmd;
    }

    /**
     * Parse the command String into a DiscoveryPick message
     *
     * @param s   the String to parse
     * @return    a DiscoveryPick message, or null of the data is garbled
     */
    public static SOCDiscoveryPick parseDataStr(String s)
    {
        String ga; // the game name
        SOCResourceSet rs; // the chosen resources

        rs = new SOCResourceSet();

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();

            /**
             * Note: this only works if SOCResourceConstants.CLAY == 1
             */
            for (int i = 1; i <= SOCResourceConstants.WOOD; i++)
            {
                rs.setAmount(Integer.parseInt(st.nextToken()), i);
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCDiscoveryPick(ga, rs);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCDiscoveryPick:game=" + game + "|resources=" + resources;
    }
}
