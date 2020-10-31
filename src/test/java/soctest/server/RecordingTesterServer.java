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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import java.util.Vector;

import soc.message.SOCMessage;
import soc.message.SOCServerPing;
import soc.server.SOCServer;

/**
 * Non-testing class: Server which records game events into {@link #records}
 * having a human-readable delimited format, suitable for comparisons in unit tests:
 * see {@link QueueEntry#toString()}.
 *<P>
 * Works with {@link DisplaylessTesterClient}.
 *
 * @since 2.4.50
 */
@SuppressWarnings("serial")
public class RecordingTesterServer
    extends SOCServer
{
    public static final String STRINGPORT_NAME = "testport";

    /** per-game queues of recorded game "event" messages */
    public final HashMap<String, Vector<QueueEntry>> records = new HashMap<>();

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
     * <LI> {@link SOCServer#PROP_JSETTLERS_STARTROBOTS} = 5 ({@link #NUM_STARTROBOTS}
     *</UL>
     */
    public static final Properties PROPS = new Properties();
    static
    {
        PROPS.setProperty(SOCServer.PROP_JSETTLERS_ALLOW_DEBUG, "Y");
        PROPS.setProperty(SOCServer.PROP_JSETTLERS_BOTS_COOKIE, "tcook");
        PROPS.setProperty(SOCServer.PROP_JSETTLERS_CONNECTIONS, "99");
        PROPS.setProperty(SOCServer.PROP_JSETTLERS_STARTROBOTS, Integer.toString(NUM_STARTROBOTS));
    }

    /**
     * To add or change server properties, update {@link #PROPS} before calling this constructor.
     */
    public RecordingTesterServer()
        throws IllegalStateException
    {
        super(STRINGPORT_NAME, PROPS);
    }

    @Override
    public boolean recordGameEventsIsActive()
    {
        return true;
    }

    private void recordEvent(final String gameName, QueueEntry entry)
    {
        if (entry.event instanceof SOCServerPing)
            // ignore unrelated administrative message which has unpredictable timing
            return;

        Vector<QueueEntry> queue = records.get(gameName);
        if (queue == null)
        {
            queue = new Vector<QueueEntry>();
            records.put(gameName, queue);
        }

        queue.add(entry);
    }

    @Override
    public void recordGameEvent(final String gameName, SOCMessage event)
    {
        recordEvent(gameName, new QueueEntry(event, -1));
    }

    @Override
    public void recordGameEventTo(final String gameName, final int pn, SOCMessage event)
    {
        recordEvent(gameName, new QueueEntry(event, pn));

    }

    @Override
    public void recordGameEventNotTo(final String gameName, final int excludedPN, SOCMessage event)
    {
        recordEvent(gameName, new QueueEntry(event, new int[]{excludedPN}));
    }

    @Override
    public void recordGameEventNotTo(final String gameName, final int[] excludedPN, SOCMessage event)
    {
        recordEvent(gameName, new QueueEntry(event, excludedPN));
    }

    /**
     * A recorded entry: Event SOCMessage, audience (all players, 1 player, or specifically excluded player(s)).
     * See {@link #toString()} for human-readable delimited format.
     *<P>
     * If this class changes, update comprehensive unit test {@link soctest.server.TestRecorder#testQueueEntry()}.
     */
    public static final class QueueEntry
    {
        /** Event message data */
        public final SOCMessage event;

        /** Which player number this event was sent to, or -1 for all; is also -1 if {@link #excludedPN} != null */
        public final int toPN;

        /** Player numbers specifically excluded from this event's audience, or null */
        public final int[] excludedPN;

        /** QueueEntry sent to one player, or all players if {@code toPN} is -1 */
        public QueueEntry(SOCMessage event, int toPN)
        {
            this.event = event;
            this.toPN = toPN;
            this.excludedPN = null;
        }

        /** QueueEntry sent to all players except specific ones, or to all if {@code excludedPN} null */
        public QueueEntry(SOCMessage event, int[] excludedPN)
        {
            this.event = event;
            this.toPN = -1;
            this.excludedPN = excludedPN;
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
         *</UL>
         * Non-playing game observers are also sent all messages, except those to a single player.
         */
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            if (toPN != -1)
                sb.append("p" + toPN + ":");
            else if (excludedPN != null)
            {
                if (excludedPN.length == 1)
                    sb.append("!p" + excludedPN[0]);
                else
                    sb.append("!p" + Arrays.toString(excludedPN));
                sb.append(':');
            }
            else
                sb.append("all:");

            sb.append(event);  // or "null"

            return sb.toString();
        }
    }

}
