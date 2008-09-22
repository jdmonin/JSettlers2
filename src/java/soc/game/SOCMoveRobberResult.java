/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
package soc.game;

import java.util.Vector;


/**
 * This class holds the results of moving the robber.
 * Specificaly, the victim or possible victims, and
 * what was stolen.
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
