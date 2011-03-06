/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010 Jeremy D Monin <jeremy@nand.net>
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.message;

import java.util.StringTokenizer;
import soc.game.SOCGame;  // for javadoc's use


/**
 * This message communicates the current state of the game
 *
 * @author Robert S Thomas
 * @see SOCGame#getGameState()
 */
public class SOCGameState extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of game
     */
    private String game;

    /**
     * Game state
     */
    private int state;

    /**
     * Create a GameState message.
     *
     * @param ga  name of the game
     * @param gs  game state
     */
    public SOCGameState(String ga, int gs)
    {
        messageType = GAMESTATE;
        game = ga;
        state = gs;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the game state
     */
    public int getState()
    {
        return state;
    }

    /**
     * GAMESTATE sep game sep2 state
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, state);
    }

    /**
     * GAMESTATE sep game sep2 state
     *
     * @param ga  the game name
     * @param gs  the game state
     * @return    the command string
     */
    public static String toCmd(String ga, int gs)
    {
        return GAMESTATE + sep + ga + sep2 + gs;
    }

    /**
     * Parse the command String into a GameState message
     *
     * @param s   the String to parse
     * @return    a GameState message, or null of the data is garbled
     */
    public static SOCGameState parseDataStr(String s)
    {
        String ga;
        int gs;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            gs = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCGameState(ga, gs);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCGameState:game=" + game + "|state=" + state;

        return s;
    }
}
