/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
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

package soctest.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

import soc.baseclient.SOCDisplaylessPlayerClient;
import soc.baseclient.ServerConnectInfo;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.message.*;
import soc.server.genericServer.StringServerSocket;
import soc.util.SOCFeatureSet;
import soc.util.SOCGameList;
import soc.util.Version;

/**
 * Non-testing class: Robot utility client to help run the actual tests.
 * Works with {@link RecordingTesterServer}.
 * Debug Traffic flag is set, which makes unit test logs larger but is helpful when troubleshooting.
 * Unlike parent class, this client connects and authenticates as a "human" player, not a bot,
 * to see same messages a human would be shown.
 * To help set a known test environment, always uses locale {@code "en_US"} unless constructor says otherwise.
 *
 * @since 2.4.50
 */
public class DisplaylessTesterClient
    extends SOCDisplaylessPlayerClient
{

    /**
     * Locale sent in {@link #init()}, or {@code null} for {@code "en_US"}
     */
    protected String localeStr;

    /**
     * Track server's games and options like SOCPlayerClient does,
     * instead of ignoring them until joined like SOCRobotClient.
     *<P>
     * This field is null until {@link MessageHandler#handleGAMES(SOCGames, boolean) handleGAMES},
     *   {@link MessageHandler#handleGAMESWITHOPTIONS(SOCGamesWithOptions, boolean) handleGAMESWITHOPTIONS},
     *   {@link MessageHandler#handleNEWGAME(SOCNewGame, boolean) handleNEWGAME}
     *   or {@link MessageHandler#handleNEWGAMEWITHOPTIONS(SOCNewGameWithOptions, boolean) handleNEWGAMEWITHOPTIONS}
     *   is called.
     */
    protected SOCGameList serverGames = new SOCGameList(knownOpts);

    /** Treat inbound messages through this client's {@link SOCDisplaylessPlayerClient#run()} method. */
    protected Thread treaterThread;

    /**
     * Constructor for a displayless client which will connect to a local server.
     * Does not actually connect here: Call {@link #init()} when ready.
     *
     * @param localeStr  Locale to test with, or {@code null} to use {@code "en_US"}
     * @param knownOpts  Known Options, or {@code null} to use defaults from {@link SOCDisplaylessPlayerClient}
     */
    public DisplaylessTesterClient
        (final String stringport, final String nickname, final String localeStr, final SOCGameOptionSet knownOpts)
    {
        super(new ServerConnectInfo(stringport, null), false);

        this.nickname = nickname;
        this.localeStr = localeStr;
        if (knownOpts != null)
            this.knownOpts = knownOpts;

        debugTraffic = true;
    }

    /**
     * Initialize the displayless client; connect to server and send first messages
     * including our version, features from {@link #buildClientFeats()}, and {@link #rbclass}.
     * If fails to connect, sets {@link #ex} and prints it to {@link System#err}.
     * Based on {@link soc.robot.SOCRobotClient#init()}.
     *<P>
     * When done testing, caller should use {@link SOCDisplaylessPlayerClient#destroy()} to shut down.
     */
    public void init()
    {
        try
        {
            if (serverConnectInfo.stringSocketName == null)
            {
                sock = new Socket(serverConnectInfo.hostname, serverConnectInfo.port);
                sock.setSoTimeout(300000);
                in = new DataInputStream(sock.getInputStream());
                out = new DataOutputStream(sock.getOutputStream());
            }
            else
            {
                sLocal = StringServerSocket.connectTo(serverConnectInfo.stringSocketName);
            }
            connected = true;
            treaterThread = new Thread(this);
            treaterThread.setDaemon(true);
            treaterThread.start();

            put(new SOCVersion
                (Version.versionNumber(), Version.version(), Version.buildnum(),
                 buildClientFeats().getEncodedList(),
                 (localeStr != null) ? localeStr : "en_US").toCmd());
            put(new SOCAuthRequest
                (SOCAuthRequest.ROLE_GAME_PLAYER, nickname, "",
                 SOCAuthRequest.SCHEME_CLIENT_PLAINTEXT, "-").toCmd());
        }
        catch (Exception e)
        {
            ex = e;
            System.err.println("Could not connect to the server: " + ex);
        }
    }

    // from SOCRobotClient ; TODO combine common later?
    protected SOCFeatureSet buildClientFeats()
    {
        SOCFeatureSet feats = new SOCFeatureSet(false, false);
        feats.add(SOCFeatureSet.CLIENT_6_PLAYERS);
        feats.add(SOCFeatureSet.CLIENT_SEA_BOARD);
        feats.add(SOCFeatureSet.CLIENT_SCENARIO_VERSION, Version.versionNumber());

        return feats;
    }

    /**
     * To show successful connection, get the server's version.
     * Same format as {@link soc.util.Version#versionNumber()}.
     */
    public int getServerVersion()
    {
        return sLocalVersion;
    }

    /** Ask to join a game; must have authed already. Sends {@link SOCJoinGame}. */
    public void askJoinGame(String gaName)
    {
        put(new SOCJoinGame(nickname, "", SOCMessage.EMPTYSTR, gaName).toCmd());
    }

    // message handlers

    /** To avoid confusion during gameplay, set both "server" version fields */
    @Override
    protected void handleVERSION(boolean isLocal, SOCVersion mes)
    {
        super.handleVERSION(isLocal, mes);

        if (isLocal)
            sVersion = sLocalVersion;
        else
            sLocalVersion = sVersion;
    }

    // TODO refactor common with SOCPlayerClient vs this and its displayless parent,
    // which currently don't share a parent client class with SOCPlayerClient

    @Override
    protected void handleGAMES(final SOCGames mes)
    {
        serverGames.addGames(mes.getGames(), Version.versionNumber());
    }

    @Override
    protected void handleGAMESWITHOPTIONS(final SOCGamesWithOptions mes)
    {
        serverGames.addGames(mes.getGameList(knownOpts), Version.versionNumber());
    }

    @Override
    protected void handleNEWGAME(final SOCNewGame mes)
    {
        String gaName = mes.getGame();
        boolean canJoin = true;
        boolean hasUnjoinMarker = (gaName.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE);
        if (hasUnjoinMarker)
        {
            gaName = gaName.substring(1);
            canJoin = false;
        }
        serverGames.addGame(gaName, null, ! canJoin);
    }

    @Override
    protected void handleNEWGAMEWITHOPTIONS(final SOCNewGameWithOptions mes)
    {
        String gaName = mes.getGame();
        boolean canJoin = (mes.getMinVersion() <= Version.versionNumber());
        if (gaName.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE)
        {
            gaName = gaName.substring(1);
            canJoin = false;
        }
        serverGames.addGame(gaName, mes.getOptionsString(), ! canJoin);
    }

    @Override
    protected void handleJOINGAMEAUTH(SOCJoinGameAuth mes, final boolean isPractice)
    {
        gotPassword = true;

        String gaName = mes.getGame();
        SOCGameOptionSet opts = serverGames.parseGameOptions(gaName);

        final int bh = mes.getBoardHeight(), bw = mes.getBoardWidth();
        if ((bh != 0) || (bw != 0))
        {
            // Encode board size to pass through game constructor
            if (opts == null)
                opts = new SOCGameOptionSet();
            SOCGameOption opt = knownOpts.getKnownOption("_BHW", true);
            opt.setIntValue((bh << 8) | bw);
            opts.put(opt);
        }

        final SOCGame ga = new SOCGame(gaName, opts, knownOpts);
        ga.isPractice = isPractice;
        ga.serverVersion = (isPractice) ? sLocalVersion : sVersion;
        games.put(gaName, ga);
    }

}
