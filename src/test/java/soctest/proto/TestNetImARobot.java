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

package soctest.proto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.Socket;

import org.junit.Test;
import static org.junit.Assert.*;

import soc.proto.Message;

/**
 * Test the most basic of protobuf network sending: 2 known messages.
 * Includes public server and client classes that similar tests can use
 * by overriding {@link ProtoClient#encodeAndSend(Socket)} and/or
 * {@link ProtoServer#receiveAndDecode(Socket)}.
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 3.0.00
 */
public class TestNetImARobot
{
    public static final String FLD_NICKNAME_1 = "testbot1", FLD_NICKNAME_2 = "testbot2",
        FLD_COOKIE = "abc", FLD_RBCLASS = soc.robot.SOCRobotBrain.RBCLASS_BUILTIN;

    /** Localhost as IP "127.0.0.1" for client connection */
    public static final String LOCALHOST_IP = "127.0.0.1";

    @Test(timeout=10000)
    public void testMsgsClientToServer()
        throws Exception
    {
        final StringBuffer sb = new StringBuffer();
        final ProtoServer ps = new ProtoServer(sb);
        final ProtoClient pc = new ProtoClient(sb, ps.port);
        final Thread st = ps.startServer();
        final Thread ct = pc.startClient();
        st.join();
        ct.join();
        if (sb.length() > 0)
            fail("Problem(s) during TestNetImARobot:\n" + sb.toString());
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.proto.TestNetImARobot");
    }

    /**
     * Server which receives protobuf test messages from a {@link ProtoClient} in its own thread.
     * Call constructor, then {@link #startServer()}, then wait for its Thread to complete.
     * Afterwards check {@link #sb} for any errors or failed checks.
     * Tests using this class should specify a timeout with syntax like
     * <tt>&commat;Test(timeout=10000)</tt>.
     */
    public static class ProtoServer implements Runnable
    {
        /** Buffer to hold any errors or failed checks from server thread and maybe other threads */
        public final StringBuffer sb;

        /** Port chosen by this server */
        public final int port;

        private final ServerSocket ss;

        /** Finds a free {@link #port}, but does not start the server thread. */
        public ProtoServer(StringBuffer sb)
            throws IOException, SecurityException
        {
            this.sb = sb;

            ss = new ServerSocket();  // picks an unused ephemeral high port
            ss.bind(null);
            port = ss.getLocalPort();
        }

        /** Creates a server daemon Thread, starts and returns it. */
        public Thread startServer()
        {
            Thread th = new Thread(this);
            th.setDaemon(true);
            th.start();
            return th;
        }

        /** Waits for one inbound connection, processes its messages in {@link #receiveAndDecode(Socket)}. */
        public void run()
        {
            String oper = null;  // current operation for failure prints
            try
            {
                oper = "ss.accept()";
                Socket s = ss.accept();

                oper = "receiveAndDecode";
                receiveAndDecode(s);

                ss.close();
            } catch (Throwable th) {
                sb.append
                    ("ProtoServer: Exception during " + oper + " on port " + port + ":" + th.toString() + "\n");
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                th.printStackTrace(pw);
                pw.flush();
                sb.append(sw.toString());
                sb.append('\n');
            }
        }

        /**
         * Called from {@link #run()}, receives and decodes protobuf messages from a client connection socket.
         * If problems occur, writes to {@link #sb}.
         * @param s  Open socket of a newly accepted client connection; do not close this socket
         * @throws Exception  Any errors thrown here are printed to {@link #sb} by {@link #run()}.
         */
        public void receiveAndDecode(final Socket s)
            throws Exception
        {
            final InputStream is = s.getInputStream();
            Message.ImARobot[] msgs = new Message.ImARobot[2];
            msgs[0] = Message.ImARobot.parseDelimitedFrom(is);
            msgs[1] = Message.ImARobot.parseDelimitedFrom(is);

            for (int i = 0; i <= 1; ++i)
            {
                Message.ImARobot msg = msgs[i];
                if (msg == null)
                {
                    sb.append("receiveAndDecode: msg " + (i+1) + " null");
                    continue;
                }
                if (! FLD_COOKIE.equals(msg.getCookie()))
                    sb.append("receiveAndDecode: msg " + (i+1) + " cookie");
                if (! FLD_RBCLASS.equals(msg.getRbClass()))
                    sb.append("receiveAndDecode: msg " + (i+1) + " rbclass");
            }
            if (! FLD_NICKNAME_1.equals(msgs[0].getNickname()))
                sb.append("receiveAndDecode: msg 1 nickname");
            if (! FLD_NICKNAME_2.equals(msgs[1].getNickname()))
                sb.append("receiveAndDecode: msg 2 nickname");
        }
    }

    /**
     * Client which connects to a {@link ProtoServer} and sends protobuf test messages in its own thread.
     * Call constructor, then {@link #startClient()}, then wait for its Thread to complete.
     * Afterwards check {@link #sb} for any errors or failed checks.
     * Tests using this class should specify a timeout with syntax like
     * <tt>&commat;Test(timeout=10000)</tt>.
     */
    public static class ProtoClient implements Runnable
    {
        /** Buffer to hold any errors or failed checks from client thread and maybe other threads */
        public final StringBuffer sb;

        public final int srvPort;

        private final Socket s;

        public ProtoClient(final StringBuffer sb, final int srvPort)
            throws IOException, SecurityException
        {
            this.sb = sb;
            this.srvPort = srvPort;
            s = new Socket(LOCALHOST_IP, srvPort);
        }

        /** Creates a client daemon Thread, starts and returns it. */
        public Thread startClient()
        {
            Thread th = new Thread(this);
            th.setDaemon(true);
            th.start();
            return th;
        }

        /** Sends messages to the server over {@link #s} in {@link #encodeAndSend(Socket)} and returns. */
        public void run()
        {
            try
            {
                encodeAndSend(s);
                s.close();
            } catch (Throwable th) {
                sb.append
                    ("ProtoClient: Exception during encodeAndSend() on port " + srvPort + ":" + th.toString() + "\n");
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                th.printStackTrace(pw);
                pw.flush();
                sb.append(sw.toString());
                sb.append('\n');
            }
        }

        /**
         * Called from {@link #run()}, encode and send protobuf messages to a server.
         * @param s  Open socket to a server; do not close this socket
         * @throws Exception  Any errors thrown here are printed to {@link #sb} by {@link #run()}.
         */
        public void encodeAndSend(final Socket s)
            throws Exception
        {
            Message.ImARobot msg1 = Message.ImARobot.newBuilder()
                .setNickname(FLD_NICKNAME_1).setCookie(FLD_COOKIE).setRbClass(FLD_RBCLASS).build();
            Message.ImARobot msg2 = Message.ImARobot.newBuilder()
                .setNickname(FLD_NICKNAME_2).setCookie(FLD_COOKIE).setRbClass(FLD_RBCLASS).build();

            final OutputStream os = s.getOutputStream();
            msg1.writeDelimitedTo(os);
            msg2.writeDelimitedTo(os);
            os.flush();
        }
    }

}