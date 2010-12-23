/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010 Jeremy D Monin <jeremy@nand.net>
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

import java.util.StringTokenizer;


/**
 * This message says which player the current player wants to
 * steal from.
 *
 * @author Robert S. Thomas
 */
public class SOCChoosePlayer extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of game
     */
    private String game;

    /**
     * The number of the chosen player
     */
    private int choice;

    /**
     * Create a ChoosePlayer message.
     *
     * @param ga  the name of the game
     * @param ch  the number of the chosen player
     */
    public SOCChoosePlayer(String ga, int ch)
    {
        messageType = CHOOSEPLAYER;
        game = ga;
        choice = ch;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the number of the chosen player
     */
    public int getChoice()
    {
        return choice;
    }

    /**
     * CHOOSEPLAYER sep game sep2 choice
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, choice);
    }

    /**
     * CHOOSEPLAYER sep game sep2 choice
     *
     * @param ga  the name of the game
     * @param ch  the number of the chosen player
     * @return the command string
     */
    public static String toCmd(String ga, int ch)
    {
        return CHOOSEPLAYER + sep + ga + sep2 + ch;
    }

    /**
     * Parse the command String into a ChoosePlayer message
     *
     * @param s   the String to parse
     * @return    a ChoosePlayer message, or null of the data is garbled
     */
    public static SOCChoosePlayer parseDataStr(String s)
    {
        String ga; // the game name
        int ch; // the number of the chosen player 

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            ch = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCChoosePlayer(ga, ch);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCChoosePlayer:game=" + game + "|choice=" + choice;
    }
}
