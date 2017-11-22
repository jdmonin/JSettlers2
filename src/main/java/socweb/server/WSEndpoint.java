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


/**
 * Web Socket endpoint.
 */
@ServerEndpoint(value="/apisock")
public class WSEndpoint
{

    @OnOpen
    public void onOpen(final Session s, final EndpointConfig ec)
    {
        // TODO add to some set of unnamed connections, to be named at auth
        System.err.println("L47 WebSocket Connected");
        try
        {
            s.getBasicRemote().sendText("Welcome!");
        } catch (IOException e) {
            System.err.println("- open can't reply: " + e);
        }
    }

    @OnMessage
    public void onMessage(final String msg, final Session s)
    {
        System.err.println("WS Received: " + msg);
        try
        {
            s.getBasicRemote().sendText("Echo: " + msg);
        } catch (IOException e) {
            System.err.println("- onMessage can't reply: " + e);
        }
    }

    @OnClose
    public void onClose(final Session s, final CloseReason cr)
    {
        // TODO remove from set of connections
        System.err.println("L72 WebSocket Closed");
    }

    @OnError
    public void onError(final Session s, final Throwable th)
    {
        System.err.println("WebSocket error for " + s);
        th.printStackTrace(System.err);
    }

}

