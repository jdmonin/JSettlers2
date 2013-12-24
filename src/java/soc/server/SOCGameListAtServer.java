/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009-2013 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import soc.debug.D;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.server.genericServer.StringConnection;
import soc.util.SOCGameBoardReset;
import soc.util.SOCGameList;
import soc.util.Version;


/**
 * A class for creating and tracking the games;
 * contains each game's name, {@link SOCGameOption game options},
 * {@link SOCGame} object, and clients ({@link StringConnection}s).
 *<P>
 * In 1.1.07, parent class SOCGameList was refactored, with
 * some methods moved to this new subclass, such as {@link #createGame(String, String, String, Hashtable) createGame}.
 *
 * @see SOCBoardLargeAtServer
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.07
 */
public class SOCGameListAtServer extends SOCGameList
{
    /**
     * Number of minutes after which a game (created on the list) is expired.
     * Default is 90.
     *
     * @see #createGame(String, String, String, Hashtable)
     * @see SOCServer#checkForExpiredGames(long)
     */
    public static int GAME_EXPIRE_MINUTES = 90;

    /** map of game names to Vector of game members ({@link StringConnection}s) */
    protected Hashtable<String, Vector<StringConnection>> gameMembers;

    /**
     * constructor
     */
    public SOCGameListAtServer()
    {
        super();
        gameMembers = new Hashtable<String, Vector<StringConnection>>();
    }

    /**
     * does the game have no members?
     * @param   gaName  the name of the game
     * @return true if the game exists and has an empty member list
     */
    public synchronized boolean isGameEmpty(String gaName)
    {
        boolean result;
        Vector<StringConnection> members;

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
     * get a game's members (client connections)
     * @param   gaName  game name
     * @return  list of members: a Vector of {@link StringConnection}s
     */
    public synchronized Vector<StringConnection> getMembers(String gaName)
    {
        return gameMembers.get(gaName);
    }

    /**
     * is this connection a member of the game?
     * @param  gaName   the name of the game
     * @param  conn     the member's connection
     * @return true if memName is a member of the game
     */
    public synchronized boolean isMember(StringConnection conn, String gaName)
    {
        Vector<StringConnection> members = getMembers(gaName);

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
    public synchronized void addMember(StringConnection conn, String gaName)
    {
        Vector<StringConnection> members = getMembers(gaName);

        if ((members != null) && (!members.contains(conn)))
        {
            final boolean firstMember = members.isEmpty();
            members.addElement(conn);

            // Check version range
            SOCGame ga = getGameData(gaName);
            final int cliVers = conn.getVersion();
            System.err.println("L139: game " + gaName + " add " + conn +" v=" + conn.getVersion());  // JM TEMP
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
    public synchronized void removeMember(StringConnection conn, String gaName)
    {
        System.err.println("L139: game " + gaName + " remove " + conn);  // JM TEMP
        Vector<StringConnection> members = getMembers(gaName);

        if ((members != null))
        {
            members.removeElement(conn);

            // Check version of remaining members
            if (! members.isEmpty())
            {
                StringConnection c = members.firstElement();
                int lowVers = c.getVersion();
                int highVers = lowVers;
                for (int i = members.size() - 1; i > 1; --i)
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
     *
     * @param  oldConn  the member's old connection
     * @param  oldConn  the member's new connection
     * @throws IllegalArgumentException  if oldConn's keyname (via {@link StringConnection#getData() getData()})
     *            differs from newConn's keyname
     *
     * @see #memberGames(StringConnection, String)
     * @since 1.1.08
     */
    public synchronized void replaceMemberAllGames(StringConnection oldConn, StringConnection newConn)
        throws IllegalArgumentException
    {
        if (! oldConn.getData().equals(newConn.getData()))
            throw new IllegalArgumentException("keyname data");

        System.err.println("L212: replaceMemberAllGames(" + oldConn + ", " + newConn + ")");  // JM TEMP
        final boolean sameVersion = (oldConn.getVersion() == newConn.getVersion());
        for (String gaName : getGameNames())
        {
            Vector<StringConnection> members = gameMembers.get(gaName);
            if ((members != null) && members.contains(oldConn))
            {
                System.err.println("L221: for game " + gaName + ":");  // JM TEMP
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
    }

    /**
     * create a new game, and add to the list; game will expire in {@link #GAME_EXPIRE_MINUTES} minutes.
     * If a game already exists (per {@link #isGame(String)}), do nothing.
     *
     * @param gaName  the name of the game
     * @param gaOwner the game owner/creator's player name, or null (added in 1.1.10)
     * @param gaLocaleStr  the game creator's locale, to later set {@link SOCGame#hasMultiLocales} if needed (added in 2.0.00)
     * @param gaOpts  if game has options, hashtable of {@link SOCGameOption}; otherwise null.
     *                Should already be validated, by calling
     *                {@link SOCGameOption#adjustOptionsToKnown(Map, Map, boolean)}
     *                with <tt>doServerPreadjust</tt> true.
     * @param typeHandler  Game type handler for this game
     * @return new game object, or null if it already existed
     * @throws IllegalArgumentException  if {@code handler} is null
     */
    public synchronized SOCGame createGame
        (final String gaName, final String gaOwner, final String gaLocaleStr,
         Hashtable<String, SOCGameOption> gaOpts, final GameHandler handler)
        throws IllegalArgumentException
    {
        if (isGame(gaName))
            return null;
        if (handler == null)
            throw new IllegalArgumentException("handler");

        // Make sure server games have SOCBoardLargeAtServer, for makeNewBoard.
        // Double-check class in case server is started at client after a client SOCGame.
        if ((SOCGame.boardFactory == null) || ! (SOCGame.boardFactory instanceof SOCBoardLargeAtServer))
            SOCGame.boardFactory = new SOCBoardLargeAtServer.BoardFactoryAtServer();

        Vector<StringConnection> members = new Vector<StringConnection>();
        gameMembers.put(gaName, members);

        SOCGame game = new SOCGame(gaName, gaOpts);
        if (gaOwner != null)
            game.setOwner(gaOwner, gaLocaleStr);

        // set the expiration to 90 min. from now
        game.setExpiration(game.getStartTime().getTime() + (60 * 1000 * GAME_EXPIRE_MINUTES));

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

            // As in createGame, set expiration timer to 90 min. from now
            rgame.setExpiration(new Date().getTime() + (60 * 1000 * GAME_EXPIRE_MINUTES));

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

    /**
     * remove the game from the list
     * and call {@link SOCGame#destroyGame()} via {@link SOCGameList#deleteGame(String)}.
     *
     * @param gaName  the name of the game
     */
    @Override
    public synchronized void deleteGame(String gaName)
    {
        // delete from super first, to destroy game and set its gameDestroyed flag
        // (Removes game from list before dealing with members, in case of locks)
        super.deleteGame(gaName);

        Vector<StringConnection> members = gameMembers.get(gaName);
        if (members != null)
        {
            members.removeAllElements();
        }
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
     * @since 1.1.08
     */
    public int playerGamesMinVersion(StringConnection plConn)
    {
        int minVers = 0;

        synchronized(gameData)
        {
            for (SOCGame ga : getGamesData())
            {
                Vector<StringConnection> members = getMembers(ga.getName());
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
     * @see #replaceMemberAllGames(StringConnection, StringConnection)
     * @since 1.1.08
     */
    public Vector<SOCGame> memberGames(StringConnection c, final String firstGameName)
    {
        Vector<SOCGame> cGames = new Vector<SOCGame>();

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
                        cGames.addElement(firstGame);
                }
            }

            for (SOCGame ga : getGamesData())
            {
                if (ga == firstGame)
                    continue;
                Vector<?> members = getMembers(ga.getName());
                if ((members == null) || ! members.contains(c))
                    continue;

                cGames.addElement(ga);
            }
        }

        return cGames;
    }

    /**
     * Game info including server-side information, such as the game type's {@link GameHandler}.
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 2.0.00
     */
    protected static class GameInfoAtServer extends GameInfo
    {
        public final GameHandler handler;

        /**
         * Constructor, with handler and optional game options.
         * @param gameOpts Hashtable of {@link SOCGameOption}s, or null
         * @param typeHandler  Game type handler for this game
         * @throws IllegalArgumentException  if {@code handler} is null
         */
        public GameInfoAtServer
            (final Hashtable<String,SOCGameOption> gameOpts, final GameHandler typeHandler)
            throws IllegalArgumentException
        {
            super(true, gameOpts);

            if (typeHandler == null)
                throw new IllegalArgumentException("handler");

            handler = typeHandler;
        }

    }

}
