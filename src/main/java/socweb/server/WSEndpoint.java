/*
 * This file is part of the Java Settlers Server Web App.
 *
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
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
 * The maintainer of this program can be reached at https://github.com/jdmonin/JSettlers2
 */

package socweb.server;

import java.io.IOException;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import soc.server.genericServer.ProtoJSONConnection;


/**
 * Web Socket endpoint for {@link ProtoJSONConnection}.
 * Has one instance per client session (that is, per active WebSocket client).
 */
@ServerEndpoint(value="/apisock")
public class WSEndpoint
    implements ProtoJSONConnection.ClientSession
{
    /** Our client session, from {@link #onOpen(Session, EndpointConfig)} */
    private Session session;

    /** Our connection into SOCServer; null after {@link #onClose(CloseReason)} */
    private ProtoJSONConnection conn;

    @OnOpen
    public void onOpen(final Session s, final EndpointConfig ec)
    {
        System.err.println("L47 WebSocket Connected");
        session = s;
        conn = new ProtoJSONConnection(this, Main.srv);
        // TODO error handling: log and disconnect (with explanation) if Main.srv is null
    }

    @OnMessage
    public void onMessage(final String msg)
    {
        System.err.println("WS Received: " + msg);
        if (conn != null)
            conn.processFromClient(msg);
    }

    /** Remove from SOCServer's set of connections. */
    @OnClose
    public void onClose(final CloseReason cr)
    {
        System.err.println("L72 WebSocket Closed");
        session = null;
        if (conn != null)
        {
            conn.closeInput();
            conn = null;
        }
    }

    @OnError
    public void onError(final Throwable th)
    {
        System.err.println("WebSocket error for " + session);
        if (conn != null)
            conn.hasError(th);
        th.printStackTrace(System.err);
    }

    // for ProtoJSONConnection.ClientSession callbacks

    public boolean isConnected()
    {
        return (session != null) && session.isOpen();
    }

    /**
     * {@inheritDoc}
     *<P>
     * Since this sends synchronously, only 1 thread should call this method
     * or risk {@code IllegalStateException: Blocking message pending 10000 for BLOCKING}
     * as warned about in 2015-08 comment at https://bugs.eclipse.org/bugs/show_bug.cgi?id=474488 (jetty 9.2).
     */
    public void sendJSON(final String objAsJson)
        throws IOException
    {
        session.getBasicRemote().sendText(objAsJson);
    }

    public void closeSession()
        throws IOException
    {
        if (session == null)
            return;

        session.close();
        session = null;
    }

}

