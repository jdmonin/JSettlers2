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
package soc.robot.protobuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import soc.baseclient.SOCDisplaylessPlayerClient;
import soc.proto.Message;
import soc.util.Version;

/**
 * A sample bot protobuf client that connects to a server, can
 * send and receive a few basic messages, but can't play in games.
 * Currently if the bot receives a join game request, that game will hang.
 *<P>
 * This sample client has rough feature parity with {@code src/main/python/soc/robot/dummy_proto_client.py}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 3.0.00
 */
public class DummyProtoClient
{
    public static final String BOTNAME = "DummyProtoBot";

    /** Server hostname (FQDN) or IP string ("127.0.0.1" etc) */
    public final String srvHost;

    /**
     * TCP port; default protobuf port for JSettlers
     * is {@link SOCDisplaylessPlayerClient#PORT_DEFAULT_PROTOBUF}
     */
    public final int srvPort;

    /** Server's required bot cookie (weak shared secret) */
    public final String cookie;

    private final Socket sock;

    /** Stream for writing delimited messages to {@link #sock} */
    private final OutputStream sockOut;

    /** Are we connected to the server, with no errors sending or decoding messages? */
    protected boolean connected = false;

    /**
     * This constructor creates a barely-useful proto client.
     * After creation, call {@link #authAndRun()} to communicate.
     * @param srvHost  Server hostname (FQDN) or IP string ("127.0.0.1" etc)
     * @param srvPort  TCP port; default protobuf port for JSettlers
     *     is {@link SOCDisplaylessPlayerClient#PORT_DEFAULT_PROTOBUF}
     * @param cookie  Server's required robot cookie (weak shared secret)
     */
    public DummyProtoClient
        (final String srvHost, final int srvPort, final String cookie)
        throws IOException, SecurityException
    {
        this.srvHost = srvHost;
        this.srvPort = srvPort;
        this.cookie = cookie;
        sock = new Socket(srvHost, srvPort);
        sockOut = sock.getOutputStream();
    }

    /**
     * Encode and send opening protobuf messages to the connected server, then loop to receive
     * replies and {@link #treat(soc.proto.Message.FromServer)} until a break.
     */
    public void authAndRun()
        throws Exception
    {
        final Message.FromClient msgVers = Message.FromClient.newBuilder()
            .setVers(Message.Version.newBuilder()
                .setVersNum(Version.versionNumber()).setVersStr(Version.version())
                .setVersBuild(Version.buildnum())).build();
        final Message.FromClient msgImARobot = Message.FromClient.newBuilder()
            .setImARobot(Message.ImARobot.newBuilder()
                .setNickname(BOTNAME).setCookie(cookie)
                .setRbClass(getClass().getName())).build();

        connected = true;
        System.out.println("Sending Version and ImARobot");
        put(msgVers);
        put(msgImARobot);

        // Loop to read any proto messages from the server, until ^C

        final InputStream is = sock.getInputStream();

        System.out.println("Entering message receive loop; ^C to exit");
        Message.FromServer smsg = null;
        while (connected)
        {
            try
            {
                smsg = Message.FromServer.parseDelimitedFrom(is);  // may throw IOException
            } catch (IOException e) {
                System.err.println("Error receiving/parsing message: " + e);
                connected = false;
                smsg = null;
            }

            if (smsg != null)
            {
                try
                {
                    treat(smsg);
                } catch (Exception e) {
                    System.err.println("Exception in treat(" + smsg.getClass().getSimpleName() + "): " + e);
                    e.printStackTrace();
                }
            } else {
                connected = false;
                System.out.println("null message from parseDelimitedFrom, exiting");

            }
        };

    }

    /**
     * Treat an incoming message from the server.
     * Messages of unknown type are ignored.
     */
    protected void treat(final Message.FromServer msg)
    {
        if (msg == null)
            return;

        final int typ = msg.getMsgCase().getNumber();
        // TODO print a timestamp too?

        switch (typ)
        {

        // auth/connect

        case Message.FromServer.VERS_FIELD_NUMBER:
            {
                Message.Version m = msg.getVers();
                System.out.println
                    ("  Version(" + m.getVersNum() + ", '" + m.getVersStr()
                     + "', '" + m.getVersBuild() + "', '" + m.getSrvFeats() + "')");
            }
            break;

        case Message.FromServer.STATUS_TEXT_FIELD_NUMBER:
            {
                Message.ServerStatusText m = msg.getStatusText();
                System.out.println
                    ("  ServerStatusText(" + m.getSvValue() + ", '" + m.getText() + "')");
            }
            break;

        // robots

        case Message.FromServer.BOT_UPDATE_PARAMS_FIELD_NUMBER:
            {
                Message.BotUpdateParams m = msg.getBotUpdateParams();
                System.out.println
                    ("  BotUpdateParams(strat=" + m.getStrategyType() + ", tf=" + m.getTradeFlag() + ", ...)");
            }
            break;

        // games

        case Message.FromServer.BOT_JOIN_REQ_FIELD_NUMBER:
            {
                Message.BotJoinGameRequest m = msg.getBotJoinReq();
                System.out.println
                    ("  BotJoinGameRequest('" + m.getGame().getGaName() + "', " + m.getSeatNumber() + ")");
                System.out.println
                    ("  -- DISCONNECTING, this bot can't join games");
                put(Message.FromClient.newBuilder()
                    .setLeaveAll(Message.LeaveAll.newBuilder()).build());
                connected = false;
            }
            break;

        // anything else

        default:
            System.out.println("  treat(): No handler for server message type " + typ);
        }
    }

    /**
     * Send a message to our connected server.
     * If an {@link IOException} occurs, writes to {@link System#err} and sets {@link #connected} false.
     */
    public void put(Message.FromClient msg)
    {
        try
        {
            msg.writeDelimitedTo(sockOut);
            sockOut.flush();
        } catch (IOException e) {
            System.err.println("Error sending message in put(" + msg.getClass().getSimpleName() + "): " + e);
            connected = false;
        }
    }

    /**
     * Close the socket connection.
     * Ignores any IOException because this is a test client.
     */
    public void close()
    {
        try
        {
            sock.close();
        } catch (IOException e) {}
    }

    /**
     * Start the client, try to connect to a server. Will need hostname, maybe port, and probably a cookie value.
     * Run with no args for help summary.
     */
    public static void main(String args[])
    {
        String srvHost;
        String cookie = "??";
        int srvPort = SOCDisplaylessPlayerClient.PORT_DEFAULT_PROTOBUF;  // 4000

        final int L = args.length;
        if ((L < 1) || (L > 3))
        {
            System.err.println("Arguments: serverhostname [cookie]   or serverhostname port cookie");
            System.err.println("Default port is " + srvPort + ", default cookie is " + cookie + " and probably wrong");
            System.exit(1);   // <--- Early exit: Printed usage ---
        }

        srvHost = args[0];
        if (L == 3)
        {
            srvPort = Integer.parseInt(args[1]);
            cookie = args[2];
        } else if (L == 2) {
            cookie = args[1];
        }

        DummyProtoClient dpc = null;
        try
        {
            dpc = new DummyProtoClient(srvHost, srvPort, cookie);
            dpc.authAndRun();
        } catch (Exception e) {
            System.err.println("Error at client: " + e);
            e.printStackTrace();
            System.exit(1);   // <--- Early exit: Error ---
        } finally {
            if (dpc != null)
                dpc.close();
        }

        System.exit(0);
    }
}
