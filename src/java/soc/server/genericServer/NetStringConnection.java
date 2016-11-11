/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2010,2013 Jeremy D Monin <jeremy@nand.net>
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

import soc.disableDebug.D;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.util.Date;
import java.util.Vector;


/** A client's connection at a server.
 *  @version 1.2.0
 *  @author <A HREF="http://www.nada.kth.se/~cristi">Cristian Bogdan</A>
 *  Reads from the net, writes atomically to the net and
 *  holds the connection data
 *<P>
 * This class has a run method, but you must start the thread yourself.
 * Constructors will not create or start a thread.
 *<P>
 * As used within JSettlers, the structure of this class has much in common
 * with {@link LocalStringConnection}, as they both subclass {@link StringConnection}.
 * If you add something to one class, you should probably add it to the other, or to the superclass instead.
 *<P>
 * Refactored in v1.2.0 to extend {@link StringConnection} instead of Thread.
 */
@SuppressWarnings("serial")
public final class NetStringConnection
    extends StringConnection implements Runnable, Serializable, Cloneable
{
    static int putters = 0;
    static Object puttersMonitor = new Object();
    protected final static int TIMEOUT_VALUE = 3600000; // approx. 1 hour

    DataInputStream in = null;
    DataOutputStream out = null;
    Socket s = null;
    /** Hostname of the remote end of the connection, for {@link #host()} */
    protected String hst;

    protected boolean connected = false;
    /** @see #disconnectSoft() */
    protected boolean inputConnected = false;
    private Vector<String> outQueue = new Vector<String>();

    /** initialize the connection data */
    NetStringConnection(Socket so, Server sve, InboundMessageQueue inboundMessageQueue)
    {
        hst = so.getInetAddress().getHostName();
        ourServer = sve;
        s = so;
        this.inboundMessageQueue = inboundMessageQueue;
    }

    /**
     * Get our connection thread name for debugging.  Also used by {@link #toString()}.
     * @return "connection-"remotehostname-portnumber, or "connection-(null)-"hashCode
     * @since 1.2.0
     */
    public String getName()
    {
        if ((hst != null) && (s != null))
            return "connection-" + hst + "-" + Integer.toString(s.getPort());
        else
            return "connection-(null)-" + Integer.toString(hashCode());
    }

    /**
     * @return Hostname of the remote end of the connection
     */
    public String host()
    {
        return hst;
    }

    /** Set up to reading from the net, start a new Putter thread to send to the net; called only by the server.
     * If successful, also sets connectTime to now.
     * Before calling {@code connect()}, be sure to call {@link #run()} to start the inbound reading thread.
     *
     * @return true if thread start was successful, false if an error occurred.
     */
    public boolean connect()
    {
        try
        {
            s.setSoTimeout(TIMEOUT_VALUE);
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
            connected = true;
            inputConnected = true;
            connectTime = new Date();

            Putter putter = new Putter();
            putter.start();

            //(reader=new Thread(this)).start();
        }
        catch (Exception e)
        {
            D.ebugPrintln("IOException in Connection.connect (" + hst + ") - " + e);

            if (D.ebugOn)
            {
                e.printStackTrace(System.out);
            }

            error = e;
            disconnect();

            return false;
        }

        return true;
    }

    /**
     * Is input available now, without blocking?
     * Same idea as {@link java.io.DataInputStream#available()}.
     */
    public boolean isInputAvailable()
    {
        try
        {
            return inputConnected && (0 < in.available());
        } catch (IOException e) {
            return false;
        }
    }

    /** continuously read from the net */
    public void run()
    {
        Thread.currentThread().setName(getName());  // connection-remotehostname-portnumber
        ourServer.addConnection(this);

        try
        {
            if (inputConnected)
            {
                String firstMsg = in.readUTF();
                if (! ourServer.processFirstCommand(firstMsg, this))
                    inboundMessageQueue.push(firstMsg, this);
            }

            while (inputConnected)
            {
                // readUTF max message size is 65535 chars, modified utf-8 format
                inboundMessageQueue.push(in.readUTF(), this);
            }
        }
        catch (IOException e)
        {
            D.ebugPrintln("IOException in Connection.run (" + hst + ") - " + e);

            if (D.ebugOn)
            {
                e.printStackTrace(System.out);
            }

            if (! connected)
            {
                return;  // Don't set error twice
            }

            error = e;
            ourServer.removeConnection(this);
        }
    }

    /**
     * Send this data over the connection.  Adds it to the {@link #outQueue}
     * to be sent by the Putter thread.
     *
     * @param str Data to send
     */
    public final void put(String str)
    {
        synchronized (outQueue)
        {
            // D.ebugPrintln("Adding " + str + " to outQueue for " + data);
            outQueue.addElement(str);
            outQueue.notify();
        }
    }

    /**
     * Data is added asynchronously (sitting in {@link #outQueue}).
     * This method is called when it's dequeued and sent over
     * the connection to the remote end.
     *
     * @param str Data to send
     *
     * @return True if sent, false if error
     *         (and sets {@link #error})
     */
    private boolean putForReal(final String str)
    {
        boolean rv = putAux(str);

        if (! rv)
        {
            if (! connected)
            {
                return false;
            }
            else
            {
                ourServer.removeConnection(this);
            }

            return false;
        }
        else
        {
            return true;
        }
    }

    /** put a message on the net
     * @return true for success, false and disconnects on failure
     *         (and sets {@link #error})
     */
    private final boolean putAux(final String str)
    {
        if ((error != null) || ! connected)
        {
            return false;
        }

        try
        {
            //D.ebugPrintln("trying to put "+str+" to "+data);
            out.writeUTF(str);
        }
        catch (IOException e)
        {
            D.ebugPrintln("IOException in Connection.putAux (" + hst + ") - " + e);

            if (D.ebugOn)
            {
                e.printStackTrace(System.out);
            }

            error = e;

            return false;
        }
        catch (Exception ex)
        {
            D.ebugPrintln("generic exception in connection putaux");

            if (D.ebugOn)
            {
                ex.printStackTrace(System.out);
            }

            return false;
        }

        return true;
    }

    /** close the socket, stop the reader; called after conn is removed from server structures */
    public void disconnect()
    {
        if (! connected)
            return;  // <--- Early return: Already disconnected ---

        D.ebugPrintln("DISCONNECTING " + data);
        connected = false;
        inputConnected = false;

        /*                if(Thread.currentThread()!=reader && reader!=null && reader.isAlive())
           reader.stop();*/
        try
        {
            if (s != null)
                s.close();
        }
        catch (IOException e)
        {
            D.ebugPrintln("IOException in Connection.disconnect (" + hst + ") - " + e);

            if (D.ebugOn)
            {
                e.printStackTrace(System.out);
            }

            error = e;
        }

        s = null;
        in = null;
        out = null;
    }

    /**
     * Accept no further input, allow output to drain, don't immediately close the socket.
     * Once called, {@link #isConnected()} will return false, even if output is still being
     * sent to the other side.
     */
    public void disconnectSoft()
    {
        if (! inputConnected)
            return;

        D.ebugPrintln("DISCONNECTING(SOFT) " + data);
        inputConnected = false;
    }

    /**
     * Are we currently connected and active?
     */
    public boolean isConnected()
    {
        return connected && inputConnected;
    }

    /**
     * toString includes data.toString for debugging, and {@link #getName()}.
     * @since 1.0.5.2
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer("Connection[");
        if (data != null)
            sb.append(data.toString());
        else
            sb.append(super.hashCode());
        sb.append('-');
        sb.append(getName());  // connection-hostname-portnumber
        sb.append(']');
        return sb.toString();
    }

    /** Connection inner class thread to send {@link NetStringConnection#outQueue} messages to the net. */
    class Putter extends Thread
    {
        //public boolean putting = true;
        public Putter()
        {
            D.ebugPrintln("NEW PUTTER CREATED FOR " + data);

            /* thread name for debug */
            String cn = host();
            if (cn != null)
                setName("putter-" + cn + "-" + Integer.toString(s.getPort()));
            else
                setName("putter-(null)-" + Integer.toString(hashCode()));
        }

        public void run()
        {
            while (connected)
            {
                String c = null;

                D.ebugPrintln("** " + data + " is at the top of the putter loop");

                synchronized (outQueue)
                {
                    if (outQueue.size() > 0)
                    {
                        c = outQueue.elementAt(0);
                        outQueue.removeElementAt(0);
                    }
                }

                if (c != null)
                {
                    /* boolean rv = */ putForReal(c);

                    // rv ignored because handled by putForReal
                }

                synchronized (outQueue)
                {
                    if (outQueue.size() == 0)
                    {
                        try
                        {
                            //D.ebugPrintln("** "+data+" is WAITING for outQueue");
                            outQueue.wait(1000);
                        }
                        catch (Exception ex)
                        {
                            D.ebugPrintln("Exception while waiting for outQueue in " + data + ". - " + ex);
                        }
                    }
                }
            }

            D.ebugPrintln("putter not putting connected==false : " + data);
        }
    }
}
