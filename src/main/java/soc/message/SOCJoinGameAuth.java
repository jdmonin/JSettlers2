/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014-2017,2019-2020 Jeremy D Monin <jeremy@nand.net>
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;
import soc.game.SOCBoard;       // for javadocs only
import soc.game.SOCBoardLarge;  // for javadocs only


/**
 * This message from server to a client means that the client's player
 * is allowed to join the game. After this message, the client is sent
 * all relevant game and player information (see {@link SOCGameMembers} for sequence).
 * Their joining is then announced to all game members with {@link SOCJoinGame}.
 *<P>
 * To help create the client's user interface to show this game,
 * if game options call for {@link SOCBoardLarge} this message includes the board layout's
 * height, width, and optional "VS" part if any. Otherwise that wouldn't be sent until
 * a later {@link SOCBoardLayout2} message, after the interface was already sized and laid out.
 * See {@link #getLayoutVS()} for details and allowed length.
 *<P>
 * <B>I18N:</B> If the game being joined uses a {@link soc.game.SOCScenario SOCScenario},
 * the client will need localized strings to explain the scenario as soon as the client
 * joins. So, server v2.0.00 and higher may send those strings before this {@code SOCJoinGameAuth} message
 * so the client will have them before showing the message dialog, using
 * {@link SOCLocalizedStrings}({@link SOCLocalizedStrings#TYPE_SCENARIO TYPE_SCENARIO})
 * or {@link SOCScenarioInfo} (see below).
 *<P>
 * <B>Scenarios:</B> If the game being joined uses a scenario added or modified in a version
 * newer than the client, server may send {@link SOCScenarioInfo} before this {@code SOCJoinGameAuth} message.
 * That message will include localized strings if available and needed by client.
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
     * Clients older than 2.4.00 will parse only the first 2 elements of {@code layoutVS} and ignore any more.
     *
     * @param gaName  Game name
     * @param height  Board height for {@link SOCBoardLarge} from {@link SOCBoard#getBoardHeight()}, or 0
     * @param width   Board width for {@link SOCBoardLarge} from {@link SOCBoard#getBoardWidth()}, or 0
     * @param layoutVS  Optional Visual Shift of board layout, or {@code null}; see {@link #getLayoutVS()}.
     * @throws IllegalArgumentException if {@code layoutVS} != {@code null} but its length &lt; 2
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
        if ((layoutVS != null) && (layoutVS.length < 2))
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
     * If sending to a client older than v2.4.00, only the first 2 elements will be parsed,
     * client will ignore any more. That version and newer permit any length &gt;= 2.
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
     * JOINGAMEAUTH sep game [sep2 height sep2 width [sep2 'S' sep2 layoutVS[0] sep2 layoutVS[1] ...]]
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
                for (final int elem : layoutVS)
                {
                    sb.append(sep2_char);
                    sb.append(elem);
                }
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
                ArrayList<String> rest = new ArrayList<>();
                while (st.hasMoreTokens())
                    rest.add(st.nextToken());

                int L = rest.size();
                if (L < 2)
                    return null;
                vs = new int[L];
                for (int i = 0; i < L; ++i)
                    vs[i] = Integer.parseInt(rest.get(i));
            } else {
                vs = null;
            }

            return new SOCJoinGameAuth(gaName, bh, bw, vs);
        }
        catch (Exception e) {}  // NoSuchElementException, NumberFormatException, etc

        return null;
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link SOCMessage#parseMsgStr(String)}.
     * Removes [] around {@code vs} int array and adds delimiter expected by {@link #parseDataStr(String)}:
     * {@code "gameName,20,21,S,-2,1,3,0"}.
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     * @since 2.4.50
     */
    public static String stripAttribNames(String messageStrParams)
    {
        final boolean hasVS = (messageStrParams.indexOf("|vs=[") > 0);
        String s = SOCMessage.stripAttribNames(messageStrParams);
        if ((s == null) || ! hasVS)
            return s;

        // "ga,20,21,[-2, 1, 3, 0]" -> "ga,20,21,S,-2,1,3,0"
        int i = s.indexOf(",[");
        if (i <= 0)
            return s;
        final StringBuilder sb = new StringBuilder(s.substring(0, i + 1));
        i += 2;  // move past '['
        final int L = s.length() - 1;
        if (s.charAt(L) != ']')
            return s;  // probably malformed

        sb.append("S,");  // added at this position in toCmd()
        for (; i < L; ++i)
        {
            char ch = s.charAt(i);
            if (ch != ' ')
                sb.append(ch);
        }

        return sb.toString();
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
                sb.append("|vs=" + Arrays.toString(layoutVS));
        }

        return sb.toString();
    }

}
