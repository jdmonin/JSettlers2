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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Vector;

import soc.game.SOCGame;
import soc.message.SOCMessage;
import soc.message.SOCMessageForGame;
import soc.message.SOCVersion;
import soc.server.SOCServer;
import soc.util.Version;

/**
 * A Game Event Log in memory or saved to/loaded from a file.
 * Log entries are {@link QueueEntry}.
 *<P>
 * These logs and their entry format are used by various tests and {@link RecordingSOCServer},
 * but aren't used by the standard SOCServer.
 *
 *<H3>Log file format</H3>
 *
 * {@link #saveToFile(SOCGame, File, String)} saves files in this format:
 *<UL>
 *  <LI> File starts with this header line:<BR>
 *    <tt>SOC game event log: version=2500, created_at=</tt>timestamp<tt>, now=</tt>timestamp<tt>, game_name=</tt>game name <BR>
 *    <UL>
 *      <LI> <tt>version</tt> is the current {@link Version#versionNumber()}
 *      <LI> Header timestamps use unix epoch time in seconds: {@link System#currentTimeMillis()} / 1000
 *      <LI> Other field may be added in the future; {@code version} will always be first in the list
 *      <LI> Although game names can't include commas, {@code game_name} will always be last for readability
 *    </UL>
 *  <LI> For convenience, {@code saveToFile(..)} then writes comments with those timestamps
 *    in human-readable local time with RFC 822 timezone:
 *    "2001-06-09 13:30:23 -0400". These comments aren't part of the format spec.
 *  <LI> Each log entry is on its own line, formatted by {@link QueueEntry#toString()}
 *  <LI> First log entry: The server's {@link SOCVersion} as sent to clients, for info on active features
 *  <LI> Next log entry: {@link SOCNewGameWithOptions} with the game's options; may be {@link SOCNewGame} if no gameopts
 *  <LI> Rest of the log entries are all game events sent from the server to players and/or observers
 *  <LI> Comment lines start with {@code #} after optional leading whitespace
 *  <LI> Blank lines are allowed
 *  <LI> For convenience, {@code saveToFile(..)} ends the log with a comment with the game's current gameState
 *</UL>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.5.00
 */
public class GameEventLog
{
    /**
     * Standard suffix/extension for {@link GameEventLog} files: {@code ".soclog"}
     * @see #saveToFile(SOCGame, File, String, boolean)
     */
    public static final String LOG_FILENAME_EXTENSION = ".soclog";

    /**
     * Timestamp format for log comments, local time with RFC 822 timezone offset:
     * "2001-06-09 13:30:23 -0400".
     * Remember to synchronize calls to this formatter.
     */
    private static final SimpleDateFormat TIMESTAMP_SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    // TODO jdoc; public for easy access, since final & Vector is thread-safe
    public final Vector<QueueEntry> entries = new Vector<>();

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
     *   Recommended suffix is {@link #LOG_FILENAME_EXTENSION} for consistency.
     * @param serverOnly  If true, don't write entries where {@link GameEventLog.QueueEntry#isFromClient} true
     * @throws IllegalArgumentException  if {@code saveDir} isn't a currently existing directory
     * @throws IOException if an I/O problem or {@link SecurityException} occurs
     */
    public void saveToFile(final SOCGame ga, final File saveDir, final String saveFilename, final boolean serverOnly)
        throws IllegalArgumentException, IOException
    {
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
            writer.flush();
        }
        catch (SecurityException e) {
            throw new IOException("Can't write to " + saveFilename, e);
        }
    }

    /**
     * A recorded entry: Event SOCMessage, its audience (all players, 1 player, or specifically excluded player(s)),
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
         * QueueEntry sent to one player or observer, or all players if {@code pn} is -1,
         * or from a player or observer.
         *
         * @param event  Event message to record.
         *     If {@code isFromClient}, must be a {@link SOCMessageForGame} and not {@code null}
         * @param pn  From server, the player number sent to, or -1 if to all players if -1.
         *     Can also be {@link SOCServer#PN_OBSERVER} or {@link SOCServer#PN_REPLY_TO_UNDETERMINED}.
         *     From client, the player number sent from, or {@link SOCServer#PN_OBSERVER} (not -1).
         * @param isFromClient  True if is from client instead of from server to member client(s)
         * @throws IllegalArgumentException if {@code isFromClient} but {@code event} is {@code null}
         *     or in't a {@link SOCMessageForGame}
         * @see #QueueEntry(SOCMessage, int[])
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
        }

        /**
         * QueueEntry sent to all players except specific ones, or to all if {@code excludedPN} null.
         * @param event  Event message to record
         * @param excludedPN  Player number(s) excluded, or all players if null
         * @see #GameEventLog(SOCMessage, int, boolean)
         */
        public QueueEntry(SOCMessage event, int[] excludedPN)
        {
            this.event = event;
            this.pn = -1;
            this.excludedPN = excludedPN;
            this.isFromClient = false;
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
         *     (for {@link SOCServer#PN_REPLY_TO_UNDETERMINED})
         * <LI> From a player client: {@code f3:MessageClassName:param=value|param=value|...}
         * <LI> From an observing client: {@code fo:MessageClassName:param=value|param=value|...}
         *</UL>
         * Non-playing game observers are also sent all messages, except those to a single player.
         */
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();

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
    }

}
