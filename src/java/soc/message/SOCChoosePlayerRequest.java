/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2010,2012,2014 Jeremy D Monin <jeremy@nand.net>
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

import java.util.StringTokenizer;


/**
 * This message from server to client asks a player to choose a player to
 * steal from.  The client responds with {@link SOCChoosePlayer}.
 *<P>
 * In some game scenarios in version 2.0.00 or newer,
 * the player might have the option to steal from no one.
 * See {@link #getChoices()} for details.  If the player
 * makes that choice, the response is {@link SOCChoosePlayer}
 * ({@link SOCChoosePlayer#CHOICE_NO_PLAYER CHOICE_NO_PLAYER}).
 *
 * @author Robert S. Thomas
 */
public class SOCChoosePlayerRequest extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * The possible choices; an array with 1 element per player number
     * (0 to <tt>game.maxPlayers - 1</tt>).
     * True means that the player with a matching index is a
     * possible choice.
     *<P>
     * In version 2.0.00+, this array may sometimes have an extra element <tt>choices[game.maxPlayers]</tt>.
     * If that element is true, the player may choose to steal from no one.
     */
    private boolean[] choices;

    /**
     * Create a ChoosePlayerRequest message.
     *
     * @param ga  the name of the game
     * @param ch  the possible choices; an array with 1 element per player number
     * (0 to <tt>game.maxPlayers - 1</tt>).
     *<P>
     * In version 2.0.00+, this array may sometimes have an extra element <tt>choices[game.maxPlayers]</tt>.
     * If that element is true, the player may choose to steal from no one.
     * This is used with some game scenarios; all scenarios require version 2.0.00 or newer.
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
     * @return the choices; an array with 1 element per player number
     * (0 to <tt>game.maxPlayers - 1</tt>).
     *<P>
     * In version 2.0.00+, this array may sometimes have an extra element <tt>choices[game.maxPlayers]</tt>.
     * If that element is true, the player may choose to steal from no one.
     * This is used with some game scenarios; all scenarios require version 2.0.00 or newer.
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
     * @param ch  the choices; an array with 1 element per player number
     *     (0 to <tt>game.maxPlayers - 1</tt>).
     *     May be longer in v2.0.00 scenarios; see {@link #getChoices()}.
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
