/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2019-2021 Jeremy D Monin <jeremy@nand.net>
 * Extracted in 2019 from SOCPlayerClient.java, so:
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
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
package soc.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Locale;

import soc.baseclient.SOCDisplaylessPlayerClient;
import soc.baseclient.ServerConnectInfo;
import soc.disableDebug.D;
import soc.game.SOCGame;
import soc.game.SOCGameOptionSet;
import soc.message.SOCChannels;
import soc.message.SOCGames;
import soc.message.SOCJoinGame;
import soc.message.SOCLeaveAll;
import soc.message.SOCMessage;
import soc.message.SOCNewGameWithOptionsRequest;
import soc.message.SOCRejectConnection;
import soc.message.SOCServerPing;
import soc.message.SOCVersion;
import soc.server.SOCServer;
import soc.server.genericServer.Connection;
import soc.server.genericServer.StringConnection;
import soc.server.genericServer.StringServerSocket;
import soc.util.SOCFeatureSet;
import soc.util.Version;

/**
 * Helper object to encapsulate and deal with {@link SOCPlayerClient}'s network connectivity.
 *<P>
 * Local practice server (if any) is started in {@link #startPracticeGame(String, SOCGameOptionSet)}.
 * Local tcp server (if any) is started in {@link #initLocalServer(int)}.
 *<br>
 * Messages from server to client are received in either {@link NetReadTask} or {@link LocalStringReaderTask},
 * which call the client's {@link MessageHandler#handle(SOCMessage, boolean)}.
 * Those Task threads call {@link MessageHandler#init(SOCPlayerClient)} before starting their run loop.
 *<br>
 * Messages from client to server are formed in {@link GameMessageSender} or other classes,
 * which call back here to send to the server via {@link #putNet(String)} or {@link #putPractice(String)}.
 *<br>
 * Network shutdown is {@link #disconnect()} or {@link #dispose()}.
 *<P>
 * Before v2.0.00, most of these fields and methods were part of the main {@link SOCPlayerClient} class.
 *
 * @author Paul Bilnoski &lt;paul@bilnoski.net&gt;
 * @since 2.0.00
 * @see SOCPlayerClient#getNet()
 */
/*package*/ class ClientNetwork
{
    /**
     * Default tcp port number 8880 to listen, and to connect to remote server.
     * Should match SOCServer.SOC_PORT_DEFAULT.
     *<P>
     * 8880 is the default SOCPlayerClient port since jsettlers 1.0.4, per cvs history.
     * @since 1.1.00
     */
    public static final int SOC_PORT_DEFAULT = 8880;

    /**
     * Timeout for initial connection to server; default is 6000 milliseconds.
     */
    public static int CONNECT_TIMEOUT_MS = 6000;

    /**
     * When client is running a local server, interval for how often to send {@link SOCServerPing}
     * keepalive messages: 45 minutes, in milliseconds. See {@link #connect(String, int)}.
     *<P>
     * Should be several minutes shorter than {@link soc.server.genericServer.NetConnection#TIMEOUT_VALUE}.
     * @since 2.5.00
     */
    protected static final int PING_LOCAL_SERVER_INTERVAL_MS = 45 * 60 * 1000;

    /**
     * The client we're communicating for.
     * @see #mainDisplay
     */
    private final SOCPlayerClient client;

    /**
     * MainDisplay for our {@link #client}, to display information and perform callbacks when needed.
     * Set after construction by calling {@link #setMainDisplay(MainDisplay)}.
     */
    private MainDisplay mainDisplay;

    /**
     * Server connection info. {@code null} until {@link #connect(String, int)} is called.
     * Unlike {@code connect} params, localhost is not represented here as {@code null}:
     * see {@link ServerConnectInfo#hostname} javadoc.
     *<P>
     * Versions before 2.2.00 instead had {@code host} and {@code port} fields.
     * @since 2.2.00
     */
    protected ServerConnectInfo serverConnectInfo;

    /**
     * Client-hosted TCP server. If client is running this server, it's also connected
     * as a client, instead of being client of a remote server.
     * Started via {@link SwingMainDisplay#startLocalTCPServer(int)}.
     * {@link #practiceServer} may still be activated at the user's request.
     * Note that {@link SOCGame#isPractice} is false for localTCPServer's games.
     * @since 1.1.00
     */
    SOCServer localTCPServer = null;

    /**
     * Network socket.
     * Before v2.5.00 this field was {@code s}.
     */
    Socket sock;
    DataInputStream in;
    DataOutputStream out;
    Thread reader = null;

    /**
     * Features supported by this built-in JSettlers client.
     * @since 2.0.00
     */
    private final SOCFeatureSet cliFeats;
    {
        cliFeats = new SOCFeatureSet(false, false);
        cliFeats.add(SOCFeatureSet.CLIENT_6_PLAYERS);
        cliFeats.add(SOCFeatureSet.CLIENT_SEA_BOARD);
        cliFeats.add(SOCFeatureSet.CLIENT_SCENARIO_VERSION, Version.versionNumber());
    }

    /**
     * Any network error (TCP communication) received while connecting
     * or sending messages in {@link #putNet(String)}, or null.
     * If {@code ex != null}, putNet will refuse to send.
     *<P>
     * The exception's {@link Throwable#toString() toString()} including its
     * {@link Throwable#getMessage() getMessage()} may be displayed to the user
     * by {@link SOCPlayerClient#shutdownFromNetwork()}; if throwing an error that the user
     * should see, be sure to set the detail message.
     * @see #ex_P
     */
    Exception ex = null;

    /**
     * Practice-server error (stringport pipes), or null.
     *<P>
     * Before v2.0.00 this field was {@code ex_L} (Local server).
     * @see #ex
     * @since 1.1.00
     */
    Exception ex_P = null;

    /**
     * Are we connected to a TCP server (remote or {@link #localTCPServer})?
     * {@link #practiceServer} is not a TCP server.
     * If true, {@link #serverConnectInfo} != null.
     * @see #ex
     */
    boolean connected = false;

    /**
     * For debug, our last message sent over the net.
     *<P>
     * Before v1.1.00 this field was {@code lastMessage}.
     * @see #lastMessage_P
     */
    protected String lastMessage_N;

    /**
     * For debug, our last message sent to practice server (stringport pipes).
     *<P>
     * Before v2.0.00 this field was {@code lastMessage_L} (Local server).
     * @see #lastMessage_N
     * @since 1.1.00
     */
    protected String lastMessage_P;

    /**
     * Server for practice games via {@link #prCli}; not connected to the network,
     * not suited for hosting multi-player games. Use {@link #localTCPServer}
     * for those.
     * SOCMessages of games where {@link SOCGame#isPractice} is true are sent
     * to practiceServer.
     *<P>
     * Null before it's started in {@link SOCPlayerClient#startPracticeGame()}.
     * @since 1.1.00
     */
    protected SOCServer practiceServer = null;

    /**
     * Client connection to {@link #practiceServer practice server}.
     * Null before it's started in {@link #startPracticeGame()}.
     *<P>
     * Last message is in {@link #lastMessage_P}; any error is in {@link #ex_P}.
     * @since 1.1.00
     */
    protected StringConnection prCli = null;

    /**
     * Create our client's ClientNetwork.
     * Before using the ClientNetwork, caller client must construct their GUI
     * and call {@link #setMainDisplay(MainDisplay)}.
     * Then, call {@link #connect(String, int)}.
     */
    public ClientNetwork(SOCPlayerClient c)
    {
        client = c;
        if (client == null)
            throw new IllegalArgumentException("client is null");
    }

    /**
     * Set our MainDisplay; must be done after construction.
     * @param md  MainDisplay to use
     * @throws IllegalArgumentException if {@code md} is {@code null}
     */
    public void setMainDisplay(final MainDisplay md)
        throws IllegalArgumentException
    {
        if (md == null)
            throw new IllegalArgumentException("null");

        mainDisplay = md;
    }

    /** Shut down the local TCP server (if any) and disconnect from the network. */
    public void dispose()
    {
        shutdownLocalServer();
        disconnect();
    }

    /**
     * Start a practice game.  If needed, create and start {@link #practiceServer}.
     * @param practiceGameName  Game name
     * @param gameOpts  Game options, or {@code null}
     * @return True if the practice game request was sent, false if there was a problem
     *         starting the practice server or client
     * @since 1.1.00
     */
    public boolean startPracticeGame(final String practiceGameName, final SOCGameOptionSet gameOpts)
    {
        if (practiceServer == null)
        {
            try
            {
                if (Version.versionNumber() == 0)
                {
                    throw new IllegalStateException("Packaging error: Cannot determine JSettlers version");
                }

                practiceServer = new SOCServer(SOCServer.PRACTICE_STRINGPORT, SOCServer.SOC_MAXCONN_DEFAULT, null, null);
                practiceServer.setPriority(5);  // same as in SOCServer.main
                practiceServer.start();
            }
            catch (Throwable th)
            {
                mainDisplay.showErrorDialog
                    (client.strings.get("pcli.error.startingpractice") + "\n" + th,  // "Problem starting practice server:"
                     client.strings.get("base.cancel"));

                return false;
            }
        }

        if (prCli == null)
        {
            try
            {
                prCli = StringServerSocket.connectTo(SOCServer.PRACTICE_STRINGPORT);
                new LocalStringReaderTask(prCli);  // Reader will start its own thread

                // Send VERSION right away
                sendVersion(true);

                // Practice server supports per-game options
                mainDisplay.enableOptions();
            }
            catch (ConnectException e)
            {
                ex_P = e;

                return false;
            }
        }

        // Ask internal practice server to create the game
        if (gameOpts == null)
            putPractice(SOCJoinGame.toCmd(client.practiceNickname, "", SOCMessage.EMPTYSTR, practiceGameName));
        else
            putPractice(SOCNewGameWithOptionsRequest.toCmd
                (client.practiceNickname, "", SOCMessage.EMPTYSTR, practiceGameName, gameOpts.getAll()));

        return true;
    }

    /**
     * Get the tcp port number of the local server.
     * @see #isRunningLocalServer()
     */
    public int getLocalServerPort()
    {
        if (localTCPServer == null)
            return 0;

        return localTCPServer.getPort();
    }

    /** Shut down the local TCP server. */
    public void shutdownLocalServer()
    {
        if ((localTCPServer != null) && (localTCPServer.isUp()))
        {
            localTCPServer.stopServer();
            localTCPServer = null;
        }
    }

    /**
     * Create and start the local TCP server on a given port.
     * If startup fails, show a {@link NotifyDialog} with the error message.
     * @return True if started, false if not
     */
    public boolean initLocalServer(int tport)
    {
        try
        {
            localTCPServer = new SOCServer(tport, SOCServer.SOC_MAXCONN_DEFAULT, null, null);
            localTCPServer.setPriority(5);  // same as in SOCServer.main
            localTCPServer.start();
        }
        catch (Throwable th)
        {
            mainDisplay.showErrorDialog
                (client.strings.get("pcli.error.startingserv") + "\n" + th,  // "Problem starting server:"
                 client.strings.get("base.cancel"));

            return false;
        }

        return true;
    }

    /**
     * Port number of the tcp server we're a client of,
     * or default {@link #SOC_PORT_DEFAULT} if not {@link #isConnected()}.
     * @see #getHost()
     */
    public int getPort()
    {
        return (connected) ? serverConnectInfo.port : SOC_PORT_DEFAULT;
    }

    /**
     * Hostname of the tcp server we're a client of,
     * from {@link ServerConnectInfo#hostname},
     * or {@code null} if not {@link #isConnected()}.
     * @see #getPort()
     */
    public String getHost()
    {
        return (connected) ? serverConnectInfo.hostname : null;
    }

    /**
     * Are we connected to a tcp server?
     * @see #getHost()
     */
    public synchronized boolean isConnected()
    {
        return connected;
    }

    /**
     * Attempts to connect to the server. See {@link #isConnected()} for success or
     * failure. Once connected, starts a {@link #reader} thread.
     * The first message sent from client to server is our {@link SOCVersion}.
     * From server to client when client connects: If server is full, it sends {@link SOCRejectConnection}.
     * Otherwise its {@code SOCVersion} and current channels and games ({@link SOCChannels}, {@link SOCGames}) are sent.
     *<P>
     * Since user login and authentication don't occur until a game or channel join is requested,
     * no username or password is needed here.
     *<P>
     * If {@code host} is {@code null}, assumes client has started a local TCP server and is now connecting to it.
     * Will start a thread here to periodically {@link SOCServerPing} that server to prevent timeouts when idle.
     *
     * @param host  Server host to connect to, or {@code null} for localhost
     * @param port  Server TCP port to connect to; the default server port is {@link ClientNetwork#SOC_PORT_DEFAULT}.
     * @throws IllegalStateException if already connected
     *     or if {@link Version#versionNumber()} returns 0 (packaging error)
     * @see soc.server.SOCServer#newConnection1(Connection)
     */
    public synchronized void connect(final String host, final int port)
        throws IllegalStateException
    {
        if (connected)
        {
            throw new IllegalStateException
                ("Already connected to " + (serverConnectInfo.hostname != null ? serverConnectInfo.hostname : "localhost")
                 + ":" + serverConnectInfo.port);
        }

        if (Version.versionNumber() == 0)
        {
            throw new IllegalStateException("Packaging error: Cannot determine JSettlers version");
        }

        ex = null;
        final String hostString = (host != null) ? host : "localhost";  // I18N: no need to localize this hostname
        serverConnectInfo = new ServerConnectInfo(hostString, port, null);

        System.out.println(/*I*/"Connecting to " + hostString + ":" + port/*18N*/);  // I18N: Not localizing console output yet
        mainDisplay.setMessage
            (client.strings.get("pcli.message.connecting.serv"));  // "Connecting to server..."

        try
        {
            if (client.gotPassword)
            {
                mainDisplay.setPassword(client.password);
                    // when ! gotPassword, SwingMainDisplay.getPassword() will read pw from there
                client.gotPassword = false;
            }

            final SocketAddress srvAddr = (host != null)
                ? new InetSocketAddress(host, port)
                : new InetSocketAddress(InetAddress.getByName(null), port);  // loopback
            sock = new Socket();
            sock.connect(srvAddr, CONNECT_TIMEOUT_MS);
            sock.setSoTimeout(0);  // ensure no read timeouts; is probably already 0 from Socket defaults
            in = new DataInputStream(sock.getInputStream());
            out = new DataOutputStream(sock.getOutputStream());
            connected = true;

            (reader = new Thread(new NetReadTask(client, this))).start();
            // send VERSION right away (1.1.06 and later)
            sendVersion(false);

            if (host == null)
            {
                final Thread pinger = new Thread("cli-ping-local-srv")
                {
                    public void run()
                    {
                        final String pingCmd = new SOCServerPing(0).toCmd();

                        while(connected)
                        {
                            try
                            {
                                Thread.sleep(PING_LOCAL_SERVER_INTERVAL_MS);
                            }
                            catch (InterruptedException e) {}

                            putNet(pingCmd);
                        }
                    }
                };

                pinger.setDaemon(true);
                pinger.start();
            }
        }
        catch (Exception e)
        {
            ex = e;
            String msg = client.strings.get("pcli.error.couldnotconnect", ex);  // "Could not connect to the server: " + ex
            System.err.println(msg);
            mainDisplay.showErrorPanel(msg, (ex_P == null));

            if (connected)
            {
                disconnect();
                connected = false;
            }
            serverConnectInfo = null;

            if (in != null)
            {
                try { in.close(); } catch (Throwable th) {}
                in = null;
            }
            if (out != null)
            {
                try { out.close(); } catch (Throwable th) {}
                out = null;
            }

            sock = null;
        }
    }

    /**
     * Disconnect from the net (client of remote server).
     * If a problem occurs, sets {@link #ex}.
     * @see #dispose()
     */
    protected synchronized void disconnect()
    {
        connected = false;

        // reader will die once 'connected' is false, and socket is closed

        try
        {
            sock.close();
        }
        catch (Exception e)
        {
            ex = e;
        }
    }

    /**
     * Construct and send a {@link SOCVersion} message during initial connection to a server.
     * Version message includes features and locale in 2.0.00 and later clients; v1.x.xx servers will ignore them.
     *<P>
     * If debug property {@link SOCPlayerClient#PROP_JSETTLERS_DEBUG_CLIENT_FEATURES PROP_JSETTLERS_DEBUG_CLIENT_FEATURES}
     * is set, its value is sent instead of {@link #cliFeats}.{@link SOCFeatureSet#getEncodedList() getEncodedList()}.
     * Then if debug property
     * {@link SOCDisplaylessPlayerClient#PROP_JSETTLERS_DEBUG_CLIENT_GAMEOPT3P PROP_JSETTLERS_DEBUG_CLIENT_GAMEOPT3P}
     * is set, its value is appended to client features as {@code "com.example.js.feat."} + gameopt3p.
     *
     * @param isPractice  True if sending to client's practice server with {@link #putPractice(String)},
     *     false if to a TCP server with {@link #putNet(String)}.
     * @since 2.0.00
     */
    private void sendVersion(final boolean isPractice)
    {
        String feats = System.getProperty(SOCPlayerClient.PROP_JSETTLERS_DEBUG_CLIENT_FEATURES);
        if (feats == null)
            feats = cliFeats.getEncodedList();
        else if (feats.length() == 0)
            feats = null;

        String gameopt3p = System.getProperty(SOCDisplaylessPlayerClient.PROP_JSETTLERS_DEBUG_CLIENT_GAMEOPT3P);
        if (gameopt3p != null)
        {
            gameopt3p = "com.example.js.feat." + gameopt3p.toUpperCase(Locale.US) + ';';
            if (feats != null)
                feats = feats + gameopt3p;
            else
                feats = ';' + gameopt3p;
        }

        final String msg = SOCVersion.toCmd
            (Version.versionNumber(), Version.version(), Version.buildnum(),
             feats, client.cliLocale.toString());

        if (isPractice)
            putPractice(msg);
        else
            putNet(msg);
    }

    /**
     * Are we running a local tcp server?
     * @see #getLocalServerPort()
     * @see #anyHostedActiveGames()
     * @since 1.1.00
     */
    public boolean isRunningLocalServer()
    {
        return localTCPServer != null;
    }

    /**
     * Look for active games that we're hosting (state >= START1A, not yet OVER).
     *
     * @return If any hosted games of ours are active
     * @see MainDisplay#hasAnyActiveGame(boolean)
     * @see SwingMainDisplay#findAnyActiveGame(boolean)
     * @since 1.1.00
     */
    public boolean anyHostedActiveGames()
    {
        if (localTCPServer == null)
            return false;

        Collection<String> gameNames = localTCPServer.getGameNames();

        for (String tryGm : gameNames)
        {
            int gs = localTCPServer.getGameState(tryGm);
            if ((gs < SOCGame.OVER) && (gs >= SOCGame.START1A))
            {
                return true;  // Active
            }
        }

        return false;  // No active games found
    }

    /**
     * write a message to the net: either to a remote server,
     * or to {@link #localTCPServer} for games we're hosting.
     *<P>
     * If {@link #ex} != null, or ! {@link #connected}, {@code putNet}
     * returns false without attempting to send the message.
     *<P>
     * This message is copied to {@link #lastMessage_N}; any error sets {@link #ex}
     * and calls {@link SOCPlayerClient#shutdownFromNetwork()} to show the error message.
     *
     * @param s  the message
     * @return true if the message was sent, false if not
     * @see GameMessageSender#put(String, boolean)
     * @see #putPractice(String)
     */
    public synchronized boolean putNet(String s)
    {
        lastMessage_N = s;

        if ((ex != null) || !isConnected())
        {
            return false;
        }

        if (client.debugTraffic || D.ebugIsEnabled())
            soc.debug.D.ebugPrintlnINFO("OUT - " + SOCMessage.toMsg(s));

        try
        {
            out.writeUTF(s);
            out.flush();
        }
        catch (IOException e)
        {
            ex = e;
            System.err.println("could not write to the net: " + ex);  // I18N: Not localizing console output yet
            client.shutdownFromNetwork();

            return false;
        }

        return true;
    }

    /**
     * write a message to the practice server. {@link #localTCPServer} is not
     * the same as the practice server; use {@link #putNet(String)} to send
     * a message to the local TCP server.
     * Use <tt>putPractice</tt> only with {@link #practiceServer}.
     *<P>
     * Before version 1.1.14, this was <tt>putLocal</tt>.
     *
     * @param s  the message
     * @return true if the message was sent, false if not
     * @see GameMessageSender#put(String, boolean)
     * @throws IllegalArgumentException if {@code s} is {@code null}
     * @see #putNet(String)
     * @since 1.1.00
     */
    public synchronized boolean putPractice(String s)
        throws IllegalArgumentException
    {
        if (s == null)
            throw new IllegalArgumentException("null");

        lastMessage_P = s;

        if ((ex_P != null) || ! prCli.isConnected())
        {
            return false;
        }

        if (client.debugTraffic || D.ebugIsEnabled())
            soc.debug.D.ebugPrintlnINFO("OUT L- " + SOCMessage.toMsg(s));

        prCli.put(s);

        return true;
    }

    /**
     * resend the last message (to the network)
     * @see #resendPractice()
     */
    public void resendNet()
    {
        if (lastMessage_N != null)
            putNet(lastMessage_N);
    }

    /**
     * resend the last message (to the practice server)
     * @see #resendNet()
     * @since 1.1.00
     */
    public void resendPractice()
    {
        if (lastMessage_P != null)
            putPractice(lastMessage_P);
    }

    /**
     * For shutdown - Tell the server we're leaving all games.
     * If we've started a practice server, also tell that server.
     * If we've started a TCP server, tell all players on that server, and shut it down.
     *<P><em>
     * Since no other state variables are set, call this only right before
     * discarding this object or calling System.exit.
     *</em>
     * @return Can we still start practice games? (No local exception yet in {@link #ex_P})
     * @since 1.1.00
     */
    public boolean putLeaveAll()
    {
        final boolean canPractice = (ex_P == null);  // Can we still start a practice game?

        SOCLeaveAll leaveAllMes = new SOCLeaveAll();
        putNet(leaveAllMes.toCmd());
        if ((prCli != null) && ! canPractice)
            putPractice(leaveAllMes.toCmd());

        shutdownLocalServer();

        return canPractice;
    }


    /**
     * A task to continuously read from the server socket.
     * Not used for talking to the practice server.
     * @see LocalStringReaderTask
     */
    static class NetReadTask implements Runnable
    {
        final ClientNetwork net;
        final SOCPlayerClient client;

        public NetReadTask(SOCPlayerClient client, ClientNetwork net)
        {
            this.client = client;
            this.net = net;
        }

        /**
         * continuously read from the net in a separate thread;
         * not used for talking to the practice server.
         * If disconnected or an {@link IOException} occurs,
         * calls {@link SOCPlayerClient#shutdownFromNetwork()}.
         */
        public void run()
        {
            Thread.currentThread().setName("cli-netread");  // Thread name for debug
            try
            {
                final MessageHandler handler = client.getMessageHandler();
                handler.init(client);

                while (net.isConnected())
                {
                    String s = net.in.readUTF();
                    SOCMessage msg = SOCMessage.toMsg(s);

                    if (msg != null)
                        handler.handle(msg, false);
                    else if (client.debugTraffic)
                        soc.debug.D.ebugERROR("Could not parse net message: " + s);
                }
            }
            catch (IOException e)
            {
                // purposefully closing the socket brings us here too
                if (net.isConnected())
                {
                    net.ex = e;
                    System.out.println("could not read from the net: " + net.ex);  // I18N: Not localizing console output yet
                    client.shutdownFromNetwork();
                }
            }
        }

    }  // nested class NetReadTask


    /**
     * For practice games, reader thread to get messages from the
     * practice server to be treated and reacted to.
     *<P>
     * Before v2.0.00 this class was {@code SOCPlayerClient.SOCPlayerLocalStringReader}.
     *
     * @see NetReadTask
     * @author jdmonin
     * @since 1.1.00
     */
    class LocalStringReaderTask implements Runnable
    {
        StringConnection locl;

        /**
         * Start a new thread and listen to practice server.
         *
         * @param prConn Active connection to practice server
         */
        protected LocalStringReaderTask(StringConnection prConn)
        {
            locl = prConn;

            Thread thr = new Thread(this);
            thr.setDaemon(true);
            thr.start();
        }

        /**
         * Continuously read from the practice string server in a separate thread.
         * If disconnected or an {@link IOException} occurs, calls
         * {@link SOCPlayerClient#shutdownFromNetwork()}.
         */
        public void run()
        {
            Thread.currentThread().setName("cli-stringread");  // Thread name for debug
            try
            {
                final MessageHandler handler = client.getMessageHandler();
                handler.init(client);

                while (locl.isConnected())
                {
                    String s = locl.readNext();
                    SOCMessage msg = SOCMessage.toMsg(s);

                    if (msg != null)
                        handler.handle(msg, true);
                    else if (client.debugTraffic)
                        soc.debug.D.ebugERROR("Could not parse practice server message: " + s);
                }
            }
            catch (IOException e)
            {
                // purposefully closing the socket brings us here too
                if (locl.isConnected())
                {
                    ex_P = e;
                    System.out.println("could not read from practice server: " + ex_P);  // I18N: Not localizing console output yet
                    client.shutdownFromNetwork();
                }
            }
        }

    }  // nested class LocalStringReaderTask

}

