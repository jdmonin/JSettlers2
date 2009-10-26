/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGameOption;


/**
 * This message from client sends a list of game options to the server.
 * The server will respond with {@link SOCGameOptionInfo GAMEOPTIONINFO} message(s),
 * one per option keyname listed in this message.
 * If the only 'option' keyname sent is '-', server will send info on all
 * options which are new or changed since the client's version. (this usage
 * assumes client is older than server).
 *<P>
 * This is so clients can find out about options which were
 * introduced in versions newer than the client's version, but which
 * may be applicable to their version or all versions.
 *<P>
 * Introduced in 1.1.07; check server version against {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}
 * before sending this message.
 *<P>
 * Robot clients don't need to know about or handle this message type,
 * because they don't create games.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.1.07
 */
public class SOCGameOptionGetInfos extends SOCMessage
{
    /**
     * List of game option keynames (Strings), or null
     */
    private Vector optkeys;

    /**
     * Create a GameOptionGetInfos Message.
     *
     * @param okeys  list of game option keynames (Strings), or null for "-"
     */
    public SOCGameOptionGetInfos(Vector okeys)
    {
        messageType = GAMEOPTIONGETINFOS;
        optkeys = okeys;
    }

    /**
     * Minimum version where this message type is used.
     * GAMEOPTIONGETINFOS introduced in 1.1.07 for game-options feature.
     * @return Version number, 1107 for JSettlers 1.1.07.
     */
    public int getMinimumVersion() { return 1107; }

    /**
     * @return the list of option keynames (a vector of Strings), or null if "-" was sent
     */
    public Vector getOptionKeys()
    {
        return optkeys;
    }

    /**
     * GAMEOPTIONGETINFOS sep optkeys
     *
     * @return the command string
     */
    public String toCmd()
    {
	if (optkeys != null)
	    return toCmd(optkeys.elements());
	else
	    return toCmd(null);
    }

    /**
     * GAMEOPTIONGETINFOS sep optkeys
     *
     * @param opts  the list of option keynames, as an enum of Strings or SOCGameOptions,
     *            or null to use "-" as 'optkeys'
     * @return    the command string
     */
    public static String toCmd(Enumeration opts)
    {
	StringBuffer cmd = new StringBuffer(Integer.toString(GAMEOPTIONGETINFOS));
	cmd.append(sep);

        if (opts == null)
        {
            cmd.append("-");
        } else {
            try
            {
                Object o = opts.nextElement();
                if (o instanceof SOCGameOption)
                    cmd.append(((SOCGameOption) o).optKey);
                else
                    cmd.append((String) o);

                while (opts.hasMoreElements())
                {
                    cmd.append(sep2);
                    o = opts.nextElement();
                    if (o instanceof SOCGameOption)
                        cmd.append(((SOCGameOption) o).optKey);
                    else
                        cmd.append((String) o);
                }
            }
            catch (Exception e) { }
        }

        return cmd.toString();
    }

    /**
     * Parse the command String into a GameOptionGetInfos message
     *
     * @param s   the String to parse
     * @return    a GetInfos message, or null of the data is garbled
     */
    public static SOCGameOptionGetInfos parseDataStr(String s)
    {
        Vector okey = new Vector();
        StringTokenizer st = new StringTokenizer(s, sep2);
	boolean hasDash = false;

        try
        {
            while (st.hasMoreTokens())
            {
		String ntok = st.nextToken();
                okey.addElement(ntok);
		if (ntok.equals("-"))
		    hasDash = true;
            }
        }
        catch (Exception e)
        {
            System.err.println("SOCGameOptionGetInfos parseDataStr ERROR - " + e);

            return null;
        }

	if (hasDash)
	{
	    if (okey.size() == 1)
		okey = null;  // empty list for "-"
	    else
		return null;  // parse error: more than "-" in list which contains "-"
	}
        return new SOCGameOptionGetInfos(okey);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer("SOCGameOptionGetInfos:options=");

	if (optkeys == null)
	    sb.append("-");
	else
	{
        try
        {
            Enumeration okEnum = optkeys.elements();
            sb.append ((String) okEnum.nextElement());

            while (okEnum.hasMoreElements())
            {
		sb.append(',');
                sb.append((String) okEnum.nextElement());
            }
        }
        catch (Exception e) {}
	}

        return sb.toString();
    }
}
