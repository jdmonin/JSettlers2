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
package soc.util;


/**
 * This is a class to store a list of robot parameters.
 * I put it in soc.util because the SOCServer and the
 * SOCDBHelper needed to use it, but I didn't think
 * they should have to include the soc.robot package.
 *
 * @author Robert S. Thomas
 */
public class SOCRobotParameters
{
    protected int maxGameLength;
    protected int maxETA;
    protected float etaBonusFactor;
    protected float adversarialFactor;
    protected float leaderAdversarialFactor;
    protected float devCardMultiplier;
    protected float threatMultiplier;
    protected int strategyType;
    protected int tradeFlag;

    /**
     * constructor
     *
     * @param mgl  the max game length
     * @param me   the max eta
     * @param ebf  the eta bonus factor
     * @param af   the adversarial factor
     * @param laf  the leader adversarial factor
     * @param dcm  the dev card multiplier
     * @param tm   the threat multiplier
     * @param st   the strategy type
     * @param tf   the trade flag
     */
    public SOCRobotParameters(int mgl, int me, float ebf, float af, float laf, float dcm, float tm, int st, int tf)
    {
        maxGameLength = mgl;
        maxETA = me;
        etaBonusFactor = ebf;
        adversarialFactor = af;
        leaderAdversarialFactor = laf;
        devCardMultiplier = dcm;
        threatMultiplier = tm;
        strategyType = st;
        tradeFlag = tf;
    }

    /**
     * constructor
     *
     * @param params  the robot parameters
     */
    public SOCRobotParameters(SOCRobotParameters params)
    {
        maxGameLength = params.getMaxGameLength();
        maxETA = params.getMaxETA();
        etaBonusFactor = params.getETABonusFactor();
        adversarialFactor = params.getAdversarialFactor();
        leaderAdversarialFactor = params.getLeaderAdversarialFactor();
        devCardMultiplier = params.getDevCardMultiplier();
        threatMultiplier = params.getThreatMultiplier();
        strategyType = params.getStrategyType();
        tradeFlag = params.getTradeFlag();
    }

    /**
     * @return maxGameLength
     */
    public int getMaxGameLength()
    {
        return maxGameLength;
    }

    /**
     * @return maxETA
     */
    public int getMaxETA()
    {
        return maxETA;
    }

    /**
     * @return etaBonusFactor
     */
    public float getETABonusFactor()
    {
        return etaBonusFactor;
    }

    /**
     * @return adversarialFactor
     */
    public float getAdversarialFactor()
    {
        return adversarialFactor;
    }

    /**
     * @return leaderAdversarialFactor
     */
    public float getLeaderAdversarialFactor()
    {
        return leaderAdversarialFactor;
    }

    /**
     * @return devCardMultiplier
     */
    public float getDevCardMultiplier()
    {
        return devCardMultiplier;
    }

    /**
     * @return threatMultiplier
     */
    public float getThreatMultiplier()
    {
        return threatMultiplier;
    }

    /**
     * @return strategyType
     */
    public int getStrategyType()
    {
        return strategyType;
    }

    /**
     * @return tradeFlag
     */
    public int getTradeFlag()
    {
        return tradeFlag;
    }

    /**
     * @return a human readable form of the data
     */
    public String toString()
    {
        String s = "mgl=" + maxGameLength + "|me=" + maxETA + "|ebf=" + etaBonusFactor + "|af=" + adversarialFactor + "|laf=" + leaderAdversarialFactor + "|dcm=" + devCardMultiplier + "|tm=" + threatMultiplier + "|st=" + strategyType + "|tf=" + tradeFlag;

        return s;
    }
}
