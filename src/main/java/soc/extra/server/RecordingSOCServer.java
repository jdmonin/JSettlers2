/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020-2021 Jeremy D Monin <jeremy@nand.net>
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

package soc.extra.server;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import soc.game.SOCGame;
import soc.message.SOCMessage;
import soc.message.SOCMessageForGame;
import soc.message.SOCServerPing;
import soc.message.SOCVersion;
import soc.server.SOCChannelList;
import soc.server.SOCGameListAtServer;
import soc.server.SOCServer;
import soc.server.SOCServerMessageHandler;
import soc.server.genericServer.Connection;
import soc.util.Version;

/**
 * SOCServer which records game events into {@link #records}
 * having a human-readable delimited format, suitable for comparisons in unit tests:
 * see {@link QueueEntry#toString()}.
 *<P>
 * Records game events from server to players and observers.
 * If you also want to record messages from clients with {@link #recordClientMessage(String, int, SOCMessageForGame)},
 * set the {@link #isRecordingFromClients} flag field.
 *<P>
 * Works with {@link DisplaylessTesterClient}.
 *<P>
 * Logs are kept in memory, and can be written to a file with {@link #saveLogToFile(SOCGame, File, String, boolean)}.
 *<P>
 * This server can also run standalone on the usual TCP {@link SOCServer#PROP_JSETTLERS_PORT} port number,
 * to connect from a client and generate a log, which by default also includes messages from the clients.
 * Server JAR and compiled test classes must be on the classpath.
 * Use debug command {@code *SAVELOG* [-s] [-f] filename} to save to {@code filename.soclog} in the current directory.
 *
 * @since 2.5.00
 */
@SuppressWarnings("serial")
public class RecordingSOCServer
    extends SOCServer
{
    public static final String STRINGPORT_NAME = "testport";

    /** per-game queues of recorded game "event" messages */
    public final HashMap<String, GameEventLog> records = new HashMap<>();

    /**
     * Should messages from clients in the game also be recorded?
     * False by default.
     * Returned by {@link #isRecordGameEventsFromClientsActive()}.
     */
    public volatile boolean isRecordingFromClients;

    /** Number of bots to start up: 5 (basic default is 7: {@link SOCServer#SOC_STARTROBOTS_DEFAULT}) */
    public static final int NUM_STARTROBOTS = 5;

    /**
     * Server properties.
     * See {@link SOCServer#SOCServer(String, Properties)} or {@link SOCServer#PROPS_LIST} for available properties.
     *<P>
     * By default, contains:
     *<UL>
     * <LI> {@link SOCServer#PROP_JSETTLERS_ALLOW_DEBUG} = "Y"
     * <LI> {@link SOCServer#PROP_JSETTLERS_BOTS_COOKIE} = "tcook"
     * <LI> {@link SOCServer#PROP_JSETTLERS_CONNECTIONS} = 99
     * <LI> {@link SOCServer#PROP_JSETTLERS_STARTROBOTS} = 5 ({@link #NUM_STARTROBOTS})
     * <LI> {@link SOCServer#PROP_JSETTLERS_GAME_DISALLOW_6PLAYER} = "N"
     * <LI> {@link SOCServer#PROP_JSETTLERS_GAME_DISALLOW_SEA__BOARD} = "N"
     *      (because {@link soctest.server.TestRecorder} unit tests use sea board)
     * <LI> {@link SOCServer#PROP_JSETTLERS_SAVEGAME_DIR} = "." (server's current directory)
     *</UL>
     */
    public static final Properties PROPS = new Properties();
    static
    {
        PROPS.setProperty(SOCServer.PROP_JSETTLERS_ALLOW_DEBUG, "Y");
        PROPS.setProperty(SOCServer.PROP_JSETTLERS_BOTS_COOKIE, "tcook");
        PROPS.setProperty(SOCServer.PROP_JSETTLERS_CONNECTIONS, "99");
        PROPS.setProperty(SOCServer.PROP_JSETTLERS_STARTROBOTS, Integer.toString(NUM_STARTROBOTS));
        PROPS.setProperty(SOCServer.PROP_JSETTLERS_GAME_DISALLOW_6PLAYER, "N");
        PROPS.setProperty(SOCServer.PROP_JSETTLERS_GAME_DISALLOW_SEA__BOARD, "N");
        PROPS.setProperty(SOCServer.PROP_JSETTLERS_SAVEGAME_DIR, ".");
    }

    /**
     * Stringport server for automated tests.
     * To add or change server properties, update {@link #PROPS} before calling this constructor.
     * @see #RecordingSOCServer(int, Properties)
     */
    public RecordingSOCServer()
        throws IllegalStateException
    {
        super(STRINGPORT_NAME, PROPS);
    }

    /**
     * TCP server for manual tests.
     * Unlike the stringport server, turns on {@link #isRecordingFromClients}
     * to capture more data by default when saving logs to disk.
     *
     * For parameters and exceptions, see parent {@link SOCServer#SOCServer(int, Properties)}.
     * @see #RecordingSOCServer()
     */
    public RecordingSOCServer(final int port, final Properties props)
        throws Exception
    {
        super(port, props);
        isRecordingFromClients = true;
    }

    @Override
    public boolean recordGameEventsIsActive()
    {
        return true;
    }

    @Override
    public boolean isRecordGameEventsFromClientsActive()
    {
        return isRecordingFromClients;
    }

    @Override
    public void startLog(final String gameName)
    {
        // Game's queue is created by recordEvent calls

        recordGameEvent(gameName, new SOCVersion
            (Version.versionNumber(), Version.version(), Version.buildnum(), getFeaturesList(), null));
    }

    private void recordEvent(final String gameName, GameEventLog.QueueEntry entry)
    {
        if (entry.event instanceof SOCServerPing)
            // ignore unrelated administrative message which has unpredictable timing
            return;

        GameEventLog log = records.get(gameName);
        if (log == null)
        {
            log = new GameEventLog();
            records.put(gameName, log);
        }

        log.add(entry);
    }

    @Override
    public void recordGameEvent(final String gameName, SOCMessage event)
    {
        recordEvent(gameName, new GameEventLog.QueueEntry(event, -1, false));
    }

    @Override
    public void recordGameEventTo(final String gameName, final int pn, SOCMessage event)
    {
        recordEvent(gameName, new GameEventLog.QueueEntry(event, pn, false));

    }

    @Override
    public void recordGameEventNotTo(final String gameName, final int excludedPN, SOCMessage event)
    {
        recordEvent(gameName, new GameEventLog.QueueEntry(event, new int[]{excludedPN}));
    }

    @Override
    public void recordGameEventNotTo(final String gameName, final int[] excludedPN, SOCMessage event)
    {
        recordEvent(gameName, new GameEventLog.QueueEntry(event, excludedPN));
    }

    @Override
    public void recordClientMessage(final String gameName, int fromPN, SOCMessageForGame event)
    {
        if (! isRecordingFromClients)
            return;

        if (fromPN == -1)
            fromPN = SOCServer.PN_OBSERVER;
        recordEvent(gameName, new GameEventLog.QueueEntry((SOCMessage) event, fromPN, true));
    }

    // No need to override endLog: Game's queue isn't removed, in case tester wants to end games and check them later

    /**
     * Save a game's current event message logs to a file.
     * First message in log file is the server's {@link SOCVersion}.
     * See {@link GameEventLog#saveToFile(SOCGame, File, String, boolean)} for format details.
     * Overwrites file if it already exists.
     *<P>
     * Reminder: If the game was previously loaded from a {@code .game.json} file, its logs will be incomplete
     * instead of starting at the game's beginning.
     *
     * @param ga  Game to save; not null.
     * @param saveDir  Existing directory into which to save the file
     * @param saveFilename  Filename to save to; not validated for format or security.
     *   Recommended suffix is {@link GameEventLog#LOG_FILENAME_EXTENSION} for consistency.
     * @param serverOnly  If true, don't write entries having {@link GameEventLog.QueueEntry#isFromClient} true
     * @throws NoSuchElementException if no logs found for game
     * @throws IllegalArgumentException  if {@code saveDir} isn't a currently existing directory
     * @throws IOException if an I/O problem or {@link SecurityException} occurs
     */
    public void saveLogToFile(final SOCGame ga, final File saveDir, final String saveFilename, final boolean serverOnly)
        throws NoSuchElementException, IllegalArgumentException, IOException
    {
        final String gameName = ga.getName();

        final GameEventLog gameLog = records.get(gameName);
        if (gameLog == null)
            throw new NoSuchElementException(gameName);

        gameLog.saveToFile(ga, saveDir, saveFilename, serverOnly);
    }

    @Override
    protected SOCServerMessageHandler buildServerMessageHandler
        (final SOCGameListAtServer games, final SOCChannelList channels)
    {
        return new RecordingServerMessageHandler(this, games, channels);
    }

    /**
     * Main method, for running {@link RecordingSOCServer} interactively to generate a log.
     * Server JAR and compiled test classes must be on the classpath.
     * @param args  Command-line args, parsed with {@link SOCServer#parseCmdline_DashedArgs(String[])}
     *     which also reads file {@code jsserver.properties} if it exists
     */
    public static void main(final String args[])
    {
        Properties argp = SOCServer.parseCmdline_DashedArgs(args);
        if (argp == null)
        {
            SOCServer.printUsage(false);
            return;
        }

        if (SOCServer.hasStartupPrintAndExit)
        {
            return;
        }

        int port = 0;
        try
        {
            port = Integer.parseInt(argp.getProperty(PROP_JSETTLERS_PORT));
        }
        catch (NumberFormatException e)
        {
            SOCServer.printUsage(false);
            return;
        }

        try
        {
            for (Map.Entry<Object, Object> prop: argp.entrySet())
                PROPS.setProperty(prop.getKey().toString(), prop.getValue().toString());

            SOCServer server = new RecordingSOCServer(port, PROPS);
            if (! server.hasUtilityModeProperty())
            {
                server.setPriority(5);
                server.start();  // <---- Start the Main SOCServer Thread: serverUp() method ----
            }
        } catch (Throwable e) {
            // runtime exception, problem in an initializer's method call, etc
            System.err.println
                ("\n" + e.getMessage()
                 + "\n* Internal error during startup: Exiting now.\n");
            e.printStackTrace();
            System.exit(1);
        }
    }


    /**
     * Extends server message handler in order to add {@code *SAVELOG*} admin/debug command.
     */
    private static class RecordingServerMessageHandler extends SOCServerMessageHandler
    {
        public RecordingServerMessageHandler
            (final RecordingSOCServer srv, final SOCGameListAtServer gameList, final SOCChannelList channelList)
        {
            super(srv, gameList, channelList);
        }

        /**
         * Process the {@code *SAVELOG*} debug/admin command: Save logs of the current game to a file.
         * Based on {@code SOCServerMessageHandler.processDebugCommand_saveGame}.
         * @param c  Client sending the command
         * @param ga  Game in which the command was sent
         * @param argsStr  Args for SAVELOG command (trimmed), or ""
         */
        private void processDebugCommand_saveLog(final Connection c, final SOCGame ga, String argsStr)
        {
            final String USAGE = "Usage: *SAVELOG* [-s] [-f] filename";  // I18N OK: debug only
            final String gaName = ga.getName();

            if (argsStr.isEmpty())
            {
                srv.messageToPlayer
                    (c, gaName, SOCServer.PN_NON_EVENT, USAGE);
                return;
            }

            // very basic flag parsing, until something better is needed
            String fname = null;
            boolean askedForce = false;
            boolean serverOnly = false;
            boolean argsOK = true;
            for (String arg : argsStr.split("\\s+"))
            {
                if (arg.startsWith("-"))
                {
                    if (arg.equals("-s"))
                        serverOnly = true;
                    else if (arg.equals("-f"))
                        askedForce = true;
                    else
                    {
                        argsOK = false;
                        break;
                    }
                } else {
                    if (fname == null)
                        fname = arg;
                    else
                        argsOK = false;
                }
            }

            if ((fname == null) || ! argsOK)
            {
                srv.messageToPlayer
                    (c, gaName, SOCServer.PN_NON_EVENT, USAGE);
                return;
            }

            if (! DEBUG_COMMAND_SAVEGAME_FILENAME_REGEX.matcher(fname).matches())
            {
                srv.messageToPlayerKeyed
                    (c, gaName, SOCServer.PN_NON_EVENT, "admin.loadsavegame.resp.gamename.chars");
                    // "gamename can only include letters, numbers, dashes, underscores."
                return;
            }

            fname += GameEventLog.LOG_FILENAME_EXTENSION;

            if (! askedForce)
                try
                {
                    File f = new File(fname);
                    if (f.exists())
                    {
                        srv.messageToPlayer
                            (c, gaName, SOCServer.PN_NON_EVENT,
                             "Log file already exists: Add -f flag to force, or use a different name");  // I18N OK: debug only
                        return;
                    }
                } catch (SecurityException e) {}
                    // ignore until actual save; that code covers this & other situations

            final int gstate = ga.getGameState();
            if ((gstate == SOCGame.LOADING) || (gstate == SOCGame.LOADING_RESUMING))
            {
                // Could technically save the log, but it would be empty
                srv.messageToPlayerKeyed
                    (c, gaName, SOCServer.PN_NON_EVENT, "admin.savegame.resp.must_resume");
                    // "Must resume loaded game before saving again."
                return;
            }

            try
            {
                ((RecordingSOCServer) srv).saveLogToFile
                    (ga, new File("."), fname, serverOnly);  // <--- The actual log save method ---

                srv.messageToPlayerKeyed
                    (c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                     "admin.savegame.ok.saved_to", fname);
                     // "Saved game to {0}"
            } catch (NoSuchElementException|IOException e) {
                srv.messageToPlayerKeyed
                    (c, gaName, SOCServer.PN_REPLY_TO_UNDETERMINED,
                     "admin.savegame.err.problem_saving", e);
                      // "Problem saving game: {0}"
            }
        }

        public boolean processAdminCommand
            (final Connection c, final SOCGame ga, final String cmdText, final String cmdTextUC)
        {
            if (cmdTextUC.startsWith("*SAVELOG*"))
            {
                processDebugCommand_saveLog(c, ga, cmdText.substring(9).trim());
                return true;
            }

            return super.processAdminCommand(c, ga, cmdText, cmdTextUC);
        }
    }

}
