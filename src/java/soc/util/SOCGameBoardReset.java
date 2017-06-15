/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2008-2010,2017 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Enumeration;
import java.util.Vector;

import soc.debug.D;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.server.genericServer.StringConnection;

/**
 * This class holds data the server needs, related to a
 * "board reset" of a game being played.
 *<P>
 * If the SOCGame is in state {@link SOCGame#READY_RESET_WAIT_ROBOT_DISMISS},
 * this object is referenced within the newly created game object.
 *
 * @see soc.server.SOCGameListAtServer#resetBoard(String)
 * @see soc.game.SOCGame#resetAsCopy()
 * @see soc.game.SOCGame#boardResetOngoingInfo
 *
 * @author Jeremy D. Monin <jeremy@nand.net>
 */
public class SOCGameBoardReset
{
    /** The new game, created from an old game by {@link soc.game.SOCGame#resetAsCopy()} */ 
    public SOCGame newGame;

    /** gamestate of old game at reset time
     * @since 1.1.06
     */
    final public int oldGameState;

    /** Were there robots in the old game? */
    public boolean hadRobots;

    /** Are we still waiting for robots to leave the old game?
     * @since 1.1.07
     */
    public int oldRobotCount;

    /**
     * Human and robot connections; both arrays null at vacant seats.
     * {@link soc.game.SOCGame#resetAsCopy()} will duplicate human players
     * in the new game, but not robot players. 
     * Indexed 0 to SOCGame.MAXPLAYERS-1
     */
    public StringConnection[] humanConns, robotConns;

    /** Was this player position a robot? Indexed 0 to SOCGame.MAXPLAYERS-1 */
    public boolean[] wasRobot;

    /** Create a SOCGameReset: Extract data, reset the old game, and gather new data.
     *  Adjust game member list to remove robots.
     *  If there were robots, the <b>new</b> game's state is set to
     *  {@link SOCGame#READY_RESET_WAIT_ROBOT_DISMISS}.
     *
     * @param oldGame Game to reset - {@link soc.game.SOCGame#resetAsCopy()}
     *   will be called.  The old game's state will be changed to RESET_OLD.
     * @param memberConns Game members (StringConnections),
     *   as retrieved by {@link soc.server.SOCGameListAtServer#getMembers(String)}.
     *   Contents of this vector will be changed to remove any robot members.
     */
    public SOCGameBoardReset (SOCGame oldGame, Vector memberConns)
    {
        oldGameState = oldGame.getGameState();
        hadRobots = false;
        oldRobotCount = 0;
        wasRobot = new boolean[oldGame.maxPlayers];
        for (int i = 0; i < oldGame.maxPlayers; ++i)
        {
            SOCPlayer pl = oldGame.getPlayer(i);
            boolean isRobot = pl.isRobot() && ! oldGame.isSeatVacant(i);
            wasRobot[i] = isRobot;
            if (isRobot)
            {
                hadRobots = true;
                ++oldRobotCount;
            }
        }

        /**
         * Reset the game
         */
        newGame = oldGame.resetAsCopy();   // TODO assert: verify maxPlayers is same for old,new

        /**
         * Gather connection information, cleanup member list
         */
        humanConns = new StringConnection[oldGame.maxPlayers];
        robotConns = new StringConnection[oldGame.maxPlayers];
        if (memberConns != null)
        {
            // Grab connection information for humans and robots.
            // memberConns is from _old_ game, so robots are included.
            // Robots aren't copied to the new game, and must re-join.
            sortPlayerConnections(newGame, oldGame, memberConns, humanConns, robotConns);

            // Remove robots from list of game members
            for (int pn = 0; pn < oldGame.maxPlayers; ++pn)
            {
                if (wasRobot[pn])
                    memberConns.remove(robotConns[pn]);
            }
        }

        if (hadRobots)
        {
            newGame.boardResetOngoingInfo = this;
            newGame.setGameState(SOCGame.READY_RESET_WAIT_ROBOT_DISMISS);
        }
    }

    /**
     * Grab connection information for this game's humans and robots.
     * memberConns is from _old_ game, so robots are included.
     * Robots aren't copied to the new game, and must re-join.
     *<P>
     * Two modes:
     *<P>
     * If currently copying a game, assumes newGame is from oldGame via {@link SOCGame#resetAsCopy()},
     * and newGame contains only the human players, oldGame contains all human and robot players.
     *<P>
     * If not copying a game, only inspecting one, then oldGame is null, and assumes newGame has all
     * players (both human and robot).
     *
     * @param newGame New game (if resetting), or only game
     * @param oldGame Old game (if resetting), or null
     * @param memberConns Members of old game, from {@link soc.server.SOCGameListAtServer#getMembers(String)}; a Vector of StringConnections
     * @param humanConns New array to fill with human players; indexed 0 to SOCGame.MAXPLAYERS-1.
     *                   humanConns[pn] will be the human player at position pn, or null.
     * @param robotConns New array to fill with robot players; indexed 0 to SOCGame.MAXPLAYERS-1.
     *                   robotConns[pn] will be the robot player at position pn, or null.
     *
     * @return The number of human players in newGame
     */
    public static int sortPlayerConnections
        (SOCGame newGame, SOCGame oldGame, Vector memberConns,
         StringConnection[] humanConns, StringConnection[] robotConns)
    {
        // This enum is easier than enumerating all connected clients;
        // there is no server-wide mapping of clientname -> connection.

        int numHuman = 0;
        Enumeration playersEnum = memberConns.elements();
        while (playersEnum.hasMoreElements())
        {
            StringConnection pCon = (StringConnection) playersEnum.nextElement();
            String pname = pCon.getData();
            SOCPlayer p = newGame.getPlayer(pname);
            if (p != null)
            {
                int pn = p.getPlayerNumber();
                if (p.isRobot())
                    robotConns[pn] = pCon;
                else
                {
                    humanConns[pn] = pCon;
                    ++numHuman;
                }
            }
            else if (oldGame != null)
            {
                // No such player in new game.
                // Assume is robot player in old game.
                p = oldGame.getPlayer(pname);
                if (p != null)
                {
                    int pn = p.getPlayerNumber();
                    if (p.isRobot())
                        robotConns[pn] = pCon;
                    else
                        // should not happen
                        D.ebugPrintln("findPlayerConnections assert failed: human player not copied: " + pn);
                }
            }
        }

        // Check all player positions after enum
        for (int pn = 0; pn < newGame.maxPlayers; ++pn)
        {
            if (! newGame.isSeatVacant(pn))
            {
                if ((humanConns[pn] == null) && (robotConns[pn] == null))
                    D.ebugPrintln("findPlayerConnections assert failed: did not find player " + pn);
            }
            else
            {
                if ((humanConns[pn] != null) ||
                    ((robotConns[pn] != null) && ((oldGame == null) || oldGame.isSeatVacant(pn))))
                    D.ebugPrintln("findPlayerConnections assert failed: memberlist had vacant player " + pn);
            }
        }

        return numHuman;
    }
  
}  // public class SOCGameBoardReset
