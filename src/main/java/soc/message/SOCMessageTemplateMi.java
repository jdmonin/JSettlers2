/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2008-2012,2015-2017 Jeremy D Monin <jeremy@nand.net>
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

// import java.util.StringTokenizer;


/**
 * Template for message types with variable number of integer parameters.
 * You will have to write parseDataStr, because of its subclass return
 * type and because it's static.
 *<P>
 * Sample implementation:
 *<pre><code>
 *   // format of s: POTENTIALSETTLEMENTS sep game sep2 settlecoord {sep2 settlecoord}*...
 *   // Must have at least game + 1 settlement param.
 *   public static SOCPotentialSettlements parseDataStr(String[] s)
 *   {
 *       String ga; // the game name
 *       int[] sett; // the settlements
 *
 *       if ((s == null) || (s.length < 2))
 *           return null;  // must have at least game + 1 settlement param
 *
 *       ga = s[0];
 *       sett = new int[s.length - 1];
 *       try
 *       {
 *           for (int i = 1; i < s.length; ++i)
 *               sett[i-1] = Integer.parseInt(s[i]);
 *       }
 *       catch (Exception e)
 *       {
 *           return null;
 *       }
 *
 *       return new SOCPotentialSettlements(ga, sett);
 *   }
 *</code></pre>
 *<P>
 * For notes on the section you must add to {@link SOCMessage#toMsg(String)},
 * see {@link SOCMessageMulti}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 * @see SOCMessageTemplateMs
 */
public abstract class SOCMessageTemplateMi extends SOCMessageMulti
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    /**
     * Name of the game, or null if none.
     */
    protected String game;

    /**
     * Array of int parameters, or null if none.
     *<P>
     * Although {@link SOCMessageTemplateMs} uses a List in v2.0.00 and newer,
     * for now this class still uses an array for compact representation.
     */
    protected int[] pa;

    /**
     * Create a new multi-message with integer parameters.
     *
     * @param id  Message type ID
     * @param ga  Name of game this message is for, or null if none
     * @param parr   Parameters, or null if none
     */
    protected SOCMessageTemplateMi(int id, String ga, int[] parr)
    {
        messageType = id;
        game = ga;
        pa = parr;
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
    public int[] getParams()
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
     * @param parr  The parameter array, or null if no additional parameters
     * @return    the command string
     */
    protected static String toCmd(final int messageType, String gaName, int[] parr)
    {
        StringBuilder sb = new StringBuilder(Integer.toString(messageType));

        if (gaName != null)
        {
            sb.append(sep);
            sb.append(gaName);
        }
        if (parr != null)
        {
            for (int i = 0; i < parr.length; ++i)
            {
                sb.append(sep);
                sb.append(parr[i]);
            }
        }

        return sb.toString();
    }

    /**
     * Parse the command String into a MessageType message.
     * Calls {@link #MessageType(String, int[])} constructor,
     * see its javadoc for parameter details.
     *
     * @param s   the String parameters
     * @return    a DiceResultResources message, or null if parsing errors
    public static SOCDiceResultResources parseDataStr(List<String> s)
    {
        String gaName;  // the game name
        int[] ipa;      // the parameters

        if ((s == null) || (s.size() < 2))
            return null;  // must have at least game name + 1 further param

        gaName = s.get(0);
        ipa = new int[s.size() - 1];
        try
        {
            for (int i = 0; i < ipa.length; ++i)
                ipa[i] = Integer.parseInt(s.get(i + 1));
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCDiceResultResources(gaName, ipa);
    }
    */

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        if (game != null)
        {
            sb.append (":game=");
            sb.append (game);
        }
        if (pa != null)
        {
            for (int i = 0; i < pa.length; ++i)
            {
                sb.append("|p=");
                sb.append(pa[i]);
            }
        }
        return sb.toString();
    }
}
