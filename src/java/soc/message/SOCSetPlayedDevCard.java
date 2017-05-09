/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2017 Jeremy D Monin <jeremy@nand.net>
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
 * This message sets the flag which says if a player has
 * played a development card this turn
 *
 * @author Robert S. Thomas
 */
public class SOCSetPlayedDevCard extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * Name of game
     */
    private String game;

    /**
     * The player number
     */
    private int playerNumber;

    /**
     * the value of the playedDevCard flag
     */
    private boolean playedDevCard;

    /**
     * Create a SetPlayedDevCard message.
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     * @param pd  the value of the playedDevCard flag
     */
    public SOCSetPlayedDevCard(String ga, int pn, boolean pd)
    {
        messageType = SETPLAYEDDEVCARD;
        game = ga;
        playerNumber = pn;
        playedDevCard = pd;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the seat number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the value of the playedDevCard flag
     */
    public boolean hasPlayedDevCard()
    {
        return playedDevCard;
    }

    /**
     * SETPLAYEDDEVCARD sep game sep2 playerNumber sep2 playedDevCard
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, playedDevCard);
    }

    /**
     * SETPLAYEDDEVCARD sep game sep2 playerNumber sep2 playedDevCard
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     * @param pd  the value of the playedDevCard flag
     * @return the command string
     */
    public static String toCmd(String ga, int pn, boolean pd)
    {
        return SETPLAYEDDEVCARD + sep + ga + sep2 + pn + sep2 + pd;
    }

    /**
     * Parse the command String into a SETPLAYEDDEVCARD message.
     *
     * @param s   the String to parse
     * @return    a SETPLAYEDDEVCARD message, or null if the data is garbled
     */
    public static SOCSetPlayedDevCard parseDataStr(String s)
    {
        String ga; // the game name
        int pn; // the seat number
        boolean pd; // the value of the playedDevCard flag

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            pd = (Boolean.valueOf(st.nextToken())).booleanValue();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCSetPlayedDevCard(ga, pn, pd);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCSetPlayedDevCard:game=" + game + "|playerNumber=" + playerNumber + "|playedDevCard=" + playedDevCard;
    }
}
