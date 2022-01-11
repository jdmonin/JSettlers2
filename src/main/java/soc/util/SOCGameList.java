/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2008-2014,2016-2021 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCGameOptionSet;
import soc.message.SOCGames;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * A class for creating and tracking the games;
 * contains each game's name, {@link SOCGameOption game options},
 * and mutex for synchronization.
 *<P>
 * In 1.1.07, moved from soc.server to soc.util package for client's use.
 * Some methods moved to new subclass {@link soc.server.SOCGameListAtServer}.
 * That subclass also tracks each game's {@link SOCGame} object and
 * its client {@link soc.server.genericServer.Connection Connection}s.
 *<P>
 * The client-side addGame methods allow game names to have a prefix which marks them
 * as unjoinable by the client ({@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}).
 * If the game name has this prefix, its {@link GameInfo#canJoin} flag is set to false,
 * as queried by {@link #isUnjoinableGame(String)}.  The prefix is stripped within addGame,
 * and not stored as part of the game name in this list.
 * Except to {@code addGame}, never supply this prefix to a SOCGameList method taking a game name;
 * supply the game name without the prefix.
 *
 * @author Robert S. Thomas
 */
public class SOCGameList
{
    /**
     * Maximum permitted game name length, default 30 characters.
     *<P>
     * Before v1.1.13, the default maximum was 20 characters.<BR>
     * From v1.1.07 through 1.2.00, this field was {@code SOCServer.GAME_NAME_MAX_LENGTH}.
     *
     * @see soc.server.SOCServer#createOrJoinGameIfUserOK(soc.server.genericServer.Connection, String, String, String, SOCGameOptionSet)
     * @since 1.2.01
     */
    public static final int GAME_NAME_MAX_LENGTH = 30;

    /**
     * Regex pattern to match a string which is entirely digits or punctuation:
     * 0-9 or unicode class {@code Nd} ({@link Character#isDigit(int)})
     * or <tt>\p{Punct}</tt> or <tt>\p{IsPunctuation}</tt>.
     * Useful for checking validity of a new game name at client or server.
     * @since 2.0.00
     */
    public static final Pattern REGEX_ALL_DIGITS_OR_PUNCT =
        Pattern.compile("^[\\p{Nd}\\p{Punct}\\p{P}]+$");
        // \d won't capture unicode digits without using (?U) not available before java 7 (UNICODE_CHARACTER_CLASS).
        // \p{P} (IsPunctuation) matches 632 unicode chars, but only \p{Punct} includes $, +, <, =, >, ^, `, |, and ~
        //    -- https://stackoverflow.com/questions/13925454/check-if-string-is-a-punctuation-character
        //       answer by hans-brende 2018-03-15
        // Replaced \p{IsPunctuation} with \p{P} for java 1.5 compat.
        // If you adjust this regex, also update soctest.util.TestGameList method testRegexAllDigitsOrPunct().

    /**
     * Info about every game in this {@code SOCGameList}.
     * key = String, value = {@link GameInfo}; includes mutexes to synchronize game state access,
     * game options, and other per-game info
     * @see soc.server.SOCGameListAtServer#gameData
     */
    protected Hashtable<String, GameInfo> gameInfo;

    /**
     * All Known Options for the server hosting these games.
     * @since 2.5.00
     */
    protected final SOCGameOptionSet knownOpts;

    /** used with gamelist's monitor */
    protected boolean inUse;

    /**
     * constructor
     * @param knownOpts All Known Options at server hosting the games in this list,
     *     if might be needed for {@link #parseGameOptions(String)}, or {@code null} if not needed
     */
    public SOCGameList(final SOCGameOptionSet knownOpts)
    {
        gameInfo = new Hashtable<String, GameInfo>();
        this.knownOpts = knownOpts;
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
                wait(1000);  // timeout to help avoid deadlock
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
                        mutex.wait(1000);  // timeout to help avoid deadlock
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
     * @see soc.server.SOCGameListAtServer#getGamesData()
     * @since 2.0.00
     */
    public Set<String> getGameNames()
    {
        return gameInfo.keySet();
    }

    /**
     * Get the size (number of games) in our list.
     * @return the number of games in our list
     * @since 1.1.07
     */
    public int size()
    {
        return gameInfo.size();
    }

    /**
     * get a game's {@link SOCGameOption}s, if stored and parsed
     * @param   gaName  game name
     * @return the game options (map of {@link SOCGameOption}), or null if none or if unparsed
     * @see #getGameOptionsString(String)
     * @see #parseGameOptions(String)
     * @since 1.1.07
     */
    public SOCGameOptionSet getGameOptions(String gaName)
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
     * Parse this game's options from string to {@link SOCGameOption}s.
     * Should not be called at client before any updates to "known options" are received from server.
     * Calls {@link GameInfo#parseOptsStr()}.
     * @param   gaName  game name
     * @return the game options, or null if none
     * @see #getGameOptionsString(String)
     * @since 1.1.07
     */
    public SOCGameOptionSet parseGameOptions(String gaName)
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
        return gameInfo.containsKey(gaName);
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
     * Server should instead call
     * {@link soc.server.SOCGameListAtServer#createGame(String, String, String, SOCGameOptionSet, soc.server.GameHandler)}
     * or {@link soc.server.SOCGameListAtServer#addGame(SOCGame, soc.server.GameHandler, String, String)}.
     *
     * @param gaName Name of added game; may be marked with the prefix
     *         {@link soc.message.SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     * @param gaOptsStr set of {@link SOCGameOption}s as packed by
     *         {@link SOCGameOption#packOptionsToString(Map, boolean, boolean)}, or null.
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
     * If a game called {@code gaName} is already in this list (per {@link #isGame(String)}),
     * at most clear its canJoin flag.
     *<P>
     * Supply gaOpts or gaOptsStr, not both.
     *<P>
     * Client should instead call {@link #addGame(String, String, boolean)} because game options should
     * remain unparsed as late as possible.
     * Server should instead call
     * {@link soc.server.SOCGameListAtServer#createGame(String, String, String, SOCGameOptionSet, soc.server.GameHandler)}.
     *
     * @param gaName Name of added game; may be marked with the prefix
     *         {@link soc.message.SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     *         Not validated here for length or naming rules.
     * @param gaOpts {@link SOCGameOption}s of added game, or null
     * @param gaOptsStr set of {@link SOCGameOption}s as packed by
     *         {@link SOCGameOption#packOptionsToString(Map, boolean, boolean)}, or null.
     *         Game options should remain unparsed as late as possible.
     * @param cannotJoin This game is unjoinable, even if its name doesn't
     *         start with the unjoinable prefix.
     *         gaName will be checked for the prefix regardless of cannotJoin's value.
     * @see #addGames(SOCGameList, int)
     * @see #addGames(Iterable, int)
     * @since 1.1.07
     */
    protected synchronized void addGame
        (String gaName, SOCGameOptionSet gaOpts, String gaOptsStr, boolean cannotJoin)
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

            return;  // <--- Early return: Already a known game, don't re-add ---
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
     *<P>
     * For use at client.  Each added game's {@link SOCGameList.GameInfo} is examined for game options
     * and {@code canJoin} flag, but not directly copied.
     *
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
     * Calls {@link #addGame(String, SOCGameOptionSet, String, boolean)} for each one.
     *<P>
     * For use at client.
     *
     * @param gameList Enumeration of Strings and/or {@link SOCGame}s (mix and match);
     *          game names may be marked with the prefix
     *          {@link soc.message.SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     *          If gameList is null, nothing happens.
     *          If any game already exists (per {@link #isGame(String)}), don't overwrite it;
     *          at most, clear its canJoin flag.
     * @param ourVersion Version to check to see if we can join,
     *          same format as {@link soc.util.Version#versionNumber()}.
     *          For each SOCGame in gameList, {@link SOCGame#getClientVersionMinRequired()}
     *          will be called.
     * @since 1.1.07
     */
    public synchronized void addGames(Iterable<?> gameList, final int ourVersion)
    {
        if (gameList == null)
            return;

        for (Object ob : gameList)
        {
            String gaName;
            SOCGameOptionSet gaOpts;
            boolean cannotJoin;
            final boolean isGameObj = (ob instanceof SOCGame);
            if (isGameObj)
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
        D.ebugPrintlnINFO("SOCGameList : deleteGame(" + gaName + ")");

        GameInfo info = gameInfo.get(gaName);
        if (info == null)
        {
            return;  // game wasn't in this SOCGameList
        }
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
     * Kept within the {@link #gameInfo} map.
     * @author Jeremy D Monin <jeremy@nand.net>
     * @since 1.1.07
     */
    protected class GameInfo
    {
        public MutexFlag mutex;
        public SOCGameOptionSet opts;  // or null
        public String optsStr;  // or null
        public boolean canJoin;

        /** Flag for when game has been destroyed, in case anything's waiting on its mutex. @since 1.1.15 */
        public boolean gameDestroyed;

        /**
         * Constructor: gameOpts is null or contains game option objects
         * @param canJoinGame can we join this game?
         * @param gameOpts The game's {@link SOCGameOption}s, or null
         */
        public GameInfo(boolean canJoinGame, SOCGameOptionSet gameOpts)
        {
            mutex = new MutexFlag();
            opts = gameOpts;
            canJoin = canJoinGame;
        }

        /**
         * Constructor: gameOptsStr is null or unparsed game options
         * @param canJoinGame can we join this game?
         * @param gameOptsStr set of {@link SOCGameOption}s as packed by
         *            {@link SOCGameOption#packOptionsToString(Map, boolean, boolean)}, or null
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
        public SOCGameOptionSet parseOptsStr()
        {
            if (opts != null)  // already parsed
                return opts;
            else if ((optsStr == null) || (knownOpts == null))  // none to parse
                return null;
            else
            {
                opts = SOCGameOption.parseOptionsToSet(optsStr, knownOpts);
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
