/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009-2010 Jeremy D Monin <jeremy@nand.net>
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.server;

import soc.disableDebug.D;

import soc.server.genericServer.StringConnection;

import soc.util.MutexFlag;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;


/**
 * A class for tracking the chat channels.
 * The list itself, and each channel, has a monitor for synchronization.
 *
 * @author Robert S. Thomas
 */
public class SOCChannelList
{
    /** key = string, value = Vector of MutexFlags */
    protected Hashtable channelMutexes;

    /** key = string, value = Vector of StringConnections */
    protected Hashtable channelMembers;

    /** Each channel's creator/owner name.
     * key = string, value = Vector of Strings.
     * @since 1.1.10
     */
    protected Hashtable channelOwners;

    /** track the monitor for this channel list */
    protected boolean inUse;

    /**
     * constructor
     */
    public SOCChannelList()
    {
        channelMutexes = new Hashtable();
        channelMembers = new Hashtable();
        channelOwners = new Hashtable();
        inUse = false;
    }

    /**
     * take the monitor for this channel list
     */
    public synchronized void takeMonitor()
    {
        D.ebugPrintln("SOCChannelList : TAKE MONITOR");

        while (inUse)
        {
            try
            {
                wait(1000);
            }
            catch (InterruptedException e)
            {
                System.out.println("EXCEPTION IN takeMonitor() -- " + e);
            }
        }

        inUse = true;
    }

    /**
     * release the monitor for this channel list
     */
    public synchronized void releaseMonitor()
    {
        D.ebugPrintln("SOCChannelList : RELEASE MONITOR");
        inUse = false;
        this.notify();
    }

    /**
     * take the monitor for this channel
     *
     * @param channel  the name of the channel
     * @return false   if the channel has no mutex
     */
    public boolean takeMonitorForChannel(String channel)
    {
        D.ebugPrintln("SOCChannelList : TAKE MONITOR FOR " + channel);

        MutexFlag mutex = (MutexFlag) channelMutexes.get(channel);

        if (mutex == null)
        {
            return false;
        }

        boolean done = false;

        while (!done)
        {
            mutex = (MutexFlag) channelMutexes.get(channel);

            if (mutex == null)
            {
                return false;
            }

            synchronized (mutex)
            {
                if (mutex.getState() == true)
                {
                    try
                    {
                        mutex.wait(1000);
                    }
                    catch (InterruptedException e)
                    {
                        System.out.println("EXCEPTION IN takeMonitor() -- " + e);
                    }
                }
                else
                {
                    done = true;
                }
            }
        }

        mutex.setState(true);

        return true;
    }

    /**
     * release the monitor for this channel
     *
     * @param channel  the name of the channel
     * @return false if the channel has no mutex
     */
    public boolean releaseMonitorForChannel(String channel)
    {
        D.ebugPrintln("SOCChannelList : RELEASE MONITOR FOR " + channel);

        MutexFlag mutex = (MutexFlag) channelMutexes.get(channel);

        if (mutex == null)
        {
            return false;
        }

        synchronized (mutex)
        {
            mutex.setState(false);
            mutex.notify();
        }

        return true;
    }

    /**
     * @return an enumeration of channel names (Strings)
     */
    public Enumeration getChannels()
    {
        return channelMembers.keys();
    }

    /**
     * Get a channel's owner name
     * @param chName  channel name
     * @return  owner's name, or null if <tt>chName</tt> isn't a channel
     * @since 1.1.10
     */
    public synchronized String getOwner(final String chName)
    {
        return (String) channelOwners.get(chName);
    }

    /**
     * @param   chName  channel name
     * @return  list of members
     */
    public synchronized Vector getMembers(String chName)
    {
        Vector result = (Vector) channelMembers.get(chName);

        if (result == null)
        {
            result = new Vector();
        }

        return result;
    }

    /**
     * @param  chName   the name of the channel
     * @param  conn     the member's connection
     * @return  true if memName is a member of the channel
     */
    public synchronized boolean isMember(StringConnection conn, String chName)
    {
        Vector members = getMembers(chName);

        if ((members != null) && (members.contains(conn)))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * add a member to the chat channel
     *
     * @param  chName   the name of the channel
     * @param  conn     the member's connection
     */
    public synchronized void addMember(StringConnection conn, String chName)
    {
        Vector members = getMembers(chName);

        if ((members != null) && (!members.contains(conn)))
        {
            members.addElement(conn);
        }
    }

    /**
     * remove member from the chat channel
     *
     * @param  chName   the name of the channel
     * @param  conn     the member's connection
     */
    public synchronized void removeMember(StringConnection conn, String chName)
    {
        Vector members = getMembers(chName);

        if ((members != null))
        {
            members.removeElement(conn);
        }
    }

    /**
     * Replace member from all chat channels, with a new connection (after a network problem).
     *
     * @param  oldConn  the member's old connection
     * @param  oldConn  the member's new connection
     * @since 1.1.08
     */
    public synchronized void replaceMemberAllChannels(StringConnection oldConn, StringConnection newConn)
    {
        Enumeration allCh = getChannels();
        while (allCh.hasMoreElements())
        {
            Vector members = (Vector) channelMembers.get((String) allCh.nextElement());
            if ((members != null) && members.contains(oldConn))
            {
                members.remove(oldConn);
                members.addElement(newConn);
            }
        }
    }

    /**
     * @param   chName  the name of the channel
     * @return true if the channel exists
     */
    public boolean isChannel(String chName)
    {
        return (channelMembers.get(chName) != null);
    }

    /**
     * @param   chName  the name of the channel
     * @return true if the channel exists and has an empty member list
     */
    public synchronized boolean isChannelEmpty(String chName)
    {
        boolean result;
        Vector members;

        members = (Vector) channelMembers.get(chName);

        if ((members != null) && (members.isEmpty()))
        {
            result = true;
        }
        else
        {
            result = false;
        }

        return result;
    }

    /**
     * create a new channel.  If channel already exists, do nothing.
     *
     * @param chName  the name of the channel
     * @param chOwner the game owner/creator's player name (added in 1.1.10)
     * @throws NullPointerException if <tt>chOwner</tt> null
     */
    public synchronized void createChannel(final String chName, final String chOwner)
        throws NullPointerException
    {
        if (!isChannel(chName))
        {
            MutexFlag mutex = new MutexFlag();
            channelMutexes.put(chName, mutex);

            Vector members = new Vector();
            channelMembers.put(chName, members);

            channelOwners.put(chName, chOwner);  // throws NullPointerException
        }
    }

    /**
     * remove the channel from the list
     *
     * @param chName  the name of the channel
     */
    public synchronized void deleteChannel(String chName)
    {
        D.ebugPrintln("SOCChannelList : deleteChannel(" + chName + ")");
        channelMembers.remove(chName);

        MutexFlag mutex = (MutexFlag) channelMutexes.get(chName);
        channelMutexes.remove(chName);

        if (mutex != null)
        {
            synchronized (mutex)
            {
                mutex.notifyAll();
            }
        }
    }
}
