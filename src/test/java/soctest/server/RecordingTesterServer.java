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

package soctest.server;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import soc.game.SOCGame;
import soc.message.SOCMessage;
import soc.message.SOCServerPing;
import soc.message.SOCVersion;
import soc.server.SOCChannelList;
import soc.server.SOCGameListAtServer;
import soc.server.SOCServer;
import soc.server.SOCServerMessageHandler;
import soc.server.genericServer.Connection;
import soc.util.Version;
import soctest.server.TestRecorder;  // for javadocs only

/**
 * Non-testing class: Server which records game events into {@link #records}
 * having a human-readable delimited format, suitable for comparisons in unit tests:
 * see {@link QueueEntry#toString()}.
 *<P>
 * Works with {@link DisplaylessTesterClient}.
 *<P>
 * Logs are kept in memory, and can be written to a file with {@link #saveLogToFile(SOCGame, File, String)}.
 *<P>
 * This server can also run standalone on the usual TCP {@link SOCServer#PROP_JSETTLERS_PORT} port number,
 * to connect from a client and generate a log. Server JAR and compiled test classes must be on the classpath.
 * Use debug command {@code *SAVELOG* filename} to save to {@code filename.soclog} in the current directory.
 *
 * @since 2.5.00
 */
@SuppressWarnings("serial")
public class RecordingTesterServer
    extends SOCServer
{
    public static final String STRINGPORT_NAME = "testport";

    /** per-game queues of recorded game "event" messages */
    public final HashMap<String, GameEventLog> records = new HashMap<>();

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
     *      (because {@link TestRecorder} unit tests use sea board)
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
    }

    /**
     * Stringport server for automated tests.
     * To add or change server properties, update {@link #PROPS} before calling this constructor.
     * @see #RecordingTesterServer(int, Properties)
     */
    public RecordingTesterServer()
        throws IllegalStateException
    {
        super(STRINGPORT_NAME, PROPS);
    }

    /**
     * TCP server for manual tests.
     * For parameters and exceptions, see parent {@link SOCServer#SOCServer(int, Properties)}.
     * @see #RecordingTesterServer()
     */
    public RecordingTesterServer(final int port, final Properties props)
        throws Exception
    {
        super(port, props);
    }

    @Override
    public boolean recordGameEventsIsActive()
    {
        return true;
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
        recordEvent(gameName, new GameEventLog.QueueEntry(event, -1));
    }

    @Override
    public void recordGameEventTo(final String gameName, final int pn, SOCMessage event)
    {
        recordEvent(gameName, new GameEventLog.QueueEntry(event, pn));

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

    // No need to override endLog: Game's queue isn't removed, in case tester wants to end games and check them later

    /**
     * Save a game's current event message logs to a file.
     * First message in log file is the server's {@link SOCVersion}.
     * See {@link GameEventLog#saveToFile(SOCGame, File, String)} for format details.
     * Overwrites file if it already exists.
     *<P>
     * Reminder: If the game was previously loaded from a {@code .game.json} file, its logs will be incomplete
     * instead of starting at the game's beginning.
     *
     * @param ga  Game to save; not null.
     * @param saveDir  Existing directory into which to save the file
     * @param saveFilename  Filename to save to; not validated for format or security.
     *   Recommended suffix is {@link GameEventLog#LOG_FILENAME_EXTENSION} for consistency.
     * @throws NoSuchElementException if no logs found for game
     * @throws IllegalArgumentException  if {@code saveDir} isn't a currently existing directory
     * @throws IOException if an I/O problem or {@link SecurityException} occurs
     */
    public void saveLogToFile(final SOCGame ga, final File saveDir, final String saveFilename)
        throws NoSuchElementException, IllegalArgumentException, IOException
    {
        final String gameName = ga.getName();

        final GameEventLog gameLog = records.get(gameName);
        if (gameLog == null)
            throw new NoSuchElementException(gameName);

        gameLog.saveToFile(ga, saveDir, saveFilename);
    }

    @Override
    protected SOCServerMessageHandler buildServerMessageHandler
        (final SOCGameListAtServer games, final SOCChannelList channels)
    {
        return new RecordingServerMessageHandler(this, games, channels);
    }

    /**
     * Main method, for running {@link RecordingTesterServer} interactively to generate a log.
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

            SOCServer server = new RecordingTesterServer(port, PROPS);
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
            (final RecordingTesterServer srv, final SOCGameListAtServer gameList, final SOCChannelList channelList)
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
            final String gaName = ga.getName();
            boolean askedForce = false;

            // very basic flag parse, as a stopgap until something better is needed
            if (argsStr.startsWith("-f "))
            {
                askedForce = true;
                argsStr = argsStr.substring(3).trim();
            } else {
                int i = argsStr.indexOf(' ');
                if ((i != -1) && (argsStr.substring(i + 1).trim().equals("-f")))
                {
                    askedForce = true;
                    argsStr = argsStr.substring(0, i);
                }
            }

            if (argsStr.isEmpty() || argsStr.indexOf(' ') != -1)
            {
                srv.messageToPlayer
                    (c, gaName, SOCServer.PN_NON_EVENT, "Usage: *SAVELOG* [-f] filename");  // I18N OK: debug only
                return;
            }

            final String fname = argsStr + GameEventLog.LOG_FILENAME_EXTENSION;

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

            if (! DEBUG_COMMAND_SAVEGAME_FILENAME_REGEX.matcher(argsStr).matches())
            {
                srv.messageToPlayerKeyed
                    (c, gaName, SOCServer.PN_NON_EVENT, "admin.loadsavegame.resp.gamename.chars");
                    // "gamename can only include letters, numbers, dashes, underscores."
                return;
            }

            try
            {
                ((RecordingTesterServer) srv).saveLogToFile(ga, new File("."), fname);  // <--- The actual log save method ---

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
