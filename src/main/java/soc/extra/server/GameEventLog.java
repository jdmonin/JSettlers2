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
 * Log entries are {@link QueueEntry}.
 *<P>
 * These logs and their entry format are used by various tests and {@link RecordingSOCServer},
 * but aren't used by the standard SOCServer.
 *<P>
 * Use {@link #load(File, boolean, boolean)} to parse from a {@code .soclog} file,
 * creating a new {@code GameEventLog} with {@link #entries}, {@link #gameName}, {@link #version},
 * {@link #optsStr}, and {@link #numLines} from the parsed entries.
 *
 *<H3>Log file format</H3>
 *
 * {@link #save(SOCGame, File, String, boolean)} saves files in this format:
 *<UL>
 *  <LI> File starts with this header line:<BR>
 *    <tt>SOC game event log: version=2500, created_at=</tt>timestamp<tt>, now=</tt>timestamp<tt>, game_name=</tt>game name <BR>
 *    <UL>
 *      <LI> <tt>version</tt> is the current {@link Version#versionNumber()}, always first in this list of properties
 *      <LI> Header timestamps use unix epoch time in seconds: {@link System#currentTimeMillis()} / 1000
 *      <LI> Other field may be added in the future; {@code version} will always be first in the list
 *      <LI> Game name must pass {@link SOCMessage#isSingleLineAndSafe(String)}
 *      <LI> Although game names can't include commas, {@code game_name} will always be last for readability
 *    </UL>
 *  <LI> For convenience, {@code save(..)} then writes comments with those timestamps
 *    in human-readable local time with RFC 822 timezone:
 *    "2001-06-09 13:30:23 -0400". These comments aren't part of the format spec.
 *  <LI> Each log entry is on its own line, formatted by {@link QueueEntry#toString()}
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
     * @see #save(SOCGame, File, String, boolean)
     */
    public static final String FILENAME_EXTENSION = ".soclog";

    /**
     * Version number (2.5.00) where this log format type was introduced.
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
    public final Vector<QueueEntry> entries = new Vector<>();

    /**
     * Name of game seen in a log that's been loaded by {@link #load(File, boolean, boolean)}. Otherwise {@code null}.
     */
    public String gameName;

    /**
     * The game data version seen in a log that's been loaded by {@link #load(File, boolean, boolean)},
     * in same format as {@link Version#versionNumber()}. Otherwise 0.
     */
    public int version;

    /**
     * The game options, or {@code null} if none, seen in a log that's been loaded
     * by {@link #load(File, boolean, boolean)}, in same format as {@link SOCNewGameWithOptions#getOptionsString()}.
     * Otherwise {@code null}.
     */
    public String optsStr;

    /**
     * Number of lines read by {@link #load(File, boolean, boolean)}, including blank and comment lines. Otherwise 0.
     */
    public int numLines;

    /**
     * Add an entry to this game's log {@link #entries}.
     * @param e  Entry to add; not null
     */
    public void add(QueueEntry e)
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
     * Save this game's current event message logs to a file.
     * Overwrites file if it already exists.
     *
     * @param ga  This log's game, to get its basic info; not null
     * @param saveDir  Existing directory into which to save the file
     * @param saveFilename  Filename to save to; not validated for format or security.
     *     Recommended suffix is {@link #FILENAME_EXTENSION} for consistency.
     * @param serverOnly  If true, don't write entries where {@link GameEventLog.QueueEntry#isFromClient} true
     * @throws IllegalStateException  if {@link #entries} doesn't start with {@link SOCVersion}
     *     followed by {@link SOCNewGame} or {@link SOCNewGameWithOptions} where
     *     {@link SOCNewGame#getGame()} equals {@link SOCGame#getName() ga.getName()}
     * @throws IllegalArgumentException  if {@code saveDir} isn't a currently existing directory
     * @throws IOException if an I/O problem or {@link SecurityException} occurs
     * @see #load(File, boolean, boolean)
     */
    public void save(final SOCGame ga, final File saveDir, final String saveFilename, final boolean serverOnly)
        throws IllegalStateException, IllegalArgumentException, IOException
    {
        // check for required start-of-log entry messages
        {
            boolean allOK = false;
            if (entries.size() >= 2)
            {
                SOCMessage msg = entries.get(0).event;
                if (! (msg instanceof SOCVersion))
                    throw new IllegalStateException("First entry must be SOCVersion");

                msg = entries.get(1).event;
                String gaName = null;
                if (msg instanceof SOCNewGame)
                    gaName = ((SOCNewGame) msg).getGame();
                else if (msg instanceof SOCNewGameWithOptions)
                    gaName = ((SOCNewGameWithOptions) msg).getGame();

                if (gaName != null)
                {
                    if (! gaName.equals(ga.getName()))
                        throw new IllegalStateException("Game name mismatch: event message has " + gaName);
                    else
                        allOK = true;
                }
            }

            if (! allOK)
                throw new IllegalStateException("First entries must be SOCVersion, SOCNewGame[WithOptions]");
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
            final Date createdAt = ga.getStartTime(), now = new Date();
            final String createdStr, nowStr;
            synchronized (TIMESTAMP_SDF)
            {
                createdStr = TIMESTAMP_SDF.format(createdAt);
                nowStr = TIMESTAMP_SDF.format(now);
            }

            writer.append
                ("SOC game event log: version=" + Version.versionNumber()
                 + ", created_at=" + (createdAt.getTime() / 1000)
                 + ", now=" + (now.getTime() / 1000)
                 + ", game_name=" + ga.getName() + '\n');
            writer.append("# Game created at: " + createdStr + '\n');
            writer.append("# Log written at:  " + nowStr + '\n');

            for (GameEventLog.QueueEntry entry : entries)
                if (! (entry.isFromClient && serverOnly))
                    writer.append(entry.toString()).append('\n');

            writer.append("# End of log; final game state is ")
                .append(Integer.toString(ga.getGameState())).append('\n');
            writer.append("# Final player info:\n");
            final SOCPlayer winner = ga.getPlayerWithWin();  // null if still playing
            for (int pn = 0; pn < ga.maxPlayers; ++pn)
            {
                final SOCPlayer pl = ga.getPlayer(pn);
                int vp = (winner != null) ? pl.getTotalVP() : pl.getPublicVP();
                if ((vp == 0) && ga.isSeatVacant(pn))
                    continue;

                String plName = pl.getName();
                if (plName == null)
                    plName = "(vacant)";
                writer.append
                    ("# - pn " + pn + ": visible score " + vp + ": " + plName);
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
     * Load and parse a log from a file in the format saved by {@link #save(SOCGame, File, String, boolean)}.
     * Adds its entries to {@link #entries}. Sets {@link #gameName}, {@link #version}, {@link #optsStr},
     * and {@link #numLines}.
     *
     * @param loadFrom  File to load from; filename usually ends with {@link #FILENAME_EXTENSION}
     * @param ignoreComments  If true, ignore comment lines instead of calling
     *     {@link QueueEntry#QueueEntry(String) new QueueEntry(String)} constructor
     * @param serverOnly  If true, ignore log entries which have {@link QueueEntry#isFromClient} set true
     * @throws IOException  if a problem occurs while loading
     * @throws ParseException  if a log line can't be parsed. Exception text will start with line number: "Line 5: ...".
     *     {@link ParseException#getErrorOffset()} will be the offset within that trimmed line,
     *     not within the overall file.
     *     Also thrown if game name or version in logged {@link SOCVersion} or {@link SOCNewGame}
     *     differs from that in header, or game name doesn't pass
     *     {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @throws NoSuchElementException if log file doesn't start with the required header line
     *     {@code "SOC game event log: version=..."}, or its first 2 event messages aren't the required
     *     {@link SOCVersion} and either {@link SOCNewGame} or {@link SOCNewGameWithOptions},
     *     or if header's {@code version} number &lt; 2500 ({@link #MIN_VERSION})
     */
    public static GameEventLog load
        (final File loadFrom, final boolean ignoreComments, final boolean serverOnly)
        throws IOException, ParseException, NoSuchElementException
    {
        final GameEventLog ret = new GameEventLog();
        final Vector<QueueEntry> entries = ret.entries;
        boolean sawVers = false, sawNewGame = false;  // required first messages

        try
            (final FileInputStream fis = new FileInputStream(loadFrom);
             final BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8")); )
        {
            String line = reader.readLine();
            if ((line == null) || ! line.startsWith("SOC game event log: version="))
                throw new NoSuchElementException("File must start with \"SOC game event log\" header line");

            // parse version, game name from header fields
            for (String field : line.substring(20).split(", "))
            {
                if (field.startsWith("version="))
                {
                    int vers;
                    try
                    {
                         vers = Integer.parseInt(field.substring(8));
                    } catch (NumberFormatException e) {
                        throw new ParseException("Couldn't parse version number in header", 21 + 8);
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
                throw new ParseException("Header missing required version field", 20);
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

                    QueueEntry qe;
                    try
                    {
                        qe = QueueEntry.parse(line);
                    } catch (ParseException e) {
                        throw new ParseException
                            ("Line " + lnum + ": " + e.getMessage() + "\n  for line: " + line, e.getErrorOffset());
                    }
                    if (qe == null)
                        continue;
                    if (ignoreComments && (qe.comment != null))
                        continue;

                    if ((! sawVers) && (qe.event instanceof SOCVersion))
                    {
                        int vers = ((SOCVersion) qe.event).getVersionNumber();
                        if (vers != ret.version)
                            throw new ParseException
                                ("Line " + lnum + ": Version " + vers
                                 + " in SOCVersion differs from header's " + ret.version, 1);

                        sawVers = true;
                    }
                    else if ((! sawNewGame) && (qe.event != null))
                    {
                        if (qe.event instanceof SOCNewGameWithOptions)
                        {
                            String gameName = ((SOCNewGameWithOptions) qe.event).getGame();
                            if (! ret.gameName.equals(gameName))
                                throw new ParseException("Line " + lnum + ": Game name differs from header", 1);

                            String ostr = ((SOCNewGameWithOptions) qe.event).getOptionsString();
                            if ((ostr != null) && (ostr.charAt(0) == ','))
                                ostr = ostr.substring(1);
                            ret.optsStr = ostr;
                        } else if (qe.event instanceof SOCNewGame) {
                            String gameName = ((SOCNewGame) qe.event).getGame();
                            if (! ret.gameName.equals(gameName))
                                throw new ParseException("Line " + lnum + ": Game name differs from header", 1);
                        } else {
                            throw new NoSuchElementException
                                ("Second event message must be SOCNewGame or SOCNewGameWithOptions");
                        }

                        sawNewGame = true;
                    }

                    if (serverOnly && qe.isFromClient)
                        continue;

                    entries.add(qe);
                }

                ret.numLines = lnum - 1;
            }
        }

        return ret;
    }

    /**
     * A recorded entry: Event {@link SOCMessage}, its audience
     * (all players, 1 player, or specifically excluded player(s)),
     * or source if from client. See {@link #toString()} for human-readable delimited format.
     *<P>
     * If this class changes, update comprehensive unit test {@link soctest.server.TestGameEventLog#testQueueEntry()}.
     */
    public static final class QueueEntry
    {
        /**
         * Event message data; can be {@code null} if ! {@link #isFromClient}.
         * If {@link #isFromClient}, can be cast to {@link SOCMessageForGame}.
         */
        public final SOCMessage event;

        /**
         * If {@link #isFromClient}, the player number this event was sent from, or {@link SOCServer#PN_OBSERVER}.
         * If from server, the player number this event was sent to, or -1 if to all;
         * is also -1 if {@link #excludedPN} != null.
         * Can also be {@link SOCServer#PN_OBSERVER} or {@link SOCServer#PN_REPLY_TO_UNDETERMINED}.
         */
        public final int pn;

        /** If from server, the player numbers specifically excluded from this event's audience, or null */
        public final int[] excludedPN;

        /**
         * True if this message is from a game member client instead of from server.
         */
        public final boolean isFromClient;

        /**
         * Contents of a comment line, starting just after {@code '#'} including whitespace, or {@code null}.
         */
        public final String comment;

        // Reminder: if you add fields, update toString(), equals(), and unit tests.

        /**
         * QueueEntry sent to one player or observer, or all players if {@code pn} is -1,
         * or from a player or observer.
         *
         * @param event  Event message to record.
         *     If {@code isFromClient}, must be a {@link SOCMessageForGame} and not {@code null}
         * @param pn  From server, the player number sent to, or -1 if to all players.
         *     Can also be {@link SOCServer#PN_OBSERVER} or {@link SOCServer#PN_REPLY_TO_UNDETERMINED}.
         *     From client, the player number sent from, or {@link SOCServer#PN_OBSERVER} (not -1).
         * @param isFromClient  True if is from client instead of from server to member client(s)
         * @throws IllegalArgumentException if {@code isFromClient} but {@code event} is {@code null}
         *     or in't a {@link SOCMessageForGame}
         * @see #QueueEntry(SOCMessage, int[])
         * @see #QueueEntry(String)
         */
        public QueueEntry(SOCMessage event, int pn, boolean isFromClient)
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
            this.comment = null;
        }

        /**
         * QueueEntry sent to all players except specific ones, or to all if {@code excludedPN} null.
         * @param event  Event message to record
         * @param excludedPN  Player number(s) excluded, or all players if null
         * @see #QueueEntry(SOCMessage, int, boolean)
         * @see #QueueEntry(String)
         */
        public QueueEntry(SOCMessage event, int[] excludedPN)
        {
            this.event = event;
            this.pn = -1;
            this.excludedPN = excludedPN;
            this.isFromClient = false;
            this.comment = null;
        }

        /**
         * QueueEntry representing a comment when log is read from file.
         * Other fields will be set to java default values, except {@link #pn} = -1.
         * @param comment  Contents of comment, not {@code null}; see {@link #comment}.
         * @throws IllegalArgumentException if {@code comment} null
         * @see #QueueEntry(SOCMessage, int, boolean)
         * @see #QueueEntry(SOCMessage, int[])
         */
        public QueueEntry(String comment)
            throws IllegalArgumentException
        {
            if (comment == null)
                throw (new IllegalArgumentException("comment"));

            this.comment = comment;
            this.event = null;
            this.pn = -1;
            this.excludedPN = null;
            this.isFromClient = false;
        }

        /**
         * Basic QueueEntry equality test.
         * This implementation is slow, and calls {@link #event}{@link SOCMessage#toString() .toString()}
         * on both objects. It's useful for testing but shouldn't be used as-is in production code.
         * Also, the class doesn't override {@code hashCode()}.
         * @param o  Object to compare, or {@code null}
         * @return  True if {@code o} is a {@code QueueEntry} whose fields all have the same values as this object.
         *     Uses {@link Arrays#equals(int[], int[])} for {@link #excludedPN},
         *     {@link String#equals(Object)} for {@link #comment}.
         */
        @Override
        public boolean equals(Object o)
        {
            if (o == this)
                return true;
            if (! (o instanceof QueueEntry))
                return false;

            final QueueEntry oe = (QueueEntry) o;

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
         * Basic delimited contents, suitable for comparisons in unit tests.
         * Calls {@link SOCMessage#toString()}, not {@link SOCMessage#toCmd()},
         * for class/field name label strings and to help test stable SOCMessage.toString results
         * for any third-party recorder implementers that use that format.
         *<P>
         * Shows message audience and human-readable but delimited {@link SOCMessage#toString()}.
         * Possible formats:
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
         */
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            if (comment != null)
            {
                sb.append('#').append(comment);
                return sb.toString();
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
         * Try to parse a QueueEntry from the format created by {@link #toString()}.
         * @param str Entry string to parse, or "" or null; trim before calling
         * @return Parsed QueueEntry, or {@code null} if {@code str} is "" or null
         * @throws ParseException
         */
        public static QueueEntry parse(final String str)
            throws ParseException
        {
            if ((str == null) || str.isEmpty())
                return null;

            if (str.charAt(0) == '#')
                return new QueueEntry(str.substring(1));

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
                return new QueueEntry(event, -1, false);

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

                return new QueueEntry(event, fromPN, true);
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

                return new QueueEntry(event, excludedPN);
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

            return new QueueEntry(event, toPN, false);
        }

    }

}
