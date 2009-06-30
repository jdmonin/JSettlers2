/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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

import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;


/**
 * This message lists all the soc games on a server
 *
 * @author Robert S Thomas
 */
public class SOCGames extends SOCMessage
{
    /**
     * List of games
     */
    private Vector games;

    /**
     * Create a Games Message.
     *
     * @param ga  list of games
     */
    public SOCGames(Vector ga)
    {
        messageType = GAMES;
        games = ga;
    }

    /**
     * @return the list of games
     */
    public Vector getGames()
    {
        return games;
    }

    /**
     * GAMES sep games
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(games);
    }

    /**
     * GAMES sep games
     *
     * @param ga  the list of games
     * @return    the command string
     */
    public static String toCmd(Vector ga)
    {
        String cmd = GAMES + sep;

        try
        {
            Enumeration gaEnum = ga.elements();
            cmd += (String) gaEnum.nextElement();

            while (gaEnum.hasMoreElements())
            {
                cmd += (sep2 + (String) gaEnum.nextElement());
            }
        }
        catch (Exception e) {}

        return cmd;
    }

    /**
     * Parse the command String into a Games message
     *
     * @param s   the String to parse
     * @return    a Games message, or null of the data is garbled
     */
    public static SOCGames parseDataStr(String s)
    {
        Vector ga = new Vector();
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            while (st.hasMoreTokens())
            {
                ga.addElement(st.nextToken());
            }
        }
        catch (Exception e)
        {
            System.err.println("SOCGames parseDataStr ERROR - " + e);

            return null;
        }

        return new SOCGames((Vector) ga);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCGames:games=";

        try
        {
            Enumeration gaEnum = games.elements();
            s += (String) gaEnum.nextElement();

            while (gaEnum.hasMoreElements())
            {
                s += ("," + (String) gaEnum.nextElement());
            }
        }
        catch (Exception e) {}

        return s;
    }
}
