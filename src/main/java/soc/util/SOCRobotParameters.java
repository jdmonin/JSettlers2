/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.util;

import java.io.Serializable;

import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;


/**
 * This is a class to store a list of robot parameters.
 * Different bots may have different parameters, especially {@link #getStrategyType()}.
 * Sent from server via network message.
 *<P>
 * I put it in soc.util because the SOCServer and the
 * SOCDBHelper needed to use it, but I didn't think
 * they should have to include the soc.robot package.
 *
 * @author Robert S. Thomas
 */
public class SOCRobotParameters implements Serializable
{
    /** no structural changes since v1.0 (1000) or earlier */
    private static final long serialVersionUID = 1000L;

    protected int maxGameLength;
    protected int maxETA;
    protected float etaBonusFactor;
    protected float adversarialFactor;
    protected float leaderAdversarialFactor;
    protected float devCardMultiplier;
    protected float threatMultiplier;
    protected int strategyType; // SOCRobotDM.FAST_STRATEGY or SMART_STRATEGY
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
     * @param st   the strategy type: {@link soc.robot.SOCRobotDM#FAST_STRATEGY FAST_STRATEGY}
     *             or {@link soc.robot.SOCRobotDM#SMART_STRATEGY SMART_STRATEGY}
     * @param tf   the trade flag: Does this robot make/accept trades with players? (1 or 0)
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
     * copy constructor
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
     * Examine game options, and if any would change the robot parameters,
     * make a copy of these parameters with the changed options.
     * For example, game option "NT" means no trading, so if
     * our {@link #getTradeFlag()} is 1, copy and set it to 0.
     *
     * @param gameOpts The game's {@link SOCGameOption}s, or null
     * @return This object, or a copy with updated parameters.
     */
    public SOCRobotParameters copyIfOptionChanged(SOCGameOptionSet gameOpts)
    {
        if (gameOpts == null)
            return this;

        boolean copied = false;
        SOCRobotParameters params = this;

        if ((gameOpts != null) && gameOpts.isOptionSet("NT")
            && (1 == params.tradeFlag))
        {
            if (! copied)
            {
                copied = true;
                params = new SOCRobotParameters(params);
            }
            params.tradeFlag = 0;
        }

        // NEW_OPTION : If your option affects RobotParameters, look for it here.

        return params;
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
     * @return strategyType: {@link soc.robot.SOCRobotDM#FAST_STRATEGY}
     *         or {@link soc.robot.SOCRobotDM#SMART_STRATEGY}
     */
    public int getStrategyType()
    {
        return strategyType;
    }

    /**
     * Does this robot make/accept trades with other players?
     * @return tradeFlag: 1 if accepts, 0 if not
     */
    public int getTradeFlag()
    {
        return tradeFlag;
    }

    /**
     * Check for equality to another {@link SOCRobotParameters} or other object.
     *
     * @return true if {@code other} is a {@link SOCRobotParameters} having the same values
     *     for all fields listed in constructor
     * @see Object#equals(Object)
     * @since 2.4.50
     */
    public boolean equals(Object o)
    {
        if (! (o instanceof SOCRobotParameters))
            return false;

        final SOCRobotParameters params = (SOCRobotParameters) o;
        return (maxGameLength == params.maxGameLength)
            && (maxETA == params.maxETA)
            && (etaBonusFactor == params.etaBonusFactor)
            && (adversarialFactor == params.adversarialFactor)
            && (leaderAdversarialFactor == params.leaderAdversarialFactor)
            && (devCardMultiplier == params.devCardMultiplier)
            && (threatMultiplier == params.threatMultiplier)
            && (strategyType == params.strategyType)
            && (tradeFlag == params.tradeFlag);
    }

    /**
     * @return a human readable form of the data
     */
    @Override
    public String toString()
    {
        String s = "mgl=" + maxGameLength + "|me=" + maxETA +
                   "|ebf=" + etaBonusFactor + "|af=" + adversarialFactor +
                   "|laf=" + leaderAdversarialFactor + "|dcm=" + devCardMultiplier +
                   "|tm=" + threatMultiplier + "|st=" + strategyType +
                   "|tf=" + tradeFlag;

        return s;
    }

}
