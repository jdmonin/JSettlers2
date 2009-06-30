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

import soc.disableDebug.D;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

import java.net.Socket;

import java.util.Date;
import java.util.Vector;


/** A server connection.
 *  @version 1.0
 *  @author <A HREF="http://www.nada.kth.se/~cristi">Cristian Bogdan</A>
 *  Reads from the net, writes atomically to the net and
 *  holds the connection data
 */
public final class Connection extends Thread implements Runnable, Serializable, Cloneable, StringConnection
{
    static int putters = 0;
    static Object puttersMonitor = new Object();
    protected final static int TIMEOUT_VALUE = 3600000; // approx. 1 hour

    /**
     * the arbitrary key data ("name") associated with this connection.
     * Protected to force callers to use getData() part of StringConnection interface.
     */
    protected Object data;    

    /**
     * the arbitrary app-specific data associated with this connection.
     * Not used or referenced by generic server.
     */
    protected Object appData;

    DataInputStream in = null;
    DataOutputStream out = null;
    Socket s = null;
    Server sv;
    public Thread reader;
    protected String hst;
    protected int remoteVersion;
    protected Exception error = null;
    protected Date connectTime = new Date();
    protected boolean connected = false;
    /** @see #disconnectSoft() */
    protected boolean inputConnected = false;
    public Vector outQueue = new Vector();

    /** initialize the connection data */
    Connection(Socket so, Server sve)
    {
        hst = so.getInetAddress().getHostName();

        sv = sve;
        s = so;
        reader = null;
        data = null;
        remoteVersion = 0;
        
        /* Thread name for debugging */
        if (hst != null)
            setName ("connection-" + hst + "-" + Integer.toString(so.getPort()));
        else
            setName ("connection-(null)-" + Integer.toString(hashCode()));
    }

    /**
     * @return Hostname of the remote end of the connection
     */
    public String host()
    {
        return hst;
    }

    /** start reading from the net; called only by the server.
     * If successful, also sets connectTime to now.
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
            reader = this;
            connectTime = new Date();

            Putter putter = new Putter(this);
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

    /** continuously read from the net */
    public void run()
    {
        sv.addConnection(this);

        try
        {
            while (inputConnected)
            {
                // readUTF max message size is 65535 chars, modified utf-8 format
                sv.treat(in.readUTF(), this);
            }
        }
        catch (IOException e)
        {
            D.ebugPrintln("IOException in Connection.run (" + hst + ") - " + e);

            if (D.ebugOn)
            {
                e.printStackTrace(System.out);
            }

            if (!connected)
            {
                return;  // Don't set error twice
            }

            error = e;
            sv.removeConnection(this);
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
            D.ebugPrintln("Adding " + str + " to outQueue for " + data);
            outQueue.addElement(str);
            outQueue.notify();
        }
    }

    /**
     * Data is added aynchronously (sitting in {@link #outQueue}).
     * This method is called when it's dequeued and sent over
     * the connection to the remote end.
     *
     * @param str Data to send
     *
     * @return True if sent, false if error 
     *         (and sets {@link #error})
     */
    public boolean putForReal(String str)
    {
        boolean rv = putAux(str);

        if (!rv)
        {
            if (!connected)
            {
                return false;
            }
            else
            {
                sv.removeConnection(this);
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
    public final boolean putAux(String str)
    {
        if ((error != null) || !connected)
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
     * so once set, it should not change.  After setting, call
     * {@link Server#nameConnection(StringConnection)}.
     *
     * @param data The new key data, or null
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

    /** close the socket, stop the reader */
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
     * @param version Version number, or 0 if unknown.
     */
    public void setVersion(int version)
    {
        remoteVersion = version;
    }

    class Putter extends Thread
    {
        Connection con;

        //public boolean putting = true;
        public Putter(Connection c)
        {
            con = c;
            D.ebugPrintln("NEW PUTTER CREATED FOR " + data);
            
            /* thread name for debug */
            String cn = c.host();
            if (cn != null)
                setName("putter-" + cn + "-" + Integer.toString(c.s.getPort()));
            else
                setName("putter-(null)-" + Integer.toString(c.hashCode()));
        }

        public void run()
        {
            while (con.connected)
            {
                String c = null;

                D.ebugPrintln("** " + data + " is at the top of the putter loop");

                synchronized (outQueue)
                {
                    if (outQueue.size() > 0)
                    {
                        c = (String) outQueue.elementAt(0);
                        outQueue.removeElementAt(0);
                    }
                }

                if (c != null)
                {
                    boolean rv = con.putForReal(c);

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
