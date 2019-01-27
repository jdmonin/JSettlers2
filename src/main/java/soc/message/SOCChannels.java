/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014-2019 Jeremy D Monin <jeremy@nand.net>
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

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import soc.util.DataUtils;


/**
 * This message lists all the chat channels on a server.
 * It's one of the first messages sent from the server after {@link SOCVersion}
 * when connecting. {@code SOCChannels} is sent even if the server isn't using
 * {@link soc.util.SOCFeatureSet#SERVER_CHANNELS} because clients see it as a
 * signal the connection is complete, and display their main user interface panel.
 *<P>
 * Robots ignore {@code SOCChannels}. They don't need to wait for "connection complete"
 * because their actions are initiated by the server.
 *
 * @author Robert S Thomas
 * @see SOCChannelMembers
 */
public class SOCChannels extends SOCMessage
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * List of channels
     */
    private final List<String> channels;

    /**
     * Create a Channels Message.
     *
     * @param cl  list of channels
     */
    public SOCChannels(final List<String> cl)
    {
        messageType = CHANNELS;
        channels = cl;
    }

    /**
     * @return the list of channels
     */
    public List<String> getChannels()
    {
        return channels;
    }

    /**
     * CHANNELS sep channels
     *
     * @return the command String
     */
    @Override
    public String toCmd()
    {
        return toCmd(channels);
    }

    /**
     * CHANNELS sep channels
     *
     * @param cl  the list of channels
     * @return    the command string
     */
    public static String toCmd(final List<String> cl)
    {
        StringBuilder cmd = new StringBuilder(CHANNELS + sep);

        boolean any = false;
        for (String chan : cl)
        {
            if (any)
                cmd.append(sep2);
            else
                any = true;
            cmd.append(chan);
        }

        return cmd.toString();
    }

    /**
     * Parse the command String into a Channels message
     *
     * @param s   the String to parse
     * @return    a Channels message, or null if the data is garbled
     */
    public static SOCChannels parseDataStr(String s)
    {
        List<String> cl = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            while (st.hasMoreTokens())
                cl.add(st.nextToken());
        }
        catch (Exception e)
        {
            System.err.println("SOCChannels parseDataStr ERROR - " + e);

            return null;
        }

        return new SOCChannels(cl);
    }

    /**
     * @return a human readable form of the message
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SOCChannels:channels=");
        if (channels != null)
            DataUtils.listIntoStringBuilder(channels, sb);

        return sb.toString();
    }

}
