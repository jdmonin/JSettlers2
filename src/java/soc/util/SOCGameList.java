/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2008-2012 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net> - getGameNames, parameterize types
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
package soc.util;

import soc.disableDebug.D;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.message.SOCGames;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Set;


/**
 * A class for creating and tracking the games;
 * contains each game's name, {@link SOCGameOption game options},
 * {@link SOCGame} object, and mutex for synchronization.
 *<P>
 * In 1.1.07, moved from soc.server to soc.util package for client's use.
 * Some methods moved to new subclass {@link soc.server.SOCGameListAtServer}.
 * That subclass also tracks each game's clients ({@link soc.server.genericServer.StringConnection}s).
 *<P>
 * The client-side addGame methods allow game names to have a prefix which marks them
 * as unjoinable by the client ({@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}).
 * If the game name has this prefix, its {@link GameInfo#canJoin} flag is set to false,
 * as queried by {@link #isUnjoinableGame(String)}.  The prefix is stripped within addGame,
 * and not stored as part of the game name in this list.
 * Besides addGame, never supply this prefix to a SOCGameList method taking a game name;
 * supply the game name without the prefix.
 *
 * @author Robert S. Thomas
 */
public class SOCGameList
{
    /** key = String, value = {@link GameInfo}; includes mutexes to synchronize game state access,
     *  game options, and other per-game info
     */
    protected Hashtable<String, GameInfo> gameInfo;

    /** map of game names to {@link SOCGame} objects */
    protected Hashtable<String, SOCGame> gameData;

    /** used with gamelist's monitor */
    protected boolean inUse;

    /**
     * constructor
     */
    public SOCGameList()
    {
        gameInfo = new Hashtable<String, GameInfo>();
        gameData = new Hashtable<String, SOCGame>();
        inUse = false;
    }

    /**
     * take the monitor for this game list; if we must wait, sleep up to 1000 ms between attempts.
     */
    public synchronized void takeMonitor()
    {
        // D.ebugPrintln("SOCGameList : TAKE MONITOR");

        while (inUse)
        {
            try
            {
                wait(1000);
            }
            catch (InterruptedException e)
            {
                System.out.println("EXCEPTION IN takeMonitor() -- " + e);
            }
        }

        inUse = true;
    }

    /**
     * release the monitor for this game list
     */
    public synchronized void releaseMonitor()
    {
        // D.ebugPrintln("SOCGameList : RELEASE MONITOR");
        inUse = false;
        this.notify();
    }

    /**
     * take the monitor for this game.
     * When done with it, you must call {@link #releaseMonitorForGame(String)}.
     *
     * @param game  the name of the game
     * @return false if the game has no mutex, or game not found in the list,
     *   or {@link GameInfo#gameDestroyed} is true
     */
    public boolean takeMonitorForGame(String game)
    {
        // D.ebugPrintln("SOCGameList : TAKE MONITOR FOR " + game);

        GameInfo info = gameInfo.get(game);
        if ((info == null) || info.gameDestroyed)
        {
            return false;
        }
        MutexFlag mutex = info.mutex;

        if (mutex == null)
        {
            return false;
        }

        boolean done = false;

        while (!done)
        {
            if (mutex == null)
            {
                return false;
            }
            if (info.gameDestroyed)
            {
                // Debug print is JM temp add: (TODO)
                soc.debug.D.ebugPrintStackTrace(null, "Game " + game + " was destroyed while waiting");
                return false;
            }

            synchronized (mutex)
            {
                if (mutex.getState() == true)
                {
                    try
                    {
                        mutex.wait(1000);
                    }
                    catch (InterruptedException e)
                    {
                        System.out.println("EXCEPTION IN takeMonitor() -- " + e);
                    }
                }
                else
                {
                    done = true;
                }
            }
        }

        mutex.setState(true);

        return true;
    }

    /**
     * Release the monitor for this game,
     * recently taken by {@link #takeMonitorForGame(String)}.
     *<P>
     * Release is allowed even if {@link GameInfo#gameDestroyed} is true.
     *
     * @param game  the name of the game
     * @return false if the game has no mutex
     */
    public boolean releaseMonitorForGame(String game)
    {
        // D.ebugPrintln("SOCGameList : RELEASE MONITOR FOR " + game);

        GameInfo info = gameInfo.get(game);
        if (info == null)
            return false;
        MutexFlag mutex = info.mutex;

        if (mutex == null)
        {
            return false;
        }

        synchronized (mutex)
        {
            mutex.setState(false);
            mutex.notify();
        }

        return true;
    }

    /**
     * Get the names of every game we know about, even those with no {@link SOCGame} object.
     * @return an set of game names (Strings)
     * @see #getGamesData()
     * @since 2.0.00
     */
    public Set<String> getGameNames()
    {
        return gameInfo.keySet();
    }

    /**
     * Get all the {@link SOCGame} data available; some games in {@link #getGames()}
     * may not have associated SOCGame data, so this enumeration may have fewer
     * elements than getGames, or even 0 elements.
     * @return an enumeration of game data (SOCGames)
     * @see #getGames()
     * @see #getGameNames()
     * @since 1.1.06
     */
    public Collection<SOCGame> getGamesData()
    {
        return gameData.values();
    }

    /**
     * the number of games in our list
     * @return the number of games in our list
     * @since 1.1.07
     */
    public int size()
    {
        return gameInfo.size();
    }

    /**
     * get a game's SOCGame, if we've stored that
     * @param   gaName  game name
     * @return the game object data, or null
     */
    public SOCGame getGameData(String gaName)
    {
        return gameData.get(gaName);
    }

    /**
     * get a game's {@link SOCGameOption}s, if stored and parsed
     * @param   gaName  game name
     * @return the game options (hashtable of {@link SOCGameOption}), or null if none or if unparsed
     * @see #getGameOptionsString(String)
     * @see #parseGameOptions(String)
     * @since 1.1.07
     */
    public Hashtable<String,SOCGameOption> getGameOptions(String gaName)
    {
        GameInfo info = gameInfo.get(gaName);
        if (info == null)
            return null;
        else
            return info.opts;
    }

    /**
     * get a game's {@link SOCGameOption}s, as a packed string
     * @param   gaName  game name
     * @return the game options string, or null if no packed version
     * @see #getGameOptions(String)
     * @since 1.1.07
     */
    public String getGameOptionsString(String gaName)
    {
        GameInfo info = gameInfo.get(gaName);
        if (info == null)
            return null;
        else
            return info.optsStr;
    }

    /**
     * Parse these game options from string to hashtable.
     * Should not be called at client before any updates to "known options" are received from server.
     * @param   gaName  game name
     * @return the game options (hashtable of {@link SOCGameOption}), or null if none
     * @see #getGameOptionsString(String)
     * @since 1.1.07
     */
    public Hashtable<String,SOCGameOption> parseGameOptions(String gaName)
    {
        GameInfo info = gameInfo.get(gaName);
        if (info == null)
            return null;
        else
            return info.parseOptsStr();
    }

    /**
     * does this game exist in our list?
     * @param   gaName  the name of the game
     * @return true if the game exists
     */
    public boolean isGame(String gaName)
    {
        return (gameInfo.get(gaName) != null);
    }

    /**
     * does this game have the unjoinable flag, either in its game info in our GameList,
     *     or by a special prefix in its name string?
     * @param   gaName  the name of the game;  may be marked with the prefix
     *         {@link soc.message.SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     *         Remember that the prefix is not stored as part of the game name in this list,
     *         so it's not necessary to add the prefix when calling this method
     *         about a game already in our list.
     * @return true if the game is in our list marked as not joinable,
     *        or has the prefix
     * @since 1.1.07
     */
    public boolean isUnjoinableGame(String gaName)
    {
        if (gaName.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE)
            return true;
        GameInfo gi = gameInfo.get(gaName);
        if (gi == null)
            return false;
        return ! gi.canJoin;
    }

    /**
     * Client-side - Add this game name, with game options.
     * If a game already exists (per {@link #isGame(String)}), at most clear its canJoin flag.
     *<P>
     * Server should instead call {@link soc.server.SOCGameListAtServer#createGame(String, Hashtable)}.
     *
     * @param gaName Name of added game; may be marked with the prefix
     *         {@link soc.message.SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     * @param gaOptsStr set of {@link SOCGameOption}s as packed by
     *         {@link SOCGameOption#packOptionsToString(Hashtable, boolean)}, or null.
     *         Game options should remain unparsed as late as possible.
     * @param cannotJoin This game is unjoinable, even if its name doesn't
     *         start with the unjoinable prefix.
     *         gaName will be checked for the prefix regardless of cannotJoin's value.
     * @since 1.1.07
     */
    public synchronized void addGame(String gaName, String gaOptsStr, boolean cannotJoin)
    {
        addGame(gaName, null, gaOptsStr, cannotJoin);
    }

    /**
     * Internal use - Add this game name, with game options.
     * If a game already exists (per {@link #isGame(String)}), at most clear its canJoin flag.
     * Supply gaOpts or gaOptsStr, not both.
     *<P>
     * Client should instead call {@link #addGame(String, String, boolean)} because game options should
     * remain unparsed as late as possible.
     * Server should instead call {@link soc.server.SOCGameListAtServer#createGame(String, String, Hashtable)}.
     *
     * @param gaName Name of added game; may be marked with the prefix
     *         {@link soc.message.SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     * @param gaOpts Hashtable of {@link SOCGameOption game options} of added game, or null
     * @param gaOptsStr set of {@link SOCGameOption}s as packed by
     *         {@link SOCGameOption#packOptionsToString(Hashtable, boolean)}, or null.
     *         Game options should remain unparsed as late as possible.
     * @param cannotJoin This game is unjoinable, even if its name doesn't
     *         start with the unjoinable prefix.
     *         gaName will be checked for the prefix regardless of cannotJoin's value.
     * @see #addGames(SOCGameList, int)
     * @see #addGames(Enumeration, int)
     * @since 1.1.07
     */
    protected synchronized void addGame(String gaName, Hashtable<String, SOCGameOption> gaOpts, String gaOptsStr, boolean cannotJoin)
    {
        if (gaName.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE)
        {
            cannotJoin = true;
            gaName = gaName.substring(1);
        }

        if (isGame(gaName))
        {
            if (cannotJoin)
            {
                GameInfo gi = gameInfo.get(gaName);
                if (gi.canJoin)
                    gi.canJoin = false;
            }
            return;
        }

        if (gaOpts != null)
            gameInfo.put(gaName, new GameInfo(! cannotJoin, gaOpts));
        else
            gameInfo.put(gaName, new GameInfo(! cannotJoin, gaOptsStr));
    }

    /**
     * Add the contents of another GameList to this GameList.
     * Calls addGame for each one.
     * gl's {@link SOCGame}s will be added first, followed by games for which we only know
     * the name and options.
     * @param gl Another SOCGameList from which to copy game data.
     *          If gl is null, nothing happens.
     *          If any game already exists here (per this.{@link #isGame(String)}), don't overwrite it.
     * @param ourVersion Version to check to see if we can join,
     *          same format as {@link soc.util.Version#versionNumber()}.
     *          For each SOCGame in gl, {@link SOCGame#getClientVersionMinRequired()}
     *          will be called.
     * @since 1.1.07
     */
    public synchronized void addGames(SOCGameList gl, final int ourVersion)
    {
        if ((gl == null) || (gl.gameInfo == null))
            return;
        if (gl.gameData != null)
            addGames(gl.gameData.values(), ourVersion);
        if (gl.gameInfo != null)
        {
            // add games, and/or update canJoin flag of games added via gameData.
            for (String gaName : gl.gameInfo.keySet())
            {
                GameInfo gi = gl.gameInfo.get(gaName);
                if (gi.opts != null)
                    addGame(gaName, gi.opts, null, ! gi.canJoin);
                else
                    addGame(gaName, null, gi.optsStr, ! gi.canJoin);
            }
        }
    }

    /**
     * Add several games to this GameList.
     * Calls {@link #addGame(String, Hashtable, String, boolean)} for each one.
     * @param gamelist Enumeration of Strings and/or {@link SOCGame}s (mix and match);
     *          game names may be marked with the prefix
     *          {@link soc.message.SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     *          If gamelist is null, nothing happens.
     *          If any game already exists (per {@link #isGame(String)}), don't overwrite it;
     *          at most, clear its canJoin flag.
     * @param ourVersion Version to check to see if we can join,
     *          same format as {@link soc.util.Version#versionNumber()}.
     *          For each SOCGame in gameList, {@link SOCGame#getClientVersionMinRequired()}
     *          will be called.
     * @since 1.1.07
     */
    public synchronized void addGames(Iterable<?> gamelist, final int ourVersion)
    {
        if (gamelist == null)
            return;

        for (Object ob : gamelist)
        {
            String gaName;
            Hashtable<String, SOCGameOption> gaOpts;
            boolean cannotJoin;
            if (ob instanceof SOCGame)
            {
                gaName = ((SOCGame) ob).getName();
                gaOpts = ((SOCGame) ob).getGameOptions();
                cannotJoin = (ourVersion < ((SOCGame) ob).getClientVersionMinRequired());
            } else {
                gaName = (String) ob;
                gaOpts = null;
                cannotJoin = false;
            }

            addGame(gaName, gaOpts, null, cannotJoin);
        }
    }

    /**
     * Remove the game from the list
     * and call {@link SOCGame#destroyGame()}.
     * Set its mutex's {@link GameInfo#gameDestroyed} flag.
     *
     * @param gaName  the name of the game; should not be marked with any prefix.
     */
    public synchronized void deleteGame(final String gaName)
    {
        D.ebugPrintln("SOCGameList : deleteGame(" + gaName + ")");

        SOCGame game = gameData.get(gaName);

        if (game != null)
        {
            game.destroyGame();
            gameData.remove(gaName);
        }

        GameInfo info = gameInfo.get(gaName);
        info.gameDestroyed = true;
        gameInfo.remove(gaName);
        synchronized (info.mutex)
        {
            info.mutex.notifyAll();
        }
        info.dispose();
    }

    /**
     * Holds most information on one game, except its SOCGame object, which is kept separately.
     * Includes mutexes to synchronize game state access.
     * Kept within {@link #gameInfo} hashtable.
     * @author Jeremy D Monin <jeremy@nand.net>
     * @since 1.1.07
     */
    protected static class GameInfo
    {
        public MutexFlag mutex;
        public Hashtable<String,SOCGameOption> opts;  // or null
        public String optsStr;  // or null
        public boolean canJoin;
        /** Flag for when game has been destroyed, in case anything's waiting on its mutex. @since 1.1.15 */
        public boolean gameDestroyed;

        /**
         * Constructor: gameOpts is null or contains game option objects
         * @param canJoinGame can we join this game?
         * @param gameOpts Hashtable of {@link SOCGameOption}s, or null
         */
        public GameInfo(boolean canJoinGame, Hashtable<String,SOCGameOption> gameOpts)
        {
            mutex = new MutexFlag();
            opts = gameOpts;
            canJoin = canJoinGame;
        }

        /**
         * Constructor: gameOptsStr is null or unparsed game options
         * @param canJoinGame can we join this game?
         * @param gameOptsStr set of {@link SOCGameOption}s as packed by
         *            {@link SOCGameOption#packOptionsToString(Hashtable, boolean)}, or null
         */
        public GameInfo(boolean canJoinGame, String gameOptsStr)
        {
            mutex = new MutexFlag();
            optsStr = gameOptsStr;
            canJoin = canJoinGame;
        }

        /**
         * Parse optsStr to opts, unless it's already been parsed.
         * @return opts, after parsing if necessary, or null if opts==null and optsStr==null.
         */
        public Hashtable<String,SOCGameOption> parseOptsStr()
        {
            if (opts != null)  // already parsed
                return opts;
            else if (optsStr == null)  // none to parse
                return null;
            else
            {
                opts = SOCGameOption.parseOptionsToHash(optsStr);
                return opts;
            }
        }

        public void dispose()
        {
            if (opts != null)
            {
                opts.clear();
                opts = null;
            }
        }
    }
}
