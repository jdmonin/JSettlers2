/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2010,2014,2017-2022 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;  // for javadocs only
import soc.game.SOCPlayer;  // javadocs only


/**
 * This message from server contains stats about a game; a few game stat types are defined here.
 * Or from client, a request to server for a specific type of stats about a game.
 * If stat type is unknown or game doesn't exist, server ignores client's request.
 *
 *<H3>Message sequence for {@link #TYPE_PLAYERS}:</H3>
 *
 * This message from server contains the scores for the people at a game.
 * Used at end of game to update game data and display true scores (totals including points from VP cards).
 * When receiving this, client should call each player's {@link SOCPlayer#forceFinalVP(int)},
 * then if {@link SOCGame#getPlayerWithWin()} == null, call {@link SOCGame#checkForWinner()}.
 *<P>
 * Any game-information messages which reveal hidden state are sent
 * before, not after, this message. When client receives this
 * message, take it as a signal to reveal true scores and maybe
 * show/announce other interesting information such as VP dev cards.
 *
 *<UL>
 *<LI> Preceded by:
 *     <UL>
 *     <LI> {@link SOCGameElements}({@link SOCGameElements.GEType#CURRENT_PLAYER CURRENT_PLAYER}=winningPlayerNumber)
 *     <LI> {@link SOCGameState}({@link soc.game.SOCGame#OVER OVER})
 *     </UL>
 *     Or if another player's turn just ended, and client newer than v1.x:
 *     <UL>
 *     <LI> {@link SOCTurn}(winningPlayerNumber, gameState={@code OVER})
 *     </UL>
 *<LI> Any other messages revealing hidden information about game's details,
 *     such as {@link SOCDevCardAction}({@link SOCDevCardAction#ADD_OLD ADD_OLD}, VPCardType,...)
 *<LI> This message: <tt>SOCGameStats({@link #TYPE_PLAYERS})</tt>
 *<LI> Each still-connected player client is sent their {@link SOCPlayerStats}
 *</UL>
 *<P>
 * Server v1.x didn't send any data messages which reveal hidden VP cards,
 * so in some games this message was necessary to reveal players' final VP totals.
 *
 *<H3>Message sequence for {@link #TYPE_TIMING}:</H3>
 *
 * Can be sent from server in response to client request, or when client is joining a game.
 *
 *<UL>
 *<LI> Optionally: Client sends <tt>SOCGameStats(gameName, {@link #TYPE_TIMING}, {})</tt>
 *<LI> Server sends client <tt>SOCGameStats(gameName, {@link #TYPE_TIMING}, stats[])</tt><BR>
 *     When receiving this, client should use data in {@code stats[]}
 *     (see {@link #TYPE_TIMING} javadoc) to call {@link SOCGame#setTimeSinceCreated(int)}
 *     and possibly {@link SOCGame#setDurationSecondsFinished(int)}.
 *<LI> If sent as part of sequence when client joining a game, this message is followed by a few optional others
 *     before {@link SOCGameMembers}.
 *</UL>
 *
 *<H3>Other stats types</H3>
 *
 * Future versions may define more types, which may be sent from a server to this client or all clients in a game.
 * Clients should ignore stats types they don't recognize.
 *
 * @author Robert S. Thomas
 */
public class SOCGameStats extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2700L;  // last structural change v2.7.00

    /**
     * First version which recognizes {@link #TYPE_TIMING}.
     * Earlier versions can receive and send only {@link #TYPE_PLAYERS}.
     */
    public static final int VERSION_FOR_TYPE_TIMING = 2700;

    /**
     * Final player scores and robot flags at end of game;
     * see {@link #getScores()} and {@link #getRobotSeats()} for details.
     * This is the original stats type, compatible with all versions.
     * @see #TYPE_TIMING
     */
    public static final int TYPE_PLAYERS = 1;

    /**
     * Overall status and timing of the game: Is it started, ongoing, finished?
     * How long ago did it start and finish?
     *<P>
     * Contents of {@link #getScores()}:
     *<UL>
     * <LI> Game creation time as seconds: {@link System#currentTimeMillis()} / 1000
     * <LI> 1 if has been started (state &gt;= {@link SOCGame#START1A}), otherwise 0
     * <LI> Finish time (or 0 if not over) in same format as start time
     *</UL>
     * Added in v2.7.00 ({@link #VERSION_FOR_TYPE_TIMING}).
     * The stats array {@link #getScores()} stores these seconds as long, not int, to avoid y2038 problems.
     *
     * @see #TYPE_PLAYERS
     * @since 2.7.00
     */
    public static final int TYPE_TIMING = 2;

    /**
     * Name of game
     */
    private String game;

    /**
     * Statistics type: {@link #TYPE_PLAYERS} or {@link #TYPE_TIMING}.
     * @since 2.7.00
     */
    private final int statType;

    /**
     * Player scores or other statistic values; see {@link #getScores()}.
     */
    private final long[] scores;

    /**
     * For {@link #TYPE_PLAYERS}, where robots are sitting; indexed same as scores. {@code null} for other types.
     */
    private final boolean[] robots;

    /**
     * Create a GameStats message of type {@link #TYPE_PLAYERS}.
     *
     * @param ga  the name of the game
     * @param sc  the scores; always indexed 0 to
     *   {@link soc.game.SOCGame#maxPlayers} - 1,
     *   regardless of number of players in the game
     * @param rb  where robots are sitting; indexed same as scores
     * @see #SOCGameStats(String, int, long[])
     */
    public SOCGameStats(String ga, int[] sc, boolean[] rb)
    {
        messageType = GAMESTATS;
        game = ga;
        statType = TYPE_PLAYERS;
        final long[] sco = new long[sc.length];
        for (int i = 0; i < sc.length; ++i)
            sco[i] = sc[i];
        scores = sco;
        robots = rb;
    }

    /**
     * Create a GameStats message of some type which isn't {@link #TYPE_PLAYERS}.
     *
     * @param ga  the name of the game
     * @param stype  the stat type, such as {@link #TYPE_TIMING};
     *     not {@link #TYPE_PLAYERS} which uses a different constructor
     * @param vals  the stat values; length varies by type. When sent from client, can be null or empty.
     *     Constructor makes an empty array if {@code null}.
     * @throws IllegalArgumentException if {@code stype} &lt;= 0 or is {@link #TYPE_PLAYERS}
     * @see #SOCGameStats(String, int[], boolean[])
     * @since 2.7.00
     */
    public SOCGameStats(String ga, int stype, long[] vals)
    {
        if ((stype == TYPE_PLAYERS) || (stype <= 0))
            throw new IllegalArgumentException("stype");
        if (vals == null)
            vals = new long[0];

        messageType = GAMESTATS;
        game = ga;
        statType = stype;
        scores = vals;
        robots = null;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * Get this message's type of game statistics.
     * @return Stats type constant, like {@link #TYPE_PLAYERS} or {@link #TYPE_TIMING}.
     *     Future versions may add more types.
     * @since 2.7.00
     */
    public int getStatType()
    {
        return statType;
    }

    /**
     * Get the stat values.
     * When set from client to server, might be empty (but not null).
     *<UL>
     * <LI> For {@link #TYPE_PLAYERS} these are the player scores,
     *   always indexed 0 to {@link soc.game.SOCGame#maxPlayers} - 1,
     *   regardless of number of players seated in the game.
     *   Vacant seats have a score of 0.
     * <LI> For other stat types like {@link #TYPE_TIMING}, see type javadocs for structure.
     *</UL>
     * In v2.7.00 and newer, return type is {@code long[]} to hold {@link #TYPE_TIMING}'s seconds (unix time epoch)
     * without y2038 problems, and to leave room for large values in possible future stat types.
     * Network interop is OK because the values are sent as strings.
     *
     * @return the stat values
     */
    public long[] getScores()
    {
        return scores;
    }

    /**
     * @return where the robots are sitting, or {@code null} if {@link #getStatType()} != {@link #TYPE_PLAYERS}
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
        StringBuilder sb = new StringBuilder(GAMESTATS + sep + game);

        if (statType != TYPE_PLAYERS)
        {
            sb.append(sep2);
            sb.append('t');
            sb.append(statType);
        }

        for (int i = 0; i < scores.length; i++)
            sb.append(sep2).append(scores[i]);

        if (statType == TYPE_PLAYERS)
            for (int i = 0; i < robots.length; i++)
                sb.append(sep2).append(robots[i]);

        return sb.toString();
    }

    /**
     * Parse the command String into a GameStats message
     *
     * @param s   the String to parse
     * @return    a GameStats message, or null if the data is garbled
     */
    public static SOCGameStats parseDataStr(String s)
    {
        String ga; // the game name

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();

            String tok = st.nextToken();
            char ch = tok.charAt(0);
            if (ch == 't')
            {
                long[] sv;

                ch = tok.charAt(1);
                if ((ch < '0') || (ch > '9'))
                    return null;
                int stype = Integer.parseInt(tok.substring(1));  // stats type
                if (stype == TYPE_PLAYERS)
                    return null;

                final int n = st.countTokens();
                sv = new long[n];

                for (int i = 0; i < n; i++)
                    sv[i] = Long.parseLong(st.nextToken());

                return new SOCGameStats(ga, stype, sv);
            }
            else if ((ch >= '0') && (ch <= '9'))
            {
                // stype = TYPE_PLAYERS
                int[] sc; // the scores
                boolean[] rb; // where robots are sitting

                final int maxPlayers = (st.countTokens() + 1) / 2;
                sc = new int[maxPlayers];
                rb = new boolean[maxPlayers];

                sc[0] = Integer.parseInt(tok);
                for (int i = 1; i < maxPlayers; i++)
                    sc[i] = Integer.parseInt(st.nextToken());

                for (int i = 0; i < maxPlayers; i++)
                    rb[i] = (Boolean.valueOf(st.nextToken())).booleanValue();

                return new SOCGameStats(ga, sc, rb);
            } else {
                return null;
            }
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Strip out and regularize the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link SOCMessage#parseMsgStr(String)}.
     * Changes statType field "2" to "t2".
     * @param message Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     * @since 2.7.00
     */
    public static String stripAttribNames(String message)
    {
        message = message.replace("|stype=", "|stype=t");
        return SOCMessage.stripAttribNames(message);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuilder text = new StringBuilder("SOCGameStats:game=");
        text.append(game);

        if (statType != TYPE_PLAYERS)
            text.append("|stype=").append(statType);

        for (int i = 0; i < scores.length; i++)
        {
            text.append("|");
            text.append(scores[i]);
        }

        if (statType == TYPE_PLAYERS)
            for (int i = 0; i < robots.length; i++)
            {
                text.append("|");
                text.append(robots[i]);
            }

        return text.toString();
    }
}
