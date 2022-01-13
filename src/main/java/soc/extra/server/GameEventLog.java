/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2021 Jeremy D Monin <jeremy@nand.net>
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Vector;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.message.SOCMessage;
import soc.message.SOCMessageForGame;
import soc.message.SOCNewGame;
import soc.message.SOCNewGameWithOptions;
import soc.message.SOCVersion;
import soc.server.SOCServer;
import soc.util.Version;

/**
 * A Game Event Log in memory or saved to/loaded from a file.
 * An {@link EventEntry} holds each log entry.
 *<P>
 * These logs and their entry format are used by various tests and {@link RecordingSOCServer},
 * but aren't used by the standard SOCServer. Their contents are the network messages relevant to gameplay
 * captured by server-side calls to {@code messageToPlayer / messageToGame(.., isEvent=true)}
 * or {@code recordGameEvent(..)}, along with messages from client players and observers.
 *<P>
 * Use {@link #load(File, boolean, int)} to parse from a {@code .soclog} file,
 * creating a new {@code GameEventLog} with {@link #entries} from the parsed entries,
 * setting the {@link #gameName}, {@link #isAtClient}, {@link #version}, {@link #optsStr},
 * and {@link #numLines} fields.
 *
 *<H3>Message Sequences</H3>
 *
 * A list of basic game actions and their message sequences is in
 * {@code doc/Message-Sequences-for-Game-Actions.md}.
 * The server uses the latest version format to record the game event sequences
 * it sends, even when it actually sent different messages to an older client to be compatible.
 * When loading a log file, {@link #version} holds that sequence/format version info.
 *
 *<H3>Log file format</H3>
 *
 * {@link #save(File, String, boolean, boolean)} saves files in this format:
 *<UL>
 *  <LI> File starts with this header line:<BR>
 *    <tt>SOC game event log: type=</tt>type<tt>, version=2500, created_at=</tt>timestamp<tt>, now=</tt>timestamp<tt>, game_name=</tt>game name <BR>
 *    <UL>
 *      <LI> {@code type} is {@code F} for a full log (all messages from and to server),
 *           or {@code C} if contains only messages from the server to one or all clients,
 *           not also from all clients to server;
 *           based on {@code isAtClient} parameter when calling {@code save(..)}.
 *           Is always first in this list of properties.
 *      <LI> {@code version} is the current {@link Version#versionNumber()}.
 *           Always follows {@code type} in this list of properties.
 *      <LI> Header timestamps use unix epoch time in seconds: {@link System#currentTimeMillis()} / 1000
 *      <LI> Other field may be added in the future; {@code version} will always be first in the list
 *      <LI> Game name must pass {@link SOCMessage#isSingleLineAndSafe(String)}
 *      <LI> Although game names can't include commas, {@code game_name} will always be last for readability
 *    </UL>
 *  <LI> For convenience, {@code save(..)} then writes comments with those timestamps
 *    in human-readable local time with RFC 822 timezone:
 *    "2001-06-09 13:30:23 -0400". These comments aren't part of the format spec.
 *  <LI> Each log entry is on its own line, formatted by {@link EventEntry#toString()}
 *  <LI> First log entry: The server's {@link SOCVersion} as sent to clients, for info on active features
 *  <LI> Next log entry: {@link SOCNewGameWithOptions} with the game's options; may be {@link SOCNewGame} if no gameopts
 *  <LI> Rest of the log entries are all game events sent from the server to players and/or observers
 *  <LI> Comment lines start with {@code #} after optional leading whitespace
 *  <LI> Blank lines are allowed
 *  <LI> For convenience, {@code save(..)} ends the log with comments with the game's current state and players
 *</UL>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.5.00
 */
public class GameEventLog
{
    /**
     * Standard suffix/extension for {@link GameEventLog} files: {@code ".soclog"}
     * @see #save(File, String, boolean, boolean)
     */
    public static final String FILENAME_EXTENSION = ".soclog";

    /**
     * Version number (2.5.00) where this log format type was introduced.
     * {@link #version} should be 0 or &gt;= {@code MIN_VERSION}.
     */
    public static final int MIN_VERSION = 2500;

    /**
     * Timestamp format for log comments, local time with RFC 822 timezone offset:
     * "2001-06-09 13:30:23 -0400".
     * Remember to synchronize calls to this formatter.
     */
    private static final SimpleDateFormat TIMESTAMP_SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    /**
     * All log entries, chronologically. Public for easy access; Vector is thread-safe, field is final.
     */
    public final Vector<EventEntry> entries = new Vector<>();

    /**
     * Game data in a log that's currently being recorded;
     * null if log was loaded by {@link #load(File, boolean, int)}.
     * @see #gameName
     */
    public final SOCGame game;

    /**
     * Name of game seen in a log that's been loaded by {@link #load(File, boolean, int)}.
     * Otherwise {@code null}, even when {@link #game} != {@code null}.
     * @see #game
     */
    public String gameName;

    /**
     * True if this log was loaded by {@link #load(File, boolean, int)} has {@code type=C} in header
     * or was filtered by {@code load(..)} to include only messages from the server to one or all client players,
     * as if log was recorded at the client (see {@link #atClientPN}). Otherwise false.
     *<P>
     * {@code type=C} is written by a call to
     * {@link #save(File, String, boolean, boolean) save(.., isAtClient=true)}.
     */
    public boolean isAtClient;

    /**
     * If not -1, this log was loaded by {@link #load(File, boolean, int)} and filtered
     * to include only messages from server to this one client player number, as if log was recorded at that client.
     * If set, {@code load(..)} also sets {@link #isAtClient}.
     */
    public int atClientPN = -1;

    /**
     * The game data version seen in an event log header that's been loaded by {@link #load(File, boolean, int)},
     * in same format as {@link Version#versionNumber()}. Otherwise 0.
     * When a log file is written by {@link #save(File, String, boolean, boolean)},
     * it uses {@link Version#versionNumber()} when writing the header.
     * @see #MIN_VERSION
     */
    public int version;

    /**
     * Should our {@link #game}'s log have timestamps?
     * If true, entries should use their {@link EventEntry#timeElapsedMS} field.
     * Not set when loading; some entries in loaded log may or may not have timestamps,
     * and {@code game} is {@code null}.
     */
    public final boolean hasTimestamps;

    /**
     * The game options, or {@code null} if none, seen in a log that's been loaded
     * by {@link #load(File, boolean, int)}, in same format as {@link SOCNewGameWithOptions#getOptionsString()}.
     * Otherwise {@code null}, even when {@link #game} != {@code null}.
     */
    public String optsStr;

    /**
     * Number of lines read by {@link #load(File, boolean, int)}, including blank and comment lines. Otherwise 0.
     * Includes lines filtered out when {@link #atClientPN} != -1 or {@link #isAtClient}.
     */
    public int numLines;

    // Reminder: if you add fields, update the copy constructor and TestGameEventLog.testFilteringCopyConstructor().

    /**
     * Create a new GameEventLog.
     * @param ga  Active game to create log for, or {@code null} for an empty log
     *     or one being reloaded with {@link #load(File, boolean, int)}.
     * @param hasTimestamps  True if {@code ga != null} and server should set the timestamp
     *     in each {@link EventEntry} it adds to this log.
     */
    public GameEventLog(final SOCGame ga, final boolean hasTimestamps)
    {
        game = ga;
        this.hasTimestamps = hasTimestamps;
    }

    /**
     * Copy constructor, optionally filtering to a client playerNumber
     * the same way {@link #load(File, boolean, int)} does.
     *<P>
     * The new log has its own new {@link #entries} List, having references to
     * the same {@link EventEntry} objects as {@code source.}{@link #entries}.
     * If {@code source.}{@link #game} is set, that reference is copied instead of making a deep copy of the game object.
     * {@link #numLines} will be same as {@code source}, not reduced by filtering.
     *
     * @param source  Log to copy from
     * @param filterAtClientPN  -1 to copy all entries,
     *     or any other value to ignore/filter out log entries which have {@link EventEntry#isFromClient} set true
     *     or aren't sent from server to that client player number, as if log was recorded at that client.
     *     Can also be {@link SOCServer#PN_OBSERVER} or {@link SOCServer#PN_REPLY_TO_UNDETERMINED}.
     *     If {@code source.}{@link #isAtClient}, the log is already server-only but probably contains messages
     *     to other client players too.
     * @throws IllegalArgumentException If filtering but {@code source.}{@link #atClientPN} != -1
     */
    public GameEventLog(final GameEventLog source, final int filterAtClientPN)
        throws IllegalArgumentException
    {
        if ((filterAtClientPN != -1) && (source.atClientPN != -1))
            throw new IllegalArgumentException("already atClientPN: " + source.atClientPN);

        game = source.game;
        gameName = source.gameName;
        if (filterAtClientPN != -1)
        {
            isAtClient = true;
            atClientPN = filterAtClientPN;
        } else {
            isAtClient = source.isAtClient;
            atClientPN = source.atClientPN;
        }
        version = source.version;
        hasTimestamps = source.hasTimestamps;
        optsStr = source.optsStr;
        numLines = source.numLines;

        if (filterAtClientPN == -1)
            entries.addAll(source.entries);
        else
            synchronized(source.entries)  // avoid some locking overhead
            {
                for (EventEntry ee : source.entries)
                {
                    if (ee.isFromClient || ((ee.pn != -1) && (ee.pn != filterAtClientPN)))
                    {
                        continue;
                    } else if (ee.excludedPN != null) {
                        boolean exclude = false;
                        for (int pn : ee.excludedPN)
                        {
                            if (pn == filterAtClientPN)
                            {
                                exclude = true;
                                break;
                            }
                        }

                        if (exclude)
                            continue;
                    }

                    entries.add(ee);
                }
            }
    }

    /**
     * Add an entry to this game's log {@link #entries}.
     * @param e  Entry to add; not null
     */
    public void add(EventEntry e)
    {
        entries.add(e);
    }

    /**
     * Clear out all of this game's log {@link #entries}.
     */
    public void clear()
    {
        entries.clear();
    }

    /**
     * Save our {@link #game}'s current event message logs to a file.
     * Overwrites file if it already exists.
     *
     * @param ga  This log's game, to get its basic info; not null
     * @param saveDir  Existing directory into which to save the file
     * @param saveFilename  Filename to save to; not validated for format or security.
     *     Recommended suffix is {@link #FILENAME_EXTENSION} for consistency.
     * @param untimed  If true, omit the optional {@link GameEventLog.EventEntry#timeElapsedMS} timestamp field
     *     when writing entries to file
     * @param atClient  If true, don't write entries where {@link GameEventLog.EventEntry#isFromClient} true;
     *     log will be {@code type=C} instead of {@code type=F}.
     * @throws IllegalStateException  if {@link #game} is {@code null}
     * @throws NoSuchElementException  unless {@link #entries} starts with {@link SOCVersion}
     *     followed by {@link SOCNewGame} or {@link SOCNewGameWithOptions} where
     *     {@link SOCNewGame#getGame()} equals {@link #game}{@link SOCGame#getName() .getName()}
     * @throws IllegalArgumentException  if {@code saveDir} isn't a currently existing directory
     * @throws IOException if an I/O problem or {@link SecurityException} occurs
     * @see #load(File, boolean, int)
     */
    public void save
        (final File saveDir, final String saveFilename, final boolean untimed, final boolean atClient)
        throws IllegalStateException, NoSuchElementException, IllegalArgumentException, IOException
    {
        if (game == null)
            throw new IllegalStateException("game null");

        // check for required start-of-log entry messages
        {
            boolean allOK = false;
            if (entries.size() >= 2)
            {
                SOCMessage msg = entries.get(0).event;
                if (! (msg instanceof SOCVersion))
                    throw new NoSuchElementException("First entry must be SOCVersion");

                msg = entries.get(1).event;
                String gaName = null;
                if (msg instanceof SOCNewGame)
                    gaName = ((SOCNewGame) msg).getGame();
                else if (msg instanceof SOCNewGameWithOptions)
                    gaName = ((SOCNewGameWithOptions) msg).getGame();

                if (gaName != null)
                {
                    if (! gaName.equals(game.getName()))
                        throw new NoSuchElementException("Game name mismatch: event message has " + gaName);
                    else
                        allOK = true;
                }
            }

            if (! allOK)
                throw new NoSuchElementException("First entries must be SOCVersion, SOCNewGame[WithOptions]");
        }

        // GameSaverJSON.saveGame uses similar logic to check status before saving.
        // If you update this, consider updating that too.

        try
        {
            if (! (saveDir.exists() && saveDir.isDirectory()))
                throw new IllegalArgumentException("Not found as directory: " + saveDir.getPath());
        } catch (SecurityException e) {
            throw new IOException("Can't read directory " + saveDir.getPath(), e);
        }

        try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(new File(saveDir, saveFilename)), "UTF-8")))
        {
            final Date createdAt = game.getStartTime(), now = new Date();
            final String createdStr, nowStr;
            synchronized (TIMESTAMP_SDF)
            {
                createdStr = TIMESTAMP_SDF.format(createdAt);
                nowStr = TIMESTAMP_SDF.format(now);
            }

            writer.append
                ("SOC game event log: type=" + (atClient ? 'C' : 'F')
                 + ", version=" + Version.versionNumber()
                 + ", created_at=" + (createdAt.getTime() / 1000)
                 + ", now=" + (now.getTime() / 1000)
                 + ", game_name=" + game.getName() + '\n');
            writer.append("# Game created at: " + createdStr + '\n');
            writer.append("# Log written at:  " + nowStr + '\n');

            for (GameEventLog.EventEntry entry : entries)
                if (! (entry.isFromClient && atClient))
                    writer.append(entry.toString(untimed)).append('\n');

            writer.append("# End of log. Game state ")
                .append(Integer.toString(game.getGameState())).append('\n');
            writer.append("# Player info:\n");
            final SOCPlayer winner = game.getPlayerWithWin();  // null if still playing
            for (int pn = 0; pn < game.maxPlayers; ++pn)
            {
                final SOCPlayer pl = game.getPlayer(pn);
                if (pl == null)
                    continue;  // maybe game's been destroyed
                final int vp = (winner != null) ? pl.getTotalVP() : pl.getPublicVP();
                if ((vp == 0) && game.isSeatVacant(pn))
                    continue;
                final int totalVP = (winner != null) ? vp : pl.getTotalVP();

                String plName = pl.getName();
                if (plName == null)
                    plName = "(vacant)";

                writer.append("# - pn " + pn);
                if (totalVP != vp)
                    writer.append(": visible score " + vp + ", total score " + totalVP + ": " + plName);
                else
                    writer.append(": total score " + vp + ": " + plName);

                if (pl.hasLargestArmy())
                    writer.append(", Largest Army");
                if (pl.hasLongestRoad())
                    writer.append(", Longest Road");
                if (winner == pl)
                    writer.append(", Winner");
                writer.append('\n');
            }

            writer.flush();
        }
        catch (SecurityException e) {
            throw new IOException("Can't write to " + saveFilename, e);
        }
    }

    /**
     * Load and parse a log from a file in the format saved by {@link #save(File, String, boolean, boolean)}.
     * Adds its entries to {@link #entries}. Sets {@link #gameName}, {@link #version}, {@link #isAtClient},
     * {@link #optsStr}, and {@link #numLines}.
     *
     * @param loadFrom  File to load from; filename usually ends with {@link #FILENAME_EXTENSION}
     * @param ignoreComments  If true, ignore comment lines instead of calling
     *     {@link EventEntry#EventEntry(String) new EventEntry(String)} constructor
     * @param filterAtClientPN  If not -1, ignore/filter out log entries which have
     *     {@link EventEntry#isFromClient} set true or aren't sent from server to that client player number.
     *     Can also be {@link SOCServer#PN_OBSERVER} or {@link SOCServer#PN_REPLY_TO_UNDETERMINED}.
     *     Can be used by client-side code or tests.
     *     If {@code type=C}, the log is already server-only but probably contains messages to other client
     *     players too.
     * @throws IOException  if a problem occurs while loading
     * @throws ParseException  if a log line can't be parsed. Exception text will start with line number: "Line 5: ...".
     *     {@link ParseException#getErrorOffset()} will be the offset within that trimmed line,
     *     not within the overall file.
     *     Also thrown if game name or version in logged {@link SOCVersion} or {@link SOCNewGame}
     *     differs from that in header, or game name doesn't pass
     *     {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @throws NoSuchElementException if log file doesn't start with the required header line
     *     {@code "SOC game event log: type=., version=..."}, or its first 2 event messages aren't the required
     *     {@link SOCVersion} and either {@link SOCNewGame} or {@link SOCNewGameWithOptions},
     *     or if header's {@code version} number &lt; 2500 ({@link #MIN_VERSION})
     * @see #GameEventLog(GameEventLog, int)
     */
    public static GameEventLog load
        (final File loadFrom, final boolean ignoreComments, final int filterAtClientPN)
        throws IOException, ParseException, NoSuchElementException
    {
        final GameEventLog ret = new GameEventLog(null, false);
        final Vector<EventEntry> entries = ret.entries;
        boolean sawVers = false, sawNewGame = false;  // required first messages

        try
            (final FileInputStream fis = new FileInputStream(loadFrom);
             final BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8")); )
        {
            String line = reader.readLine();
            if ((line == null) || ! line.startsWith("SOC game event log: type="))
                throw new NoSuchElementException("File must start with \"SOC game event log\" header line");

            // parse type, version, game name from header fields
            for (String field : line.substring(20).split(", "))
            {
                if (field.startsWith("type="))
                {
                    if (field.length() != 6)
                        throw new ParseException("unknown log type, must be 1 character: " + field, 26);
                    final char logType = field.charAt(5);
                    switch(logType)
                    {
                    case 'F':
                        // GameEventLog is full by default
                        break;
                    case 'C':
                        ret.isAtClient = true;
                        break;
                    default:
                        throw new ParseException("unknown log type: " + logType, 26);
                    }
                }
                else if (field.startsWith("version="))
                {
                    int vers;
                    try
                    {
                         vers = Integer.parseInt(field.substring(8));
                    } catch (NumberFormatException e) {
                        throw new ParseException("Couldn't parse version number in header", 21 + 7 + 8);
                    }
                    if (vers < MIN_VERSION)
                        throw new NoSuchElementException
                            ("Minimum version for format is " + MIN_VERSION +", this file has " + vers);

                    ret.version = vers;
                }
                else if (field.startsWith("game_name="))
                {
                    String gameName = field.substring(10);
                    if (gameName.isEmpty())
                        throw new ParseException("Empty game_name", 1);
                    if (! SOCMessage.isSingleLineAndSafe(gameName))
                        throw new ParseException("Invalid game_name", 1);

                    ret.gameName = gameName;
                }
            }
            if (ret.version == 0)
                throw new NoSuchElementException("Header missing required version field");
            if (ret.gameName == null)
                throw new ParseException("Header missing required game_name field", 20);

            synchronized (entries)  // avoid some locking overhead
            {
                int lnum = 2;
                for (line = reader.readLine(); line != null; line = reader.readLine(), ++lnum)
                {
                    line = line.trim();
                    if (line.isEmpty())
                        continue;

                    if ((! sawVers) && (line.charAt(0) != '#'))
                    {
                        // Should see SOCVersion on first game event entry line.
                        // This is checked before the usual parsing, so that
                        // any other contents throw NoSuchElementException not ParseException.
                        if (! line.contains(":SOCVersion"))
                            throw new NoSuchElementException("First event message must be SOCVersion");
                    }

                    EventEntry ee;
                    try
                    {
                        ee = EventEntry.parse(line);
                    } catch (ParseException e) {
                        throw new ParseException
                            ("Line " + lnum + ": " + e.getMessage() + "\n  for line: " + line, e.getErrorOffset());
                    }
                    if (ee == null)
                        continue;
                    if (ignoreComments && (ee.comment != null))
                        continue;

                    if ((! sawVers) && (ee.event instanceof SOCVersion))
                    {
                        int vers = ((SOCVersion) ee.event).getVersionNumber();
                        if (vers != ret.version)
                            throw new ParseException
                                ("Line " + lnum + ": Version " + vers
                                 + " in SOCVersion differs from header's " + ret.version, 1);

                        sawVers = true;
                    }
                    else if ((! sawNewGame) && (ee.event != null))
                    {
                        if (ee.event instanceof SOCNewGameWithOptions)
                        {
                            String gameName = ((SOCNewGameWithOptions) ee.event).getGame();
                            if (! ret.gameName.equals(gameName))
                                throw new ParseException("Line " + lnum + ": Game name differs from header", 1);

                            String ostr = ((SOCNewGameWithOptions) ee.event).getOptionsString();
                            if ((ostr != null) && (ostr.charAt(0) == ','))
                                ostr = ostr.substring(1);
                            ret.optsStr = ostr;
                        } else if (ee.event instanceof SOCNewGame) {
                            String gameName = ((SOCNewGame) ee.event).getGame();
                            if (! ret.gameName.equals(gameName))
                                throw new ParseException("Line " + lnum + ": Game name differs from header", 1);
                        } else {
                            throw new NoSuchElementException
                                ("Second event message must be SOCNewGame or SOCNewGameWithOptions");
                        }

                        sawNewGame = true;
                    }

                    if (filterAtClientPN != -1)
                    {
                        if (ee.isFromClient || ((ee.pn != -1) && (ee.pn != filterAtClientPN)))
                            continue;

                        if (ee.excludedPN != null)
                        {
                            boolean exclude = false;
                            for (int pn : ee.excludedPN)
                            {
                                if (pn == filterAtClientPN)
                                {
                                    exclude = true;
                                    break;
                                }
                            }

                            if (exclude)
                                continue;
                        }
                    }

                    entries.add(ee);
                }

                ret.numLines = lnum - 1;
            }
        }

        if (filterAtClientPN != -1)
        {
            ret.isAtClient = true;
            ret.atClientPN = filterAtClientPN;
        }

        return ret;
    }

    /**
     * A recorded entry: Event {@link SOCMessage}, its audience
     * (all players, 1 player, or specifically excluded player(s)),
     * or source if from client. See {@link #toString()} for human-readable delimited format.
     *<P>
     * If this class changes, update comprehensive unit test {@link soctest.server.TestGameEventLog#testEventEntry()}.
     */
    public static final class EventEntry
    {
        /**
         * Optional timestamp of event, in milliseconds since {@link SOCGame#getStartTime()}, or -1.
         *<P>
         * {@link Integer#MAX_VALUE} millis is 24.855 days, which is reasonably long enough,
         * so for efficiency we use {@code int} not {@code long}.
         */
        public final int timeElapsedMS;

        /**
         * Event message data; can be {@code null} if ! {@link #isFromClient}.
         * If {@link #isFromClient}, can be cast to {@link SOCMessageForGame}.
         */
        public final SOCMessage event;

        /**
         * If {@link #isFromClient}, the player number this event was sent from, or {@link SOCServer#PN_OBSERVER}.
         * If from server, the player number this event was sent to, or -1 if to all;
         * is also -1 if {@link #excludedPN} != null or {@link #comment} != null.
         * Can also be {@link SOCServer#PN_OBSERVER} or {@link SOCServer#PN_REPLY_TO_UNDETERMINED}.
         * @see #isToAll()
         */
        public final int pn;

        /**
         * If from server, the player numbers specifically excluded from this event's audience, or null.
         * Ignored if {@link #isFromClient}.
         * @see #pn
         */
        public final int[] excludedPN;

        /**
         * True if this message is from a game member client instead of from server.
         * @see #pn
         */
        public final boolean isFromClient;

        /**
         * Contents of a comment line, starting just after {@code '#'} including whitespace, or {@code null}.
         */
        public final String comment;

        // Reminder: if you add fields, update toString(), equals(), and unit tests.

        /**
         * EventEntry sent to one player or observer, or all players if {@code pn} is -1,
         * or from a player or observer.
         *
         * @param event  Event message to record.
         *     If {@code isFromClient}, must be a {@link SOCMessageForGame} and not {@code null}
         * @param pn  From server, the player number sent to, or -1 if to all players.
         *     Can also be {@link SOCServer#PN_OBSERVER} or {@link SOCServer#PN_REPLY_TO_UNDETERMINED}.
         *     From client, the player number sent from, or {@link SOCServer#PN_OBSERVER} (not -1).
         * @param isFromClient  True if is from client instead of from server to member client(s)
         * @param timeElapsedMS  Relative timestamp or -1; see {@link EventEntry#timeElapsedMS} for format
         * @throws IllegalArgumentException if {@code isFromClient} but {@code event} is {@code null}
         *     or in't a {@link SOCMessageForGame}
         * @see #EventEntry(SOCMessage, int[], int)
         * @see #EventEntry(String)
         */
        public EventEntry(final SOCMessage event, final int pn, final boolean isFromClient, final int timeElapsedMS)
            throws IllegalArgumentException
        {
            this.event = event;
            this.pn = pn;
            this.excludedPN = null;
            this.isFromClient = isFromClient;
            if (isFromClient && ! (event instanceof SOCMessageForGame))
                throw new IllegalArgumentException
                    ((event != null)
                     ? "isFromClient but not SOCMessageForGame: " + event.getClass().getSimpleName()
                     : "can't be null when isFromClient");
            this.timeElapsedMS = timeElapsedMS;
            this.comment = null;
        }

        /**
         * EventEntry sent to all players except specific ones, or to all if {@code excludedPN} null.
         * @param event  Event message to record
         * @param excludedPN  Player number(s) excluded, or all players if null
         * @param timeElapsedMS  Relative timestamp or -1; see {@link EventEntry#timeElapsedMS} for format
         * @see #EventEntry(SOCMessage, int, boolean, int)
         * @see #EventEntry(String)
         */
        public EventEntry(final SOCMessage event, final int[] excludedPN, final int timeElapsedMS)
        {
            this.event = event;
            this.pn = -1;
            this.excludedPN = excludedPN;
            this.isFromClient = false;
            this.timeElapsedMS = timeElapsedMS;
            this.comment = null;
        }

        /**
         * EventEntry representing a comment when log is read from file.
         * Other fields will be set to java default values, except {@link #pn} = -1.
         * @param comment  Contents of comment, not {@code null}; see {@link #comment}.
         * @throws IllegalArgumentException if {@code comment} null
         * @see #EventEntry(SOCMessage, int, boolean, int)
         * @see #EventEntry(SOCMessage, int[], int)
         */
        public EventEntry(String comment)
            throws IllegalArgumentException
        {
            if (comment == null)
                throw (new IllegalArgumentException("comment"));

            this.comment = comment;
            this.event = null;
            this.pn = -1;
            this.excludedPN = null;
            this.timeElapsedMS = -1;
            this.isFromClient = false;
        }

        /**
         * Is this a server message sent to all players?
         * @return true if {@link #isFromClient} false, {@link #pn} == -1, and {@link #excludedPN} == null
         */
        public boolean isToAll()
        {
            return (! isFromClient) && (pn == -1) && (excludedPN == null);
        }

        /**
         * Basic EventEntry equality test.
         * This implementation is slow, and calls {@link #event}{@link SOCMessage#toString() .toString()}
         * on both objects. It's useful for testing but shouldn't be used as-is in production code.
         * Also, the class doesn't override {@code hashCode()}.
         * @param o  Object to compare, or {@code null}
         * @return  True if {@code o} is a {@code EventEntry} whose fields all have the same values as this object.
         *     Uses {@link Arrays#equals(int[], int[])} for {@link #excludedPN},
         *     {@link String#equals(Object)} for {@link #comment}.
         */
        @Override
        public boolean equals(Object o)
        {
            if (o == this)
                return true;
            if (! (o instanceof EventEntry))
                return false;

            final EventEntry oe = (EventEntry) o;

            if ((oe.pn != this.pn) || (oe.isFromClient != this.isFromClient)
                || ! Arrays.equals(oe.excludedPN, this.excludedPN))
                return false;

            if (oe.comment == null)
            {
                if (this.comment != null)
                    return false;
            }
            else if (! oe.comment.equals(this.comment))
                return false;

            if (oe.timeElapsedMS != this.timeElapsedMS)
                return false;

            if (oe.event == null)
            {
                if (this.event != null)
                    return false;
            } else {
                if (this.event == null)
                    return false;
                if (! oe.event.toString().equals(this.event.toString()))
                    return false;
            }

            return true;
        }

        /**
         * Formats this entry's fields.
         * Call {@link #parse(String)} to parse the returned string.
         * To omit optional {@link #timeElapsedMS} timestamp field, call {@link #toString(boolean)} instead.
         *<P>
         * Calls {@link SOCMessage#toString()}, not {@link SOCMessage#toCmd()},
         * for class/field name label strings and to help test stable SOCMessage.toString results
         * for any third-party recorder implementers that use that format.
         *<P>
         * Shows message audience and human-readable but delimited {@link SOCMessage#toString()}.
         *<P>
         * Preceded by {@link #timeElapsedMS} if set, as minutes:seconds.millis + ":":
         * {@code "m:ss.ddd:"}
         *<P>
         * Possible formats for rest of the fields:
         *<UL>
         * <LI> To all players: {@code all:MessageClassName:param=value|param=value|...}
         * <LI> To a single player number: {@code p3:MessageClassName:param=value|param=value|...}
         * <LI> To all but one player: {@code !p3:MessageClassName:param=value|param=value|...}
         * <LI> To all but two players: {@code !p[3, 1]:MessageClassName:param=value|param=value|...}
         * <LI> To an observer: {@code ob:MessageClassName:param=value|param=value|...}
         * <LI> To an undetermined game member: {@code un:MessageClassName:param=value|param=value|...} <BR>
         *      (for {@link SOCServer#PN_REPLY_TO_UNDETERMINED})
         * <LI> From a player client: {@code f3:MessageClassName:param=value|param=value|...}
         * <LI> From an observing client: {@code fo:MessageClassName:param=value|param=value|...}
         *</UL>
         * Non-playing game observers are also sent all messages, except those to a single player.
         *
         * @return Formatted delimited contents of this entry's fields
         */
        @Override
        public String toString()
        {
            return toString(false);
        }

        /**
         * Formats this entry's fields, optionally without {@link #timeElapsedMS} timestamp.
         * See {@link #toString()} for format details.
         * @param untimed  If true, omit the optional {@link #timeElapsedMS} timestamp field if present
         * @return Formatted delimited contents of this entry's fields
         */
        public String toString(final boolean untimed)
        {
            StringBuilder sb = new StringBuilder();

            if (comment != null)
            {
                sb.append('#').append(comment);
                return sb.toString();
            }

            if ((timeElapsedMS >= 0) && ! untimed)
            {
                int msec = timeElapsedMS % 1000, sec = timeElapsedMS / 1000;
                int min = sec / 60;
                sec %= 60;

                sb.append(min).append(':');
                if (sec < 10)
                    sb.append('0');
                sb.append(sec).append('.');
                if (msec < 100)
                    sb.append('0');
                if (msec < 10)
                    sb.append('0');
                sb.append(msec).append(':');
            }

            if (isFromClient)
                sb.append((pn == SOCServer.PN_OBSERVER)
                    ? "fo:"
                    : ("f" + pn + ":"));
            else
                switch(pn)
                {
                case SOCServer.PN_OBSERVER:
                    sb.append("ob:");
                    break;
                case SOCServer.PN_REPLY_TO_UNDETERMINED:
                    sb.append("un:");
                    break;
                case -1:
                    if (excludedPN != null)
                    {
                        if (excludedPN.length == 1)
                            sb.append("!p" + excludedPN[0]);
                        else
                            sb.append("!p" + Arrays.toString(excludedPN));
                        sb.append(':');
                    } else {
                        sb.append("all:");
                    }
                    break;
                default:
                    sb.append("p" + pn + ":");
                }

            sb.append(event);  // or "null"

            return sb.toString();
        }

        /**
         * Try to parse a EventEntry from the format created by {@link #toString()}.
         * @param str Entry string to parse, or "" or null; trim before calling
         * @return Parsed EventEntry, or {@code null} if {@code str} is "" or null
         * @throws ParseException
         */
        public static EventEntry parse(String str)
            throws ParseException
        {
            if ((str == null) || str.isEmpty())
                return null;

            if (str.charAt(0) == '#')
                return new EventEntry(str.substring(1));

            int elapsedMS = -1;
            if (Character.isDigit(str.charAt(0)))
            {
                // try to parse 'm:ss.ddd:' relative timestamp
                int pos = str.indexOf(':');
                if (pos == -1)
                    throw new ParseException("Expected start to be \"digits:\"", 1);
                int min;
                try
                {
                    min = Integer.parseInt(str.substring(0, pos));
                } catch (NumberFormatException e) {
                    throw new ParseException("Couldn't parse minutes", 0);
                }
                int pos2 = str.indexOf('.', pos + 1);
                if (pos2 == -1)
                    throw new ParseException("Missing . after seconds", pos);
                ++pos;
                int sec;
                try
                {
                    sec = Integer.parseInt(str.substring(pos, pos2));
                } catch (NumberFormatException e) {
                    throw new ParseException("Couldn't parse seconds", pos);
                }
                pos = str.indexOf(':', pos2 + 1);
                if (pos != (pos2 + 4))
                    throw new ParseException("msec: Expected 3 digits + :", pos2);
                ++pos2;
                int msec;
                try
                {
                    msec = Integer.parseInt(str.substring(pos2, pos));
                } catch (NumberFormatException e) {
                    throw new ParseException("Couldn't parse msec", pos);
                }

                if ((sec < 0) || (msec < 0))
                    throw new ParseException("Negative time", pos2);
                long elapsed = (1000 * ((60L * min) + sec)) + msec;
                elapsedMS = (elapsed <= Integer.MAX_VALUE) ? ((int) elapsed) : Integer.MAX_VALUE;

                // pos points to ':' after timestamp
                str = str.substring(pos + 1);
                if (str.isEmpty())
                    throw new ParseException("Expected users after timestamp", pos);
            }

            int pos = str.indexOf(':');
            if (pos < 1)
                throw new ParseException("Must start with users + ':'", 0);
            if (pos == str.length() - 1)
                throw new ParseException("Missing event after ':'", pos);

            String users = str.substring(0, pos);
            ++pos;

            SOCMessage event;
            try
            {
                String msg = str.substring(pos);
                event = (msg.equals("null")) ? null : SOCMessage.parseMsgStr(msg);
            } catch (ParseException e) {
                throw new ParseException("Error parsing event: " + e.getMessage(), pos + e.getErrorOffset());
            } catch (InputMismatchException e) {
                throw new ParseException("Can't parse malformed event", pos);
            }

            // parse users, make the entry

            if (users.equals("all"))
                return new EventEntry(event, -1, false, elapsedMS);

            if (users.charAt(0) == 'f')
            {
                final int fromPN;
                if (users.equals("fo"))
                {
                    fromPN = SOCServer.PN_OBSERVER;
                } else {
                    try { fromPN = Integer.parseInt(users.substring(1)); }
                    catch (NumberFormatException e) {
                        throw new ParseException("expected 'f' + number, got " + users, 0);
                    }
                }

                return new EventEntry(event, fromPN, true, elapsedMS);
            }

            if (users.startsWith("!p"))
            {
                final int[] excludedPN;
                char ch = (users.length() > 2) ? users.charAt(2) : (char) 1;

                if (Character.isDigit(ch))
                {
                    final String ps = users.substring(2);
                    final int pn;
                    try { pn = Integer.parseInt(ps); }
                    catch (NumberFormatException e) {
                        throw new ParseException("expected number, got " + ps, 2);
                    }
                    excludedPN = new int[]{ pn };
                } else if (ch == '[') {
                    final int L = users.length();
                    if (users.charAt(L - 1) != ']')
                        throw new ParseException("missing closing ']'", L - 1);
                    final String pnList = users.substring(3, L - 1);
                    if (pnList.isEmpty())
                        throw new ParseException("empty player list []", 2);
                    String[] pss = pnList.split(", ?");
                    excludedPN = new int[pss.length];
                    for (int i = 0; i < pss.length; ++i)
                    {
                        String ps = pss[i];
                        if (ps.isEmpty())
                            throw new ParseException("missing number in player list", 2);
                        try {
                            excludedPN[i] = Integer.parseInt(ps);
                        } catch (NumberFormatException e) {
                            throw new ParseException("can't parse number in player list", 2);
                        }
                    }
                } else {
                    throw new ParseException("Can't parse event user spec preceding ':'", 2);
                }

                return new EventEntry(event, excludedPN, elapsedMS);
            }

            final int toPN;
            if (users.equals("ob"))
                toPN = SOCServer.PN_OBSERVER;
            else if (users.equals("un"))
                toPN = SOCServer.PN_REPLY_TO_UNDETERMINED;
            else if (users.charAt(0) == 'p')
            {
                try { toPN = Integer.parseInt(users.substring(1)); }
                catch (NumberFormatException e) {
                    throw new ParseException("expected 'p' + number, got " + users, 0);
                }
            }
            else
                throw new ParseException("Can't parse event user spec preceding ':'", 0);

            return new EventEntry(event, toPN, false, elapsedMS);
        }

    }

}
