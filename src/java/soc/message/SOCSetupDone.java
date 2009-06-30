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
 * This message means that all players are done placing
 * their initial settlements.
 *
 * @author Robert S. Thomas
 */
public class SOCSetupDone extends SOCMessage
{
    /**
     * Name of game
     */
    private String game;

    /**
     * Create a SetupDone message.
     *
     * @param ga  the name of the game
     */
    public SOCSetupDone(String ga)
    {
        messageType = SETUPDONE;
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
     * SETUPDONE sep game
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game);
    }

    /**
     * SETUPDONE sep game
     *
     * @param ga  the name of the game
     * @return the command string
     */
    public static String toCmd(String ga)
    {
        return SETUPDONE + sep + ga;
    }

    /**
     * Parse the command String into a SetupDone message
     *
     * @param s   the String to parse
     * @return    a SetupDone message, or null of the data is garbled
     */
    public static SOCSetupDone parseDataStr(String s)
    {
        return new SOCSetupDone(s);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCSetupDone:game=" + game;
    }
}
