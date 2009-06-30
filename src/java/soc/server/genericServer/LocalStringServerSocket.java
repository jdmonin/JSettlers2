/**
 * Local (StringConnection) network system.  Version 1.0.4.
 * Copyright (C) 2007-2008 Jeremy D Monin <jeremy@nand.net>.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at jeremy@nand.net
 **/
package soc.server.genericServer;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * 
 * Clients who want to connect, call connectTo and are queued. (Thread.wait is used internally)
 * Server-side calls accept to retrieve them.
 * 
 * @author Jeremy D. Monin <jeremy@nand.net>
 *
 *<PRE>
 *  1.0.0 - 2007-11-18 - initial release
 *  1.0.3 - 2008-08-08 - add change history; no other changes in this file since 1.0.0
 *  1.0.4 - 2008-09-04 - no change in this file
 *</PRE>
 */
public class LocalStringServerSocket implements StringServerSocket
{
    protected static Hashtable allSockets = new Hashtable();

    /**
     * Length of queue for accepting new connections; default 100.
     * Changing it here affects future calls to connectTo() in all
     * instances.
     */
    public static int ACCEPT_QUEUELENGTH = 100; 

    /** Server-peer sides of connected clients; Added by accept method */
    protected Vector allConnected;

    /** Waiting client connections (client-peer sides); Added by connectClient, removed by accept method */
    protected Vector acceptQueue;

    private String socketName;
    boolean out_setEOF;

    private Object sync_out_setEOF;  // For synchronized methods, so we don't sync on "this".

    public LocalStringServerSocket(String name)
    {
        socketName = name;
        allConnected = new Vector();
        acceptQueue = new Vector();
        out_setEOF = false;
        sync_out_setEOF = new Object();
        allSockets.put(name, this);
    }

    /**
     * Find and connect to stringport with this name.
     * Intended to be called by client thread.
     * Will block-wait until the server calls accept().
     * Returns a new client connection after accept.
     *
     * @param name Stringport server name to connect to
     *
     * @throws ConnectException If stringport name is not found, or is EOF,
     *                          or if its connect/accept queue is full.
     * @throws IllegalArgumentException If name is null
     */
    public static LocalStringConnection connectTo(String name)
        throws ConnectException, IllegalArgumentException
    {
        return connectTo (name, new LocalStringConnection());
    }

    /**
     * Find and connect to stringport with this name.
     * Intended to be called by client thread.
     * Will block-wait until the server calls accept().
     *
     * @param name Stringport server name to connect to
     * @param client Existing unused connection object to connect with
     *
     * @throws ConnectException If stringport name is not found, or is EOF,
     *                          or if its connect/accept queue is full.
     * @throws IllegalArgumentException If name is null, client is null,
     *                          or client is already peered/connected.
     *
     * @return client parameter object, connected to a LocalStringServer
     */
    public static LocalStringConnection connectTo(String name, LocalStringConnection client)
        throws ConnectException, IllegalArgumentException
    {
        if (name == null)
            throw new IllegalArgumentException("name null");
        if (client == null)
            throw new IllegalArgumentException("client null");
        if (client.getPeer() != null)
            throw new IllegalArgumentException("client already peered");

        if (! allSockets.containsKey(name))
            throw new ConnectException("LocalStringServerSocket name not found: " + name);

        LocalStringServerSocket ss = (LocalStringServerSocket) allSockets.get(name);       
        if (ss.isOutEOF())
            throw new ConnectException("LocalStringServerSocket name is EOF: " + name);

        LocalStringConnection servSidePeer;
        try
        {
            servSidePeer = ss.queueAcceptClient(client);
        }
        catch (ConnectException ce)
        {
            throw ce;
        }
        catch (Throwable t)
        {
            ConnectException ce = new ConnectException("Error queueing to accept for " + name);
            ce.initCause(t);
            throw ce;
        }

        // Since we called queueAcceptClient, that server-side thread may have woken
        // and accepted the connection from this client-side thread already.
        // So, check if we're accepted, before waiting to be accepted.
        //
        synchronized (servSidePeer)
        {
            // Sync vs. critical section in accept

            if (! servSidePeer.isAccepted())
            {
                try
                {
                    servSidePeer.wait();  // Notified by accept method
                }
                catch (InterruptedException e) {}
            }
        }

        if (client != servSidePeer.getPeer())
            throw new IllegalStateException("Internal error: Peer is wrong");

        if (client.isOutEOF())
            throw new ConnectException("Server at EOF, closed waiting to be accepted");

        return client;
    }

    /**
     * Queue this client to be accepted, and return their new server-peer;
     * if calling this from methods initiated by the client, check if accepted.
     * If not accepted yet, call Thread.wait on the returned new peer object.
     * Once the server has accepted them, it will call Thread.notify on that object.
     * 
     * @param client Client to queue to accept
     * @return peer Server-side peer of this client
     *
     * @throws IllegalStateException If we are at EOF already
     * @throws IllegalArgumentException If client is or was accepted somewhere already
     * @throws ConnectException If accept queue is full (ACCEPT_QUEUELENGTH)
     * @throws EOFException  If client is at EOF already
     *
     * @see #accept()
     * @see #ACCEPT_QUEUELENGTH
     */
    protected LocalStringConnection queueAcceptClient(LocalStringConnection client)
        throws IllegalStateException, IllegalArgumentException, ConnectException, EOFException
    {
        if (isOutEOF())
            throw new IllegalStateException("Internal error, already at EOF");
        if (client.isAccepted())
            throw new IllegalArgumentException("Client is already accepted somewhere");
        if (client.isOutEOF() || client.isInEOF())
            throw new EOFException("client is already at EOF");

        // Create server-side peer of client connect object, add client
        // to the accept queue, then notify any server thread waiting to
        // accept clients.  Accept() callers thread-wait on the newly
        // created peer object to prevent possible contention with other
        // objects; we know this new object won't have any locks on it.

        LocalStringConnection serverPeer = new LocalStringConnection(client);
        synchronized (acceptQueue)
        {
            if (acceptQueue.size() > ACCEPT_QUEUELENGTH)
                throw new ConnectException("Server accept queue is full");
            acceptQueue.add(client);
            acceptQueue.notifyAll();
        }

        return serverPeer;
    }

    /**
     * For server to call.  Blocks waiting for next inbound connection.
     * (Synchronizes on accept queue.)
     * 
     * @return The server-side peer to the inbound client connection
     * @throws SocketException if our setEOF() has been called, thus
     *    new clients won't receive any data from us
     * @throws IOException if a network problem occurs (Which won't happen with this local communication)
     */
    public StringConnection accept() throws SocketException, IOException
    {
        if (out_setEOF)
            throw new SocketException("Server socket already at EOF");

        LocalStringConnection cliPeer;

        synchronized (acceptQueue)
        {
            while (acceptQueue.isEmpty())
            {
                if (out_setEOF)
                    throw new SocketException("Server socket already at EOF");
                else
                {
                    try
                    {
                        acceptQueue.wait();  // Notified by queueAcceptClient 
                    }
                    catch (InterruptedException e) {}
                }
            }
            cliPeer = (LocalStringConnection) acceptQueue.elementAt(0);
            acceptQueue.removeElementAt(0);            
        }

        LocalStringConnection servPeer = cliPeer.getPeer();        
        cliPeer.setAccepted();

        if (out_setEOF)
        {
            servPeer.disconnect();
            cliPeer.disconnect();
        }

        synchronized (servPeer)
        {
            // Sync vs. critical section in connectTo;
            // client has been waiting there for our accept.

            if (! out_setEOF)
                servPeer.setAccepted();
            servPeer.notifyAll();
        }

        if (out_setEOF)
            throw new SocketException("Server socket already at EOF");

        allConnected.addElement(servPeer);

        return servPeer;
    }

    /**
     * @return Server-peer sides of all currently connected clients (LocalStringConnections)
     */
    public Enumeration allClients()
    {
        return allConnected.elements();
    }

    /**
     * Send to all connected clients.
     * 
     * @param msg String to send
     *  
     * @see #allClients()
     */
    public void broadcast(String msg)
    {
        synchronized (allConnected)
        {
            for (int i = allConnected.size() - 1; i >= 0; --i)
            {
                LocalStringConnection c = (LocalStringConnection) allConnected.elementAt(i);
                c.put(msg);
            }
        }
    }

    /** 
     * If our server won't receive any more data from the client, disconnect them.
     * Considered EOF if the client's server-side peer connection inbound EOF is set.
     * Removes from allConnected and set outbound EOF flag on that connection.
     */
    public void disconnectEOFClients()
    {
        LocalStringConnection servPeer;

        synchronized (allConnected)
        {
            for (int i = allConnected.size() - 1; i >= 0; --i)
            {
                servPeer = (LocalStringConnection) allConnected.elementAt(i);
                if (servPeer.isInEOF())
                {
                    allConnected.removeElementAt(i);
                    servPeer.setEOF();
                }
            }
        }        
    }

    /**
     * @return Returns the socketName.
     */
    public String getSocketName()
    {
        return socketName;
    }

    /**
     * Close down server socket, but don't disconnect anyone:
     * Accept no new inbound connections.
     * Send EOF marker in all current outbound connections.
     * Continue to allow data from open inbound connections.
     * 
     * @see #close()
     */
    public void setEOF()
    {
        setEOF(false);
    }

    /**
     * Close down server socket, possibly disconnect everyone;
     * For use by setEOF() and close().
     * Accept no new inbound connections.
     * Send EOF marker in all current outbound connections.
     * 
     * @param forceDisconnect Call disconnect on clients, or just send them an EOF marker?
     * 
     * @see #close()
     * @see LocalStringConnection#disconnect()
     * @see LocalStringConnection#setEOF()
     */
    protected void setEOF(boolean forceDisconnect)
    {
        synchronized (sync_out_setEOF)
        {
            out_setEOF = true;
        }

        Enumeration connected = allConnected.elements();
        while (connected.hasMoreElements())
        {
            if (forceDisconnect)
                ((LocalStringConnection) connected.nextElement()).disconnect();
            else
                ((LocalStringConnection) connected.nextElement()).setEOF();
        }
    }

    /**
     * Have we closed our outbound side?
     *
     * @see #close()
     * @see #setEOF()
     */
    public boolean isOutEOF()
    {
        synchronized (sync_out_setEOF)
        {
            return out_setEOF;
        }
    }

    /**
     * Close down server socket immediately:
     * Do not let inbound data drain.
     * Accept no new inbound connections.
     * Send EOF marker in all current outbound connections.
     * Like java.net.ServerSocket, any thread currently blocked in
     * accept() will throw a SocketException.
     *
     * @see #setEOF()
     */
    public void close() throws IOException
    {
        setEOF(true);

        // Notify any threads waiting for accept.
        // In those threads, our connectTo method will see
        // the EOF and throw SocketException.
        Enumeration waits = acceptQueue.elements();
        while (waits.hasMoreElements())
        {
            LocalStringConnection cliPeer = (LocalStringConnection) waits.nextElement();
            cliPeer.disconnect();
            synchronized (cliPeer)
            {
                cliPeer.notifyAll();
            }
        }
    }

}
