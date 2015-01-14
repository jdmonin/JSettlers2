/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2008-2012,2014-2015 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;

import java.util.List;

// import java.util.StringTokenizer;


/**
 * Template for message types with variable number of string parameters.
 * You will have to write parseDataStr, because of its subclass return
 * type and because it's static.
 *<P>
 * Sample implementation:
 *<code>
 *   // format of s: POTENTIALSETTLEMENTS sep game sep2 settlement {sep2 settlement}*...
 *   // Must have at least game + 1 settlement param.
 *   public static SOCPotentialSettlements parseDataStr(String[] s)
 *   {
 *       String ga; // the game name
 *       String[] sett; // the settlements
 *
 *       if ((s == null) || (s.length < 2))
 *           return null;  // must have at least game + 1 settlement param
 *
 *       ga = s[0];
 *       sett = new String[s.length - 1];
 *       for (int i = 1; i < s.length; ++i)
 *           sett[i-1] = s[i];
 *
 *       return new SOCPotentialSettlements(ga, sett);
 *   }
 *</code>
 *<P>
 * For notes on the section you must add to {@link SOCMessage#toMsg(String)},
 * see {@link SOCMessageMulti}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
public abstract class SOCMessageTemplateMs extends SOCMessageMulti
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    /**
     * Name of the game, or null if none.
     */
    protected String game;

    /**
     * List of string parameters, or null if none.
     *<P>
     * Before v2.0.00, this was an array of Strings.
     */
    protected List<String> pa;

    /**
     * Create a new multi-message with string parameters.
     *
     * @param id  Message type ID
     * @param ga  Name of game this message is for, or null if none
     * @param pal List of parameters, or null if none
     */
    protected SOCMessageTemplateMs(final int id, final String ga, final List<String> pal)
    {
        messageType = id;
        game = ga;
        pa = pal;
    }

    /**
     * @return the name of the game, or null if none
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the parameters, or null if none
     */
    public List<String> getParams()
    {
        return pa;
    }

    /**
     * MESSAGETYPE [sep game] sep param1 sep param2 sep ...
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(messageType, game, pa);
    }

    /**
     * MESSAGETYPE [sep game] sep param1 sep param2 sep ...
     *
     * @param messageType The message type id
     * @param ga  the game name, or null
     * @param pal The parameter list, or null if no additional parameters;
     *             elements of {@code pal} can be null.
     * @return    the command string
     */
    protected static String toCmd(final int messageType, final String ga, final List<String> pal)
    {
        StringBuilder sb = new StringBuilder(Integer.toString(messageType));

        if (ga != null)
        {
            sb.append(sep);
            sb.append(ga);
        }
        if (pal != null)
        {
            for (final String p : pal)
            {
                sb.append(sep);
                if (p != null)
                    sb.append(p);
            }
        }

        return sb.toString();
    }

    /**
     * Parse the command String into a MessageType message
     *
     * @param s   the String parameters
     * @return    a PotentialSettlements message, or null if parsing errors
    public static SOCPotentialSettlements parseDataStr(String[] s)
    {
        String ga; // the game name
        String[] sett; // the settlements

        if ((s == null) || (s.length < 2))
            return null;  // must have at least game + 1 settlement param

        ga = s[0];
        sett = new String[s.length - 1];
        for (int i = 1; i < s.length; ++i)
            sett[i-1] = s[i];

        return new SOCPotentialSettlements(ga, sett);
    }
    */

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder(getClassNameShort());

        if (game != null)
        {
            sb.append (":game=");
            sb.append (game);
        }
        if (pa != null)
        {
            for (final String p : pa)
            {
                sb.append("|p=");
                if (p != null)
                    sb.append(p);
            }
        }

        return sb.toString();
    }
}
