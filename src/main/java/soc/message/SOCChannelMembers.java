/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2012,2014,2016-2017,2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.server.genericServer.Connection;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;


/**
 * This message lists all the members of a single chat channel.
 *<P>
 * When joining a channel, after {@link SOCJoinChannelAuth} the client is sent this message
 * with all current members (not including the client), then client and those other
 * channel members are sent a {@link SOCJoinChannel} to announce client's nickname joining.
 *<P>
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
    private List<String> members;

    /**
     * Name of channel
     */
    private String channel;

    /**
     * Create a Channel Members message at server.
     *
     * @param ch  the new channel name
     * @param ml  the list of members (String or {@link Connection})
     * @return    the command string
     * @since 2.4.50
     */
    public SOCChannelMembers(String ch, List<?> ml)
    {
        this(ch, new ArrayList<String>(), false);

        try
        {
            for (Object obj : ml)
            {
                if (obj instanceof Connection)
                    members.add(((Connection) obj).getData());
                else if (obj instanceof String)
                    members.add((String) obj);
                else
                    members.add(obj.toString());  // fallback; expecting String or conn
            }
        }
        catch (Exception e) {}
    }

    /**
     * Create a Channel Members message.
     *
     * @param ch  name of chat channel
     * @param ml  list of member names
     * @param clientMarker  Parameter is here only to differentiate the public server-side (List&lt;Object>) constructor
     *     from this private/client-side (List&lt;String>) one
     */
    private SOCChannelMembers(String ch, List<String> ml, final boolean clientMarker)
    {
        messageType = CHANNELMEMBERS;
        members = ml;
        channel = ch;
    }

    /**
     * @return the list of members
     */
    public List<String> getMembers()
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
        StringBuilder cmd = new StringBuilder(CHANNELMEMBERS + sep + channel);

        try
        {
            for (String mname : members)
                cmd.append(sep2_char).append(mname);
        }
        catch (Exception e) {}

        return cmd.toString();
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
        ArrayList<String> ml = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ch = st.nextToken();

            while (st.hasMoreTokens())
            {
                ml.add(st.nextToken());
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCChannelMembers(ch, ml, true);
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for
     * {@link SOCMessage#parseMsgStr(String)} to pass to {@link #parseDataStr(String)}.
     * Handles square brackets around list of members.
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Member list for {@link #parseDataStr(String)}, or {@code null} if params are malformed
     * @see #stripAttribNamesToMemberList(String, String)
     * @since 2.4.50
     */
    public static String stripAttribNames(String messageStrParams)
    {
        // "channel=chn|members=[player0, droid 1, robot 2, debug]"

        return SOCGameMembers.stripAttribNamesToMemberList("channel=", messageStrParams);
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
            sb.append(members);  // "[joe, bob, lily, ...]"

        return sb.toString();
    }

}
