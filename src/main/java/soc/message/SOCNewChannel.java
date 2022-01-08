/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2014,2017,2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.proto.Message;


/**
 * This message means that a new chat channel has been created.
 *
 * @author Robert S Thomas
 */
public class SOCNewChannel extends SOCMessage
{
    private static final long serialVersionUID = 100L;  // last structural change v1.0.0 or earlier

    /**
     * Name of the new channel.
     */
    private String channel;

    /**
     * Create a NewChannel message.
     *
     * @param ch  name of new channel
     */
    public SOCNewChannel(String ch)
    {
        messageType = NEWCHANNEL;
        channel = ch;
    }

    /**
     * @return the name of the channel
     */
    public String getChannel()
    {
        return channel;
    }

    /**
     * NEWCHANNEL sep channel
     *
     * @return the command String
     */
    public String toCmd()
    {
        return NEWCHANNEL + sep + channel;
    }

    /**
     * Parse the command String into a NewChannel message
     *
     * @param s   the String to parse
     * @return    a NewChannel message
     */
    public static SOCNewChannel parseDataStr(String s)
    {
        return new SOCNewChannel(s);
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        Message.NewChannel.Builder b = Message.NewChannel.newBuilder()
            .setChName(channel);
        return Message.FromServer.newBuilder()
            .setChNew(b).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCNewChannel:channel=" + channel;
    }

}
