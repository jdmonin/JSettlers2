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
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

import soc.proto.Message;

/**
 * Test protobuf network sending and receiving a few known {@link Message.FromClient} message types:
 * {@link Message.Version}, an empty {@link Message.FromClient},
 * {@link Message.ImARobot}, {@link Message.JoinGame}.
 * The server then sends the client some important {@link Message.FromServer} messages:
 * {@link Message.Version}, {@link Message.ServerStatusText}, {@link Message.Games}.
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
    public static final String FLD_SRV_STATUS_TEXT = "Welcome to JSettlers Test!";
    public static final String FLD_SRV_GAMENAME_1 = "testGame #1", FLD_SRV_GAMENAME_2 = "testGame #2",
        FLD_SRV_GAME1_OPTS = "VP=t13";
    public static final int FLD_VERS_NUM = 3000,  // hardcode like FLD_VERS_BUILD
        FLD_SRV_STATUS_VALUE = Message.ServerStatusText.StatusValue.OK_DEBUG_MODE_ON_VALUE;

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
     * from a {@link ProtoAnyClient} in its own thread. This test also
     * sends a few important messages to the client.
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
            typ = msg2.getMsgCase().getNumber();
            if (typ != 0)
                sb.append("receiveAndDecode: msg 2: expected type number 0, saw " + typ);

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

            // Send a few messages to the client

            final OutputStream os = s.getOutputStream();

            Message.FromServer smsg1 = Message.FromServer.newBuilder()
                .setVers(Message.Version.newBuilder()
                    .setVersNum(FLD_VERS_NUM).setVersBuild(FLD_VERS_BUILD)).build();
            Message.FromServer smsg2 = Message.FromServer.newBuilder()
                .setStatusText(Message.ServerStatusText.newBuilder()
                    .setText(FLD_SRV_STATUS_TEXT)
                    .setSv(Message.ServerStatusText.StatusValue.OK_DEBUG_MODE_ON)  // set from enum, cli will test value
                    ).build();
            List<Message._GameWithOptions> gaList = new ArrayList<>();
            {
                Message._GameWithOptions ga1 = Message._GameWithOptions.newBuilder()
                    .setGaName(FLD_SRV_GAMENAME_1).setOpts(FLD_SRV_GAME1_OPTS).build();
                Message._GameWithOptions ga2 = Message._GameWithOptions.newBuilder(ga1)
                    .setGaName(FLD_SRV_GAMENAME_2).clearOpts().build();
                gaList.add(ga1);
                gaList.add(ga2);
            }
            Message.FromServer smsg3 = Message.FromServer.newBuilder()
                .setGames(Message.Games.newBuilder()
                    .addAllGame(gaList)).build();

            smsg1.writeDelimitedTo(os);
            smsg2.writeDelimitedTo(os);
            smsg3.writeDelimitedTo(os);
            os.flush();
        }
    }

    /**
     * Client which connects to a {@link ProtoAnyServer} and sends
     * specific protobuf {@code Any} test messages in its own thread.
     * This test also receives and decodes test messages from the server.
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

            // A few messages from the server

            final InputStream is = s.getInputStream();

            Message.FromServer smsg1 = Message.FromServer.parseDelimitedFrom(is),
                smsg2 = Message.FromServer.parseDelimitedFrom(is),
                smsg3 = Message.FromServer.parseDelimitedFrom(is);

            int typ = smsg1.getMsgCase().getNumber();
            if (typ == Message.FromServer.VERS_FIELD_NUMBER)
            {
                Message.Version msg = smsg1.getVers();
                if (FLD_VERS_NUM != msg.getVersNum())
                    sb.append("encodeAndSend: srv msg 1 versNum");
                if (! FLD_VERS_BUILD.equals(msg.getVersBuild()))
                    sb.append("encodeAndSend: srv msg 1 versBuild");
            } else {
                sb.append("encodeAndSend: srv msg 1: expected VERS_FIELD_NUMBER, saw " + typ);
            }

            typ = smsg2.getMsgCase().getNumber();
            if (typ == Message.FromServer.STATUS_TEXT_FIELD_NUMBER)
            {
                Message.ServerStatusText msg = smsg2.getStatusText();
                if (FLD_SRV_STATUS_VALUE != msg.getSvValue())  // server sent from enum, test here as numeric value
                    sb.append("encodeAndSend: srv msg 2 svValue");
                if (! FLD_SRV_STATUS_TEXT.equals(msg.getText()))
                    sb.append("encodeAndSend: srv msg 2 text");
            } else {
                sb.append("encodeAndSend: srv msg 2: expected STATUS_TEXT_FIELD_NUMBER, saw " + typ);
            }

            typ = smsg3.getMsgCase().getNumber();
            if (typ == Message.FromServer.GAMES_FIELD_NUMBER)
            {
                Message.Games msg = smsg3.getGames();
                int n = msg.getGameCount();
                if (2 != n)
                {
                    sb.append("encodeAndSend: srv msg 3: expected 2 games, saw " + n);
                } else {
                    Message._GameWithOptions ga1 = msg.getGame(0);
                    Message._GameWithOptions ga2 = msg.getGame(1);
                    if (! FLD_SRV_GAMENAME_1.equals(ga1.getGaName()))
                        sb.append("encodeAndSend: srv msg 3 game 1 name");
                    if (! FLD_SRV_GAME1_OPTS.equals(ga1.getOpts()))
                        sb.append("encodeAndSend: srv msg 3 game 1 options");
                    if (! FLD_SRV_GAMENAME_2.equals(ga2.getGaName()))
                        sb.append("encodeAndSend: srv msg 3 game 2 name");
                    if (! "".equals(ga2.getOpts()))
                        sb.append("encodeAndSend: srv msg 3 game 2 options not empty");
                }
            } else {
                sb.append("encodeAndSend: srv msg 3: expected GAMES_FIELD_NUMBER, saw " + typ);
            }
        }
    }

}