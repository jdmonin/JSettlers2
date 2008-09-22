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


/**
 * This message means that a client isn't allowed to connect
 *
 * @author Robert S Thomas
 */
@SuppressWarnings("serial")
public class SOCRejectConnection extends SOCMessage
{
    /**
     * Text message
     */
    private String text;

    /**
     * Create a RejectConnection message.
     *
     * @param message   the text message
     */
    public SOCRejectConnection(String message)
    {
        messageType = REJECTCONNECTION;
        text = message;
    }

    /**
     * @return the text message
     */
    public String getText()
    {
        return text;
    }

    /**
     * <REJECTCONNECTION>
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(text);
    }

    /**
     * TEXTMSG sep text
     *
     * @param tm  the text message
     * @return    the command string
     */
    public static String toCmd(String tm)
    {
        return REJECTCONNECTION + sep + tm;
    }

    /**
     * Parse the command String into a RejectConnection message
     *
     * @param s   the String to parse
     * @return    a RejectConnection message
     */
    public static SOCRejectConnection parseDataStr(String s)
    {
        return new SOCRejectConnection(s);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCRejectConnection:" + text;
    }
}
