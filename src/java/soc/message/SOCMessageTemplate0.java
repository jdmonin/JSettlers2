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


/**
 * Template for per-game message types with no parameters.
 * You will have to write parseDataStr, because of its return
 * type and because it's static.
 *<P>
 * Sample implementation:
 *<code>
 *   public static SOCAdminPing parseDataStr(String s)
 *   {
 *       return new SOCAdminPing(s);
 *   }
 *</code>
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
public abstract class SOCMessageTemplate0 extends SOCMessage
{
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
     *
     * @param messageType The message type id
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(int messageType, String ga)
    {
        return Integer.toString(messageType) + sep + ga;
    }

    /**
     * Parse the command String into a MessageType message
     *
     * @param s   the String to parse
     * @return    an AdminPing message
    public static SOCAdminPing parseDataStr(String s)
    {
        return new SOCAdminPing(Integer.parseInt(s));
    }
     */

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return getClassNameShort() + ":game=" + game;
    }
}
