/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.robot;

import soc.game.SOCDevCardConstants;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;

import java.util.Vector;


/**
 * This represents a possible thing to build.
 * It includes what and where to build.  A score
 * that represents how many VP this build is worth,
 * and a list of other building possibilities that
 * result from building this thing.
 */
public class SOCBuildPossibility
{
    SOCPlayingPiece piece;
    boolean buyDevCard;
    int devCardType;
    int freeRoads;
    int score;
    int[] buildingSpeedup;
    int eta;
    int priority;
    SOCPlayer player;
    SOCBuildPossibility parent;
    Vector<SOCBuildPossibility> children;

    /**
     * this is a constructor
     *
     * @param pi  what and where to build
     * @param sc  how many VP this build is worth
     * @param bs  how our building speeds are affected
     * @param et  how many turns until we can build this
     * @param pr  the priority
     * @param pl  the player's state after the build
     */
    public SOCBuildPossibility(SOCPlayingPiece pi, int sc, int[] bs, int et, int pr, SOCPlayer pl)
    {
        piece = pi;
        buyDevCard = false;
        devCardType = -1;
        freeRoads = 0;
        score = sc;
        buildingSpeedup = bs;
        this.eta = et;
        priority = pr;
        player = pl;
        parent = null;
        children = new Vector<SOCBuildPossibility>();
    }

    /**
     * this is a constructor for when you are using a road building card
     *
     * @param pi  what and where to build
     * @param sc  how many VP this build is worth
     * @param bs  how our building speeds are affected
     * @param et  how many turns until we can build this
     * @param pr  the priority
     * @param pl  the player's state after the build
     * @param fr  how many free roads are left
     */
    public SOCBuildPossibility(SOCPlayingPiece pi, int sc, int[] bs, int et, int pr, SOCPlayer pl, int fr)
    {
        piece = pi;
        buyDevCard = false;
        devCardType = -1;
        freeRoads = fr;
        score = sc;
        buildingSpeedup = bs;
        this.eta = et;
        priority = pr;
        player = pl;
        parent = null;
        children = new Vector<SOCBuildPossibility>();
    }

    /**
     * this is a constructor for buying a dev card
     *
     * @param sc  how many VP this build is worth
     * @param bs  how our building speeds are affected
     * @param et  how many turns until we can build this
     * @param pr  the priority
     * @param pl  the player's state after the build
     */
    public SOCBuildPossibility(int sc, int[] bs, int et, int pr, SOCPlayer pl)
    {
        piece = null;
        buyDevCard = true;
        devCardType = SOCDevCardConstants.KNIGHT;
        freeRoads = 0;
        score = sc;
        buildingSpeedup = bs;
        this.eta = et;
        priority = pr;
        player = pl;
        parent = null;
        children = new Vector<SOCBuildPossibility>();
    }

    /**
     * this is a constructor for PLAYING a dev card
     *
     * @param dt  which dev card to play
     * @param sc  how many VP this build is worth
     * @param bs  how our building speeds are affected
     * @param et  how many turns until we can build this
     * @param pr  the priority
     * @param pl  the player's state after the build
     */
    public SOCBuildPossibility(int dt, int sc, int[] bs, int et, int pr, SOCPlayer pl)
    {
        piece = null;
        buyDevCard = false;
        devCardType = dt;
        freeRoads = 0;
        score = sc;
        buildingSpeedup = bs;
        this.eta = et;
        priority = pr;
        player = pl;
        parent = null;
        children = new Vector<SOCBuildPossibility>();
    }

    /**
     * @return the piece
     */
    public SOCPlayingPiece getPiece()
    {
        return piece;
    }

    /**
     * @return true if this is a request to buy a dev card
     */
    public boolean isBuyDevCard()
    {
        return buyDevCard;
    }

    /**
     * @return true if this is a request to play a dev card
     */
    public boolean isPlayDevCard()
    {
        return ((piece == null) && !buyDevCard);
    }

    /**
     * @return the type of dev card to play or buy
     */
    public int getDevCardType()
    {
        return devCardType;
    }

    /**
     * @return the number of free roads left
     */
    public int getFreeRoads()
    {
        return freeRoads;
    }

    /**
     * @return the score
     */
    public int getScore()
    {
        return score;
    }

    /**
     * @return the building speed differences
     */
    public int[] getBuildingSpeedup()
    {
        return buildingSpeedup;
    }

    /**
     * @return the number of turns it will take to do this
     */
    public int getETA()
    {
        return eta;
    }

    /**
     * @return the priority
     */
    public int getPriority()
    {
        return priority;
    }

    /**
     * @return the player's future state
     */
    public SOCPlayer getPlayer()
    {
        return player;
    }

    /**
     * @return the building children that this one makes
     */
    public Vector<SOCBuildPossibility> getChildren()
    {
        return children;
    }

    /**
     * @return the parent of this node
     */
    public SOCBuildPossibility getParent()
    {
        return parent;
    }

    /**
     * set the parent for this node
     *
     * @param par  the parent
     */
    public void setParent(SOCBuildPossibility par)
    {
        parent = par;
    }

    /**
     * add a building possibility to the list of children
     *
     * @param poss  the building possibility
     */
    public void addChild(SOCBuildPossibility poss)
    {
        children.addElement(poss);
        poss.setParent(this);
    }

    /**
     * @return a human readable form of this object
     */
    @Override
    public String toString()
    {
        String str = "SOCBP:player=" + player + "|piece=" + piece + "|score=" + score + "|speedup=";

        if (buildingSpeedup != null)
        {
            for (int i = SOCBuildingSpeedEstimate.MIN;
                    i < SOCBuildingSpeedEstimate.MAXPLUSONE; i++)
            {
                str += (" " + buildingSpeedup[i]);
            }
        }
        else
        {
            str += "null";
        }

        str += ("|eta=" + eta + "|priority=" + priority + "|children=" + children.size());

        return str;
    }
}
