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

import java.util.Hashtable;
import java.util.StringTokenizer;
import soc.game.SOCGameOption;

/**
 * This message means that client wants to create a new game, with options;
 * needs same username/password options as JOINGAME.
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
public class SOCNewGameWithOptionsRequest extends SOCMessageTemplateJoinGame
{
    private String optsStr;
    private Hashtable opts;

    /**
     * Create a NEWGameWithOptionsRequest message.
     *
     * @param nn  nickname
     * @param pw  password
     * @param hn  host name
     * @param ga  name of the game
     * @param opts the game options (hashtable of {@link SOCGameOption})
     */
    public SOCNewGameWithOptionsRequest(String nn, String pw, String hn, String ga, Hashtable opts)
    {
	super(nn, pw, hn, ga);
        messageType = NEWGAMEWITHOPTIONSREQUEST;
	this.opts = opts;
	optsStr = SOCGameOption.packOptionsToString(opts);
    }

    /**
     * Create a NEWGameWithOptionsRequest message.
     *
     * @param nn  nickname
     * @param pw  password
     * @param hn  host name
     * @param ga  name of the game
     * @param optstr the game options as a string name-value pairs, as created by
     *             {@link #packOptionsToString(Hashtable)}.
     */
    public SOCNewGameWithOptionsRequest(String nn, String pw, String hn, String ga, String optstr)
    {
	super(nn, pw, hn, ga);
        messageType = NEWGAMEWITHOPTIONSREQUEST;
	optsStr = optstr;
	opts = SOCGameOption.parseOptionsToHash(optstr);
    }

    /**
     * @return the game options (hashtable of {@link SOCGameOption})
     */
    public Hashtable getOptions()
    {
       return opts;
    }

    /**
     * NEWGAMEWITHOPTIONSREQUEST sep nickname sep2 password sep2 host sep2 game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(nickname, password, host, game, optsStr);
    }

    /**
     * NEWGAMEWITHOPTIONSREQUEST sep nickname sep2 password sep2 host sep2 game sep2 options
     *
     * @param nn  the nickname
     * @param pw  the password
     * @param hn  the host name
     * @param ga  the game name
     * @param optstr the game options as a string name-value pairs, as created by
     *             {@link #packOptionsToString(Hashtable)}.
     * @return    the command string
     */
    public static String toCmd(String nn, String pw, String hn, String ga, String optstr)
    {
        if (pw.equals(""))
        {
            pw = NULLPASS;
        }

        return NEWGAMEWITHOPTIONSREQUEST + sep + nn + sep2 + pw + sep2 + hn + sep2 + ga + sep2 + optstr;
    }

    /**
     * @param opts the game options ({@link SOCGameOption})
     * @return    the command string
     */
    public static String toCmd(String nn, String pw, String hn, String ga, Hashtable opts)
    {
	return toCmd(nn, pw, hn, ga, SOCGameOption.packOptionsToString(opts));
    }

    /**
     * Parse the command String into a NewGameWithOptionsRequest message
     *
     * @param s   the String to parse
     * @return    a NewGameWithOptionsRequest message, or null of the data is garbled
     */
    public static SOCNewGameWithOptionsRequest parseDataStr(String s)
    {
        String nn;
        String pw;
        String hn;
        String ga;
	String optstr;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            nn = st.nextToken();
            pw = st.nextToken();
            hn = st.nextToken();
            ga = st.nextToken();
            optstr = st.nextToken(sep);  // not sep2 ! Commas are used to sep options.

            if (pw.equals(NULLPASS))
            {
                pw = "";
            }
        }
        catch (Exception e)
        {
            return null;
        }

	return new SOCNewGameWithOptionsRequest(nn, pw, hn, ga, optstr);
    }

    /**
     * Minimum version where this message type is used.
     * NEWGAMEWITHOPTIONS introduced in 1.1.07 for game-options feature.
     * @return Version number, 1107 for JSettlers 1.1.07.
     */
    public int getMinimumVersion() { return 1107; }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return super.toString("SOCNewGameWithOptionsRequest");
    }
}
