/**
 * JSettlers network message system.
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>.
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
 * Uses {@link ServerSocket} to implement {@link SOCServerSocket} over a network
 * and send and receive Protobuf with client {@link ProtoConnection}s.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 3.0.00
 */
public class ProtoServerSocket extends NetServerSocket
{
    /**
     * {@inheritDoc}
     *<P>
     * To detect during startup whether the {@code protobuf-lite} JAR is on the classpath,
     * instead of later when a connection comes in, attempts to instantiate a class
     * from that JAR. Throws {@link LinkageError} if that can't be done.
     * @throws IOException If an error occurs creating the ServerSocket
     * @throws LinkageError If {@code protobuf-lite}'s JAR does not appear to be on the classpath;
     *     thrown if cannot instantiate class {@link com.google.protobuf.MessageLiteOrBuilder}
     */
    public ProtoServerSocket(int port, Server server)
        throws IOException, LinkageError
    {
        super(port, server);

        Class<?> cl = com.google.protobuf.MessageLiteOrBuilder.class;  // may throw NoClassDefFoundError
        if (cl.getCanonicalName().length() > port)
            System.err.println();  // Side effect here to prevent optimizing cl away
    }

    /**
     * {@inheritDoc}
     * Returns a new {@link ProtoConnection}.
     */
    @Override
    public Connection accept()
        throws SocketException, IOException
    {
        Socket s = implServSocket.accept();
        return new ProtoConnection(s, server);
    }

}
