/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.baseclient;

import soc.server.genericServer.StringConnection;  // for javadocs only

/**
 * Data class to hold the info a client must know to connect to a server.
 *<P>
 * Includes an optional cookie field for robot clients to use.
 * Does not include user's nickname or optional password.
 *<P>
 * This class is a way to avoid having pairs of client constructors (for TCP and string sockets).
 * It also gives a backwards-compatible way to give future versions' bot clients
 * more server info or a new protocol's port number without changing their constructors.
 *
 * @see soc.robot.SOCRobotClient
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.2.00
 */
public class ServerConnectInfo
{
    /**
     * TCP server hostname or IP address,
     * or {@code null} when using {@link #stringSocketName} instead.
     * Localhost is stored as the string {@code "localhost"}, not {@code "127.0.0.1"} or {@code "::1"}.
     */
    public final String hostname;

    /**
     * TCP port number which can be used for legacy {@code SOCMessage} connection to server on {@link #hostname},
     * or 0 when instead using {@link #stringSocketName} or solely {@link #protoPort}.
     * @see #protoPort
     */
    public final int port;

    /**
     * TCP port number which can be used for protobuf connection to server on {@link #hostname}, or 0 if not enabled.
     * @see #port
     * @since 3.0.00
     */
    public final int protoPort;

    /**
     * The server's stringport socket name if it's a same-JVM local server
     * using {@link StringConnection}, or {@code null} when using TCP {@link #port} instead.
     * Can be used by robots in local practice games.
     */
    public final String stringSocketName;

    /**
     * Security cookie (weak shared secret) for robot connections to server.
     * Required by server v1.1.19 and higher.
     * Unused ({@code null}) for non-bot human clients.
     */
    public final String robotCookie;

    /**
     * ServerConnectInfo to connect to a server using TCP supporting only the legacy {@code SOCMessage} protocol.
     * @param host  Server hostname; see {@link #hostname} for details
     * @param port  Server port number. Default port is {@link soc.client.ClientNetwork#SOC_PORT_DEFAULT}.
     * @param cookie  Bot cookie, or {@code null} for human client; see {@link #robotCookie} for details
     */
    public ServerConnectInfo(final String host, final int port, final String cookie)
    {
        this(host, port, 0, cookie);
    }

    /**
     * ServerConnectInfo to connect to a server using TCP
     * supporting protobuf and/or the legacy {@code SOCMessage} protocol.
     * @param host  Server hostname; see {@link #hostname} for details
     * @param port  Server port number for legacy {@code SOCMessage} protocol, or 0.
     *     Default port is {@link soc.client.ClientNetwork#SOC_PORT_DEFAULT}.
     * @param protoPort  Server port number for protobuf, or 0.
     *     Default protobuf port for JSettlers is {@link SOCDisplaylessPlayerClient#PORT_DEFAULT_PROTOBUF}.
     * @param cookie  Bot cookie, or {@code null} for human client; see {@link #robotCookie} for details
     * @throws IllegalArgumentException if both {@code port} and {@code protoPort} are 0
     * @since 3.0.00
     */
    public ServerConnectInfo(final String host, final int port, final int protoPort, final String cookie)
        throws IllegalArgumentException
    {
        if ((port == 0) && (protoPort == 0))
            throw new IllegalArgumentException();

        hostname = host;
        this.port = port;
        this.protoPort = protoPort;
        stringSocketName = null;
        robotCookie = cookie;
    }

    /**
     * ServerConnectInfo to connect to a same-JVM server using {@link StringConnection}.
     * @param stringSocketName  Server stringPort name; see {@link #stringSocketName} for details
     * @param cookie  Bot cookie, or {@code null} for human client; see {@link #robotCookie} for details
     */
    public ServerConnectInfo(final String stringSocketName, final String cookie)
    {
        hostname = null;
        port = 0;
        protoPort = 0;
        this.stringSocketName = stringSocketName;
        robotCookie = cookie;
    }

}
