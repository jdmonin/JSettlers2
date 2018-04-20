/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2010,2012,2014,2017-2018 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Arrays;
import java.util.StringTokenizer;

import soc.game.SOCGameOption;  // for javadocs only


/**
 * This message from server to a client prompts that player to choose another
 * player to steal from.  The client responds with {@link SOCChoosePlayer}.
 *<P>
 * In some game scenarios like {@link SOCGameOption#K_SC_PIRI SC_PIRI},
 * the player might have the option to not steal from anyone: Message will
 * have its {@link #canChooseNone()} flag set.
 * If the player makes that choice, their response to server is {@link SOCChoosePlayer}
 * ({@link SOCChoosePlayer#CHOICE_NO_PLAYER CHOICE_NO_PLAYER}).
 *
 * @author Robert S. Thomas
 */
public class SOCChoosePlayerRequest extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Name of game
     */
    private final String game;

    /**
     * True if can choose to not steal from anyone.
     * @see #canChooseNone()
     * @see #choices
     * @since 2.0.00
     */
    private final boolean allowChooseNone;

    /**
     * The possible choices; an array with 1 element per player number
     * (0 to <tt>game.maxPlayers - 1</tt>).
     * True means that the player with a matching index is a possible choice.
     * @see #allowChooseNone
     */
    private final boolean[] choices;

    /**
     * Create a ChoosePlayerRequest message.
     *
     * @param ga  the name of the game
     * @param ch  the possible choices; an array with 1 element per player number
     *     (0 to <tt>game.maxPlayers - 1</tt>)
     * @param canChooseNone  true if can choose to not steal from anyone.
     *     This is used with some game scenarios; all scenarios require version 2.0.00 or newer.
     */
    public SOCChoosePlayerRequest(final String ga, final boolean[] ch, final boolean canChooseNone)
    {
        messageType = CHOOSEPLAYERREQUEST;
        game = ga;
        choices = ch;
        allowChooseNone = canChooseNone;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the choices; an array with 1 element per player number (0 to <tt>game.maxPlayers - 1</tt>)
     * @see #canChooseNone()
     */
    public boolean[] getChoices()
    {
        return choices;
    }

    /**
     * Can the player choose to not steal from anyone?
     * This is used with some game scenarios; all scenarios require version 2.0.00 or newer.
     * @return true if can make that choice
     * @see #getChoices()
     * @since 2.0.00
     */
    public boolean canChooseNone()
    {
        return allowChooseNone;
    }

    /**
     * CHOOSEPLAYERREQUEST sep game sep2 [ "NONE" sep2 ] choices[0] sep2 choices[1] ...
     *<BR>
     * Each {@code choices} element is lowercase "true" or "false".
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, choices, allowChooseNone);
    }

    /**
     * CHOOSEPLAYERREQUEST sep game sep2 [ "NONE" sep2 ] choices[0] sep2 choices[1] ...
     *<BR>
     * Each {@code choices} element is lowercase "true" or "false".
     *
     * @param ga  the name of the game
     * @param ch  the choices; an array with 1 element per player number
     *     (0 to <tt>game.maxPlayers - 1</tt>)
     * @param canChooseNone  true if can choose to not steal from anyone.
     *     This is used with some game scenarios; all scenarios require version 2.0.00 or newer.
     * @return the command string
     */
    public static String toCmd(final String ga, final boolean[] ch, final boolean canChooseNone)
    {
        StringBuilder mes = new StringBuilder(CHOOSEPLAYERREQUEST + sep + ga);

        if (canChooseNone)
            mes.append(sep2 + "NONE");

        for (int i = 0; i < ch.length; i++)
        {
            mes.append(sep2_char);
            mes.append(ch[i] ? "true" : "false");
        }

        return mes.toString();
    }

    /**
     * Parse the command String into a ChoosePlayerRequest message
     *
     * @param s   the String to parse
     * @return    a ChoosePlayerRequest message, or null if the data is garbled
     */
    public static SOCChoosePlayerRequest parseDataStr(String s)
    {
        String ga; // the game name
        boolean canChooseNone = false;
        boolean[] ch; // the choices

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();

            int n = st.countTokens();
            if (n == 0)
                return null;

            String tok = st.nextToken();
            if (tok.equals("NONE"))
            {
                canChooseNone = true;
                --n;
                if (n == 0)
                    return null;
                tok = st.nextToken();
            }

            ch = new boolean[n];
            for (int count = 0; ; ++count)
            {
                ch[count] = (tok.equals("true"));
                if (! st.hasMoreTokens())
                    break;
                tok = st.nextToken();
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCChoosePlayerRequest(ga, ch, canChooseNone);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SOCChoosePlayerRequest:game=" + game);
        if (canChooseNone())
            sb.append("|canChooseNone=true");
        sb.append("|choices=" + Arrays.toString(choices));  // "[true, false, ...]"
        return sb.toString();
    }

}
