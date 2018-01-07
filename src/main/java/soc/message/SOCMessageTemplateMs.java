/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2008-2012,2014-2018 Jeremy D Monin <jeremy@nand.net>
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
import java.util.ListIterator;

// import java.util.StringTokenizer;


/**
 * Template for message types with variable number of string parameters.
 * You will have to write parseDataStr, because of its subclass return
 * type and because it's static.
 *<P>
 * Sample implementation:
 *<pre><code>
 *   // format of s: POTENTIALSETTLEMENTS sep game sep2 settlement {sep2 settlement}*...
 *   // Must have at least game + 1 settlement param.
 *   public static SOCDiceResultResources parseDataStr(List<String> s)
 *   {
 *       String gaName;  // the game name
 *       String[] pa;    // the parameters
 *
 *       if ((s == null) || (s.size() < 2))
 *           return null;  // must have at least game name + 1 more param
 *
 *       parseData_FindEmptyStrs(s);  // EMPTYSTR -> ""
 *       gaName = s.get(0);
 *       pa = new String[s.size() - 1];
 *       for (int i = 0; i < pa.length; ++i)
 *           pa[i] = s.get(i + 1);
 *
 *       return new SOCDiceResultResources(gaName, pa);
 *   }
 *</code></pre>
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
     * The server's message treater requires a non-null {@link #getGame()} for incoming messages
     * from clients; see {@link SOCMessageForGame#getGame()} for details.
     */
    protected String game;

    /**
     * List of string parameters, or null if none.
     * Blank field values must be sent over network as {@link SOCMessage#EMPTYSTR}
     * because empty {@code pa} elements can't be parsed. This List itself should contain "" instead of
     * an {@code EMPTYSTR} token; {@link #toCmd()} will translate "" and {@code null} to {@code EMPTYSTR}.
     *<P>
     * Before v2.0.00, this was an array of Strings.
     */
    protected List<String> pa;

    /**
     * Create a new multi-message with string parameters.
     *
     * @param id  Message type ID
     * @param ga  Name of game this message is for, or null if none. See {@link #getGame()} for details.
     *     The server's message treater requires a non-null {@link #getGame()}
     *     for incoming messages from clients; see {@link SOCMessageForGame#getGame()} for details.
     * @param pal List of parameters, or null if none.
     *     Sets {@link #pa} field to {@code pal}: Afterwards method calls on {@code pa} or {@code pal}
     *     will affect the same List object.
     *     <P>
     *     This constructor does not convert {@link SOCMessage#EMPTYSTR} field values to "";
     *     see {@link #parseData_FindEmptyStrs(List)}.
     */
    protected SOCMessageTemplateMs(final int id, final String ga, final List<String> pal)
    {
        messageType = id;
        game = ga;
        pa = pal;
    }

    /**
     * Get the game name; see {@link SOCMessageForGame#getGame()} for details.
     * If not null, {@link #toCmd()} sends the game name before {@link #getParams()} contents, and
     * at the receiver {@code parseDataStr(params)} will see that game name as the first parameter.
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
     * @param gaName  the game name, or null
     * @param pal  The parameter list, or null if no additional parameters.
     *     Blank or null values in this list are automatically sent as the {@link SOCMessage#EMPTYSTR} token
     *     and must be converted back on the receiving end: See {@link #parseData_FindEmptyStrs(List)}.
     * @return    the command string
     */
    protected static String toCmd(final int messageType, final String gaName, final List<String> pal)
    {
        StringBuilder sb = new StringBuilder(Integer.toString(messageType));

        if (gaName != null)
        {
            sb.append(sep);
            sb.append(gaName);
        }

        if (pal != null)
        {
            for (final String p : pal)
            {
                sb.append(sep);
                if ((p != null) && (p.length() > 0))
                    sb.append(p);
                else
                    sb.append(EMPTYSTR);
            }
        }

        return sb.toString();
    }

    /**
     * Parse the command String into a MessageType message.
     * Calls {@link #MessageType(gaName, List)} constructor,
     * see its javadoc for parameter details.
     *
     * @param s   the String parameters
     * @return    a DiceResultResources message, or null if parsing errors
    public static SOCDiceResultResources parseDataStr(List<String> s)
    {
        String gaName;  // the game name
        String[] pa;    // the parameters

        if ((s == null) || (s.size() < 2))
            return null;  // must have at least game name + 1 more param

        parseData_FindEmptyStrs(s);  // EMPTYSTR -> ""
        gaName = s.get(0);
        pa = new String[s.size() - 1];
        for (int i = 0; i < pa.length; ++i)
            pa[i] = s.get(i + 1);

        return new SOCDiceResultResources(gaName, pa);
    }
    */

    /**
     * Parse helper method: Iterate over the received parameter list
     * and replace any {@link SOCMessage#EMPTYSTR} parameter with "" in place.
     * Used in child classes' {@code parseDataStr(..)} methods.
     * Ignores {@link #GAME_NONE}.
     * @param slist  The String parameters received over the network, or {@code null} to do nothing
     * @return {@code slist}, for convenience during constructor calls to {@code super(..)}
     * @since 2.0.00
     */
    public static List<String> parseData_FindEmptyStrs(final List<String> slist)
    {
        if (slist == null)
            return null;  // unlikely to occur

        final ListIterator<String> li = slist.listIterator();
        while (li.hasNext())
            if (EMPTYSTR.equals(li.next()))
                li.set("");

        return slist;
    }

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
