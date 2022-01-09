/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2014,2016-2018,2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;

import soc.proto.Message;
import soc.util.SOCRobotParameters;

import java.util.StringTokenizer;


/**
 * This message from server tells the robot client to update its
 * {@link SOCRobotParameters robot parameters} to these values.
 * Sent in response to bot's {@link SOCImARobot}.
 *
 * @author Robert S. Thomas
 */
public class SOCUpdateRobotParams extends SOCMessage
{
    private static final long serialVersionUID = 100L;  // last structural change v1.0.0 or earlier

    private SOCRobotParameters params;

    /**
     * Create a UpdateRobotParams message.
     *
     * @param par  the robot parameters
     */
    public SOCUpdateRobotParams(SOCRobotParameters par)
    {
        messageType = UPDATEROBOTPARAMS;
        params = par;
    }

    /**
     * @return the robot parameters
     */
    public SOCRobotParameters getRobotParameters()
    {
        return params;
    }

    /**
     * UPDATEROBOTPARAMS sep maxGameLength sep2 maxETA sep2 etaBonusFactor sep2 adversarialFactor
     *   sep2 leaderAdversarialFactor sep2 devCardMultiplier sep2 threatMultiplier sep2 strategyType sep2 tradeFlag
     *
     * @return the command string
     */
    public String toCmd()
    {
        return UPDATEROBOTPARAMS + sep + params.getMaxGameLength() + sep2 + params.getMaxETA()
            + sep2 + params.getETABonusFactor() + sep2 + params.getAdversarialFactor()
            + sep2 + params.getLeaderAdversarialFactor() + sep2 + params.getDevCardMultiplier()
            + sep2 + params.getThreatMultiplier() + sep2 + params.getStrategyType() + sep2 + params.getTradeFlag();
    }

    /**
     * Parse the command String into a UpdateRobotParams message
     *
     * @param s   the String to parse
     * @return    a UpdateRobotParams message, or null if the data is garbled
     */
    public static SOCUpdateRobotParams parseDataStr(String s)
    {
        int mgl;   // maxGameLength
        int me;    // maxETA
        float ebf; // etaBonusFactor
        float af;  // adversarialFactor
        float laf; // leaderAdversarialFactor
        float dcm; // devCardMultiplier
        float tm;  // threatMultiplier
        int st;    // strategyType
        int tf;    // trade flag

        StringTokenizer stok = new StringTokenizer(s, sep2);

        try
        {
            mgl = Integer.parseInt(stok.nextToken());
            me = Integer.parseInt(stok.nextToken());
            ebf = (Float.valueOf(stok.nextToken())).floatValue();
            af = (Float.valueOf(stok.nextToken())).floatValue();
            laf = (Float.valueOf(stok.nextToken())).floatValue();
            dcm = (Float.valueOf(stok.nextToken())).floatValue();
            tm = (Float.valueOf(stok.nextToken())).floatValue();
            st = Integer.parseInt(stok.nextToken());
            tf = Integer.parseInt(stok.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCUpdateRobotParams(new SOCRobotParameters(mgl, me, ebf, af, laf, dcm, tm, st, tf));
    }

    @Override
    public Message.FromServer toProtoFromServer()
    {
        return Message.FromServer.newBuilder()
            .setBotUpdateParams(Message.BotUpdateParams.newBuilder()
                .setMaxGameLength(params.getMaxGameLength()).setMaxEta(params.getMaxETA())
                .setEtaBonusFactor(params.getETABonusFactor()).setAdversarialFactor(params.getAdversarialFactor())
                .setLeaderAdversarialFactor(params.getLeaderAdversarialFactor())
                .setDevCardMultiplier(params.getDevCardMultiplier())
                .setThreatMultiplier(params.getThreatMultiplier())
                .setStrategyType(params.getStrategyType())
                .setTradeFlag(1 == params.getTradeFlag())).build();
    }

    /**
     * @return a human readable form of the message, calling {@link SOCRobotParameters#toString()} for field contents
     */
    public String toString()
    {
        return "SOCUpdateRobotParams:" + params;
    }

}
