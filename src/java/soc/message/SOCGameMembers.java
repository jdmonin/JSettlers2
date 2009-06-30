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

import soc.server.genericServer.StringConnection;

import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;


/**
 * This message lists all the members of a game
 *
 * @author Robert S Thomas
 */
public class SOCGameMembers extends SOCMessage
{
    /**
     * List of members
     */
    private Vector members;

    /**
     * Name of game
     */
    private String game;

    /**
     * Create a GameMembers message.
     *
     * @param ga  name of game
     * @param ml  list of members
     */
    public SOCGameMembers(String ga, Vector ml)
    {
        messageType = GAMEMEMBERS;
        members = ml;
        game = ga;
    }

    /**
     * @return the list of members
     */
    public Vector getMembers()
    {
        return members;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * GAMEMEMBERS sep game sep2 members
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, members);
    }

    /**
     * GAMEMEMBERS sep game sep2 members
     *
     * @param ga  the game name
     * @param ml  the list of members
     * @return    the command string
     */
    public static String toCmd(String ga, Vector ml)
    {
        String cmd = GAMEMEMBERS + sep + ga;

        try
        {
            Enumeration mlEnum = ml.elements();

            while (mlEnum.hasMoreElements())
            {
                StringConnection con = (StringConnection) mlEnum.nextElement();
                cmd += (sep2 + (String) con.getData());
            }
        }
        catch (Exception e) {}

        return cmd;
    }

    /**
     * Parse the command String into a Members message
     *
     * @param s   the String to parse
     * @return    a Members message, or null of the data is garbled
     */
    public static SOCGameMembers parseDataStr(String s)
    {
        String ga;
        Vector ml = new Vector();
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();

            while (st.hasMoreTokens())
            {
                ml.addElement(st.nextToken());
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCGameMembers(ga, ml);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCGameMembers:game=" + game + "|members=";

        try
        {
            Enumeration mlEnum = members.elements();
            s += (String) mlEnum.nextElement();

            while (mlEnum.hasMoreElements())
            {
                s += ("," + (String) mlEnum.nextElement());
            }
        }
        catch (Exception e) {}

        return s;
    }
}
