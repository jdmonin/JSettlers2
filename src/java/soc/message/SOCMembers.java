/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2012,2014 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

import soc.server.genericServer.StringConnection;

import java.util.StringTokenizer;
import java.util.Vector;


/**
 * This message lists all the members of a chat channel
 *
 * @author Robert S Thomas
 */
public class SOCMembers extends SOCMessage
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * List of members
     */
    private Vector<String> members;

    /**
     * Name of channel
     */
    private String channel;

    /**
     * Create a Members message.
     *
     * @param ch  name of chat channel
     * @param ml  list of members
     */
    public SOCMembers(String ch, Vector<String> ml)
    {
        messageType = MEMBERS;
        members = ml;
        channel = ch;
    }

    /**
     * @return the list of members
     */
    public Vector<String> getMembers()
    {
        return members;
    }

    /**
     * @return the channel name
     */
    public String getChannel()
    {
        return channel;
    }

    /**
     * MEMBERS sep channel sep2 members
     *
     * @return the command String
     */
    @Override
    public String toCmd()
    {
        return toCmd(channel, members);
    }

    /**
     * MEMBERS sep channel sep2 members
     *<P>
     * Used from instance method {@link #toCmd()} with Strings,
     * and from other callers with StringConnections for convenience.
     *
     * @param ch  the new channel name
     * @param ml  the list of members (String or StringConnection)
     * @return    the command string
     */
    public static String toCmd(String ch, Vector<?> ml)
    {
        String cmd = MEMBERS + sep + ch;

        try
        {
            for (Object obj : ml)
            {
                String msg;
                if (obj instanceof StringConnection)
                {
                    msg = (String)((StringConnection)obj).getData();
                }
                else if (obj instanceof String)
                {
                    msg = (String)obj;
                }
                else
                {
                    msg = obj.toString();  // fallback; expecting String or conn
                }
                cmd += (sep2 + msg);
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
    public static SOCMembers parseDataStr(String s)
    {
        String ch;
        Vector<String> ml = new Vector<String>();
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ch = st.nextToken();

            while (st.hasMoreTokens())
            {
                ml.addElement(st.nextToken());
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCMembers(ch, ml);
    }

    /**
     * @return a human readable form of the message
     */
    @Override
    public String toString()
    {
        StringBuffer sb = new StringBuffer("SOCMembers:channel=");
        sb.append(channel);
        sb.append("|members=");
        if (members != null)
            enumIntoStringBuf(members.elements(), sb);
        return sb.toString();
    }
}
