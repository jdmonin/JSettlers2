/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2007-2008,2010,2013,2016-2017,2021 Jeremy D Monin <jeremy@nand.net>
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
 * This message from server to game reminds the current player that their turn is starting
 * and they may roll the dice, or take other action allowable at that time.
 * For example, the player's hand panel can start an auto-roll countdown timer.
 *<P>
 * Was also sent during initial placement from servers older than v2.5.00 when it's a player's turn to place
 * a settlement. (That version and newer send {@link SOCTurn} instead.)
 *<P>
 * If the player is rolling the dice they will respond with {@link SOCRollDice},
 * or {@link SOCPlayDevCardRequest} to play a development card instead.
 *<P>
 * This message was most useful before v2.0.00, because v1.x.xx servers didn't
 * send a {@link SOCTurn} message at the start of the first player's normal turn
 * after initial placement.
 *<P>
 * If the client is older than v1.1.00 (1.0.6 for example) it will ignore this prompt.
 *<P>
 * Before v2.0.00 this message was preceded by a {@link SOCGameTextMsg} announcing the same information:
 * "It's Joe's turn to roll the dice." Clients v2.0.00 and newer print this announcement when they
 * receive a {@code SOCRollDicePrompt}, because server v2.0.00 and newer won't send that redundant text.
 * Check version against {@link soc.util.SOCStringManager#VERSION_FOR_I18N}.
 *
 * @author Jeremy D. Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
public class SOCRollDicePrompt extends SOCMessage
    implements SOCMessageForGame
{
    /** Class marked for v1.1.11 with SOCMessageForGame.
     *  Introduced at v1.1.00.
     */
    private static final long serialVersionUID = 1111L;

    /**
     * Name of game
     */
    private String game;

    /**
     * The player whose turn it is to roll
     */
    private int playerNumber;

    /**
     * Create a RollDicePrompt message.
     *
     * @param ga  the name of the game
     * @param pn  the player number who should roll
     */
    public SOCRollDicePrompt(String ga, int pn)
    {
        messageType = ROLLDICEPROMPT;
        game = ga;
        playerNumber = pn;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the player number whose turn it is to roll dice
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * ROLLDICEPROMPT sep game sep2 playernumber
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber);
    }

    /**
     * ROLLDICEPROMPT sep game sep2 playernumber
     *
     * @param ga  the name of the game
     * @param pn  the player number to roll
     * @return the command string
     */
    public static String toCmd(String ga, int pn)
    {
        return ROLLDICEPROMPT + sep + ga + sep2 + pn;
    }

    /**
     * Parse the command String into a ROLLDICEPROMPT message.
     *
     * @param s   the String to parse
     * @return    a ROLLDICEPROMPT message, or null if the data is garbled
     */
    public static SOCRollDicePrompt parseDataStr(String s)
    {
        String ga; // the game name
        int pn;    // the player number

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCRollDicePrompt(ga, pn);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCRollDicePrompt:game=" + game + "|playerNumber=" + playerNumber;
    }

    /**
     * Minimum version where this message type is used.
     * ROLLDICEPROMPT introduced in 1.1.00 for automatic rolling after x seconds.
     * @return Version number, 1100 for JSettlers 1.1.00.
     */
    public int getMinimumVersion() { return 1100; }

}
