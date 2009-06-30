/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
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
 * A class for tracking the chat channels
 *
 * @author Robert S. Thomas
 */
public class SOCChannelList
{
    protected Hashtable channelMutexes;
    protected Hashtable channelMembers;
    protected boolean inUse;

    /**
     * constructor
     */
    public SOCChannelList()
    {
        channelMutexes = new Hashtable();
        channelMembers = new Hashtable();
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
     * @return an enumeration of channel names
     */
    public Enumeration getChannels()
    {
        return channelMembers.keys();
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
     * create a new channel
     *
     * @param chName  the name of the channel
     */
    public synchronized void createChannel(String chName)
    {
        if (!isChannel(chName))
        {
            MutexFlag mutex = new MutexFlag();
            channelMutexes.put(chName, mutex);

            Vector members = new Vector();
            channelMembers.put(chName, members);
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
