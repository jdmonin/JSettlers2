/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014-2017,2019 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCBoard;       // for javadocs only
import soc.game.SOCBoardLarge;  // for javadocs only


/**
 * This message from server to a client means that the client's player
 * is allowed to join the game. After this message, the client is sent
 * all relevant game and player information (see {@link SOCGameMembers}).
 * Their joining is then announced to all game members with {@link SOCJoinGame}.
 *<P>
 * To help create the client's user interface to show this game,
 * if game options call for {@link SOCBoardLarge} this message includes the board layout's
 * height, width, and optional "VS" part if any. Otherwise that wouldn't be sent until
 * a later {@link SOCBoardLayout2} message, after the interface was already sized and laid out.
 * See {@link #getLayoutVS()}.
 *<P>
 * <B>I18N:</B> If the game being joined uses a {@link soc.game.SOCScenario SOCScenario},
 * the client will need localized strings to explain the scenario as soon as the client
 * joins.  So, v2.0.00 sends those strings before this JOINGAMEAUTH message so that the
 * client will have them before showing the message dialog. The strings are sent using
 * {@link SOCLocalizedStrings}.
 *
 * @author Robert S Thomas
 * @see SOCJoinChannelAuth
 */
public class SOCJoinGameAuth extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Name of game
     */
    private final String game;

    /**
     * Board height and width for {@link SOCBoardLarge}, or 0.
     * @since 2.0.00
     */
    private final int boardHeight, boardWidth;

    /**
     * Optional Visual Shift of board layout, from Layout Extra Part "VS":
     * See {@link #getLayoutVS()} for details.
     * @since 2.0.00
     */
    private final int[] layoutVS;

    /**
     * Create a JoinGameAuth message without the optional {@link #getLayoutVS()}.
     * This message can be sent to any client version.
     *
     * @param gaName  name of game
     * @see #SOCJoinGameAuth(String, int, int, int[])
     */
    public SOCJoinGameAuth(final String gaName)
    {
        this(gaName, 0, 0, null);
    }

    /**
     * Create a JoinGameAuth message which may contain the optional {@link #getLayoutVS()}.
     * That parameter can be sent only to client version 2.0.00 or newer ({@link SOCBoardLarge#MIN_VERSION}).
     *
     * @param gaName  Game name
     * @param height  Board height for {@link SOCBoardLarge} from {@link SOCBoard#getBoardHeight()}, or 0
     * @param width   Board width for {@link SOCBoardLarge} from {@link SOCBoard#getBoardWidth()}, or 0
     * @param layoutVS  Optional Visual Shift of board layout, or {@code null}; see {@link #getLayoutVS()}.
     * @throws IllegalArgumentException if {@code layoutVS} != {@code null} but its length != 2
     * @see #SOCJoinGameAuth(String)
     * @since 2.0.00
     */
    public SOCJoinGameAuth(final String gaName, final int height, final int width, final int[] layoutVS)
        throws IllegalArgumentException
    {
        messageType = JOINGAMEAUTH;
        game = gaName;
        this.boardHeight = height;
        this.boardWidth = width;
        if ((layoutVS != null) && (layoutVS.length != 2))
            throw new IllegalArgumentException("layoutVS");
        this.layoutVS = layoutVS;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * Get this message's board height, if server sent it for this game's board type and layout.
     * @return Board height if game uses {@link SOCBoardLarge}, from {@link SOCBoard#getBoardHeight()}, or 0
     * @see #getBoardWidth()
     * @since 2.0.00
     */
    public int getBoardHeight()
    {
        return boardHeight;
    }

    /**
     * Get this message's board width, if server sent it for this game's board type and layout.
     * @return Board width if game uses {@link SOCBoardLarge}, from {@link SOCBoard#getBoardWidth()}, or 0
     * @see #getBoardHeight()
     * @since 2.0.00
     */
    public int getBoardWidth()
    {
        return boardWidth;
    }

    /**
     * Get this message's optional Visual Shift for the board layout, from Layout Extra Part "VS".
     * A signed int array of length 2, or {@code null}.
     * See {@link SOCBoardLarge#getAddedLayoutPart(String) SOCBoardLarge.getAddedLayoutPart("VS")}
     * for more details on "VS".
     *<P>
     * Can't be sent to client v1.x.xx (&lt; {@link SOCBoardLarge#MIN_VERSION}), which would
     * interpret it as part of the game name. Not an issue because that client version can't join
     * any game which uses {@link SOCBoardLarge}.
     *
     * @return Board layout's optional "VS" part, or {@code null}
     * @see #getBoardHeight()
     * @see #getBoardWidth()
     * @since 2.0.00
     */
    public int[] getLayoutVS()
    {
        return layoutVS;
    }

    /**
     * JOINGAMEAUTH sep game [sep2 height sep2 width [sep2 'S' sep2 layoutVS[0] sep2 layoutVS[1]]]
     *
     * @return the command String
     */
    public String toCmd()
    {
        StringBuilder sb = new StringBuilder(JOINGAMEAUTH + sep + game);

        if ((boardHeight != 0) || (boardWidth != 0))
        {
            sb.append(sep2_char);
            sb.append(boardHeight);
            sb.append(sep2_char);
            sb.append(boardWidth);
            if (layoutVS != null)
            {
                sb.append(sep2_char);
                sb.append('S');  // in case a later version adds other optional fields
                sb.append(sep2_char);
                sb.append(layoutVS[0]);
                sb.append(sep2_char);
                sb.append(layoutVS[1]);
            }
        }

        return sb.toString();
    }

    /**
     * Parse the command String into a JoinGameAuth message, which may have a
     * height, width, and optional {@link #getLayoutVS()}.
     *
     * @param s   the String to parse
     * @return    a JoinGameAuth message, or null if the data is garbled
     */
    public static SOCJoinGameAuth parseDataStr(String s)
    {
        if (-1 == s.indexOf(sep2_char))
            return new SOCJoinGameAuth(s, 0, 0, null);

        try
        {
            final StringTokenizer st = new StringTokenizer(s, sep2);
            final String gaName = st.nextToken();
            final int bh = Integer.parseInt(st.nextToken()),
                      bw = Integer.parseInt(st.nextToken());
            final int[] vs;
            if (st.hasMoreTokens())
            {
                if (! st.nextToken().equals("S"))
                    return null;  // unrecognized optional-field marker
                vs = new int[2];
                vs[0] = Integer.parseInt(st.nextToken());
                vs[1] = Integer.parseInt(st.nextToken());
            } else {
                vs = null;
            }

            return new SOCJoinGameAuth(gaName, bh, bw, vs);
        }
        catch (Exception e) {}  // NoSuchElementException, NumberFormatException, etc

        return null;
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SOCJoinGameAuth:game=" + game);
        if ((boardHeight != 0) || (boardWidth != 0))
        {
            sb.append("|bh=" + boardHeight + "|bw=" + boardWidth);
            if (layoutVS != null)
                sb.append("|vs={" + layoutVS[0] + ", " + layoutVS[1] + '}');
        }

        return sb.toString();
    }

}
