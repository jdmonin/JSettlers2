/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2008 Jeremy D. Monin <jeremy@nand.net>
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.server.genericServer;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.apache.log4j.Logger;


/** a general purpose server.
 *<P>
 *  This is the real stuff. Server subclasses won't have to care about
 *  reading/writing on the net, data consistency among threads, etc.
 *<P>
 *  Newly connecting clients arrive in {@link #run()},
 *  start a thread for the server side of their Connection or LocalStringConnection,
 *  and are integrated into server data via {@link #addConnection(StringConnection)}
 *  called from that thread.
 *<P>
 *  The first message over the connection should be from the server to the client,
 *  in {@link #newConnection1(StringConnection)} or {@link #newConnection2(StringConnection)}.
 *<P>
 *  @version 1.5
 *  @author Original author: <A HREF="http://www.nada.kth.se/~cristi">Cristian Bogdan</A> <br>
 *  Lots of mods by Robert S. Thomas and Jay Budzik <br>
 *  Local (StringConnection) network system by Jeremy D Monin <jeremy@nand.net>
 */
public abstract class Server extends Thread implements Serializable, Cloneable
{
    StringServerSocket ss;
    boolean up = false;
    protected Exception error = null;
    protected int port;  // -1 for local mode (LocalStringServerSocket, etc)
    protected String strSocketName;  // null for network mode

    /**
     * total number of connections made
     */
    protected int numberOfConnections;

    /** the named connections */
    protected Hashtable conns = new Hashtable();
    /** the newly connected, unnamed connections.
     *  Adding/removing/naming connections synchronizes on this Vector. */
    protected Vector unnamedConns = new Vector();
    /** in process of connecting */
    public Vector inQueue = new Vector();

    /** debug logging */
    private transient Logger log = Logger.getLogger(this.getClass().getName());

    /** start listening to the given port */
    public Server(int port)
    {
        this.port = port;
        this.strSocketName = null;
        numberOfConnections = 0;

        try
        {
            ss = new NetStringServerSocket(port, this);
        }
        catch (IOException e)
        {
            System.err.println("Could not listen to port " + port + ": " + e);
            error = e;
        }
        
        setName("server-" + port);  // Thread name for debugging
    }

    /** start listening to the given local string port (practice game) */
    public Server(String stringSocketName)
    {
        if (stringSocketName == null)
            throw new IllegalArgumentException("stringSocketName null");

        this.port = -1;
        this.strSocketName = stringSocketName;
        numberOfConnections = 0;
        ss = new LocalStringServerSocket(stringSocketName);
        setName("server-localstring-" + stringSocketName);  // Thread name for debugging
    }

    /**
     * Given a key data, return the connected client.
     * @param connKey Object key data, as in {@link StringConnection#getData()}; if null, returns null
     * @return The connection with this key, or null if none
     */
    protected StringConnection getConnection(Object connKey)
    {
        if (connKey != null)
            return (StringConnection) conns.get(connKey);
        else
            return null;
    }

    protected Enumeration getConnections()
    {
        return conns.elements();
    }

    protected synchronized int connectionCount()
    {
        return conns.size();
    }

    public synchronized boolean isUp()
    {
        return up;
    }

    /** run method for Server */
    public void run()
    {
        Treater treater = new Treater(this);

        if (error != null)
        {
            return;
        }

        up = true;
        
        treater.start();  // Set "up" before starting treater (race condition)

        while (isUp())
        {
            try
            {
                while (isUp())
                {
                    // we could limit the number of accepted connections here
                    StringConnection con = ss.accept();
                    if (port != -1)
                    {
                        ((Connection) con).start();
                    }
                    else
                    {
                        ((LocalStringConnection) con).setServer(this);
                        new Thread((LocalStringConnection) con).start();
                    }

                    //addConnection(new StringConnection());
                }
            }
            catch (IOException e)
            {
                error = e;
                log.debug("Exception " + e + " during accept");

                //System.out.println("STOPPING SERVER");
                //stopServer();
            }

            try
            {
                ss.close();
                if (strSocketName == null)
                    ss = new NetStringServerSocket(port, this);
                else
                    ss = new LocalStringServerSocket(strSocketName);
            }
            catch (IOException e)
            {
                System.err.println("Could not listen to port " + port + ": " + e);
                up = false;
                error = e;
            }
        }
    }

    /** treat a request from the given connection */
    public void treat(String s, StringConnection c)
    {
        // log.debug("IN got: " + s);
        synchronized (inQueue)
        {
            inQueue.addElement(new Command(s, c));
            inQueue.notify();
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param str DOCUMENT ME!
     * @param con DOCUMENT ME!
     */
    abstract public void processCommand(String str, StringConnection con);

    /** placeholder for doing things when server gets down */
    protected void serverDown() {}

    /**
     * placeholder for doing things when a new connection comes, part 1 -
     * decide whether to accept.
     * If the connection is accepted, it's added to a list ({@link #unnamedConns} or {@link #conns}).
     * Unless you override this method, always returns true.
     *<P>
     * Should send a message to the client in either {@link #newConnection1(StringConnection)}
     * or {@link #newConnection2(StringConnection)}.
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
     */
    protected void newConnection2(StringConnection c) {}

    /** placeholder for doing things when a connection is closed.
     *  called after connection is removed from conns collection,
     *  and after c.disconnect() has been called.
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
        serverDown();

        for (Enumeration e = conns.elements(); e.hasMoreElements();)
        {
            ((StringConnection) e.nextElement()).disconnect();
        }

        conns.clear();
    }

    /**
     * remove a connection from the system; synchronized on list of connections.
     * The callback {@link #leaveConnection(StringConnection)} will be called,
     * after calling {@link StringConnection#disconnect()} on c.
     *
     * @param c Connection to remove; will call its disconnect() method
     *          and remove it from the server state.
     */
    public void removeConnection(StringConnection c)
    {
        Object cKey = c.getData();
        synchronized (unnamedConns)
        {
            if (cKey != null)
            {
                if (null == conns.remove(cKey))
                {
                    // Was not a member
                    return;
                }
            }
            else
            {
                unnamedConns.removeElement(c);
            }
        }

        c.disconnect();
        leaveConnection(c);
        log.debug(c.host() + " left (" + connectionCount() + ")  " + (new Date()).toString() + ((c.getError() != null) ? (": " + c.getError().toString()) : ""));
    }

    /** do cleanup after a remove connection */
    protected void removeConnectionCleanup(StringConnection c) {}

    /**
     * Add a connection to the system.
     * c.connect() is called at the start of this method.
     * Synchronized on unnamedConns, although named conns (getData not null) are
     * added to conns, not unnamedConns.
     *<P>
     * App-specific work should be done by overriding
     * {@link #newConnection1(StringConnection)} and
     * {@link #newConnection2(StringConnection)}.
     *
     * @param c Connecting client; its key data ({@link StringConnection#getData()}) must not be null.
     * @see #nameConnection(StringConnection)
     * @see #removeConnection(StringConnection)
     */
    public void addConnection(StringConnection c)
    {
        Object cKey = c.getData();  // May be null
        boolean connAccepted;

        synchronized (unnamedConns)
        {
            if (c.connect())
            {
                connAccepted = newConnection1(c);
                if (connAccepted)
                {
                    if (cKey != null)
                        conns.put(cKey, c);
                    else
                        unnamedConns.add(c);
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
            log.debug(c.host() + " came (" + connectionCount() + ")  " + (new Date()).toString());
            newConnection2(c);
        } else {
            log.debug(c.host() + " came but rejected (" + connectionCount() + ")  " + (new Date()).toString());
        }
    }

    /**
     * Name a current connection to the system.
     * Can be called once per connection (once named, cannot be changed).
     * Synchronized on unnamedConns.
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
                throw new IllegalArgumentException("was not connected and unnamed");
            }
        }
    }

    /**
     * Broadcast a SOCmessage to all connected clients, named and unnamed.
     *
     * @param m SOCmessage string, generated by {@link soc.message.SOCMessage#toCmd()}
     */
    protected synchronized void broadcast(String m)
    {
        for (Enumeration e = getConnections(); e.hasMoreElements();)
        {
            ((StringConnection) e.nextElement()).put(m);
        }
        for (Enumeration e = unnamedConns.elements(); e.hasMoreElements();)
        {
            ((StringConnection) e.nextElement()).put(m);
        }
    }

    class Command
    {
        public String str;
        public StringConnection con;

        public Command(String s, StringConnection c)
        {
            str = s;
            con = c;
        }
    }

    class Treater extends Thread
    {
        Server svr;

        public Treater(Server s)
        {
            svr = s;
            setName("treater");  // Thread name for debug
        }

        public void run()
        {
            while (svr.isUp())
            {
                //log.debug("treater server is up");
                Command c = null;

                synchronized (inQueue)
                {
                    if (inQueue.size() > 0)
                    {
                        //log.debug("treater getting command");
                        c = (Command) inQueue.elementAt(0);
                        inQueue.removeElementAt(0);
                    }
                }

                try
                {
                    if (c != null)
                    {
                        svr.processCommand(c.str, c.con);
                    }
                }
                catch (Exception e)
                {
                    System.out.println("Exception in treater (processCommand) - " + e);
                }

                yield();

                synchronized (inQueue)
                {
                    if (inQueue.size() == 0)
                    {
                        try
                        {
                            //log.debug("treater waiting");
                            inQueue.wait(1000);
                        }
                        catch (Exception ex)
                        {
                            ;
                        }
                    }
                }
            }

            // log.debug("treater returning; server not up");
        }
    }

    /**
     * Uses ServerSocket to implement StringServerSocket over a network.
     */
    protected class NetStringServerSocket implements StringServerSocket
    {
        private ServerSocket implServSocket;
        private Server server;

        public NetStringServerSocket (int port, Server serv) throws IOException
        {
            implServSocket = new ServerSocket(port);
            server = serv;
        }

        public StringConnection accept() throws SocketException, IOException
        {
            Socket s = implServSocket.accept();
            return new Connection(s, server);  // Good old net, not generic StringConnection
        }

        public void close() throws IOException
        {
            implServSocket.close();
        }
    }

}
