/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2011-2012,2018-2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.game;

import java.util.List;


/**
 * At server, this class holds the results of moving the robber or pirate:
 * The victim or possible victims, and what was stolen.
 * Call {@link SOCGame#getRobberyPirateFlag()} to see which one was moved.
 * Each game has 1 instance of this object, updated each time the robber or pirate is moved
 * and a victim chosen: {@link SOCGame#getRobberyResult()}.
 *
 * @see SOCGame.RollResult
 */
public class SOCMoveRobberResult
{
    /** Victim, or possible victims, or empty or null */
    List<SOCPlayer> victims;

    /** Resource type of loot stolen, as in {@link SOCResourceConstants}, or -1 */
    int loot;

    /**
     * When the pirate fleet moves in game scenario {@link SOCScenario#K_SC_PIRI SC_PIRI},
     * the resources stolen from victim; may be empty. Otherwise null and ignored.
     *<P>
     * When {@link #sc_piri_loot} is set, the other {@link #loot} field is -1.
     * When {@link #victims} is empty, ignore this field.
     *
     * @see SOCGame#rollDice()
     * @see SOCGame#stealFromPlayerPirateFleet(int, int)
     * @see SOCGame.RollResult#sc_piri_fleetAttackRsrcs
     * @since 2.0.00
     */
    public SOCResourceSet sc_piri_loot;

    /**
     * Creates a new SOCMoveRobberResult object.
     */
    public SOCMoveRobberResult()
    {
        victims = null;
        loot = -1;
    }

    /**
     * Clear common fields for reuse of this object.
     * Does not clear the infrequently-used {@link #sc_piri_loot}.
     * @since 2.0.00
     */
    public void clear()
    {
        victims = null;
        loot = -1;
    }

    /**
     * Set the victim (if any) or possible victims
     *
     * @param v Victim or possible victims, may be empty or null
     */
    public void setVictims(List<SOCPlayer> v)
    {
        victims = v;
    }

    /**
     * Get the victim (if any) or possible victims
     *
     * @return Victim or possible victims, may be empty or null
     */
    public List<SOCPlayer> getVictims()
    {
        return victims;
    }

    /**
     * Set the type of resource stolen from the victim
     *
     * @param l type of resource stolen, as in {@link SOCResourceConstants},
     *          or -1 if nothing stolen,
     *          or {@link SOCResourceConstants#CLOTH_STOLEN_LOCAL} for cloth.
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
     *         or -1 if nothing stolen,
     *         or {@link SOCResourceConstants#CLOTH_STOLEN_LOCAL} for cloth.
     */
    public int getLoot()
    {
        return loot;
    }
}
