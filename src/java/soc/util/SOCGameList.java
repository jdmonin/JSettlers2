/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2008-2009 Jeremy D Monin <jeremy@nand.net>
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
package soc.util;

import soc.disableDebug.D;
import soc.game.SOCGame;
import soc.game.SOCGameOption;


import soc.message.SOCGames;

import java.util.Enumeration;
import java.util.Hashtable;


/**
 * A class for creating and tracking the games;
 * contains each game's name, {@link SOCGameOption game options},
 * {@link SOCGame} object, and clients ({@link #StringConnection}s).
 *<P>
 * In 1.1.07, moved from soc.server to soc.util package for client's use.
 * Some methods moved to new subclass {@link soc.server.SOCGameListAtServer}.
 *
 * @author Robert S. Thomas
 */
public class SOCGameList
{
    /** key = String, value = {@link GameInfo}; includes mutexes to synchronize game state access,
     *  and other per-game data
     */
    protected Hashtable gameInfo;

    /** map of game names to {@link SOCGame} objects */
    protected Hashtable gameData;

    /** used with gamelist's monitor */
    protected boolean inUse;

    /**
     * constructor
     */
    public SOCGameList()
    {
        gameInfo = new Hashtable();
        gameData = new Hashtable();
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
     * take the monitor for this game
     *
     * @param game  the name of the game
     * @return false if the game has no mutex
     */
    public boolean takeMonitorForGame(String game)
    {
        // D.ebugPrintln("SOCGameList : TAKE MONITOR FOR " + game);

        GameInfo info = (GameInfo) gameInfo.get(game);
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
     * release the monitor for this game
     *
     * @param game  the name of the game
     * @return false if the game has no mutex
     */
    public boolean releaseMonitorForGame(String game)
    {
        // D.ebugPrintln("SOCGameList : RELEASE MONITOR FOR " + game);

        GameInfo info = (GameInfo) gameInfo.get(game);
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
     * @return an enumeration of game names (Strings)
     * @see #getGameObjects()
     */
    public Enumeration getGames()
    {
        return gameInfo.keys();
    }

    /**
     * Get all the {@link SOCGame} data available; some games in {@link #getGames()}
     * may not have associated SOCGame data, so this enumeration may have fewer
     * elements than getGames, or even 0 elements.
     * @return an enumeration of game data (SOCGames)
     * @see #getGames()
     * @since 1.1.06
     */
    public Enumeration getGamesData()
    {
        return gameData.elements();
    }

    /**
     * @return the number of games in our list
     * @since 1.1.07
     */
    public int size()
    {
        return gameInfo.size();
    }

    /**
     * @param   gaName  game name
     * @return the game data
     */
    public SOCGame getGameData(String gaName)
    {
        return (SOCGame) gameData.get(gaName);
    }

    /**
     * @param   gaName  game name
     * @return the game options (hashtable of {@link SOCGameOption}), or null if none
     * @since 1.1.07
     */
    public Hashtable getGameOptions(String gaName)
    {
        GameInfo info = (GameInfo) gameInfo.get(gaName);
        if (info == null)
            return null;
        else
            return info.opts;
    }

    /**
     * @param   gaName  the name of the game
     * @return true if the game exists
     */
    public boolean isGame(String gaName)
    {
        return (gameInfo.get(gaName) != null);
    }

    /**
     * @param   gaName  the name of the game;  may be marked with the prefix
     *         {@link soc.message.SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     * @return true if the game is in our list marked as not joinable,
     *        or has the prefix
     * @since 1.1.07
     */
    public boolean isUnjoinableGame(String gaName)
    {
        if (gaName.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE)
            return true;
        GameInfo gi = (GameInfo) gameInfo.get(gaName);
        if (gi == null)
            return false;
        return ! gi.canJoin;
    }

    /**
     * Client-side - Add this game name, with game options.
     * If a game already exists (per {@link #isGame(String)}), do nothing.
     *
     * @param gaName Name of added game; may be marked with the prefix
     *         {@link soc.message.SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     * @param gaOpts Hashtable of {@link SOCGameOption game options} of added game, or null 
     * @param cannotJoin This game is unjoinable, even if its name doesn't
     *         start with the unjoinable prefix.
     *         gaName will be checked for the prefix regardless of cannotJoin's value.
     * @since 1.1.07
     */
    public synchronized void addGame(String gaName, Hashtable gaOpts, boolean cannotJoin)
    {
        if (gaName.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE)
        {
            cannotJoin = true;
            gaName = gaName.substring(1);
        }

        if (isGame(gaName))
            return;

        gameInfo.put(gaName, new GameInfo(! cannotJoin, gaOpts));
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
            addGames(gl.gameData.elements(), ourVersion);
        if (gl.gameInfo != null)
        {
            for (Enumeration gnEnum = gl.gameInfo.keys(); gnEnum.hasMoreElements(); )
            {
                String gaName = (String) gnEnum.nextElement();
                GameInfo gi = (GameInfo) gl.gameInfo.get(gaName);
                addGame(gaName, gi.opts, ! gi.canJoin);
            }
        }
    }

    /**
     * Add several games to this GameList.
     * Calls {@link #addGame(String, Hashtable, boolean)} for each one.
     * @param gamelist Enumeration of Strings and/or {@link SOCGame}s (mix and match);
     *          game names may be marked with the prefix
     *          {@link soc.message.SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     *          If gamelist is null, nothing happens.
     *          If any game already exists (per {@link #isGame(String)}), don't overwrite it.
     * @param ourVersion Version to check to see if we can join,
     *          same format as {@link soc.util.Version#versionNumber()}.
     *          For each SOCGame in gameList, {@link SOCGame#getClientVersionMinRequired()}
     *          will be called.
     * @since 1.1.07
     */
    public synchronized void addGames(Enumeration gamelist, final int ourVersion)
    {
        if (gamelist == null)
            return;

        while (gamelist.hasMoreElements())
        {
            Object ob = gamelist.nextElement();
            String gaName;
            Hashtable gaOpts;
            boolean cannotJoin;
            if (ob instanceof SOCGame)
            {
                gaName = ((SOCGame) ob).getName();
                gaOpts = ((SOCGame) ob).getGameOptions();
                cannotJoin = (ourVersion >= ((SOCGame) ob).getClientVersionMinRequired());
            } else {
                gaName = (String) ob;
                gaOpts = null;
                cannotJoin = false;
            }

            addGame (gaName, gaOpts, cannotJoin);
        }    
    }

    /**
     * remove the game from the list
     *
     * @param gaName  the name of the game; should not be marked with any prefix.
     */
    public synchronized void deleteGame(final String gaName)
    {
        D.ebugPrintln("SOCGameList : deleteGame(" + gaName + ")");

        SOCGame game = (SOCGame) gameData.get(gaName);

        if (game != null)
        {
            game.destroyGame();
            gameData.remove(gaName);
        }

        GameInfo info = (GameInfo) gameInfo.get(gaName);
        gameInfo.remove(gaName);
        synchronized (info.mutex)
        {
            info.mutex.notifyAll();
        }
        info.finalize();
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
        public Hashtable opts;  // or null
        public String optsStr;  // or null
        public boolean canJoin;

        /**
         * Constructor: gameOpts is null or contains game option objects
         * @param canJoinGame can we join this game?
         * @param gameOpts Hashtable of {@link SOCGameOption}s, or null
         */
        public GameInfo (boolean canJoinGame, Hashtable gameOpts)
        {
            mutex = new MutexFlag();
            opts = gameOpts;
            canJoin = canJoinGame;
        }

        /**
         * Constructor: gameOptsStr is null or unparsed game options
         * @param canJoinGame can we join this game?
         * @param gameOptsStr set of {@link SOCGameOption}s as packed by {@link SOCGameOption#packOptionsToString(Hashtable)}, or null
         */
        public GameInfo (boolean canJoinGame, String gameOptsStr)
        {
            mutex = new MutexFlag();
            optsStr = gameOptsStr;
            canJoin = canJoinGame;
        }

        public void finalize()
        {
            if (opts != null)
            {
                opts.clear();
                opts = null;
            }
        }
    }
}
