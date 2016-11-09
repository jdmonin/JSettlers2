/**
 * Local (StringConnection) network system.  Version 1.0.5.
 * Copyright (C) 2007-2009 Jeremy D Monin <jeremy@nand.net>.
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
 * The author of this program can be reached at jeremy@nand.net
 **/
package soc.server.genericServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import soc.server.SOCInboundMessageQueue;

/**
 * Uses ServerSocket to implement StringServerSocket over a network.<br>
 * before the version 2.0.00 this class was an inner class in the {@link Server} class
 * 
 * @author Alessandro D'Ottavio
 * @since 2.0.00
 */
public class NetStringServerSocket implements StringServerSocket
{
    
    private ServerSocket implServSocket;
    private Server server;
    private SOCInboundMessageQueue inboundMessageQueue;

    public NetStringServerSocket (int port, Server server,SOCInboundMessageQueue inboundMessageQueue) throws IOException
    {
        this.implServSocket = new ServerSocket(port);
        this.server = server;
        this.inboundMessageQueue = inboundMessageQueue;
    }

    public StringConnection accept() throws SocketException, IOException
    {
        Socket s = implServSocket.accept();
        return new NetStringConnection(s, server,inboundMessageQueue);
    }

    public void close() throws IOException
    {
        implServSocket.close();
    }

}
