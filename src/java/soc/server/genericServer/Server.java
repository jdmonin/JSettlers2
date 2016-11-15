/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2016 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net> - parameterize types, removeConnection bugfix
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

import java.io.IOException;
import java.io.Serializable;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.Vector;

import soc.debug.D; // JM
import soc.message.SOCMessage;  // for javadocs only
import soc.server.SOCServer;


/** a general purpose server.
 *<P>
 *  This is the real stuff. Server subclasses won't have to care about
 *  reading/writing on the net, data consistency among threads, etc.
 *  The Server listens on either a TCP {@link #port}, or for practice mode,
 *  to a {@link LocalStringServerSocket}.
 *<P>
 *  Newly connecting clients arrive in {@link #run()},
 *  start a thread for the server side of their Connection or LocalStringConnection,
 *  and are integrated into server data via {@link #addConnection(StringConnection)}
 *  called from that thread.  If the client's connection is accepted in
 *  {@link #newConnection1(StringConnection)},
 *  the per-client thread enters a while-loop and calls {@link #treat(String, StringConnection)}
 *  to handle messages from the client.  Treat places them in a server-wide {@link #inQueue},
 *  which is processed in a server-wide single thread called the "treater".
 *<P>
 *  Alternately, it could be rejected in <tt>newConnection1</tt> for any reason,
 *  including too many connections versus {@link #getNamedConnectionCount()}.
 *<P>
 *  To handle inbound messages from the clients, the server-wide "treater" thread
 *  of {@link InboundMessageQueue} will call {@link InboundMessageDispatcher#dispatch(String, StringConnection)}
 *  for each message.
 *<P>
 *  The first processed message over the connection will be from the server to the client,
 *  in {@link #newConnection1(StringConnection)} or {@link #newConnection2(StringConnection)}.
 *  You can send out to the client there, but can't yet receive messages from it,
 *  until after newConnection2 returns.
 *  The client should ideally be named and versioned in newConnection1, but this
 *  can also be done later.
 *<P>
 *  Although this generic client/server will track client versions once they are set,
 *  its basic protocol has no standardized way to inform server/client of each other's
 *  version.  You must send this in an app-specific way, during the initial exchange
 *  when a client connects.
 *<P>
 *  @version 1.7
 *  @author Original author: <A HREF="http://www.nada.kth.se/~cristi">Cristian Bogdan</A> <br>
 *  Lots of mods by Robert S. Thomas and Jay Budzik <br>
 *  Local (StringConnection) network system by Jeremy D Monin &lt;jeremy@nand.net&gt; <br>
 *  Version-tracking system, javadocs, and other minor mods by Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
@SuppressWarnings("serial")  // not expecting to persist an instance between versions
public abstract class Server extends Thread implements Serializable, Cloneable
{

    StringServerSocket ss;

    /**
     * Dispatcher for all inbound messages from clients.
     * @since 2.0.00
     */
    protected final InboundMessageDispatcher inboundMsgDispatcher;

    boolean up = false;
    protected Exception error = null;

    /** TCP port number, or -1 for local/practice mode ({@link LocalStringServerSocket}, etc). */
    protected int port;

    /** {@link LocalStringServerSocket} name, or {@code null} for network mode. */
    protected String strSocketName;

    /**
     * Consistency-check the {@link #cliVersionsConnected} set every so often (33 minutes).
     * @since 1.1.06
     */
    public static final int CLI_VERSION_SET_CONSIS_CHECK_MINUTES = 33;

    /**
     * Do this many quick consistency-checks of {@link #cliVersionsConnected}
     * before doing a full check.
     * @since 1.1.06
     */
    public static final int CLI_VERSION_SET_CONSIS_CHECK_QUICK_COUNT = 5;

    /**
     * total number of connections made since startup
     */
    protected int numberOfConnections = 0;

    /**
     * total number of current connections
     * @since 1.1.06
     */
    protected int numberCurrentConnections = 0;

    /** The named connections: {@link StringConnection#getData()} != {@code null}.
     * @see #unnamedConns
     */
    protected Hashtable<Object, StringConnection> conns = new Hashtable<Object, StringConnection>();

    /** the newly connected, unnamed client connections;
     *  Adding/removing/naming/versioning of connections synchronizes on this Vector.
     *  @see #conns
     */
    protected Vector<StringConnection> unnamedConns = new Vector<StringConnection>();


    /**
     * The queue of messages received from all clients. This queue class also includes the Treater thread
     * which calls {@link Server.InboundMessageDispatcher#dispatch(String, StringConnection)} for each
     * inbound message.
     *<P>
     * Before v2.0.00, this was a {@link Vector}.
     */
    public InboundMessageQueue inQueue;

    /**
     * Versions of currently connected clients, according to
     * {@link StringConnection#getVersion()}.
     * Key = Integer(version). Value = ConnVersionCounter.
     * Synchronized on {@link #unnamedConns}, like many other
     * client-related structures.
     * @see #clientVersionAdd(int)
     * @see #clientVersionRem(int)
     * @since 1.1.06
     */
    private TreeMap<Integer, ConnVersionCounter> cliVersionsConnected = new TreeMap<Integer, ConnVersionCounter>();

    /**
     * Minimum and maximum client version currently connected.
     * Meaningless if {@linkplain #numberOfConnections} is 0.
     * @see #cliVersionsConnected
     * @since 1.1.06
     */
    private int cliVersionMin, cliVersionMax;

    /**
     * For {@link #cliVersionsConnected}, the
     * count of "quick" consistency-checks since the last full check.
     * @since 1.1.06
     */
    private int cliVersionsConnectedQuickCheckCount = 0;

    /**
     * Timer for scheduling timed/recurring tasks.
     * @since 1.1.06
     */
    public Timer utilTimer = new Timer(true);  // use daemon thread

    /**
     * Client disconnect error messages, to be printed after a short delay
     * by {@link Server.ConnExcepDelayedPrintTask}.
     * If the client reconnects during the delay, the disconnect and reconnect
     * messages are not printed, so long as your app removes them.
     * This is only used if {@link D#ebugIsEnabled()} is true.
     *<P>
     * <em>Keys:</em> The {@link StringConnection} object is used as the key
     *    within {@link #addConnection(StringConnection)} (for the rejoining message).
     *    The {@link StringConnection#getData() connection keyname} is used as the key
     *    within {@link #removeConnection(StringConnection, boolean)} (for the leaving message);
     *    if this is null, the message is printed immediately and not added to this map.
     *<br>
     * <em>Values:</em> A {@link Server.ConnExcepDelayedPrintTask} which will
     *    print the rejoining or leaving message after a delay.
     *<P>
     * After your app (which extends Server) determines that a connection is the
     * same client that just disconnected, it should find both of the tasks in
     * this HashMap, call {@link TimerTask#cancel()} on them, and remove them.
     *
     * @see #CLI_DISCON_PRINT_TIMER_FIRE_MS
     * @since 1.1.07
     */
    public HashMap<Object, ConnExcepDelayedPrintTask> cliConnDisconPrintsPending = new HashMap<Object, ConnExcepDelayedPrintTask>();

    /**
     * Delay before printing a client disconnect error announcement.
     * Should be at least 300 ms more than {@link #CLI_CONN_PRINT_TIMER_FIRE_MS},
     * so that connects are printed before disconnects, if a connection is
     * lost right away.
     * @see #cliConnDisconPrintsPending
     * @since 1.1.07
     */
    public static int CLI_DISCON_PRINT_TIMER_FIRE_MS = 1300;

    /**
     * Delay before printing a client connect/arrival announcement.
     * @see #cliConnDisconPrintsPending
     * @since 1.1.07
     */
    public static int CLI_CONN_PRINT_TIMER_FIRE_MS = 1000;

    /** start listening to the given port */
    public Server(final int port, final InboundMessageDispatcher imd)
        throws IllegalArgumentException
    {
        if (imd == null)
            throw new IllegalArgumentException("imd null");

        this.port = port;
        this.strSocketName = null;
        this.inboundMsgDispatcher = imd;
        this.inQueue = new InboundMessageQueue(imd);

        try
        {
            ss = new NetStringServerSocket(port, this, inQueue);
        }
        catch (IOException e)
        {
            System.err.println("Could not listen to port " + port + ": " + e);
            error = e;
        }

        setName("server-" + port);  // Thread name for debugging

        initMisc();

        // Most other fields are set by initializers in their declaration.
    }

    /** start listening to the given local string port (practice game) */
    public Server(final String stringSocketName, final InboundMessageDispatcher imd)
        throws IllegalArgumentException
    {
        if (stringSocketName == null)
            throw new IllegalArgumentException("stringSocketName null");
        if (imd == null)
            throw new IllegalArgumentException("imd null");

        this.port = -1;
        this.strSocketName = stringSocketName;
        this.inboundMsgDispatcher = imd;
        this.inQueue = new InboundMessageQueue(imd);

        ss = new LocalStringServerSocket(stringSocketName);
        setName("server-localstring-" + stringSocketName);  // Thread name for debugging

        initMisc();

        // Most other fields are set by initializers in their declaration.
    }

    /**
     * Minor init tasks from both constructors.
     * Set up the recurring schedule of {@link #cliVersionsConnected} here.
     */
    private void initMisc()
    {
        // recurring schedule the version set's consistency-chk
        ConnVersionSetCheckerTask cvChkTask = new ConnVersionSetCheckerTask(this);
        utilTimer.schedule(cvChkTask, 0L, SOCServer.CLI_VERSION_SET_CONSIS_CHECK_MINUTES * 60 * 1000);
    }

    /**
     * Given a connection's key, return the connected client.
     * @param connKey Object key data, as in {@link StringConnection#getData()}; if null, returns null
     * @return The connection with this key, or null if none
     */
    protected StringConnection getConnection(Object connKey)
    {
        if (connKey != null)
            return conns.get(connKey);
        else
            return null;
    }

    /**
     * @return the list of named connections: StringConnections where {@link StringConnection#getData()}
     *         is not null
     */
    protected Enumeration<StringConnection> getConnections()
    {
        return conns.elements();
    }

    /**
     * @return the TCP port number we're listening on, if any,
     *   or null if using local string ports instead.
     * @see #getLocalSocketName()
     * @since 1.1.12
     */
    public int getPort()
    {
        return port;
    }

    /**
     * @return the local socket name we're listening on, or null if using TCP instead.
     * @see #getPort()
     * @since 1.1.12
     */
    public String getLocalSocketName()
    {
        return strSocketName;
    }

    /**
     * Get the current number of named connections to the server.
     * @return the count of named connections: StringConnections where {@link StringConnection#getData()}
     *         is not null
     * @see #getCurrentConnectionCount()
     */
    protected final int getNamedConnectionCount()
    {
        return conns.size();
    }

    /**
     * Get the current number of connections (both named and unnamed) to the server.
     * @return the count of connections, both unnamed and named
     *         ({@link StringConnection#getData()} not null).
     * @since 1.1.13
     * @see #getNamedConnectionCount()
     */
    protected int getCurrentConnectionCount()
    {
        return numberCurrentConnections;
    }

    public synchronized boolean isUp()
    {
        return up;
    }

    /**
     * Run method for Server:
     * Start a single "treater" thread for processing inbound messages,
     * call the {@link #serverUp()} callback, then wait for new connections
     * and set them up in their own threads.
     */
    @Override
    public void run()
    {

        if (error != null)
        {
            return;
        }

        // Set "up" _before_ starting treater (avoid race condition)
        up = true;

        inQueue.startMessageProcessing();

        serverUp();  // Any processing for child class to do after serversocket is bound, before the main loop begins

        while (isUp())
        {
            try
            {
                while (isUp())
                {
                    // we could limit the number of accepted connections here
                    // Currently it's limited in SOCServer.newConnection1 by checking connectionCount()
                    // which is more modular.
                    StringConnection connection = ss.accept();
                    if (port != -1)
                    {
                        new Thread((NetStringConnection) connection).start();
                    }
                    else
                    {
                        LocalStringConnection localConnection = (LocalStringConnection) connection;
                        localConnection.setServer(this);
                        localConnection.setInboundMessageQueue(inQueue);

                        new Thread(localConnection).start();
                    }

                    //addConnection(new StringConnection());
                }
            }
            catch (IOException e)
            {
                error = e;
                D.ebugPrintln("Exception " + e + " during accept");

                //System.out.println("STOPPING SERVER");
                //stopServer();
            }

            try
            {
                ss.close();
                if (strSocketName == null)
                    ss = new NetStringServerSocket(port, this, inQueue);
                else
                    ss = new LocalStringServerSocket(strSocketName);
            }
            catch (IOException e)
            {
                System.err.println("Could not listen to port " + port + ": " + e);
                up = false;
                inQueue.stopMessageProcessing();
                error = e;
            }
        }
    }

    /**
     * Callback to process the client's first message command specially.
     * This default implementation does nothing and returns false;
     * override it in your app if needed.
     *
     * @param str Contents of first message from the client
     * @param con Connection (client) sending this message
     * @return true if processed here, false if this message should be
     *     queued up and processed as normal by
     *     {@link Server.InboundMessageDispatcher#dispatch(String, StringConnection)}.
     */
    public boolean processFirstCommand(String str, StringConnection con)
    {
        return false;
    }

    /**
     * Placeholder (callback) for doing things when server comes up, after the server socket
     * is bound and listening, in the main thread before handling any incoming connections.
     *<P>
     * Once this method completes, server begins its main loop of listening for incoming
     * client connections, and starting a Thread for each one to handle that client's messages.
     *
     * @since 1.1.09
     */
    protected void serverUp() {}

    /** placeholder for doing things when server gets down */
    protected void serverDown() {}

    /**
     * placeholder for doing things when a new connection comes, part 1 -
     * decide whether to accept.
     * Unless you override this method, always returns true.
     * This is called within {@link #addConnection(StringConnection)}.
     *<P>
     * If the connection is accepted, it's added to a list ({@link #unnamedConns}
     * or {@link #conns}), and also added to the version collection.
     *<P>
     * This method is called within a per-client thread.
     * You can send to client, but can't yet receive messages from them.
     *<P>
     * Should send a message to the client in either {@link #newConnection1(StringConnection)}
     * or {@link #newConnection2(StringConnection)}.
     * You may also name the connection here by calling
     * {@link StringConnection#setData(Object) c.setData},
     * which will help add to conns or unnamedConns.
     * This is also where the version should be set.
     *<P>
     * Note that {@link #addConnection(StringConnection)} won't close the channel or
     * take other action to disconnect a rejected client.
     *<P>
     * SYNCHRONIZATION NOTE: During the call to newConnection1, the monitor lock of
     * {@link #unnamedConns} is held.  Thus, defer as much as possible until
     * {@link #newConnection2(StringConnection)} (after the connection is accepted).
     *
     * @param c incoming connection to evaluate and act on
     * @return true to accept and continue, false if you have rejected this connection;
     *         if false, addConnection will call {@link StringConnection#disconnectSoft()}.
     *
     * @see #addConnection(StringConnection)
     * @see #newConnection2(StringConnection)
     * @see #nameConnection(StringConnection)
     */
    protected boolean newConnection1(StringConnection c) { return true; }

    /** placeholder for doing things when a new connection comes, part 2 -
     *  has been accepted and added to a connection list.
     *  Unlike {@link #newConnection1(StringConnection)},
     *  no connection-list locks are held when this method is called.
     *  This is called within {@link #addConnection(StringConnection)}.
     *<P>
     *  This method is called within a per-client thread.
     *  You can send to client, but can't yet receive messages from them.
     */
    protected void newConnection2(StringConnection c) {}

    /** placeholder for doing things when a connection is closed.
     *  Called after connection is removed from conns collection
     *  and version collection, and after c.disconnect() has been called.
     *<P>
     * This method is called within a per-client thread.
     */
    protected void leaveConnection(StringConnection c) {}

    /** The server is being cleanly stopped, disconnect all the connections.
     * Calls {@link #serverDown()} before disconnect; if your child class has more work
     * to do (such as sending a final message to all clients, or
     * disconnecting from a database), override serverDown() or stopServer().
     * Check {@link #isUp()} before calling.
     */
    public synchronized void stopServer()
    {
        up = false;

        inQueue.stopMessageProcessing();

        serverDown();

        for (Enumeration<StringConnection> e = conns.elements(); e.hasMoreElements();)
        {
            e.nextElement().disconnect();
        }

        conns.clear();
    }

    /**
     * Remove a connection from the system, and then optionally call the cleanup callback.
     * The callback {@link #leaveConnection(StringConnection)} will be called,
     * after calling {@link StringConnection#disconnect()} on c.
     * If {@code doCleanup}, the callback {@link #removeConnectionCleanup(StringConnection)}
     * will be called afterwards.
     *<P>
     *<B>Locks:</B> Synchronized on list of connections.
     * This method is called within a per-client thread.
     * The add to {@link #cliConnDisconPrintsPending} is unsynchronized.
     *
     * @param c Connection to remove; will call its disconnect() method and remove it from the server state.
     * @param doCleanup  If true, will also call {@link #removeConnectionCleanup(StringConnection)}.
     */
    public void removeConnection(final StringConnection c, final boolean doCleanup)
    {
        Object cKey = c.getData();  // client player name
        synchronized (unnamedConns)
        {
            if (cKey != null)
            {
                final StringConnection cKeyConn = conns.get(cKey);
                if (null == cKeyConn)
                {
                    // Was not a member
                    return;
                }
                if (c == cKeyConn)
                {
                    conns.remove(cKey);
                }
                // else, was replaced by a
                // different conn for cKey.
                // don't remove the replacement.
            }
            else
            {
                unnamedConns.removeElement(c);
            }

            --numberCurrentConnections;
            clientVersionRem(c.getVersion());  // One less of the cli's version
            c.setVersionTracking(false);
        }

        c.disconnect();
        leaveConnection(c);
        if (D.ebugIsEnabled())
        {
            Exception cerr = c.getError();
            if ((cerr == null) || (! (cerr instanceof SocketTimeoutException)) || ! c.wantsHideTimeoutMessage())
            {
                if (cKey != null)
                {
                    ConnExcepDelayedPrintTask leftMsgTask = new ConnExcepDelayedPrintTask(false, cerr, c);
                    cliConnDisconPrintsPending.put(cKey, leftMsgTask);
                    utilTimer.schedule(leftMsgTask, CLI_DISCON_PRINT_TIMER_FIRE_MS);
                } else {
                    // no connection-key data; we can't identify it later if it reconnects;
                    // just print the announcement right now.
                    D.ebugPrintln(c.host() + " left (" + getNamedConnectionCount() + "," + numberCurrentConnections + ")  " + (new Date()).toString() + ((cerr != null) ? (": " + cerr.toString()) : ""));
                }
            }
        }

        if (doCleanup)
            removeConnectionCleanup(c);
    }

    /** Do cleanup after removing a connection. This is a generic stub that subclass servers can override. */
    protected void removeConnectionCleanup(StringConnection c) {}

    /**
     * Add a connection to the system.
     * Called within a per-client thread.
     * {@link StringConnection#connect()} is called at the start of this method.
     *<P>
     * App-specific work should be done by overriding
     * {@link #newConnection1(StringConnection)} and
     * {@link #newConnection2(StringConnection)}.
     * The connection naming and version is checked here (after newConnection1).
     *<P>
     * <b>Locking:</b> Synchronized on unnamedConns, although
     * named conns (getData not null) are added to conns, not unnamedConns.
     * The add to {@link #cliConnDisconPrintsPending} is unsynchronized.
     *
     * @param c Connecting client; its key data ({@link StringConnection#getData()}) must not be null.
     * @see #nameConnection(StringConnection)
     * @see #removeConnection(StringConnection, boolean)
     */
    public void addConnection(StringConnection c)
    {
        boolean connAccepted;

        synchronized (unnamedConns)
        {
            if (c.connect())
            {
                connAccepted = newConnection1(c);  // <-- App-specific #1 --
                if (connAccepted)
                {
                    Object cKey = c.getData();  // May be null
                    if (cKey != null)
                        conns.put(cKey, c);
                    else
                        unnamedConns.add(c);

                    clientVersionAdd(c.getVersion());  // Count one more client with that version
                    numberCurrentConnections++;
                    c.setVersionTracking(true);
                }
                else
                {
                    c.disconnectSoft();
                }
            } else {
                return;  // <--- early return: c.connect failed ---
            }
        }

        // Now that they're accepted, finish their init/welcome
        if (connAccepted)
        {
            numberOfConnections++;
            if (D.ebugIsEnabled())
            {
                ConnExcepDelayedPrintTask cameMsgTask = new ConnExcepDelayedPrintTask(true, null, c);
                cliConnDisconPrintsPending.put(c, cameMsgTask);
                utilTimer.schedule(cameMsgTask, CLI_CONN_PRINT_TIMER_FIRE_MS);

                // D.ebugPrintln(c.host() + " came (" + connectionCount() + ")  " + (new Date()).toString());
            }
            newConnection2(c);  // <-- App-specific #2 --
        } else {
            D.ebugPrintln(c.host() + " came but rejected (" + getNamedConnectionCount() + "," + numberCurrentConnections + ")  " + (new Date()).toString());
        }
    }

    /**
     * Name a current connection to the system.
     * Call c.setData(name) just before calling this method.
     * Can be called once per connection (once named, cannot be changed).
     * Synchronized on unnamedConns.
     *<P>
     * If you name the connection inside {@link #newConnection1(StringConnection)},
     * you don't need to call nameConnection, because it hasn't yet been added
     * to a connection list.
     *
     * @param c Connected client; its key data ({@link StringConnection#getData()}) must not be null
     * @throws IllegalArgumentException If c isn't already connected, if c.getData() returns null,
     *          or if nameConnection has previously been called for this connection.
     * @see #addConnection(StringConnection)
     */
    public void nameConnection(StringConnection c)
        throws IllegalArgumentException
    {
        Object cKey = c.getData();
        if (cKey == null)
            throw new IllegalArgumentException("null c.getData");

        synchronized (unnamedConns)
        {
            if (unnamedConns.removeElement(c))
            {
                conns.put(cKey, c);
            }
            else
            {
                throw new IllegalArgumentException("was not both connected and unnamed");
            }
        }
    }

    /**
     * Add 1 client, with this version, to {@link #cliVersionsConnected}.
     *<P>
     * <b>Locks:</b> Caller should synchronize on {@link #unnamedConns},
     *   and call just before incrementing {@link #numberCurrentConnections}.
     *
     * @param cvers Client version number, from {@link StringConnection#getVersion()}.
     * @see #clientVersionRem(int)
     * @see #getMinConnectedCliVersion()
     * @see #getMaxConnectedCliVersion()
     * @since 1.1.06
     */
    public void clientVersionAdd(final int cvers)
    {
        Integer cvkey = new Integer(cvers);
        ConnVersionCounter cv = cliVersionsConnected.get(cvkey);
        if (cv == null)
        {
            cv = new ConnVersionCounter(cvers);
            cliVersionsConnected.put(cvkey, cv);  // with cliCount == 1
        } else {
            cv.cliCount++;
            return;  // <---- Early return: We already have this version ----
        }

        if (1 == cliVersionsConnected.size())
        {
            // This is the only connection.
            // Use its version# as the min/max.
            cliVersionMin = cvers;
            cliVersionMax = cvers;
        } else {
            if (cvers < cliVersionMin)
                cliVersionMin = cvers;
            else if (cvers > cliVersionMax)
                cliVersionMax = cvers;
        }
    }

    /**
     * Remove 1 client, with this version, from {@link #cliVersionsConnected}.
     *<P>
     * <b>Locks:</b> Caller should synchronize on {@link #unnamedConns},
     *   right after decrementing numberCurrentConnections (in case a consistency-check
     *   is called from here).
     *
     * @param cvers Client version number, from {@link StringConnection#getVersion()}.
     * @see #clientVersionAdd(int)
     * @see #getMinConnectedCliVersion()
     * @see #getMaxConnectedCliVersion()
     * @since 1.1.06
     */
    public void clientVersionRem(final int cvers)
    {
        Integer cvkey = new Integer(cvers);
        ConnVersionCounter cv = cliVersionsConnected.get(cvkey);
        if (cv == null)
        {
            // not found - must rebuild
            clientVersionRebuildMap(null);
            return;  // <---- Early return: Had to rebuild ----
        }

        cv.cliCount--;
        if (cv.cliCount > 0)
        {
            return;  // <---- Early return: Nothing else to do ----
        }

        // We've removed the last client of a particular version.
        // Update min/max if needed.
        // (If there aren't any clients connected, doesn't matter.)

        cliVersionsConnected.remove(cvkey);

        if (cliVersionsConnected.size() == 0)
        {
            return;  // <---- Early return: No other clients ----
        }

        if (cv.cliCount < 0)
        {
            // must rebuild - got below 0 somehow
            clientVersionRebuildMap(null);
            return;  // <---- Early return: Had to rebuild ----
        }

        if (cvers == cliVersionMin)
        {
            cliVersionMin = cliVersionsConnected.firstKey().intValue();
        }
        else if (cvers == cliVersionMax)
        {
            cliVersionMax = cliVersionsConnected.lastKey().intValue();
        }
    }

    /**
     * @return the version number of the oldest-version client
     *         that is currently connected
     * @since 1.1.06
     * @see #isCliVersionConnected(int)
     */
    public int getMinConnectedCliVersion()
    {
        return cliVersionMin;
    }

    /**
     * @return the version number of the newest-version client
     *         that is currently connected
     * @since 1.1.06
     * @see #isCliVersionConnected(int)
     */
    public int getMaxConnectedCliVersion()
    {
        return cliVersionMax;
    }

    /**
     * Is a client with this version number currently connected?
     * @param cvers Client version number, from {@link StringConnection#getVersion()}.
     * @return  True if a client of this version is currently connected,
     *    according to calls to {@link #clientVersionAdd(int)}
     *    and {@link #clientVersionRem(int)}
     * @since 1.1.13
     * @see #getMinConnectedCliVersion()
     * @see #getMaxConnectedCliVersion()
     */
    public boolean isCliVersionConnected(final int cvers)
    {
        ConnVersionCounter cv = cliVersionsConnected.get(new Integer(cvers));
        return (cv != null) && (cv.cliCount > 0);
    }

    /**
     * Build a fresh TreeMap of the client versions connected,
     * to check consistency of {@link #cliVersionsConnected}.
     *<P>
     * <b>Locks:</b> Caller should synchronize on {@link #unnamedConns}.
     *
     * @see #clientVersionCheckMap(TreeMap, boolean)
     * @see #clientVersionRebuildMap(TreeMap)
     * @since 1.1.06
     */
    private TreeMap<Integer, ConnVersionCounter> clientVersionBuildMap()
    {
        int cvers;
        int lastVers = 0;  // =0 needed to satisfy compiler; first iter will set a value.
        Integer cvkey = null;
        ConnVersionCounter cvc = null;

        TreeMap<Integer, ConnVersionCounter> cvmap = new TreeMap<Integer, ConnVersionCounter>();

        // same enums as broadcast()

        for (Enumeration<StringConnection> e = getConnections(); e.hasMoreElements();)
        {
            cvers = e.nextElement().getVersion();

            if ((cvkey == null) || (cvers != lastVers))
            {
                cvkey = new Integer(cvers);
                cvc = cvmap.get(cvkey);
                if (cvc == null)
                {
                    cvc = new ConnVersionCounter(cvers);
                    cvmap.put(cvkey, cvc);  // with cliCount == 1
                    cvc.cliCount--;  // -- now, since we'll ++ it just below
                }
            }
            cvc.cliCount++;
            lastVers = cvers;
        }

        for (Enumeration<StringConnection> e = unnamedConns.elements(); e.hasMoreElements();)
        {
            cvers = e.nextElement().getVersion();

            if ((cvkey == null) || (cvers != lastVers))
            {
                cvkey = new Integer(cvers);
                cvc = cvmap.get(cvkey);
                if (cvc == null)
                {
                    cvc = new ConnVersionCounter(cvers);
                    cvmap.put(cvkey, cvc);  // with cliCount == 1
                    cvc.cliCount--;  // -- now, since we'll ++ it just below
                }
            }
            cvc.cliCount++;
            lastVers = cvers;
        }

        return cvmap;
    }

    /**
     * Perform a quick or full consistency-check of {@link #cliVersionsConnected}.
     * <b>Quick check:</b>
     *   Check the number of connected clients, versus the number in {@link #cliVersionsConnected}.
     * <br>
     * <b>Full check:</b>
     *   Build a second tree, compare it to the current tree {@link #cliVersionsConnected}.
     *<P>
     * <b>Locks:</b> Caller should synchronize on {@link #unnamedConns}.
     *
     * @param tree2     A tree to check, or null to generate a new one
     *                  here by calling {@link #clientVersionBuildMap()}.
     *                  Not used in the quick check.
     * @param fullCheck True for the full check, false for the quick check.
     *
     * @return True if consistent, false if any problems were found.
     *
     * @see #clientVersionRebuildMap(TreeMap)
     * @since 1.1.06
     */
    private boolean clientVersionCheckMap(TreeMap<Integer, ConnVersionCounter> tree2, final boolean fullCheck)
    {
        if (fullCheck)
        {
            if (tree2 == null)
                tree2 = clientVersionBuildMap();

            if (tree2.size() != cliVersionsConnected.size())
                return false;
        }

        // FULL CHECK:
        // Since they're both ordered, and should be identical,
        // iterate through one, and check the other as we go.
        //
        // QUICK CHECK:
        // Iterate through tree and count the # of clients.

        try
        {
            int cliCount = 0;  // quick only
            Iterator<ConnVersionCounter> cve1 = cliVersionsConnected.values().iterator();  // quick, full
            Iterator<ConnVersionCounter> cve2 = (fullCheck ? tree2.values().iterator() : null);  // full only

            while (cve1.hasNext())
            {
                ConnVersionCounter cvc1 = cve1.next();
                if (fullCheck)
                {
                    ConnVersionCounter cvc2 = cve2.next();
                    if ((cvc1.vers != cvc2.vers) || (cvc1.cliCount != cvc2.cliCount))
                    {
                        return false;
                    }
                } else {
                    cliCount += cvc1.cliCount;
                }
            }

            if (fullCheck)
            {
                if (cve2.hasNext())
                    return false;
            } else {
                return (cliCount == numberCurrentConnections);
            }
        }
        catch (Throwable t)
        {
            return false;  // obj mismatch, iterator failure, other problem
        }

        return true;
    }

    /**
     * Replace the current client-version map with a consistent new one,
     * and update related fields such as minimum/maximum connected version.
     *<P>
     * <b>Locks:</b> Caller should synchronize on {@link #unnamedConns}.
     *
     * @param newTree Newly built version treemap as generated by
     *                {@link #clientVersionBuildMap()}, or null to
     *                generate here.
     * @see #clientVersionCheckMap(TreeMap, boolean)
     * @since 1.1.06
     */
    private void clientVersionRebuildMap(TreeMap<Integer, ConnVersionCounter> newTree)
    {
        if (newTree == null)
            newTree = clientVersionBuildMap();

        cliVersionsConnected = newTree;
        cliVersionsConnectedQuickCheckCount = 0;

        final int treeSize = cliVersionsConnected.size();
        if (treeSize == 0)
            return;  // <---- Early return: Min/max version fields not needed ----

        final int cvers = cliVersionsConnected.firstKey().intValue();
        cliVersionMin = cvers;
        if (1 == treeSize)
        {
            cliVersionMax = cvers;
        } else {
            cliVersionMax = cliVersionsConnected.lastKey().intValue();
        }
    }

    /**
     * Broadcast a SOCmessage to all connected clients, named and unnamed.
     *
     * @param m SOCmessage string, generated by {@link soc.message.SOCMessage#toCmd()}
     * @see #broadcastToVers(String, int, int)
     * @throws IllegalArgumentException if {@code m} is {@code null}
     */
    public synchronized void broadcast(String m)
        throws IllegalArgumentException
    {
        if (m == null)
            throw new IllegalArgumentException("null");

        for (Enumeration<StringConnection> e = getConnections(); e.hasMoreElements();)
        {
            e.nextElement().put(m);
        }
        for (Enumeration<StringConnection> e = unnamedConns.elements(); e.hasMoreElements();)
        {
            e.nextElement().put(m);
        }
    }

    /**
     * Broadcast a SOCmessage to all connected clients (named and
     * unnamed) within a certain version range.
     *<P>
     * The range is inclusive: Clients of version <tt>vmin</tt> and newer,
     * up to and including <tt>vmax</tt>, receive the broadcast.
     * If vmin > vmax, do nothing.
     *
     * @param m SOCmessage string, generated by {@link soc.message.SOCMessage#toCmd()}
     * @param vmin Minimum version, as returned by {@link StringConnection#getVersion()},
     *             or {@link Integer#MIN_VALUE}
     * @param vmax Maximum version, or {@link Integer#MAX_VALUE}
     * @since 1.1.06
     * @see #broadcast(String)
     * @throws IllegalArgumentException if {@code m} is {@code null}
     */
    public synchronized void broadcastToVers(String m, final int vmin, final int vmax)
        throws IllegalArgumentException
    {
        if (m == null)
            throw new IllegalArgumentException("null");

        if (vmin > vmax)
            return;

        for (Enumeration<StringConnection> e = getConnections(); e.hasMoreElements();)
        {
            StringConnection c = e.nextElement();
            int cvers = c.getVersion();
            if ((cvers >= vmin) && (cvers <= vmax))
                c.put(m);
        }
        for (Enumeration<StringConnection> e = unnamedConns.elements(); e.hasMoreElements();)
        {
            StringConnection c = e.nextElement();
            int cvers = c.getVersion();
            if ((cvers >= vmin) && (cvers <= vmax))
                c.put(m);
        }
    }

    /**
     * Nested classes/interfaces begin here
     * --------------------------------------------------------
     */

    /**
     * Server dispatcher for all incoming messages from clients.
     *<P>
     * This interface was created in v2.0.00 refactoring from the {@code Server.processCommand(..)} method.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.0.00
     */
    public interface InboundMessageDispatcher
    {
        /**
         * Remove a queued incoming message from a client, and treat it.
         * Messages of unknown type are ignored.
         * Called from the single 'treater' thread of {@link InboundMessageQueue}.
         *<P>
         * <em>Do not block or sleep</em> because this is single-threaded.
         * {@code dispatch(..)} must catch any exception thrown by conditions or
         * bugs in server or game code it calls.
         *<P>
         * The first message from a client is treated by
         * {@link #processFirstCommand(String, StringConnection)} instead.
         *<P>
         *<B>Security Note:</B> When there is a choice, always use local information
         * over information from the message.  For example, use the nickname from the connection to get the player
         * information rather than the player information from the message.  This makes it harder to send false
         * messages making players do things they didn't want to do.
         *
         * @param str Contents of message from the client. Will never be {@code null}.
         *       SOCServer implementation parses this with {@link SOCMessage#toMsg(String)}.
         * @param con Connection (client) sending this message. Will never be {@code null}.
         * @throws IllegalStateException if not ready to dispatch because some
         *    initialization method needs to be called first;
         *    see dispatcher class javadoc
         */
        abstract public void dispatch(String str, StringConnection con)
            throws IllegalStateException;
    }

    /**
     * Hold info about 1 version of connected clients; for use in {@link #cliVersionsConnected}.
     *
     * @since 1.1.06
     */
    private static class ConnVersionCounter implements Comparable<ConnVersionCounter>
    {
        public final int vers;
        public int cliCount;

        public ConnVersionCounter(final int version)
        {
            vers = version;
            cliCount = 1;
        }

        @Override
        public boolean equals(Object o)
        {
            return (o instanceof ConnVersionCounter)
                && (this.vers == ((ConnVersionCounter) o).vers);
        }

        public int compareTo(ConnVersionCounter cvc)
        {
            return (this.vers - cvc.vers);
        }

    }  // ConnVersionSetMember

    /**
     * Perform the periodic consistency-check of {@link #cliVersionsConnected}.
     * Most checks are very quick (check size vs. # of clients).
     * Scheduled by {@linkplain Server#initMisc()}.
     * Synchronizes on {@link Server#unnamedConns}.
     *
     * @see Server#clientVersionCheckMap(TreeMap, boolean)
     * @see Server#CLI_VERSION_SET_CONSIS_CHECK_QUICK_COUNT
     * @since 1.1.06
     */
    private static class ConnVersionSetCheckerTask extends TimerTask
    {
        private Server srv;

        public ConnVersionSetCheckerTask(Server s)
        {
            srv = s;
        }

        /**
         * Called when timer fires. See class description for action taken.
         * Synchronizes on {@link Server#unnamedConns}.
         */
        @Override
        public void run()
        {
            final boolean wantsFull = (srv.cliVersionsConnectedQuickCheckCount
                    >= CLI_VERSION_SET_CONSIS_CHECK_QUICK_COUNT);

            synchronized (srv.unnamedConns)
            {
                TreeMap<Integer, ConnVersionCounter> tree2 = (wantsFull ? srv.clientVersionBuildMap() : null);

                boolean checkPassed = srv.clientVersionCheckMap(tree2, wantsFull);
                if (! checkPassed)
                {
                    srv.clientVersionRebuildMap(tree2);
                } else
                {
                    if (wantsFull)
                        srv.cliVersionsConnectedQuickCheckCount = 0;
                    else
                        srv.cliVersionsConnectedQuickCheckCount++;
                }
            }
        }

    }  // ConnVersionSetCheckerTask

    /**
     * This object represents one client-connect or disconnect
     * debug-print announcement within {@link Server#cliConnDisconPrintsPending}.
     * When a client is {@link Server#removeConnection(StringConnection, boolean) removed}
     * due to an error, the error message print is delayed briefly, in case the client
     * is doing a disconnect/reconnect (as some robot clients do).
     * This gives the server a chance to suppress the 2 left/rejoined messages if the
     * client quickly reconnects.
     *<P>
     * It's up to the server application (extending this generic Server) to recognize
     * that the arrived client is the same as the departed one, and remove both
     * messages from the pending vector.  This is typically done via the client's username
     * or nickname, as stored in {@link StringConnection#getData()}.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.07
     * @see Server#addConnection(StringConnection).
     * @see Server#CLI_DISCON_PRINT_TIMER_FIRE_MS
     */
    protected class ConnExcepDelayedPrintTask extends TimerTask
    {
        /** may be null */
        public Throwable excep;

        /** Key for {@link #cliConnDisconPrintsPending};
         *  non-null unless isArriveNotDepart; if so,
         *  connection name from {@link StringConnection#getData()}
         */
        public Object connData;

        /** @see StringConnection#host() */
        public String connHost;

        /** Arrival, not a departure */
        public boolean isArriveNotDepart;

        /** Key for {@link #cliConnDisconPrintsPending}; null unless isArriveNotDepart */
        public StringConnection arrivingConn;

        /** Time at which this message was constructed, via {@link System#currentTimeMillis()} */
        public long thrownAt;

        /**
         * Create a new delayed print.  See class javadoc for details.
         * If debug isn't enabled, will not do anything useful.
         *
         * @param isArrival Is this an arriving, not departing, connection?
         *               Store connection data only if true.
         * @param ex  Exception to print after the delay; may be null
         * @param c   Connection being disconnected; may not be null,
         *              and unless isArrival,
         *              {@link StringConnection#getData() c.getData()}
         *              may not be null.
         *
         * @throws IllegalArgumentException if c or c.getData is null
         * @see D#ebugIsEnabled()
         */
        public ConnExcepDelayedPrintTask(boolean isArrival, Throwable ex, StringConnection c)
            throws IllegalArgumentException
        {
            if (! D.ebugIsEnabled())
                return;
            if (c == null)
                throw new IllegalArgumentException("null conn");
            excep = ex;
            thrownAt = System.currentTimeMillis();
            isArriveNotDepart = isArrival;
            connHost = c.host();
            connData = c.getData();
            if (isArrival)
                arrivingConn = c;
            else if (connData == null)
                throw new IllegalArgumentException("null c.getData");
        }

        /**
         * Debug-print connection's arrival or departure,
         * and remove from pending list.
         */
        @Override
        public void run()
        {
            if (isArriveNotDepart)
            {
                D.ebugPrintln(connHost + " came (" + getNamedConnectionCount() + "," + numberCurrentConnections + ")  " + (new Date(thrownAt)).toString());
                cliConnDisconPrintsPending.remove(arrivingConn);
            } else {
                D.ebugPrintln(connHost + " left (" + getNamedConnectionCount() + "," + numberCurrentConnections + ")  " + (new Date(thrownAt)).toString() + ((excep != null) ? (": " + excep.toString()) : ""));
                cliConnDisconPrintsPending.remove(connData);
            }
        }

    }  // ConnExcepPrintDelayedTask

}  // Server
