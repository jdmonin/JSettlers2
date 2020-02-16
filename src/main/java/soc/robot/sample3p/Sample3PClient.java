/*
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017-2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.baseclient.ServerConnectInfo;
import soc.game.SOCGame;
import soc.message.SOCMessage;
import soc.robot.SOCRobotBrain;
import soc.robot.SOCRobotClient;
import soc.util.CappedQueue;
import soc.util.SOCFeatureSet;
import soc.util.SOCRobotParameters;

/**
 * Sample of a trivially simple "third-party" subclass of {@link SOCRobotClient}
 * using {@link Sample3PBrain}. See that brain class's javadoc for what's different
 * from standard behavior. Also demonstrates how to tell the server this bot isn't
 * programmed to handle seafarers scenarios ({@link SOCFeatureSet#CLIENT_SCENARIO_VERSION}):
 * See {@link #buildClientFeats()}.
 *
 *<H5>Starting this bot as part of the Server:</H5>
 *
 * To have this bot run automatically as part of the server,
 * start the server with command-line parameter
 * {@code -Djsettlers.bots.start3p=2,soc.robot.sample3p.Sample3PClient} <BR>
 * For details, see {@link soc.server.SOCServer#PROP_JSETTLERS_BOTS_START3P}.
 *
 *<H5>Connecting to the Server:</H5>
 *
 * If this bot isn't started up as part of the SOCServer,
 * it must know the server's robot cookie to connect:
 *<UL>
 * <LI> Start the server with command-line parameter {@code -Djsettlers.bots.showcookie=Y}
 *      or a specific value like {@code -Djsettlers.bots.cookie=bottest97481483}
 * <LI> Start your bot client(s) with command line parameters like:<pre>
 *      localhost 8880 samplebot1 x bottest97481483
 *      localhost 8880 samplebot2 x bottest97481483
 *      localhost 8880 samplebot3 x bottest97481483</pre>
 *</UL>
 *
 *<H5>Other Useful Server Properties:</H5>
 *
 * See {@code /src/main/bin/jsserver.properties.sample} comments for more details on any parameter.
 *<BR>
 * All server properties can be specified in a {@code jsserver.properties} file,
 * or on the command line as {@code -Dpropertyname=value}.
 *<UL>
 * <LI> To use third-party bots as a certain percentage of the bots in each game:<BR>
 *      {@code jsettlers.bots.percent3p=50} (for 50%)
 * <LI> To give third-party bots more time before forcing their turn to end:<BR>
 *      {@code jsettlers.bots.timeout.turn=18} (for 18 seconds). The default is
 *      {@link soc.server.SOCServer#ROBOT_FORCE_ENDTURN_SECONDS SOCServer.ROBOT_FORCE_ENDTURN_SECONDS}.
 * <LI> To wait before starting robot-only games at server startup:<BR>
 *      {@code jsettlers.bots.botgames.wait_sec=30} (for 30 seconds)<BR>
        in order to give bot clients more time to connect first.
 *</UL>
 *
 * @author Jeremy D Monin
 * @since 2.0.00
 */
public class Sample3PClient extends SOCRobotClient
{
    /** Our class name, for {@link #rbclass}: {@code "soc.robot.sample3p.Sample3PClient"} */
    private static final String RBCLASSNAME_SAMPLE = Sample3PClient.class.getName();

    /**
     * Constructor for connecting to the specified server. Does not actually connect here:
     * Afterwards, must call {@link SOCRobotClient#init()} to actually initialize, start threads, and connect.
     *
     * @param sci server connect info with {@code robotCookie}; not {@code null}
     * @param nn nickname for robot
     * @param pw password for robot
     * @throws IllegalArgumentException if {@code sci == null}
     */
    public Sample3PClient(final ServerConnectInfo sci, final String nn, final String pw)
        throws IllegalArgumentException
    {
        super(sci, nn, pw);

        rbclass = RBCLASSNAME_SAMPLE;
    }

    /**
     * Build the set of optional client features this bot supports, to send to the server.
     * This sample client omits SOCScenario support ({@link SOCFeatureSet#CLIENT_SCENARIO_VERSION})
     * as an example.
     *<P>
     * Called from {@link SOCRobotClient#init()}.
     */
    @Override
    protected SOCFeatureSet buildClientFeats()
    {
        SOCFeatureSet feats = new SOCFeatureSet(false, false);
        feats.add(SOCFeatureSet.CLIENT_6_PLAYERS);
        feats.add(SOCFeatureSet.CLIENT_SEA_BOARD);
        // omits CLIENT_SCENARIO_VERSION

        return feats;
    }

    /**
     * Factory to provide our client's {@link Sample3PBrain} to games instead of the standard brain.
     *<P>
     * Javadocs from original factory:
     *<BR>
     * {@inheritDoc}
     */
    @Override
    public SOCRobotBrain createBrain
        (final SOCRobotParameters params, final SOCGame ga, final CappedQueue<SOCMessage> mq)
    {
        return new Sample3PBrain(this, params, ga, mq);
    }

    /**
     * Main method.
     * @param args  Expected arguments: server hostname, port, bot username, bot password, server cookie
     */
    public static void main(String[] args)
    {
        if (args.length < 5)
        {
            System.err.println("Java Settlers sample robotclient");
            System.err.println("usage: java " + RBCLASSNAME_SAMPLE + " hostname port_number bot_nickname password cookie");

            return;
        }

        Sample3PClient cli = new Sample3PClient
            (new ServerConnectInfo(args[0], Integer.parseInt(args[1]), args[4]), args[2], args[3]);
        cli.init();
    }

}
