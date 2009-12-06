/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
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
 * This message asks a player to choose a player to
 * steal from.
 *
 * @author Robert S. Thomas
 */
public class SOCChoosePlayerRequest extends SOCMessage
{
    /**
     * Name of game
     */
    private String game;

    /**
     * The possible choices
     * True means that the player with a matching index is a
     * possible choice.
     */
    private boolean[] choices;

    /**
     * Create a ChoosePlayerRequest message.
     *
     * @param ga  the name of the game
     * @param ch  the possible choices
     */
    public SOCChoosePlayerRequest(String ga, boolean[] ch)
    {
        messageType = CHOOSEPLAYERREQUEST;
        game = ga;
        choices = ch;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the choices
     */
    public boolean[] getChoices()
    {
        return choices;
    }

    /**
     * CHOOSEPLAYERREQUEST sep game sep2 choices[0] sep2 choices[1] ...
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, choices);
    }

    /**
     * CHOOSEPLAYERREQUEST sep game sep2 choices[0] sep2 choices[1] ...
     *
     * @param ga  the name of the game
     * @param ch  the choices
     * @return the command string
     */
    public static String toCmd(String ga, boolean[] ch)
    {
        String mes = CHOOSEPLAYERREQUEST + sep + ga;

        for (int i = 0; i < ch.length; i++)
        {
            mes += (sep2 + ch[i]);
        }

        return mes;
    }

    /**
     * Parse the command String into a ChoosePlayerRequest message
     *
     * @param s   the String to parse
     * @return    a ChoosePlayerRequest message, or null of the data is garbled
     */
    public static SOCChoosePlayerRequest parseDataStr(String s)
    {
        String ga; // the game name
        boolean[] ch; // the choices

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();

            ch = new boolean[st.countTokens()];
            int count = 0;

            while (st.hasMoreTokens())
            {
                ch[count] = (Boolean.valueOf(st.nextToken())).booleanValue();
                count++;
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCChoosePlayerRequest(ga, ch);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String mes = "SOCChoosePlayerRequest:game=" + game + "|choices=" + choices[0];

        for (int i = 1; i < choices.length; i++)
        {
            mes += (", " + choices[i]);
        }

        return mes;
    }
}
