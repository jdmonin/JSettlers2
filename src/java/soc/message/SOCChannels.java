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
package soc.message;

import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.log4j.Logger;


/**
 * This message lists all the chat channels on a server
 *
 * @author Robert S Thomas
 */
@SuppressWarnings("serial")
public class SOCChannels extends SOCMessage
{
	/** static method debug logging */
    private static Logger staticLog = Logger.getLogger("soc.message.SOCChannels");
    
    /**
     * List of channels
     */
    private Vector<String> channels;

    /**
     * Create a Channels Message.
     *
     * @param cl  list of channels
     */
    public SOCChannels(Vector<String> cl)
    {
        messageType = CHANNELS;
        channels = cl;
    }

    /**
     * @return the list of channels
     */
    public Vector<String> getChannels()
    {
        return channels;
    }

    /**
     * CHANNELS sep channels
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(channels);
    }

    /**
     * CHANNELS sep channels
     *
     * @param cl  the list of channels
     * @return    the command string
     */
    public static String toCmd(Vector<String> cl)
    {
        String cmd = CHANNELS + sep;

        try
        {
            Enumeration<String> clEnum = cl.elements();
            cmd += clEnum.nextElement();

            while (clEnum.hasMoreElements())
            {
                cmd += (sep2 + clEnum.nextElement());
            }
        }
        catch (Exception e) {}

        return cmd;
    }

    /**
     * Parse the command String into a Channels message
     *
     * @param s   the String to parse
     * @return    a Channels message, or null of the data is garbled
     */
    public static SOCChannels parseDataStr(String s)
    {
        Vector<String> cl = new Vector<String>();
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            while (st.hasMoreTokens())
            {
                cl.addElement(st.nextToken());
            }
        }
        catch (Exception e)
        {
        	staticLog.error("SOCChannels parseDataStr ERROR - " + e);

            return null;
        }

        return new SOCChannels((Vector<String>) cl);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCChannels:channels=";

        try
        {
            Enumeration<String> clEnum = channels.elements();
            s += clEnum.nextElement();

            while (clEnum.hasMoreElements())
            {
                s += ("," + clEnum.nextElement());
            }
        }
        catch (Exception e) {}

        return s;
    }
}
