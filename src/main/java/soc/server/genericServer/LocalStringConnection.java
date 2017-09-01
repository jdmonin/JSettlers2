/**
 * Local (StringConnection) network system.
 * This file Copyright (C) 2007-2010,2012-2013,2016-2017 Jeremy D Monin <jeremy@nand.net>.
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
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/
package soc.server.genericServer;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Date;
import java.util.Vector;

import soc.disableDebug.D;

/**
 * Symmetric buffered connection sending strings between two local peers.
 * Uses vectors and thread synchronization, no actual network traffic.
 * When using this class from the server (not client), after the constructor
 * call {@link #setServer(Server)}.
 *<P>
 * This class has a run method, but you must start the thread yourself.
 * Constructors will not create or start a thread.
 *<P>
 * As used within JSettlers, the structure of this class has much in common
 * with {@link LocalStringConnection}, as they both subclass {@link StringConnection}.
 * If you add something to one class, you should probably add it to the other, or to the superclass instead.
 *
 *<PRE>
 *  1.0.0 - 2007-11-18 - initial release, becoming part of jsettlers v1.1.00
 *  1.0.1 - 2008-06-28 - add getConnectTime
 *  1.0.2 - 2008-07-30 - check if s already null in disconnect
 *  1.0.3 - 2008-08-08 - add disconnectSoft, getVersion, setVersion
 *  1.0.4 - 2008-09-04 - add appData
 *  1.0.5 - 2009-05-31 - add isVersionKnown, setVersion(int,bool), setVersionTracking,
 *                       isInputAvailable, callback to processFirstCommand,
 *                       wantsHideTimeoutMessage, setHideTimeoutMessage;
 *                       common constructor code moved to init().
 *  1.0.5.1- 2009-10-26- javadoc warnings fixed
 *  1.0.5.2- 2010-04-05- add toString for debugging
 *  1.2.0 - 2017-06-03 - {@link #setData(String)} now takes a String, not Object.
 *  2.0.0 - 2017-06-16 - StringConnection is now a superclass, not an interface.
 *</PRE>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @version 2.0.0
 */
public class LocalStringConnection
    extends StringConnection implements Runnable
{
    /** Unique end-of-file marker object.  Always compare against this with == not string.equals. */
    protected static String EOF_MARKER = "__EOF_MARKER__" + '\004';

    /** Message contents between the peers on this connection; never contains {@code null} elements. */
    protected Vector<String> in, out;
    protected boolean in_reachedEOF;
    protected boolean out_setEOF;
    /** Active connection, server has called accept, and not disconnected yet */
    protected boolean accepted;
    private LocalStringConnection ourPeer;

    /**
     * Create a new, unused LocalStringConnection.
     *<P>
     * After construction, call {@link #connect(String)} to use this object.
     * When using this class from the server (not client)
     * call {@link #setServer(Server)} before {@code connect(..)}
     * and before starting any thread.
     *<P>
     * This class has a run method, but you must start the thread yourself.
     * Constructors will not create or start a thread.
     */
    public LocalStringConnection()
    {
        in = new Vector<String>();
        out = new Vector<String>();
        init();
    }

    /**
     * Constructor for an existing peer; we'll share two Vectors for in/out queues.
     *<P>
     * When using this class from the server (not client)
     * call {@link #setServer(Server)} before starting any thread.
     *<P>
     * This class has a run method, but you must start the thread yourself.
     * Constructors will not create or start a thread.
     *
     * @param peer The peer to use.
     *
     * @throws EOFException If peer is at EOF already
     * @throws IllegalArgumentException if peer is null, or already
     *   has a peer.
     */
    public LocalStringConnection(LocalStringConnection peer) throws EOFException
    {
        if (peer == null)
            throw new IllegalArgumentException("peer null");
        if (peer.ourPeer != null)
            throw new IllegalArgumentException("peer already has a peer");
        if (peer.isOutEOF() || peer.isInEOF())
            throw new EOFException("peer EOF at constructor");

        in = peer.out;
        out = peer.in;
        peer.ourPeer = this;
        this.ourPeer = peer;
        init();
    }

    /**
     * Constructor common-fields initialization
     */
    private void init()
    {
        in_reachedEOF = false;
        out_setEOF = false;
        accepted = false;
    }

    /**
     * Read the next string sent from the remote end,
     * blocking if necessary to wait.
     *<P>
     * Synchronized on in-buffer.
     *
     * @return Next string in the in-buffer; never {@code null}.
     * @throws EOFException Our input buffer has reached EOF
     * @throws IllegalStateException Server has not yet accepted our connection
     */
    public String readNext() throws EOFException, IllegalStateException
    {
        if (! accepted)
        {
            error = new IllegalStateException("Not accepted by server yet");
            throw (IllegalStateException) error;
        }
        if (in_reachedEOF)
        {
            error = new EOFException();
            throw (EOFException) error;
        }

        Object obj;

        synchronized (in)
        {
            while (in.isEmpty())
            {
                if (in_reachedEOF)
                {
                    error = new EOFException();
                    throw (EOFException) error;
                }

                try
                {
                    in.wait();
                }
                catch (InterruptedException e)
                {
                    // interruption is normal, not exceptional
                }
            }
            obj = in.elementAt(0);
            in.removeElementAt(0);

            if (obj == EOF_MARKER)
            {
                in_reachedEOF = true;
                if (ourServer != null)
                    ourServer.removeConnection(this, false);
                error = new EOFException();
                throw (EOFException) error;
            }
        }
        return (String) obj;
    }

    /**
     * Send data over the connection.  Does not block.
     * Ignored if setEOF() has been called.
     *
     * @param dat Data to send
     *
     * @throws IllegalArgumentException if {@code dat} is {@code null}
     * @throws IllegalStateException if not yet accepted by server
     */
    public void put(String dat)
        throws IllegalArgumentException, IllegalStateException
    {
        if (dat == null)
            throw new IllegalArgumentException("null");

        if (! accepted)
        {
            error = new IllegalStateException("Not accepted by server yet");
            throw (IllegalStateException) error;
        }
        if (out_setEOF)
            return;

        synchronized (out)
        {
            out.addElement(dat);
            out.notifyAll();  // Another thread may have been waiting for input
        }
    }

    /**
     * close the socket, discard pending buffered data, set EOF.
     * Called after conn is removed from server structures.
     */
    public void disconnect()
    {
        if (! accepted)
            return;  // <--- Early return: Already disconnected, or never connected ---

        D.ebugPrintln("DISCONNECTING " + data);
        accepted = false;
        synchronized (out)
        {
            // let the remote-end know we're closing
            out.clear();
            out.addElement(EOF_MARKER);
            out_setEOF = true;
            out.notifyAll();
        }
        disconnectSoft();  // clear "in", set its EOF
    }

    /**
     * Accept no further input, allow output to drain, don't immediately close the socket.
     * Once called, {@link #isConnected()} will return false, even if output is still being
     * sent to the other side.
     */
    public void disconnectSoft()
    {
        if (in_reachedEOF)
            return;  // <--- Early return: Already stopped input and draining output ---

        // Don't check accepted; it'll be false if we're called from
        // disconnect(), and it's OK to do this part twice.

        D.ebugPrintln("DISCONNECTING(SOFT) " + data);
        synchronized (in)
        {
            in.clear();
            in.addElement(EOF_MARKER);
            in_reachedEOF = true;
            in.notifyAll();
        }
    }

    /**
     * Connect to specified stringport. Calling thread waits until accepted.
     *<P>
     * Connection must be unnamed (<tt>{@link #getData()} == null</tt>) at this point.
     *
     * @param serverSocketName  stringport name to connect to
     * @throws ConnectException If stringport name is not found, or is EOF,
     *                          or if its connect/accept queue is full.
     * @throws IllegalStateException If this object is already connected
     */
    public void connect(String serverSocketName) throws ConnectException, IllegalStateException
    {
        if (accepted)
            throw new IllegalStateException("Already accepted by a server");

        LocalStringServerSocket.connectTo(serverSocketName, this);
        connectTime = new Date();

        // ** connectTo will Thread.wait until accepted by server.

        accepted = true;
    }

    /**
     * Remember, the peer's in is our out, and vice versa.
     *
     * @return Returns our peer, or null if not yet connected.
     */
    public LocalStringConnection getPeer()
    {
        return ourPeer;
    }

    /**
     * Is currently accepted by a server
     *
     * @return Are we currently connected, accepted, and ready to send/receive data?
     */
    public boolean isAccepted()
    {
        return accepted;
    }

    /**
     * Intended for server to call: Set our accepted flag.
     * Peer must be non-null to set accepted.
     * If our EOF is set, will not set accepted, but will not throw exception.
     * (This happens if the server socket closes while we're in its accept queue.)
     *
     * @throws IllegalStateException If we can't be, or already are, accepted
     */
    public void setAccepted() throws IllegalStateException
    {
        if (ourPeer == null)
            throw new IllegalStateException("No peer, can't be accepted");
        if (accepted)
            throw new IllegalStateException("Already accepted");
        if (! (out_setEOF || in_reachedEOF))
            accepted = true;
    }

    /**
     * Signal the end of outbound data.
     * Not the same as closing, because we don't terminate the inbound side.
     *
     * Synchronizes on out-buffer.
     */
    public void setEOF()
    {
        synchronized (out)
        {
            // let the remote-end know we're closing
            out.addElement(EOF_MARKER);
            out_setEOF = true;
            out.notifyAll();
        }
    }

    /**
     * Have we received an EOF marker inbound?
     */
    public boolean isInEOF()
    {
        synchronized (in)
        {
            return in_reachedEOF;
        }
    }

    /**
     * Have we closed our outbound side?
     *
     * @see #setEOF()
     */
    public boolean isOutEOF()
    {
        synchronized (out)
        {
            return out_setEOF;
        }
    }

    /**
     * Server-side: Reference to the server handling this connection.
     *
     * @return The generic server (optional) for this connection
     */
    public Server getServer()
    {
        return ourServer;
    }

    /**
     * Server-side: Set the generic server for this connection.
     * This is how the code knows it's on the server (not client) side.
     * If a server is set, its removeConnection method is called if our input reaches EOF,
     * and it's notified if our version changes.
     *<P>
     * Call this before calling run().
     *
     * @param srv The new server, or null
     * @see #setVersionTracking(boolean)
     */
    public void setServer(Server srv)
    {
        ourServer = srv;
    }

    /**
     * Hostname of the remote side of the connection -
     * Always returns localhost; this method required for
     * StringConnection interface.
     */
    public String host()
    {
        return "localhost";
    }

    /**
     * Local version; nothing special to do to start reading messages.
     * Call connect(serverSocketName) instead of this method.
     *
     * @see #connect(String)
     *
     * @return Whether we've connected and been accepted by a StringServerSocket.
     */
    public boolean connect()
    {
        return accepted;
    }

    /** Are we currently connected and active? */
    public boolean isConnected()
    {
        return accepted && ! (out_setEOF || in_reachedEOF);
    }

    /**
     * Is input available now, without blocking?
     * Same idea as {@link java.io.DataInputStream#available()}.
     * @since 1.0.5
     */
    public boolean isInputAvailable()
    {
        return (! in_reachedEOF) && (0 < in.size());
    }

    /**
     * For server-side; continuously read and treat input.
     * You must create and start the thread.
     * We are on server side if ourServer != null.
     *<P>
     * When starting the thread, {@link #getData()} must be null.
     */
    public void run()
    {
        Thread.currentThread().setName("connection-srv-localstring");

        if (ourServer == null)
            return;

        ourServer.addConnection(this);
            // won't throw IllegalArgumentException, because conn is unnamed at this point; getData() is null

        try
        {
            final InboundMessageQueue inQueue = ourServer.inQueue;

            if (! in_reachedEOF)
            {
                String firstMsg = readNext();
                if (! ourServer.processFirstCommand(firstMsg, this)){
                    inQueue.push(firstMsg, this);
                }

            }

            while (! in_reachedEOF)
            {
                inQueue.push(readNext(), this);  // readNext() blocks until next message is available
            }
        }
        catch (IOException e)
        {
            D.ebugPrintln("IOException in LocalStringConnection.run - " + e);

            if (D.ebugOn)
            {
                e.printStackTrace(System.out);
            }

            if (in_reachedEOF)
            {
                return;
            }

            error = e;
            ourServer.removeConnection(this, false);
        }
    }

    /**
     * For debugging, toString includes connection name key ({@link #getData()}) if available.
     * @since 1.0.5.2
     */
    @Override
    public String toString()
    {
        StringBuffer sb = new StringBuffer("LocalStringConnection[");
        if (data != null)
            sb.append(data);
        else
            sb.append(super.hashCode());
        sb.append(']');
        return sb.toString();
    }

}
