/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2012,2014,2016-2017 Jeremy D Monin <jeremy@nand.net>
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
 * This message lists all the members of a single chat channel.
<P>
 * Before v2.0.00 this class was named {@code SOCMembers}.
 *
 * @author Robert S Thomas
 * @see SOCGameMembers
 * @see SOCChannels
 */
public class SOCChannelMembers extends SOCMessage
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
     * Create a Channel Members message.
     *
     * @param ch  name of chat channel
     * @param ml  list of members
     */
    public SOCChannelMembers(String ch, Vector<String> ml)
    {
        messageType = CHANNELMEMBERS;
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
     * CHANNELMEMBERS sep channel sep2 members
     *
     * @return the command String
     */
    @Override
    public String toCmd()
    {
        return toCmd(channel, members);
    }

    /**
     * CHANNELMEMBERS sep channel sep2 members
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
        String cmd = CHANNELMEMBERS + sep + ch;

        try
        {
            for (Object obj : ml)
            {
                String msg;
                if (obj instanceof StringConnection)
                {
                    msg = ((StringConnection) obj).getData();
                }
                else if (obj instanceof String)
                {
                    msg = (String) obj;
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
     * Parse the command String into a Channel Members message.
     *
     * @param s   the String to parse
     * @return    a Channel Members message, or null if the data is garbled
     */
    public static SOCChannelMembers parseDataStr(String s)
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

        return new SOCChannelMembers(ch, ml);
    }

    /**
     * @return a human readable form of the message
     */
    @Override
    public String toString()
    {
        StringBuffer sb = new StringBuffer("SOCChannelMembers:channel=");
        sb.append(channel);
        sb.append("|members=");
        if (members != null)
            enumIntoStringBuf(members.elements(), sb);
        return sb.toString();
    }
}
