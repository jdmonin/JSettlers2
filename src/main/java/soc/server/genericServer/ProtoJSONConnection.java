/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
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
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/
package soc.server.genericServer;

import soc.disableDebug.D;
import soc.message.SOCMessage;
import soc.proto.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Date;
import java.util.Vector;

import com.google.protobuf.util.JsonFormat;


/**
 * A protobuf JSON client's connection at a web app server like Jetty or Tomcat,
 * via WebSocket or some other connection through that web app framework.
 * Reads from the net, writes atomically to the net and
 * holds the connection data.
 *<P>
 * This non-Runnable class has no run method to read inbound data:
 * Instead, the web app server handles that using these calls:
 *<UL>
 * <LI> When the connection is ready for use: {@link #connect()}
 * <LI> When a message arrives: {@link #processFromClient(String)}
 * <LI> If an error occurs: {@link #hasError(Throwable)}
 * <LI> When the client closes its connection: {@link #closeInput()}
 *</UL>
 *
 * Added in v3.0.00 based on ProtoConnection and its parent NetConnection.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 3.0.00
 */
@SuppressWarnings("serial")
public final class ProtoJSONConnection
    extends Connection
{
    private final ClientSession cliSession;

    private final String hst = "???";  // TODO remote hostname; also needed for Putter thread

    protected boolean connected = false;

    /** @see #disconnectSoft() */
    protected boolean inputConnected = false;

    /** Messages from server to client, sent in {@link Putter} thread. */
    private Vector<Message.FromServer> outQueue = new Vector<Message.FromServer>();

    /** Queue inbound messages onto here (except first message, if it gets special treatment). */
    final InboundMessageQueue inQueue;

    /** True if any inbound messages on this connection have already been processed. */
    boolean hadFirstInbound;

    /**
     * Initialize the connection data.
     * @throws IllegalArgumentException if {@code sess} or {@code srv} is null
     */
    public ProtoJSONConnection(ClientSession sess, Server srv)
        throws IllegalArgumentException
    {
        super();
        if ((sess == null) || (srv == null))
            throw new IllegalArgumentException("null param");
        cliSession = sess;
        ourServer = srv;
        inQueue = srv.inQueue;
    }

    /**
     * Get our connection thread name for debugging.  Also used by {@link #toString()}.
     * @return "protoconn-" + <em>remotehostname-portnumber</em>, or "protoconn-(null)-" + {@link #hashCode()}
     */
    public String getName()
    {
        // TODO look up host details, from session etc
        /*
        if ((hst != null) && (s != null))
            return "protojsonconn-" + hst + "-" + Integer.toString(s.getPort());
        else
        */
        return "protojsonconn-" + Integer.toString(hashCode());
    }

    /**
     * Currently does not have this info, returns "???".
     * @return Hostname of the remote end of the connection
     */
    public String host()
    {
        return hst;
    }

    /**
     * Set up to read from the net, start a new Putter thread to send to the net; called only by the server.
     * If successful, also sets connectTime to now.
     * Also calls {@link Server#addConnection(Connection) ourServer.addConnection(this)}.
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
            ourServer.addConnection(this);
                // won't throw IllegalArgumentException, because conn is unnamed at this point; getData() is null

            connected = true;
            inputConnected = true;
            connectTime = new Date();

            Putter putter = new Putter();
            putter.start();

            //(reader=new Thread(this)).start();
        }
        catch (Exception e)
        {
            D.ebugPrintln("Exception in ProtoJSONConnection.connect (" + hst + ") - " + e);

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
     * This subclass always returns {@code false} because its input is passed to a server-wide queue.
     */
    @Override
    public boolean isInputAvailable()
    {
        return false;
    }

    /**
     * Callback to process or queue an inbound message from the client.
     * This method has the same structure as other {@link Connection} types' {@code run()} method.
     * @param asJson  Message data, formatted as JSON
     * @see ProtoJSONConnection.ClientSession#sendJSON(String)
     */
    public void processFromClient(final String asJson)
    {
        if (asJson == null)
            return;

        try
        {
            Message.FromClient.Builder b = Message.FromClient.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(asJson, b);  // parse it
            Message.FromClient msgProto = b.build();
            System.err.println("Received JSON proto type# " + msgProto.getMsgCase().getNumber());
                // TODO remove or use debug-traffic flag
            final SOCMessage msg = SOCMessage.toMsg(msgProto);  // convert

            if (! hadFirstInbound)
            {
                hadFirstInbound = true;
                if (ourServer.processFirstCommand(msg, this))
                    return;
            }

            inQueue.push(msg, this);
        }
        catch (Exception e)
        {
            D.ebugPrintln("Exception in ProtoJSONConnection.run (" + hst + ") - " + e);

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
     * Callback to indicate an error was received.
     * Calls {@link #closeInput()}.
     */
    public void hasError(Throwable th)
    {
        if (th == null)
            return;

        error =
            (th instanceof Exception)
            ? ((Exception) th)
            : new Exception("WebSocket error: " + th.toString(), th);
        closeInput();
    }

    /**
     * Callback to indicate the client has disconnected, and no further input will be received (EOF).
     * Will call {@link Server#removeConnection(Connection, boolean) ourServer.removeConnection(this, true).
     */
    public void closeInput()
    {
        if (! inputConnected)
            return;

        inputConnected = false;
        ourServer.removeConnection(this, true);
    }

    /**
     * Send this server message over the connection to the client.
     * Adds it to the {@link #outQueue} to be sent by the Putter thread.
     *<P>
     * <B>Threads:</B> Safe to call from any thread; synchronizes on internal {@code outQueue}.
     *
     * @param msg  Message to send
     */
    public final void put(SOCMessage msg)  // TODO later: pure proto: Message.FromServer instead of SOCMessage
    {
        final Message.FromServer pmsg = (Message.FromServer) msg.makeProto(true);
        if (pmsg == null)
        {
            if (D.ebugIsEnabled())
                D.ebugPrintln("proto: " + data + ": null proto for put(" + msg.getClassNameShort() + ")");
            return;
        }

        if (D.ebugIsEnabled())
            D.ebugPrintln("proto: " + data + ": put(" + msg.getClassNameShort() + ")");
        synchronized (outQueue)
        {
            outQueue.addElement(pmsg);
            outQueue.notify();
        }
    }

    /**
     * Data is added asynchronously (sitting in {@link #outQueue}).
     * This method is called when it's dequeued and sent over
     * the connection to the remote end.
     * If an error occurs, sets {@link #error} and calls removeConnection.
     *
     * @param str Data to send
     *
     */
    private void putForReal(final Message.FromServer pmsg)
    {
        boolean rv = putAux(pmsg);

        if ((! rv) && connected)
            ourServer.removeConnection(this, false);
    }

    /** put a message on the net
     * @return true for success, false and disconnects on failure
     *         (and sets {@link #error})
     */
    private final boolean putAux(final Message.FromServer pmsg)
    {
        if ((error != null) || ! connected)
        {
            return false;
        }

        try
        {
            if (D.ebugIsEnabled())
                D.ebugPrintln("proto: " + data + " sending out: typ = " + pmsg.getMsgCase().getNumber());
            cliSession.sendJSON(JsonFormat.printer().print(pmsg));
        }
        catch (IOException e)
        {
            D.ebugPrintln("IOException in ProtoJSONConnection.putAux (" + hst + ") - " + e);

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
        if (! connected)
            return;  // <--- Early return: Already disconnected ---

        D.ebugPrintln("DISCONNECTING " + data);
        connected = false;
        inputConnected = false;

        try
        {
            cliSession.closeSession();
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
    }

    @Override
    public void disconnectSoft()
    {
        if (! inputConnected)
            return;

        D.ebugPrintln("DISCONNECTING(SOFT) " + data);
        inputConnected = false;
    }

    /** Inner class thread to send {@link ProtoJSONConnection#outQueue} messages to the net. */
    class Putter extends Thread
    {
        public Putter()
        {
            /* thread name for debug */
            //String cn = host();
            //if (cn != null)   // TODO
            //    setName("putter-proto-" + cn + "-" + Integer.toString(s.getPort()));
            //else
                setName("putter-proto-(null)-" + Integer.toString(hashCode()));
        }

        public void run()
        {
            while (connected)
            {
                Message.FromServer pmsg = null;

                synchronized (outQueue)
                {
                    if (outQueue.size() > 0)
                        pmsg = outQueue.remove(0);
                }

                if (pmsg != null)
                    putForReal(pmsg);

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

            D.ebugPrintln("putter not putting; connected==false : " + data);
        }
    }

    /**
     * Are we currently connected and active?
     */
    public boolean isConnected()
    {
        return connected && inputConnected;
    }

    /** Required {@code run()} method; stub, does nothing. Use {@link #processFromClient(String)} instead. */
    @Override
    public void run() {}

    /**
     * Facade for callbacks, to abstract the details of the WebSocket Session with our client.
     */
    public interface ClientSession
    {
        /** True if the socket is connected and open. */
        boolean isConnected();

        /**
         * Send this preformatted JSON object to the client.
         * @see ProtoJSONConnection#processFromClient(String)
         */
        void sendJSON(String objAsJson)
            throws IOException;

        /** Close the websocket. */
        public void closeSession()
            throws IOException;
    }

}
