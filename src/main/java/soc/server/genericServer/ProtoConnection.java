/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003 Robert S. Thomas <thomas@infolab.northwestern.edu>
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
import soc.message.SOCMessage;
import soc.proto.Message;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Date;
import java.util.Vector;


/**
 * A protobuf TCP client's connection at a server.
 * Reads from the net, writes atomically to the net and
 * holds the connection data.
 *<P>
 * This Runnable class has a run method, but you must start the thread yourself.
 * Constructors will not create or start a thread.
 *<P>
 * Added in v3.0.00 by copying and subclassing NetConnection, so some methods
 * have code or javadocs from earlier versions.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 3.0.00
 */
@SuppressWarnings("serial")
public final class ProtoConnection
    extends NetConnection  // which implements Runnable, Serializable, Cloneable
{
    private InputStream in = null;
    private DataOutputStream out = null;  // TODO bare OutputStream for Proto

    /** Messages from server to client, sent in {@link Putter} thread. */
    private Vector<String> outQueue = new Vector<String>();  // TODO proto Message.FromServer instead

    /** initialize the connection data */
    ProtoConnection(Socket so, Server sve)
    {
        super(so, sve);
    }

    /**
     * Get our connection thread name for debugging.  Also used by {@link #toString()}.
     * @return "protoconn-" + <em>remotehostname-portnumber</em>, or "protoconn-(null)-" + {@link #hashCode()}
     * @since 2.0.0
     */
    @Override
    public String getName()
    {
        if ((hst != null) && (s != null))
            return "protoconn-" + hst + "-" + Integer.toString(s.getPort());
        else
            return "protoconn-(null)-" + Integer.toString(hashCode());
    }

    /**
     * Set up to read from the net, start a new Putter thread to send to the net; called only by the server.
     * If successful, also sets connectTime to now.
     * Before calling {@code connect()}, be sure to make a new {@link Thread}{@code (this)} and {@code start()} it
     * for the inbound reading thread.
     *<P>
     * Connection must be unnamed (<tt>{@link #getData()} == null</tt>) at this point.
     *
     * @return true if thread start was successful, false if an error occurred.
     */
    public boolean connect()
    {
        if (getData() != null)
        {
            D.ebugPrintln("conn.connect() requires null getData()");
            return false;
        }

        try
        {
            s.setSoTimeout(TIMEOUT_VALUE);
            in = s.getInputStream();
            out = new DataOutputStream(s.getOutputStream());  // TODO raw OutputStream for proto
            connected = true;
            inputConnected = true;
            connectTime = new Date();

            Putter putter = new Putter();
            putter.start();

            //(reader=new Thread(this)).start();
        }
        catch (Exception e)
        {
            D.ebugPrintln("Exception in ProtoConnection.connect (" + hst + ") - " + e);

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
    @Override
    public boolean isInputAvailable()
    {
        try
        {
            return inputConnected && (0 < in.available());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Inbound reading thread: continuously read from the net.
     *<P>
     * When starting the thread, {@link #getData()} must be null;
     * {@link #connect()} mentions and checks that, but {@code connect()} is a different thread.
     */
    public void run()
    {
        Thread.currentThread().setName(getName());  // protoconn-remotehostname-portnumber
        ourServer.addConnection(this);
            // won't throw IllegalArgumentException, because conn is unnamed at this point; getData() is null

        try
        {
            final InboundMessageQueue inQueue = ourServer.inQueue;

            if (inputConnected)
            {
                Message.FromClient firstMsg = Message.FromClient.parseDelimitedFrom(in);
                System.err.println("Received proto type# " + firstMsg.getMsgCase().getNumber());
                    // TODO remove soon or use a debug-traffic flag
                final SOCMessage msgObj = SOCMessage.toMsg(firstMsg);  // convert
                if (! ourServer.processFirstCommand(msgObj, this))
                {
                    if (msgObj != null)
                        inQueue.push(msgObj, this);
                }
            }

            while (inputConnected)
            {
                final Message.FromClient msgProto = Message.FromClient.parseDelimitedFrom(in);
                System.err.println("Received proto type# " + msgProto.getMsgCase().getNumber());
                    // TODO remove or use a debug-traffic flag
                final SOCMessage msgObj = SOCMessage.toMsg(msgProto);
                if (msgObj != null)
                    inQueue.push(msgObj, this);
            }
        }
        catch (Exception e)
        {
            D.ebugPrintln("Exception in ProtoConnection.run (" + hst + ") - " + e);

            if (D.ebugOn)
            {
                e.printStackTrace(System.out);
            }

            if (! connected)
            {
                return;  // Don't set error twice
            }

            error = e;
            ourServer.removeConnection(this, false);
        }
    }

    /**
     * Send this data over the connection.  Adds it to the {@link #outQueue}
     * to be sent by the Putter thread.
     *<P>
     * Because the connection protocol uses {@link DataOutputStream#writeUTF(String)},
     * {@code str} must be no longer than 65535 bytes when encoded into {@code UTF-8}
     * (which is not Java's internal string encoding): See {@link Connection#MAX_MESSAGE_SIZE_UTF8}.
     *<P>
     * <B>Threads:</B> Safe to call from any thread; synchronizes on internal {@code outQueue}.
     *
     * @param str Data to send
     */
    public final void put(String str)  // TODO proto Message.FromServer instead
    {
        synchronized (outQueue)
        {
            // D.ebugPrintln("Adding " + str + " to outQueue for " + data);
            outQueue.addElement(str);
            outQueue.notify();
        }
    }

    /**
     * Send this message over the connection. Adds it to the {@link #outQueue}
     * to be sent by the Putter thread.
     *<P>
     * Because the connection protocol uses {@link DataOutputStream#writeUTF(String)},
     * {@link SOCMessage#toCmd() msg.toCmd()} must be no longer than 65535 bytes when
     * encoded into {@code UTF-8} (which is not Java's internal string encoding):
     * See {@link Connection#MAX_MESSAGE_SIZE_UTF8}.
     *<P>
     * <B>Threads:</B> Safe to call from any thread; synchronizes on internal {@code outQueue}.
     *<P>
     * Before v3.0.00 this method was {@code put(String)}.
     *
     * @param msg  Message to send
     * @since 3.0.00
     */
    public final void put(SOCMessage msg)  // TODO proto Message.FromServer instead
    {
        final String str = msg.toCmd();
        synchronized (outQueue)
        {
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
    private boolean putForReal(final String str)  // TODO proto Message.FromServer instead
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
                ourServer.removeConnection(this, false);
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
    private final boolean putAux(final String str)  // TODO proto Message.FromServer instead
    {
        if ((error != null) || ! connected)
        {
            return false;
        }

        try
        {
            out.writeUTF(str);
        }
        catch (IOException e)
        {
            D.ebugPrintln("IOException in ProtoConnection.putAux (" + hst + ") - " + e);

            if (D.ebugOn)
            {
                e.printStackTrace(System.out);
            }

            error = e;

            return false;
        }
        catch (Exception ex)
        {
            D.ebugPrintln("generic exception in ProtoConnection.putAux");

            if (D.ebugOn)
            {
                ex.printStackTrace(System.out);
            }

            return false;
        }

        return true;
    }

    /** close the socket, stop the reader; called after conn is removed from server structures */
    @Override
    public void disconnect()
    {
        super.disconnect();
        in = null;
        out = null;
    }

    // TODO send proto to client
    /** Connection inner class thread to send {@link NetConnection#outQueue} messages to the net. */
    class Putter extends Thread
    {
        //public boolean putting = true;
        public Putter()
        {
            D.ebugPrintln("NEW PUTTER CREATED FOR " + data);

            /* thread name for debug */
            String cn = host();
            if (cn != null)
                setName("putter-proto-" + cn + "-" + Integer.toString(s.getPort()));
            else
                setName("putter-proto-(null)-" + Integer.toString(hashCode()));
        }

        public void run()
        {
            while (connected)
            {
                String c = null;

                if (D.ebugIsEnabled())
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
                            outQueue.wait(1000);  // timeout to help avoid deadlock
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
