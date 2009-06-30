/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file Copyright (C) 2008 Jeremy D Monin <jeremy@nand.net>
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

// import java.util.StringTokenizer;


/**
 * Template for per-game message types with variable number of string parameters.
 * You will have to write parseDataStr, because of its return
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
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
public abstract class SOCMessageTemplateMs extends SOCMessageMulti
{
    /**
     * Name of the game.
     */
    protected String game;

    /**
     * Array of string parameters, or null if none.
     */
    protected String[] pa;

    /**
     * Create a new multi-message with string parameters.
     *
     * @param id  Message type ID
     * @param ga  Name of game this message is for
     * @param parr   Parameters, or null if none
     */
    protected SOCMessageTemplateMs(int id, String ga, String[] parr)
    {
        messageType = id;
        game = ga;
        pa = parr;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the parameters, or null if none
     */
    public String[] getParams()
    {
        return pa;
    }

    /**
     * MESSAGETYPE sep game sep2 param
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(messageType, game, pa);
    }

    /**
     * MESSAGETYPE sep game sep param1 sep param2 sep ...
     *
     * @param messageType The message type id
     * @param ga  the game name
     * @param parr The parameter array, or null if no additional parameters;
     *             elements of parr can be null.
     * @return    the command string
     */
    public static String toCmd(int messageType, String ga, String[] parr)
    {
        StringBuffer sb = new StringBuffer(Integer.toString(messageType));
        sb.append(sep);
        sb.append(ga);
        if (parr != null)
        {
            for (int i = 0; i < parr.length; ++i)
            {
                sb.append(sep);
                if (parr[i] != null)
                    sb.append(parr[i]);
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
        StringBuffer sb = new StringBuffer(getClassNameShort());
        sb.append (":game=");
        sb.append (game);
        if (pa != null)
        {
            for (int i = 0; i < pa.length; ++i)
            {
                sb.append("|param=");
                if (pa[i] != null)
                    sb.append(pa[i]);
            }
        }
        return sb.toString();
    }
}
