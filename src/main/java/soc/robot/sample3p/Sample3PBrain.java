/*
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017-2021 Jeremy D Monin <jeremy@nand.net>
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
 */
package soc.robot.sample3p;

import soc.game.SOCGame;
import soc.game.SOCGameOptionSet;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.message.SOCMessage;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.robot.SOCRobotNegotiator;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

/**
 * Sample of a trivially simple "third-party" subclass of {@link SOCRobotBrain}
 * Instantiated by {@link Sample3PClient}.
 *<P>
 * Trivial behavioral changes from standard {@code SOCRobotBrain}:
 *<UL>
 * <LI> When sitting down, greet the game members: {@link #setOurPlayerData()}
 * <LI> Uses third-party {@link SampleDiscardStrategy}: {@link #setStrategyFields()}
 * <LI> Reject trades unless we're offered clay or sheep: {@link #considerOffer(SOCTradeOffer)}
 *</UL>
 *
 * @author Jeremy D Monin
 * @since 2.0.00
 */
public class Sample3PBrain extends SOCRobotBrain
{
    /**
     * Number of declined trades in the current negotiation.
     * An example of custom state tracked by the bot during turns.
     * @since 2.5.00
     */
    protected int numDeclinedTrades = 0;

    /**
     * Standard brain constructor; for javadocs see
     * {@link SOCRobotBrain#SOCRobotBrain(SOCRobotClient, SOCRobotParameters, SOCGame, CappedQueue)}.
     */
    public Sample3PBrain(SOCRobotClient rc, SOCRobotParameters params, SOCGame ga, CappedQueue<SOCMessage> mq)
    {
        super(rc, params, ga, mq);
    }

    /**
     * After the standard actions of {@link SOCRobotBrain#setOurPlayerData()},
     * sends a "hello" chat message as a sample action using {@link SOCRobotClient#sendText(SOCGame, String)}.
     * This bot also overrides {@link #setStrategyFields()}.
     *<P>
     * If the for-bots extra game option {@link SOCGameOptionSet#K__EXT_BOT} was set at the server command line,
     * prints its value to {@link System#err}. A third-party bot might want to use that option's value
     * to configure its behavior or debug settings.
     *<P>
     *<B>I18N Note:</B> Robots don't know what languages or locales the human players can read:
     * It would be unfair for a bot to ever send text that the players must understand
     * for gameplay. So this sample bot's "hello" is not localized.
     */
    @Override
    public void setOurPlayerData()
    {
        super.setOurPlayerData();

        final String botName = client.getNickname();
        client.sendText(game, "Hello from sample bot " + botName + "!");

        final String optExtBot = game.getGameOptionStringValue(SOCGameOptionSet.K__EXT_BOT);
        if (optExtBot != null)
            System.err.println("Bot " + botName + ": __EXT_BOT is: " + optExtBot);
    }

    /**
     * Override to use our custom {@link SampleDiscardStrategy}.
     * All other strategies are standard.
     */
    @Override
    protected void setStrategyFields()
    {
        super.setStrategyFields();
        discardStrategy = new SampleDiscardStrategy(game, ourPlayerData, this, rand);
    }

    /**
     * Override to clear our custom trade-related counter.
     */
    @Override
    public void resetFieldsAndBuildingPlan()
    {
        super.resetFieldsAndBuildingPlan();
        numDeclinedTrades = 0;
    }

    /**
     * Override to clear our custom trade-related counter.
     */
    @Override
    public void resetFieldsAtEndTurn()
    {
        super.resetFieldsAtEndTurn();
        numDeclinedTrades = 0;
    }

    /**
     * Consider a trade offer; reject if we aren't offered clay or sheep
     * unless {@link #numDeclinedTrades} &gt; 2.
     *<P>
     * {@inheritDoc}
     */
    @Override
    protected int considerOffer(SOCTradeOffer offer)
    {
        if (! offer.getTo()[getOurPlayerNumber()])
        {
            return SOCRobotNegotiator.IGNORE_OFFER;
        }

        final SOCResourceSet res = offer.getGiveSet();
        if ((numDeclinedTrades <= 2)
            && ! (res.contains(SOCResourceConstants.CLAY) || res.contains(SOCResourceConstants.SHEEP)))
        {
            return SOCRobotNegotiator.REJECT_OFFER;
        }

        return super.considerOffer(offer);
    }

}
