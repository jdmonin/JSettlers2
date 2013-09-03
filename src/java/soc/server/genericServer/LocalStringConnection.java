/**
 * Local (StringConnection) network system.  Version 1.0.5.
 * Copyright (C) 2007-2010,2012 Jeremy D Monin <jeremy@nand.net>.
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

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Date;
import java.util.MissingResourceException;
import java.util.Vector;

import soc.disableDebug.D;
import soc.util.SOCStringManager;

/**
 * Symmetric buffered connection sending strings between two local peers.
 * Uses vectors and thread synchronization, no actual network traffic.
 *<P>
 * This class has a run method, but you must start the thread yourself.
 * Constructors will not create or start a thread.
 *<P>
 * As used within JSettlers, the structure of this class has much in common
 * with {@link Connection}, as they both implement the {@link StringConnection}
 * interface.  If you add something to one class (or to StringConnection),
 * you should probably add it to the other.
 *
 *<PRE>
 *  1.0.0 - 2007-11-18 - initial release
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
 *  1.2.0 - 2013-09-01- for I18N, add {@link #setI18NStringManager(SOCStringManager)} and {@link #getLocalized(String)}
 *</PRE>
 *
 * @author Jeremy D. Monin <jeremy@nand.net>
 * @version 1.2.0
 */
public class LocalStringConnection
    implements StringConnection, Runnable
{
    /** Unique end-of-file marker object.  Always compare against this with == not string.equals. */
    protected static String EOF_MARKER = "__EOF_MARKER__" + '\004';

    protected Vector<String> in, out;
    protected boolean in_reachedEOF;
    protected boolean out_setEOF;
    /** Active connection, server has called accept, and not disconnected yet */
    protected boolean accepted;
    private LocalStringConnection ourPeer;

    protected Server ourServer;  // Is set if server-side. Notifies at EOF (calls removeConnection).
    protected Exception error;
    protected Date connectTime;
    protected int  remoteVersion;
    protected boolean remoteVersionKnown;
    protected boolean remoteVersionTrack;
    protected boolean hideTimeoutMessage = false;

    /**
     * the arbitrary key data associated with this connection.
     */
    protected Object data;

    /**
     * the arbitrary app-specific data associated with this connection.
     * Not used or referenced by generic server.
     */
    protected Object appData;

    /**
     * The server-side string manager for app-specific client message formatting.
     * Not used or referenced by the generic server layer.
     * @since 1.2.0
     */
    protected SOCStringManager stringMgr;

    /**
     * Create a new, unused LocalStringConnection.
     *
     * After construction, call connect to use this object.
     *
     * This class has a run method, but you must start the thread yourself.
     * Constructors will not create or start a thread.
     *
     * @see #connect(String)
     */
    public LocalStringConnection()
    {
        in = new Vector<String>();
        out = new Vector<String>();
        init();
    }

    /**
     * Constructor for an existing peer; we'll share two Vectors for in/out queues.
     *
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
        data = null;
        ourServer = null;
        error = null;
        connectTime = new Date();
        appData = null;
        remoteVersion = 0;
        remoteVersionKnown = false;
        remoteVersionTrack = false;
    }

    /**
     * Read the next string sent from the remote end,
     * blocking if necessary to wait.
     *
     * Synchronized on in-buffer.
     * 
     * @return Next string in the in-buffer
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
                    ourServer.removeConnection(this);
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
     * @throws IllegalStateException if not yet accepted by server
     */
    public void put(String dat) throws IllegalStateException
    {
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
     * The optional key data used to name this connection.
     *
     * @return The key data for this connection, or null.
     * @see #getAppData()
     */
    public Object getData()
    {
        return data;
    }

    /**
     * The optional app-specific changeable data for this connection.
     * Not used anywhere in the generic server, only in your app.
     *
     * @return The app-specific data for this connection.
     * @see #getData()
     */
    public Object getAppData()
    {
        return appData;
    }

    /**
     * Set the optional key data for this connection.
     *
     * This is anything your application wants to associate with the connection.
     * The StringConnection system uses this data to name the connection,
     * so it should not change once set.
     *<P>
     * If you call setData after {@link Server#newConnection1(StringConnection)},
     * please call {@link Server#nameConnection(StringConnection)} afterwards
     * to ensure the name is tracked properly at the server.
     *
     * @param dat The new key data, or null
     * @see #setAppData(Object)
     */
    public void setData(Object dat)
    {
        data = dat;
    }

    /**
     * Set the app-specific non-key data for this connection.
     *
     * This is anything your application wants to associate with the connection.
     * The StringConnection system itself does not reference or use this data.
     * You can change it as often as you'd like, or not use it.
     *
     * @param data The new data, or null
     * @see #setData(Object)
     */
    public void setAppData(Object data)
    {
        appData = data;
    }

    // javadoc inherited from StringConnection
    public void setI18NStringManager(SOCStringManager mgr)
    {
        stringMgr = mgr;
    }

    // javadoc inherited from StringConnection
    public String getLocalized(final String key)
        throws MissingResourceException
    {
        SOCStringManager sm = stringMgr;
        if (sm == null)
            sm = SOCStringManager.getFallbackServerManagerForClient();

        return sm.get(key);
    }

    // javadoc inherited from StringConnection
    public String getLocalized(final String key, final Object ... arguments)
        throws MissingResourceException
    {
        SOCStringManager sm = stringMgr;
        if (sm == null)
            sm = SOCStringManager.getFallbackServerManagerForClient();

        return sm.get(key, arguments);
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
     * @return Any error encountered, or null
     */
    public Exception getError()
    {
        return error;
    }

    /**
     * @return Time of connection to server, or of object creation if that time's not available
     */
    public Date getConnectTime()
    {
        return connectTime;
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
     * Give the version number (if known) of the remote end of this connection.
     * The meaning of this number is application-defined.
     * @return Version number, or 0 if unknown.
     */
    public int getVersion()
    {
        return remoteVersion;
    }

    /**
     * Set the version number of the remote end of this connection.
     * The meaning of this number is application-defined.
     *<P>
     * <b>Locking:</b> If we're on server side, and {@link #setVersionTracking(boolean)} is true,
     *  caller should synchronize on {@link Server#unnamedConns}.
     *
     * @param version Version number, or 0 if unknown.
     *                If version is greater than 0, future calls to {@link #isVersionKnown()}
     *                should return true.
     */
    public void setVersion(int version)
    {
        setVersion(version, version > 0);
    }

    /**
     * Set the version number of the remote end of this connection.
     * The meaning of this number is application-defined.
     *<P>
     * <b>Locking:</b> If we're on server side, and {@link #setVersionTracking(boolean)} is true,
     *  caller should synchronize on {@link Server#unnamedConns}.
     *
     * @param version Version number, or 0 if unknown.
     * @param isKnown Should this version be considered confirmed/known by {@link #isVersionKnown()}?
     */
    public void setVersion(int version, boolean isKnown)
    {
        final int prevVers = remoteVersion;
        remoteVersion = version;
        remoteVersionKnown = isKnown;
        if (remoteVersionTrack && (ourServer != null) && (prevVers != version))
        {
            ourServer.clientVersionRem(prevVers);
            ourServer.clientVersionAdd(version);
        }
    }

    /**
     * Is the version known of the remote end of this connection?
     * We may have just assumed it, or taken a default.
     * @return True if we've confirmed the version, false if it's assumed or default.
     * @since 1.0.5
     */
    public boolean isVersionKnown()
    {
        return remoteVersionKnown;
    }

    /**
     * For server-side use, should we notify the server when our version
     * is changed by setVersion calls?
     * @param doTracking true if we should notify server, false otherwise.
     *        If true, please call both setVersion and
     *        {@link Server#clientVersionAdd(int)} before calling setVersionTracking.
     *        If false, please call {@link Server#clientVersionRem(int)} before
     *        calling setVersionTracking.
     * @since 1.0.5
     */
    public void setVersionTracking(boolean doTracking)
    {
        remoteVersionTrack = doTracking;
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
     * If client connection times out at server, should the server not print a message to console?
     * This would be desired, for instance, in automated clients, which would reconnect
     * if they become disconnected.
     * @see #setHideTimeoutMessage(boolean)
     * @since 1.0.5
     */
    public boolean wantsHideTimeoutMessage()
    {
        return hideTimeoutMessage;
    }

    /**
     * If client connection times out at server, should the server not print a message to console?
     * This would be desired, for instance, in automated clients, which would reconnect
     * if they become disconnected.
     * @param wantsHide true to hide, false to print, the log message on idle-disconnect
     * @see #wantsHideTimeoutMessage()
     * @since 1.0.5
     */
    public void setHideTimeoutMessage(boolean wantsHide)
    {
        hideTimeoutMessage = wantsHide;
    }

    /**
     * For server-side; continuously read and treat input.
     * You must create and start the thread.
     * We are on server side if ourServer != null.
     */
    public void run()
    {
        Thread.currentThread().setName("connection-srv-localstring");

        if (ourServer == null)
            return;

        ourServer.addConnection(this);

        try
        {
            if (! in_reachedEOF)
            {
                String firstMsg = readNext();
                if (! ourServer.processFirstCommand(firstMsg, this))
                    ourServer.treat(firstMsg, this);
            }

            while (! in_reachedEOF)
            {
                ourServer.treat(readNext(), this);
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
            ourServer.removeConnection(this);
        }
    }

    /**
     * toString includes data.toString for debugging.
     * @since 1.0.5.2
     */
    @Override
    public String toString()
    {
        StringBuffer sb = new StringBuffer("LocalStringConnection[");
        if (data != null)
            sb.append(data.toString());
        else
            sb.append(super.hashCode());
        sb.append(']');
        return sb.toString();
    }

}
