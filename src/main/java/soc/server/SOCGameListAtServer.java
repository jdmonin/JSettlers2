/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009-2014,2016-2019 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003 Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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
package soc.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import soc.debug.D;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.message.SOCDeleteGame;
import soc.message.SOCGames;
import soc.message.SOCGamesWithOptions;
import soc.message.SOCNewGame;
import soc.message.SOCNewGameWithOptions;
import soc.server.genericServer.Connection;
import soc.util.SOCFeatureSet;
import soc.util.SOCGameBoardReset;
import soc.util.SOCGameList;
import soc.util.Version;


/**
 * A class for creating and tracking the games;
 * contains each game's name, {@link SOCGameOption game options},
 * {@link SOCGame} object, member client {@link Connection}s, and
 * {@link SOCChatRecentBuffer}.
 *<P>
 * In 1.1.07, parent class SOCGameList was refactored, with
 * some methods moved to this new subclass, such as
 * {@link #createGame(String, String, String, Map, GameHandler) createGame}.
 *
 * @see SOCBoardAtServer
 * @see SOCChannelList
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.07
 */
public class SOCGameListAtServer extends SOCGameList
{
    /**
     * Number of minutes after which a game (created on the list) is expired.
     * Default is 120.
     *<P>
     * Before v2.0.00 this field was named {@code GAME_EXPIRE_MINUTES}. <BR>
     * Before v1.2.01 the default was 90.
     *
     * @see #createGame(String, String, String, Map, GameHandler)
     * @see SOCGame#setExpiration(long)
     * @see SOCServer#checkForExpiredGames(long)
     */
    public static int GAME_TIME_EXPIRE_MINUTES = 120;

    /**
     * Synchronized map of game names to {@link SOCGame} objects.
     *<P>
     * Before v2.0.00 this field was in parent class {@link SOCGameList} but only the Server used it.
     * @see SOCGameList#gameInfo
     */
    private final Hashtable<String, SOCGame> gameData;

    /** synchronized map of game names to Vector of game members ({@link Connection}s) */
    protected final Hashtable<String, Vector<Connection>> gameMembers;

    /**
     * Each game's buffer of recent chat text.
     * @since 2.0.00
     */
    protected final Hashtable<String, SOCChatRecentBuffer> gameChatBuffer;

    /**
     * constructor
     */
    public SOCGameListAtServer()
    {
        super();

        gameData = new Hashtable<String, SOCGame>();
        gameMembers = new Hashtable<String, Vector<Connection>>();
        gameChatBuffer = new Hashtable<String, SOCChatRecentBuffer>();
    }

    /**
     * does the game have no members?
     * @param   gaName  the name of the game
     * @return true if the game exists and has an empty member list
     */
    public synchronized boolean isGameEmpty(String gaName)
    {
        boolean result;
        Vector<Connection> members;

        members = gameMembers.get(gaName);

        if ((members != null) && (members.isEmpty()))
        {
            result = true;
        }
        else
        {
            result = false;
        }

        return result;
    }

    /**
     * Get a game's SOCGame, if we've stored that.
     *<P>
     * Before v2.0.00 this method was in parent class {@link SOCGameList}.
     *
     * @param gaName  game name
     * @return the game object data, or null
     * @see #getGamesData()
     */
    public SOCGame getGameData(String gaName)
    {
        return gameData.get(gaName);
    }

    /**
     * Get all the {@link SOCGame} data available; some of the games in {@link SOCGameList#getGameNames()}
     * may not have associated SOCGame data, so this enumeration may have fewer
     * elements than {@code SOCGameList#getGameNames()} or even 0 elements.
     *<P>
     * Before v2.0.00 this method was in parent class {@link SOCGameList}.
     *
     * @return an enumeration of game data ({@code SOCGame}s)
     * @see SOCGameList#getGameNames()
     * @see #getGameData(String)
     * @since 1.1.06
     */
    public Collection<SOCGame> getGamesData()
    {
        return gameData.values();
    }

    /**
     * Get this game's type handler from its {@link GameInfoAtServer}.
     * @param gaName  Game name
     * @return  handler, or {@code null} if game unknown or its GameInfo doesn't have a handler
     * @since 2.0.00
     */
    public GameHandler getGameTypeHandler(final String gaName)
    {
        GameInfo gi = gameInfo.get(gaName);
        if ((gi == null) || ! (gi instanceof GameInfoAtServer))
            return null;

        return ((GameInfoAtServer) gi).handler;
    }

    /**
     * Get this game's type inbound message handler from its {@link GameInfoAtServer}.
     * @param gaName  Game name
     * @return  handler, or {@code null} if game unknown or its GameInfo doesn't have a handler
     * @since 2.0.00
     */
    public GameMessageHandler getGameTypeMessageHandler(final String gaName)
    {
        GameInfo gi = gameInfo.get(gaName);
        if ((gi == null) || ! (gi instanceof GameInfoAtServer))
            return null;

        return ((GameInfoAtServer) gi).messageHandler;
    }

    /**
     * Get a game's recent-chat buffer.
     * @param gaName  Game name
     * @return  Game's chat buffer
     * @since 2.0.00
     */
    public SOCChatRecentBuffer getChatBuffer(final String gaName)
    {
        return gameChatBuffer.get(gaName);
    }

    /**
     * get a game's members (client connections)
     * @param   gaName  game name
     * @return  list of members: a Vector of {@link Connection}s
     */
    public synchronized Vector<Connection> getMembers(String gaName)
    {
        return gameMembers.get(gaName);
    }

    /**
     * is this connection a member of the game?
     * @param  gaName   the name of the game
     * @param  conn     the member's connection
     * @return true if memName is a member of the game
     */
    public synchronized boolean isMember(Connection conn, String gaName)
    {
        Vector<Connection> members = getMembers(gaName);

        if ((members != null) && (members.contains(conn)))
            return true;
        else
            return false;
    }

    /**
     * add a member to the game.
     * Also checks client's version against game's current range of client versions.
     * Please call {@link #takeMonitorForGame(String)} before calling this.
     *
     * @param  gaName   the name of the game
     * @param  conn     the member's connection; version should already be set
     */
    public synchronized void addMember(Connection conn, String gaName)
    {
        Vector<Connection> members = getMembers(gaName);

        if ((members != null) && (!members.contains(conn)))
        {
            final boolean firstMember = members.isEmpty();
            members.addElement(conn);

            // Check version range
            SOCGame ga = getGameData(gaName);
            final int cliVers = conn.getVersion();
            if (firstMember)
            {
                ga.clientVersionLowest = cliVers;
                ga.clientVersionHighest = cliVers;
                ga.hasOldClients = (cliVers < Version.versionNumber());
            }
            else
            {
                final int cliLowestAlready  = ga.clientVersionLowest;
                final int cliHighestAlready = ga.clientVersionHighest;
                if (cliVers < cliLowestAlready)
                {
                    ga.clientVersionLowest = cliVers;
                    if (cliVers < Version.versionNumber())
                        ga.hasOldClients = true;
                }
                if (cliVers > cliHighestAlready)
                {
                    ga.clientVersionHighest = cliVers;
                }
            }

            if (! ga.hasMultiLocales)
            {
                final String gaLocale = ga.getOwnerLocale();
                if (gaLocale != null)
                {
                    final SOCClientData scd = (SOCClientData) conn.getAppData();
                    if ((scd != null) && (scd.localeStr != null) && ! gaLocale.equals(scd.localeStr))
                        ga.hasMultiLocales = true;  // client's locale differs from other game members'
                }
            }
        }
    }

    /**
     * remove member from the game.
     * Also updates game's client version range, with remaining connected members.
     * Please call {@link #takeMonitorForGame(String)} before calling this.
     *
     * @param  gaName   the name of the game
     * @param  conn     the member's connection
     */
    public synchronized void removeMember(Connection conn, String gaName)
    {
        Vector<Connection> members = getMembers(gaName);

        if ((members != null))
        {
            members.removeElement(conn);

            // Check version of remaining members
            if (! members.isEmpty())
            {
                Connection c = members.firstElement();
                int lowVers = c.getVersion();
                int highVers = lowVers;

                for (int i = members.size() - 1; i >= 1; --i)
                {
                    c = members.elementAt(i);
                    int v = c.getVersion();
                    if (v < lowVers)
                        lowVers = v;
                    if (v > highVers)
                        highVers = v;
                }

                SOCGame ga = getGameData(gaName);
                ga.clientVersionLowest  = lowVers;
                ga.clientVersionHighest = highVers;
                ga.hasOldClients = (lowVers < Version.versionNumber());
            }
        }
    }

    /**
     * Replace member from all games, with a new connection with same name (after a network problem).
     *<P>
     * Assumes {@link #playerGamesMinVersion(Connection)} has already been called to validate {@code newConn} can join
     * all of {@code oldConn}'s games.
     *<P>
     * The newly joining client is almost always using the same release as the old client. It's possible but
     * very unlikely that the new client is different software with more limited client features. This method checks
     * {@code newConn}'s {@link SOCClientData#hasLimitedFeats} and each game's {@link SOCGame#canClientJoin(SOCFeatureSet)}
     * just in case. If {@code newConn} can't join a game because of this, the game is added to the list returned from
     * this method.
     *
     * @param  oldConn  the member's old connection
     * @param  newConn  the member's new connection
     * @return  {@code null} if replacement was done in all games; otherwise those games having {@code oldConn} which
     *            {@code newConn} can't join because of its more limited client features (this is unlikely to occur)
     * @throws IllegalArgumentException  if oldConn's keyname (via {@link Connection#getData()})
     *            differs from newConn's keyname
     *
     * @see #memberGames(Connection, String)
     * @since 1.1.08
     */
    public synchronized List<SOCGame> replaceMemberAllGames(Connection oldConn, Connection newConn)
        throws IllegalArgumentException
    {
        if (! oldConn.getData().equals(newConn.getData()))
            throw new IllegalArgumentException("keyname data");

        List<SOCGame> unjoinables = new ArrayList<SOCGame>();

        System.err.println("L212: replaceMemberAllGames(" + oldConn + ", " + newConn + ")");  // JM TEMP
        final boolean sameVersion = (oldConn.getVersion() == newConn.getVersion());
        final SOCClientData scd = (SOCClientData) newConn.getAppData();
        final boolean cliHasLimitedFeats = scd.hasLimitedFeats;
        for (String gaName : getGameNames())
        {
            Vector<Connection> members = gameMembers.get(gaName);
            if ((members != null) && members.contains(oldConn))
            {
                System.err.println("L221: for game " + gaName + ":");  // JM TEMP

                if (cliHasLimitedFeats)
                {
                    SOCGame ga = getGameData(gaName);
                    if ((ga != null) && ! ga.canClientJoin(scd.feats))
                    {
                        // new client can't join this game (unlikely)
                        unjoinables.add(ga);
                        continue;
                    }
                }

                if (sameVersion)
                {
                    if (members.remove(oldConn))
                        System.err.println("   OK");
                    else
                        System.err.println("   ** not found");
                    members.addElement(newConn);
                } else {
                    removeMember(oldConn, gaName);
                    addMember(newConn, gaName);
                }
            }
        }

        return (unjoinables.isEmpty()) ? null : unjoinables;
    }

    /**
     * create a new game, and add to the list; game will expire in {@link #GAME_TIME_EXPIRE_MINUTES} minutes.
     * If a game already exists (per {@link #isGame(String)}), do nothing.
     *
     * @param gaName  the name of the game
     * @param gaOwner the game owner/creator's player name, or null (added in 1.1.10)
     * @param gaLocaleStr  the game creator's locale, to later set {@link SOCGame#hasMultiLocales} if needed (added in 2.0.00)
     * @param gaOpts  if game has options, its {@link SOCGameOption}s; otherwise null.
     *                Must already be validated, by calling
     *                {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean)}
     *                with <tt>doServerPreadjust</tt> true.
     *                That call is also needed to add any {@code "SC"} options into {@code gaOpts}.
     * @param handler  Game type handler for this game
     * @return new game object, or null if it already existed
     * @throws IllegalArgumentException  if {@code handler} is null
     */
    public synchronized SOCGame createGame
        (final String gaName, final String gaOwner, final String gaLocaleStr,
         final Map<String, SOCGameOption> gaOpts, final GameHandler handler)
        throws IllegalArgumentException
    {
        if (isGame(gaName))
            return null;
        if (handler == null)
            throw new IllegalArgumentException("handler");

        // Make sure server games have SOCBoardAtServer, for makeNewBoard.
        // Double-check class in case server is started at client after a client SOCGame.
        if ((SOCGame.boardFactory == null) || ! (SOCGame.boardFactory instanceof SOCBoardAtServer.BoardFactoryAtServer))
            SOCGame.boardFactory = new SOCBoardAtServer.BoardFactoryAtServer();

        Vector<Connection> members = new Vector<Connection>();
        gameMembers.put(gaName, members);
        gameChatBuffer.put(gaName, new SOCChatRecentBuffer());

        SOCGame game = new SOCGame(gaName, gaOpts);
        if (gaOwner != null)
            game.setOwner(gaOwner, gaLocaleStr);

        game.setExpiration(game.getStartTime().getTime() + (60 * 1000 * GAME_TIME_EXPIRE_MINUTES));

        handler.calcGameClientFeaturesRequired(game);
        gameInfo.put(gaName, new GameInfoAtServer(game.getGameOptions(), handler));  // also creates MutexFlag
        gameData.put(gaName, game);

        return game;
    }

    /**
     * Reset the board of this game, create a new game of same name,
     * same players, new layout.  The new "reset" board takes the place
     * of the old game in the game list.
     *<P>
     * Robots are not copied and
     * must re-join the game. (They're removed from the list of game members.)
     * If the game had robots, they must leave the old game before any players can
     * join the new game; the new game's {@link SOCGame#boardResetOngoingInfo} field
     * is set to the object returned by this method, and its gameState will be
     * {@link SOCGame#READY_RESET_WAIT_ROBOT_DISMISS} instead of {@link SOCGame#NEW}.
     *<P>
     * <b>Locking:</b>
     * Takes game monitor.
     * Copies old game.
     * Adds reset-copy to gamelist.
     * Destroys old game.
     * Releases game monitor.
     *
     * @param gaName Name of game - If not found, do nothing. No monitor is taken.
     * @return New game if gaName was found and copied; null if no game called gaName,
     *         or if a problem occurs during reset
     * @see soc.game.SOCGame#resetAsCopy()
     */
    public SOCGameBoardReset resetBoard(String gaName)
    {
        SOCGame oldGame = gameData.get(gaName);
        if (oldGame == null)
            return null;

        takeMonitorForGame(gaName);

        // Create reset-copy of game;
        // also removes robots from game obj and its member list,
        // and sets boardResetOngoingInfo field/gamestate if there are robots.
        SOCGameBoardReset reset = null;
        try
        {
            reset = new SOCGameBoardReset(oldGame, getMembers(gaName));
            SOCGame rgame = reset.newGame;

            // As in createGame, set expiration timer
            rgame.setExpiration(System.currentTimeMillis() + (60 * 1000 * GAME_TIME_EXPIRE_MINUTES));

            // Adjust game-list
            gameData.remove(gaName);
            gameData.put(gaName, rgame);

            // Done.
            oldGame.destroyGame();
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "ERROR -> gamelist.resetBoard");
        }
        finally
        {
            releaseMonitorForGame(gaName);
        }

        return reset;  // null if error during reset
    }

    @Override
    public synchronized void addGames(SOCGameList gl, final int ourVersion)
    {
        if (gl == null)
            return;

        if (gl instanceof SOCGameListAtServer)
        {
            Hashtable<String, SOCGame> gdata = ((SOCGameListAtServer) gl).gameData;
            if (gdata != null)
                addGames(gdata.values(), ourVersion);
        }

        super.addGames(gl, ourVersion);
    }

    /**
     * Call {@link SOCGame#destroyGame()} and remove the game from the list.
     *
     * @param gaName  the name of the game
     */
    @Override
    public synchronized void deleteGame(String gaName)
    {
        SOCGame game = gameData.get(gaName);
        if (game != null)
        {
            game.destroyGame();
            gameData.remove(gaName);
        }

        // delete from super to destroy GameInfo and set its gameDestroyed flag
        // (Removes game from list before dealing with members, in case of locks)
        super.deleteGame(gaName);

        Vector<Connection> members = gameMembers.get(gaName);
        if (members != null)
        {
            members.removeAllElements();
        }

        SOCChatRecentBuffer buf = gameChatBuffer.remove(gaName);
        if (buf != null)
            buf.clear();
    }

    /**
     * For the games this player is in, what's the
     * minimum required client version?
     * Checks {@link SOCGame#getClientVersionMinRequired()}.
     *<P>
     * This method helps determine if a client's connection can be
     * "taken over" after a network problem.  It synchronizes on <tt>gameData</tt>.
     *
     * @param  plConn   the previous connection of the player, which might be taken over
     * @return Minimum version, in same format as {@link SOCGame#getClientVersionMinRequired()},
     *         or 0 if player isn't in any games.
     * @see #replaceMemberAllGames(Connection, Connection)
     * @since 1.1.08
     */
    public int playerGamesMinVersion(Connection plConn)
    {
        int minVers = 0;

        synchronized(gameData)
        {
            for (SOCGame ga : getGamesData())
            {
                Vector<Connection> members = getMembers(ga.getName());
                if ((members == null) || ! members.contains(plConn))
                    continue;

                // plConn is a member of this game.
                int vers = ga.getClientVersionMinRequired();
                if (vers > minVers)
                    minVers = vers;
            }
        }

        return minVers;
    }

    /**
     * List of games containing this member.
     *
     * @param c  Connection
     * @param firstGameName  Game name that should be first element of list
     *           (if <tt>newConn</tt> is a member of it), or null.
     * @return The games, in no particular order (past firstGameName),
     *           or a 0-length Vector, if member isn't in any game.
     *
     * @see #replaceMemberAllGames(Connection, Connection)
     * @since 1.1.08
     */
    public List<SOCGame> memberGames(Connection c, final String firstGameName)
    {
        List<SOCGame> cGames = new ArrayList<SOCGame>();

        synchronized(gameData)
        {
            SOCGame firstGame = null;
            if (firstGameName != null)
            {
                firstGame = getGameData(firstGameName);
                if (firstGame != null)
                {
                    Vector<?> members = getMembers(firstGameName);
                    if ((members != null) && members.contains(c))
                        cGames.add(firstGame);
                }
            }

            for (SOCGame ga : getGamesData())
            {
                if (ga == firstGame)
                    continue;
                Vector<?> members = getMembers(ga.getName());
                if ((members == null) || ! members.contains(c))
                    continue;

                cGames.add(ga);
            }
        }

        return cGames;
    }

    /**
     * Send the entire list of games to this client; this is sent once per connecting client.
     * Or, send the set of changed games, if the client's guessed version was wrong.
     * The list includes a flag on games which can't be joined by this client version
     * ({@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}).
     *<P>
     * If <b>entire list</b>, then depending on client's version, the message sent will be
     * either {@link SOCGames GAMES} or {@link SOCGamesWithOptions GAMESWITHOPTIONS}.
     * If <b>set of changed games</b>, sent as matching pairs of {@link SOCDeleteGame DELETEGAME}
     * and either {@link SOCNewGame NEWGAME} or {@link SOCNewGameWithOptions NEWGAMEWITHOPTIONS}.
     *<P>
     * There are 2 possible scenarios for when this method is called:
     *<P>
     * - (A) Sending game list to client, for the first time:
     *    Iterate through all games, looking for ones the client's version
     *    is capable of joining.  If not capable, mark the game name as such
     *    before sending it to the client.  (As a special case, very old
     *    client versions "can't know" about the game they can't join, because
     *    they don't recognize the marker.)
     *    Also set the client data's hasSentGameList flag.
     *<P>
     * - (B) The client didn't give its version, and was thus
     *    identified as an old version.  Now we know its newer true version,
     *    so we must tell it about games that it can now join,
     *    which couldn't have been joined by the older assumed version.
     *    So:  Look for games with those criteria.
     *<P>
     * Sending the list is done here, and not in newConnection2, because we must first
     * know the client's version.
     *<P>
     * The minimum version which recognizes the "can't join" marker is
     * 1.1.06 ({@link SOCGames#VERSION_FOR_UNJOINABLE}).  Older clients won't be sent
     * the game names they can't join.
     *<P>
     * <b>Locks:</b> Calls {@link #takeMonitor()} / {@link #releaseMonitor()}
     *<P>
     * Before v2.0.00 this method was <tt>{@link SOCServer}.sendGameList(..)</tt>.
     *
     * @param c Client's connection; will call getVersion() on it
     * @param prevVers  Previously assumed version of this client;
     *                  if re-sending the list, should be less than c.getVersion.
     * @since 1.1.06
     */
    public void sendGameList(Connection c, int prevVers)
    {
        final int cliVers = c.getVersion();   // Need to know this before sending
        final SOCClientData scd = (SOCClientData) c.getAppData();

        // Before send list of games, try for a client version.
        // Give client 1.2 seconds to send it, before we assume it's old
        // (too old to know VERSION).
        // This waiting is done from SOCClientData.setVersionTimer;
        // time to wait is SOCServer.CLI_VERSION_TIMER_FIRE_MS.

        // GAMES / GAMESWITHOPTIONS

        // Based on version:
        // If client is too old (< 1.1.06), it can't be told names of games
        // that it isn't capable of joining.
        boolean cliCanKnow = (cliVers >= SOCGames.VERSION_FOR_UNJOINABLE);
        final boolean cliCouldKnow = (prevVers >= SOCGames.VERSION_FOR_UNJOINABLE);
        final boolean cliNotLimitedFeats = ! scd.hasLimitedFeats;
        final SOCFeatureSet cliLimitedFeats = cliNotLimitedFeats ? null : scd.feats;

        ArrayList<Object> gl = new ArrayList<Object>();  // contains Strings and/or SOCGames;
                                   // strings are names of unjoinable games,
                                   // with the UNJOINABLE prefix.
        takeMonitor();

        // Note this flag now, while gamelist monitor is held
        final boolean alreadySent = scd.hasSentGameList();
        boolean cliVersionChange = alreadySent && (cliVers > prevVers);

        if (alreadySent && ! cliVersionChange)
        {
            releaseMonitor();

            return;  // <---- Early return: Nothing to do ----
        }

        if (! alreadySent)
        {
            scd.setSentGameList();  // Set while gamelist monitor is held
        }

        /**
         * We release the monitor as soon as we can, even though we haven't yet
         * sent the list to the client.  It's theoretically possible the client will get
         * a NEWGAME message, which is OK, or a DELETEGAME message, before it receives the list
         * we're building.
         * NEWGAME is OK because the GAMES message won't clear the list contents at client.
         * DELETEGAME is less OK, but it's not very likely.
         * If the game is deleted, and then they see it in the list, trying to join that game
         * will create a new empty game with that name.
         */
        Collection<SOCGame> gaEnum = getGamesData();
        releaseMonitor();

        if (cliVersionChange && cliCouldKnow)
        {
            // If they already have the names of games they can't join,
            // no need to re-send those names.
            cliCanKnow = false;
        }

        try
        {
            // Build the list of game names.  This loop is used for the
            // initial list, or for sending just the delta after the version fix.

            for (SOCGame g : gaEnum)
            {
                int gameVers = g.getClientVersionMinRequired();

                if (cliVersionChange && (prevVers >= gameVers))
                {
                    continue;  // No need to re-announce, they already
                               // could join it with lower (prev-assumed) version
                }

                if ((cliVers >= gameVers)
                    && (cliNotLimitedFeats || g.canClientJoin(cliLimitedFeats)))
                {
                    gl.add(g);  // Can join
                } else if (cliCanKnow)
                {
                    //  Cannot join, but can see it
                    StringBuffer sb = new StringBuffer();
                    sb.append(SOCGames.MARKER_THIS_GAME_UNJOINABLE);
                    sb.append(g.getName());
                    gl.add(sb.toString());
                }
                // else
                //   can't join, and won't see it

            }

            // We now have the list of game names / socgame objs.

            if (! alreadySent)
            {
                // send the full list as 1 message
                if (cliVers >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
                    c.put(SOCGamesWithOptions.toCmd(gl, cliVers));
                else
                    c.put(SOCGames.toCmd(gl));
            } else {
                // send deltas only
                for (int i = 0; i < gl.size(); ++i)
                {
                    Object ob = gl.get(i);
                    String gaName;
                    if (ob instanceof SOCGame)
                        gaName = ((SOCGame) ob).getName();
                    else
                        gaName = (String) ob;

                    if (cliCouldKnow)
                    {
                        // first send delete, if it's on their list already
                        c.put(SOCDeleteGame.toCmd(gaName));
                    }
                    // announce as 'new game' to client
                    if ((ob instanceof SOCGame) && (cliVers >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS))
                        c.put(SOCNewGameWithOptions.toCmd((SOCGame) ob, cliVers));
                    else
                        c.put(SOCNewGame.toCmd(gaName));
                }
            }
        }
        catch (Exception e)
        {
            D.ebugPrintStackTrace(e, "Exception in GLAS.sendGameList");
        }
    }

    /**
     * Game info including server-side information, such as the game type's {@link GameHandler}
     * and {@link GameMessageHandler}.
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.0.00
     */
    protected static class GameInfoAtServer extends GameInfo
    {
        /** Game type handler */
        public final GameHandler handler;

        /** {@link #handler}'s inbound message handler, denormalized from {@link GameHandler#getMessageHandler()} */
        public final GameMessageHandler messageHandler;

        /**
         * Constructor, with handler and optional game options.
         * @param gameOpts  Game's {@link SOCGameOption}s, or null
         * @param typeHandler  Game type handler for this game
         * @throws IllegalArgumentException  if {@code handler} is null
         */
        public GameInfoAtServer
            (final Map<String, SOCGameOption> gameOpts, final GameHandler typeHandler)
            throws IllegalArgumentException
        {
            super(true, gameOpts);

            if (typeHandler == null)
                throw new IllegalArgumentException("handler");

            handler = typeHandler;
            messageHandler = handler.getMessageHandler();
        }

    }

}
