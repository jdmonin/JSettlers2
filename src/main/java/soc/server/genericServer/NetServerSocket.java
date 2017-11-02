/**
 * JSettlers network message system.
 * This file Copyright (C) 2007-2009,2016-2017 Jeremy D Monin <jeremy@nand.net>.
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2016 Alessandro D'Ottavio
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

/**
 * Uses {@link ServerSocket} to implement {@link SOCServerSocket} over a network.
 *<P>
 * Before version 2.0.00 this class was an inner class {@code NetStringServerSocket} in {@link Server}.
 *
 * @since 2.0.00
 */
public class NetServerSocket implements SOCServerSocket
{
    private final ServerSocket implServSocket;
    private final Server server;

    public NetServerSocket(int port, Server server)
        throws IOException
    {
        this.implServSocket = new ServerSocket(port);
        this.server = server;
    }

    public Connection accept()
        throws SocketException, IOException
    {
        Socket s = implServSocket.accept();
        return new NetConnection(s, server);
    }

    public void close()
        throws IOException
    {
        implServSocket.close();
    }

}
