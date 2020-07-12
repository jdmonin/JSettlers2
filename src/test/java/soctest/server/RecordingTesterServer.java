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
import soc.server.SOCServer;
import soc.server.savegame.SavedGameModel;

/**
 * Non-testing class: Server which records game events into {@link #records}.
 * Works with {@link DisplaylessTesterClient}.
 *
 * @since 2.4.10
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

        if (SavedGameModel.glas == null)
            SavedGameModel.glas = gameList;
    }

    @Override
    public boolean recordGameEventsIsActive()
    {
        return true;
    }

    private void recordEvent(final String gameName, QueueEntry entry)
    {
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

    public static class QueueEntry
    {
        /** Event message data */
        public final SOCMessage event;

        /** Which player number this event was sent to, or -1 for all */
        public final int toPN;

        /** Player numbers excluded from this event's audience, or null */
        public final int[] excludedPN;

        public QueueEntry(SOCMessage event, int toPN)
        {
            this.event = event;
            this.toPN = toPN;
            this.excludedPN = null;
        }

        public QueueEntry(SOCMessage event, int[] excludedPN)
        {
            this.event = event;
            this.toPN = -1;
            this.excludedPN = excludedPN;
        }

        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            if (toPN != -1)
                sb.append("pn=" + toPN + ":");
            else if (excludedPN != null)
            {
                if (excludedPN.length == 1)
                    sb.append("pn=!" + excludedPN[0]);
                else
                    sb.append("pn=!" + Arrays.toString(excludedPN));
                sb.append(':');
            }
            else
                sb.append("all:");

            sb.append(event);

            return sb.toString();
        }
    }

}
