/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010 Jeremy D Monin <jeremy@nand.net>
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


/**
 * This message says what resource the current player wants to
 * monopolize
 *
 * @author Robert S. Thomas
 */
public class SOCMonopolyPick extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of game
     */
    private String game;

    /**
     * The chosen resource
     */
    private int resource;

    /**
     * Create a MonopolyPick message.
     *
     * @param ga  the name of the game
     * @param rs  the resource
     */
    public SOCMonopolyPick(String ga, int rs)
    {
        messageType = MONOPOLYPICK;
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
     * @return the chosen resource
     */
    public int getResource()
    {
        return resource;
    }

    /**
     * MONOPOLYPICK sep game sep2 resource
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, resource);
    }

    /**
     * MONOPOLYPICK sep game sep2 resource
     *
     * @param ga  the name of the game
     * @param rs  the chosen resource
     * @return the command string
     */
    public static String toCmd(String ga, int rs)
    {
        return MONOPOLYPICK + sep + ga + sep2 + rs;
    }

    /**
     * Parse the command String into a StartGame message
     *
     * @param s   the String to parse
     * @return    a StartGame message, or null of the data is garbled
     */
    public static SOCMonopolyPick parseDataStr(String s)
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

        return new SOCMonopolyPick(ga, rs);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCMonopolyPick:game=" + game + "|resource=" + resource;
    }
}
