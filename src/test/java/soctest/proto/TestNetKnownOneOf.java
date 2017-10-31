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
import soc.util.Version;

/**
 * Test protobuf network sending and receiving a few known {@link Message.FromClient} message types:
 * {@link Message.Version}, an empty {@link Message.FromClient},
 * {@link Message.ImARobot}, {@link Message.JoinGame}.
 *<P>
 * Extends and uses server and client from {@link TestNetImARobot}.
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 3.0.00
 */
public class TestNetKnownOneOf
{
    public static final String FLD_NICKNAME = "testbot", FLD_COOKIE = "abc",
        FLD_RBCLASS = soc.robot.SOCRobotBrain.RBCLASS_BUILTIN,
        FLD_VERS_BUILD = "JX20171031",  // hardcode so soc.util.Version doesn't need version.info in place
        FLD_GAMENAME = "dummy game";
    public static final int FLD_VERS_NUM = 3000;  // hardcode like FLD_VERS_BUILD

    /** Localhost as IP "127.0.0.1" for client connection */
    public static final String LOCALHOST_IP = "127.0.0.1";

    @Test(timeout=10000)
    public void testMsgsClientToServer()
        throws Exception
    {
        final StringBuffer sb = new StringBuffer();
        final ProtoAnyServer ps = new ProtoAnyServer(sb);
        final ProtoAnyClient pc = new ProtoAnyClient(sb, ps.port);
        final Thread st = ps.startServer();
        final Thread ct = pc.startClient();
        st.join();
        ct.join();
        if (sb.length() > 0)
            fail("Problem(s) during TestNetKnownOneOf:\n" + sb.toString());
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.proto.TestNetKnownOneOf");
    }

    /**
     * Server which receives specific protobuf {@code Any} test messages
     * from a {@link ProtoAnyClient} in its own thread.
     *<P>
     * {@inheritDoc}
     */
    public static class ProtoAnyServer extends TestNetImARobot.ProtoServer
    {
        public ProtoAnyServer(StringBuffer sb)
            throws IOException, SecurityException
        {
            super(sb);
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
            Message.FromClient msg1 = Message.FromClient.parseDelimitedFrom(is),
                msg2 = Message.FromClient.parseDelimitedFrom(is),
                msg3 = Message.FromClient.parseDelimitedFrom(is),
                msg4 = Message.FromClient.parseDelimitedFrom(is);

            // Use several available techniques to determine message type

            int typ = msg1.getMsgCase().getNumber();
            if (typ == Message.FromClient.VERS_FIELD_NUMBER)
            {
                Message.Version msg = msg1.getVers();
                if (FLD_VERS_NUM != msg.getVersNum())
                    sb.append("receiveAndDecode: msg 1 versNum");
                if (! FLD_VERS_BUILD.equals(msg.getVersBuild()))
                    sb.append("receiveAndDecode: msg 1 versBuild");
            } else {
                sb.append("receiveAndDecode: msg 1: expected VERS_FIELD_NUMBER, saw " + typ);
            }

            if (! msg2.getMsgCase().equals(Message.FromClient.MsgCase.MSG_NOT_SET))
                sb.append("receiveAndDecode: msg 2: expected MSG_NOT_SET, saw " + msg2.getMsgCase());

            if (msg3.getMsgCase().getClass().equals(Message.ImARobot.class))
            {
                Message.ImARobot msg = msg3.getImARobot();
                if (! FLD_NICKNAME.equals(msg.getNickname()))
                    sb.append("receiveAndDecode: msg 3 nickname");
                if (! FLD_COOKIE.equals(msg.getCookie()))
                    sb.append("receiveAndDecode: msg 3 cookie");
                if (! FLD_RBCLASS.equals(msg.getRbClass()))
                    sb.append("receiveAndDecode: msg 3 rbclass");
            }

            if (msg4.getMsgCase().equals(Message.FromClient.MsgCase.GA_JOIN))
            {
                Message.JoinGame msg = msg4.getGaJoin();
                if (! FLD_GAMENAME.equals(msg.getGaName()))
                    sb.append("receiveAndDecode: msg 4 gameName");
                if (! FLD_NICKNAME.equals(msg.getMemberName()))
                    sb.append("receiveAndDecode: msg 4 memberName");
            }
        }
    }

    /**
     * Client which connects to a {@link ProtoAnyServer} and sends
     * specific protobuf {@code Any} test messages in its own thread.
     *<P>
     * {@inheritDoc}
     */
    public static class ProtoAnyClient extends TestNetImARobot.ProtoClient
    {
        public ProtoAnyClient(final StringBuffer sb, final int srvPort)
            throws IOException, SecurityException
        {
            super(sb, srvPort);
        }

        /**
         * Called from {@link #run()}, encode and send protobuf messages to a server.
         * @param s  Open socket to a server; do not close this socket
         * @throws Exception  Any errors thrown here are printed to {@link #sb} by {@link #run()}.
         */
        public void encodeAndSend(final Socket s)
            throws Exception
        {
            Message.FromClient msg1 = Message.FromClient.newBuilder()
                .setVers(Message.Version.newBuilder()
                    .setVersNum(FLD_VERS_NUM).setVersBuild(FLD_VERS_BUILD)).build();
            Message.FromClient msg2 = Message.FromClient.newBuilder().build();  // deliberately empty
            Message.FromClient msg3 = Message.FromClient.newBuilder()
                .setImARobot(Message.ImARobot.newBuilder()
                    .setNickname(FLD_NICKNAME).setCookie(FLD_COOKIE).setRbClass(FLD_RBCLASS)).build();
            Message.FromClient msg4 = Message.FromClient.newBuilder()
                .setGaJoin(Message.JoinGame.newBuilder()
                    .setGaName(FLD_GAMENAME).setMemberName(FLD_NICKNAME)).build();

            final OutputStream os = s.getOutputStream();
            msg1.writeDelimitedTo(os);
            msg2.writeDelimitedTo(os);
            msg3.writeDelimitedTo(os);
            msg4.writeDelimitedTo(os);
            os.flush();
        }
    }

}