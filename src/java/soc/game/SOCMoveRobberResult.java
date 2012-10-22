/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2011 Jeremy D Monin <jeremy@nand.net>
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
package soc.game;

import java.util.Vector;


/**
 * This class holds the results of moving the robber or pirate.
 * Specifically, the victim or possible victims, and
 * what was stolen.
 * Call {@link SOCGame#getRobberyPirateFlag()} to see which one was moved.
 */
public class SOCMoveRobberResult
{
    /** Victim, or possible victims, or empty or null; content type {@link SOCPlayer} */
    Vector<SOCPlayer> victims;

    /** Resource type of loot stolen, as in {@link SOCResourceConstants}, or -1 */
    int loot;

    /**
     * Creates a new SOCMoveRobberResult object.
     */
    public SOCMoveRobberResult()
    {
        victims = null;
        loot = -1;
    }

    /**
     * Set the victim (if any) or possible victims
     *
     * @param v Victim or possible victims, may be empty or null; Vector of {@link SOCPlayer}
     */
    public void setVictims(Vector<SOCPlayer> v)
    {
        victims = v;
    }

    /**
     * Get the victim (if any) or possible victims
     *
     * @return Victim or possible victims, may be empty or null; Vector of {@link SOCPlayer}
     */
    public Vector<SOCPlayer> getVictims()
    {
        return victims;
    }

    /**
     * Set the type of resource stolen from the victim
     *
     * @param l type of resource stolen, as in {@link SOCResourceConstants},
     *          or -1 if nothing stolen
     */
    public void setLoot(int l)
    {
        loot = l;
    }

    /**
     * Get the type of resource stolen from the victim;
     * undefined unless {@link #getVictims()}.size() == 1.
     *
     * @return type of resource stolen, as in {@link SOCResourceConstants},
     *         or -1 if nothing stolen
     */
    public int getLoot()
    {
        return loot;
    }
}
