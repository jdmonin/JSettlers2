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
 * A simple bot protobuf client that connects to a server, can
 * send and receive a few basic messages, but can't play in games.
 * Currently if the bot receives a join game request, that game will hang.
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

    private final Socket s;

    /**
     * This constructor creates a barely-useful proto client.
     * After creation, call {@link #sendThenReceive()} to communicate.
     * @param srvHost  Server hostname (FQDN) or IP string ("127.0.0.1" etc)
     * @param srvPort  TCP port; default protobuf port for JSettlers
     *     is {@link SOCDisplaylessPlayerClient#PORT_DEFAULT_PROTOBUF}
     * @param cookie  Server's required bot cookie (weak shared secret)
     */
    public DummyProtoClient
        (final String srvHost, final int srvPort, final String cookie)
        throws IOException, SecurityException
    {
        this.srvHost = srvHost;
        this.srvPort = srvPort;
        this.cookie = cookie;
        s = new Socket(srvHost, srvPort);
    }

    /**
     * Encode and send opening protobuf messages to a server, then loop to receive any until a break.
     * @param s  Open socket to a server; do not close this socket
     */
    public void sendThenReceive()
        throws Exception
    {
        Message.FromClient msg1 = Message.FromClient.newBuilder()
            .setVers(Message.Version.newBuilder()
                .setVersNum(Version.versionNumber()).setVersStr(Version.version())
                .setVersBuild(Version.buildnum())).build();
        Message.FromClient msg2 = Message.FromClient.newBuilder()
            .setImARobot(Message.ImARobot.newBuilder()
                .setNickname(BOTNAME).setCookie(cookie)
                .setRbClass(getClass().getName())).build();

        final OutputStream os = s.getOutputStream();
        System.out.println("Sending Version and ImARobot");
        msg1.writeDelimitedTo(os);
        msg2.writeDelimitedTo(os);
        os.flush();

        // Loop to read any proto messages from the server, until ^C

        final InputStream is = s.getInputStream();

        System.out.println("Entering message receive loop; ^C to exit");
        do
        {
            Message.FromServer smsg = Message.FromServer.parseDelimitedFrom(is);  // may throw IOException
            treat(smsg);
        } while (true);

    }

    /**
     * Treat the incoming messages.
     * Messages of unknown type are ignored.
     */
    protected void treat(final Message.FromServer msg)
    {
        if (msg == null)
            return;

        final int typ = msg.getMsgCase().getNumber();
        System.out.println("Got message from server: type " + typ);
            // TODO print a timestamp too?

        switch (typ)
        {
        case Message.FromServer.VERS_FIELD_NUMBER:
            {
                Message.Version m = msg.getVers();
                System.out.println
                    ("  Version(" + m.getVersNum() + ", '" + m.getVersStr()
                     + "', '" + m.getVersBuild() + "', '" + m.getCliLocale() + "')");
            }
            break;

        case Message.FromServer.STATUS_TEXT_FIELD_NUMBER:
            {
                Message.ServerStatusText m = msg.getStatusText();
                System.out.println
                    ("  ServerStatusText(" + m.getSvValue() + ", '" + m.getText() + "')");
            }
            break;

        case Message.FromServer.BOT_UPDATE_PARAMS_FIELD_NUMBER:
            {
                Message.BotUpdateParams m = msg.getBotUpdateParams();
                System.out.println
                    ("  BotUpdateParams(strat=" + m.getStrategyType() + ", tf=" + m.getTradeFlag() + ", ...)");
            }
            break;

        case Message.FromServer.BOT_JOIN_REQ_FIELD_NUMBER:
            {
                Message.BotJoinGameRequest m = msg.getBotJoinReq();
                System.out.println
                    ("  RobotJoinGameRequest('" + m.getGame().getGaName() + "', " + m.getSeatNumber() + ")");

                // TODO respond with disconnect, including send LeaveAll: Not complete enough to play in a game
            }
            break;
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
            s.close();
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
            dpc.sendThenReceive();
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
