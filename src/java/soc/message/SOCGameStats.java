/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009,2010 Jeremy D Monin <jeremy@nand.net>
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
 * This message contains the scores for the people at a game.
 * Used for displaying games in the player client list.
 * Also used at end of game to display true scores (with VP cards).
 *
 * @author Robert S. Thomas
 */
public class SOCGameStats extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of game
     */
    private String game;

    /**
     * The scores; always indexed 0 to {@link soc.game.SOCGame#maxPlayers} - 1,
     *   regardless of number of players in the game.
     */
    private int[] scores;

    /**
     * Where robots are sitting; indexed same as scores.
     */
    private boolean[] robots;

    /**
     * Create a GameStats message
     *
     * @param ga  the name of the game
     * @param sc  the scores; always indexed 0 to
     *   {@link soc.game.SOCGame#maxPlayers} - 1,
     *   regardless of number of players in the game
     * @param rb  where robots are sitting; indexed same as scores
     */
    public SOCGameStats(String ga, int[] sc, boolean[] rb)
    {
        messageType = GAMESTATS;
        game = ga;
        scores = sc;
        robots = rb;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the scores
     */
    public int[] getScores()
    {
        return scores;
    }

    /**
     * @return where the robots are sitting
     */
    public boolean[] getRobotSeats()
    {
        return robots;
    }

    /**
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, scores, robots);
    }

    /**
     * @return the command string
     *
     * @param ga  the name of the game
     * @param sc  the scores
     * @param rb  where robots are sitting
     */
    public static String toCmd(String ga, int[] sc, boolean[] rb)
    {
        String cmd = GAMESTATS + sep + ga;

        for (int i = 0; i < sc.length; i++)
        {
            cmd += (sep2 + sc[i]);
        }

        for (int i = 0; i < rb.length; i++)
        {
            cmd += (sep2 + rb[i]);
        }

        return cmd;
    }

    /**
     * Parse the command String into a GameStats message
     *
     * @param s   the String to parse
     * @return    a GameStats message, or null of the data is garbled
     */
    public static SOCGameStats parseDataStr(String s)
    {
        String ga; // the game name
        int[] sc; // the scores
        boolean[] rb; // where robots are sitting

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            final int maxPlayers = st.countTokens() / 2;
            sc = new int[maxPlayers];
            rb = new boolean[maxPlayers];

            for (int i = 0; i < maxPlayers; i++)
            {
                sc[i] = Integer.parseInt(st.nextToken());
            }

            for (int i = 0; i < maxPlayers; i++)
            {
                rb[i] = (Boolean.valueOf(st.nextToken())).booleanValue();
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCGameStats(ga, sc, rb);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuffer text = new StringBuffer("SOCGameStats:game=");
        text.append(game);
        for (int i = 0; i < scores.length; i++)
        {
            text.append("|");
            text.append(scores[i]);
        }

        for (int i = 0; i < robots.length; i++)
        {
            text.append("|");
            text.append(robots[i]);
        }

        return text.toString();
    }
}
