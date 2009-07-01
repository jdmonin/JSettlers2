/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003 Robert S. Thomas
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
package soc.server;

import soc.game.SOCGame;
import soc.game.SOCGameOption;

import soc.server.genericServer.StringConnection;

import soc.util.SOCGameList;
import soc.util.Version;

import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;


/**
 * A class for creating and tracking the games;
 * contains each game's name, {@link SOCGameOption game options},
 * {@link SOCGame} object, and clients ({@link #StringConnection}s).
 *<P>
 * In 1.1.07, parent class SOCGameList was refactored, with
 * some methods moved to this new subclass, such as {@link #createGame(String, Hashtable) createGame}.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
public class SOCGameListAtServer extends SOCGameList
{
    /**
     * Number of minutes after which a game (created on the list) is expired.
     * Default is 90.
     *
     * @see #createGame(String, Hashtable)
     */
    public static int GAME_EXPIRE_MINUTES = 90;

    /** map of game names to Vector of game members */
    protected Hashtable gameMembers;

    /**
     * constructor
     */
    public SOCGameListAtServer()
    {
        super();
        gameMembers = new Hashtable();
    }

    /**
     * @param   gaName  the name of the game
     * @return true if the channel exists and has an empty member list
     */
    public synchronized boolean isGameEmpty(String gaName)
    {
        boolean result;
        Vector members;

        members = (Vector) gameMembers.get(gaName);

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
     * @param   gaName  game name
     * @return  list of members: a Vector of StringConnections
     */
    public synchronized Vector getMembers(String gaName)
    {
        return (Vector) gameMembers.get(gaName);
    }

    /**
     * @param  gaName   the name of the game
     * @param  conn     the member's connection
     * @return true if memName is a member of the game
     */
    public synchronized boolean isMember(StringConnection conn, String gaName)
    {
        Vector members = getMembers(gaName);

        if ((members != null) && (members.contains(conn)))
        {
            return true;
        }
        else
        {
            return false;
        }
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
        Vector members = getMembers(gaName);

        if ((members != null) && (!members.contains(conn)))
        {
            members.addElement(conn);

            // Check version range
            SOCGame ga = getGameData(gaName);
            int cliLowestAlready  = ga.clientVersionLowest;
            int cliHighestAlready = ga.clientVersionHighest;
            final int cliVers = conn.getVersion();
            if (cliVers < cliLowestAlready)
            {
                ga.clientVersionLowest = cliVers;
                ga.hasOldClients = true;
            }
            if (cliVers > cliHighestAlready)
            {
                ga.clientVersionHighest = cliVers;
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
        Vector members = getMembers(gaName);

        if ((members != null))
        {
            members.removeElement(conn);

            // Check version of remaining members
            if (! members.isEmpty())
            {
                StringConnection c = (StringConnection) members.firstElement();
                int lowVers = c.getVersion();
                int highVers = lowVers;
                for (int i = members.size() - 1; i > 1; --i)
                {
                    c = (StringConnection) members.elementAt(i);
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
     * create a new game, and add to the list; game will expire in {@link #GAME_EXPIRE_MINUTES} minutes.
     * If a game already exists (per {@link #isGame(String)}), do nothing.
     *
     * @param gaName  the name of the game
     * @param gaOpts  if game has options, hashtable of {@link SOCGameOption}; otherwise null.
     *                Should already be validated, by calling
     *                {@link SOCGameOption#adjustOptionsToKnown(Hashtable, Hashtable)}.
     * @return new game object, or null if it already existed
     */
    public synchronized SOCGame createGame(final String gaName, Hashtable gaOpts)
    {
        if (isGame(gaName))
            return null;

        Vector members = new Vector();
        gameMembers.put(gaName, members);

        SOCGame game = new SOCGame(gaName, gaOpts);

        // set the expiration to 90 min. from now
        game.setExpiration(game.getStartTime().getTime() + (60 * 1000 * GAME_EXPIRE_MINUTES));

        gameInfo.put(gaName, new GameInfo(true, game.getGameOptions()));  // also creates MutexFlag
        gameData.put(gaName, game);

        return game;
    }

    /**
     * Reset the board of this game, create a new game of same name,
     * same players, new layout.  The new "reset" board takes the place
     * of the old game in the game list.  Robots are not copied and
     * must re-join the game. (They're removed from the list of game members.)
     * Takes game monitor.
     * Destroys old game.
     * YOU MUST RELEASE the game monitor after returning.
     *
     * @param gaName Name of game - If not found, do nothing. No monitor is taken.
     * @return New game if gaName was found and copied; null if no game called gaName
     * @see soc.game.SOCGame#resetAsCopy()
     * @see #releaseMonitorForGame(String)
     */
    public SOCGameBoardReset resetBoard(String gaName)
    {
        SOCGame oldGame = (SOCGame) gameData.get(gaName);
        if (oldGame == null)
            return null;

        takeMonitorForGame(gaName);

        // Create reset-copy of game;
        // also removes robots from game and its member list.
        SOCGameBoardReset reset = new SOCGameBoardReset(oldGame, getMembers(gaName));
        SOCGame rgame = reset.newGame;

        // As in createGame, set expiration timer to 90 min. from now
        rgame.setExpiration(new Date().getTime() + (60 * 1000 * GAME_EXPIRE_MINUTES));

        // Adjust game-list
        gameData.remove(gaName);
        gameData.put(gaName, rgame);

        // Done.
        oldGame.destroyGame();
        return reset;
    }

    /**
     * remove the game from the list
     *
     * @param gaName  the name of the game
     */
    public synchronized void deleteGame(String gaName)
    {
        Vector members = (Vector) gameMembers.get(gaName);
        if (members != null)
        {
            members.removeAllElements();
        }        
        super.deleteGame(gaName);
    }
 
 }
