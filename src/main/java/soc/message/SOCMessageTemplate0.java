/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2008,2010-2012,2014 Jeremy D Monin <jeremy@nand.net>
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


/**
 * Template for per-game message types with no parameters.
 * You will have to write parseDataStr, because of its return
 * type and because it's static.
 *<P>
 * Sample implementation:
 *<code><pre>
 *   public static SOCAdminPing parseDataStr(final String s)
 *   {
 *       return new SOCAdminPing(s);
 *   }
 *</pre></code>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
public abstract class SOCMessageTemplate0 extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    /**
     * Name of the game.
     */
    protected String game;

    /**
     * Create a new message.
     *
     * @param id  Message type ID
     * @param ga  Name of game this message is for
     */
    protected SOCMessageTemplate0(int id, String ga)
    {
        messageType = id;
        game = ga;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * MESSAGETYPE sep game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(messageType, game);
    }

    /**
     * MESSAGETYPE sep game
     *<P>
     * Public method only because there are no parameters, so this is easy to call.
     *
     * @param messageType The message type id
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(final int messageType, final String ga)
    {
        return Integer.toString(messageType) + sep + ga;
    }

    /**
     * Parse the command String into a MessageType message
     *
     * @param s   the String to parse
     * @return    an AdminPing message
    public static SOCAdminPing parseDataStr(final String s)
    {
        return new SOCAdminPing(s);
    }
     */

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return getClass().getSimpleName() + ":game=" + game;
    }

}
