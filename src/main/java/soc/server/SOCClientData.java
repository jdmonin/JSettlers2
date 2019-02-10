/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2008-2010,2013,2015,2017-2019 Jeremy D Monin <jeremy@nand.net>
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
 **/
package soc.server;

import java.util.Locale;
import java.util.Map;
import java.util.TimerTask;

import soc.message.SOCGameOptionGetInfos;  // for javadoc
import soc.message.SOCMessage;  // for javadoc
import soc.server.genericServer.Connection;
import soc.util.SOCFeatureSet;
import soc.util.SOCGameList;
import soc.util.SOCStringManager;  // for javadoc

/**
 * The server's place to track client-specific information across games.
 * The win-loss count is kept here.
 * Not tied to the optional database; information here is only for the current
 * session, not persistent across disconnects/reconnects by clients.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.1.04
 */
public class SOCClientData
{
    /** Number of games won and lost since client connected */
    private int wins, losses;

    /**
     * Client's reported optional features, or {@code null}.
     * Sent as an optional part of the SOCVersion message from clients 2.0.00 or newer;
     * third-party clients or simple bots may have no features.
     * For older 1.x.xx clients, this field has the default features from
     * {@link SOCFeatureSet#SOCFeatureSet(boolean, boolean) new SOCFeatureSet(true, false)}.
     * @see #hasLimitedFeats
     * @see #scenVersion
     * @since 2.0.00
     */
    public SOCFeatureSet feats;

    /**
     * If true, client is missing some optional features that the server expects
     * the built-in client to have. This client might not be able to join some games.
     * @see #feats
     * @since 2.0.00
     */
    public boolean hasLimitedFeats;

    /**
     * Client's reported JVM locale, or {@code null}, as in {@link java.util.Locale#getDefault()}.
     * Sent via {@link Locale#toString()} as part of the SOCVersion message.
     * Kept as {@link #localeStr} and also parsed to this field.
     * Not sent from jsettlers clients older than 2.0.00;
     * if null, should probably assume <tt>en_US</tt>
     * since older versions had all messages in english.
     * Bots always use a {@code null} locale; they don't care about message text contents, and a
     * null locale means they won't set any {@code SOCGame.hasMultiLocales} flag by joining.
     * @see #wantsI18N
     * @since 2.0.00
     */
    public Locale locale;

    /**
     * Client's reported JVM locale, or {@code null}, as in {@link java.util.Locale#toString()}.
     * Sent as part of the SOCVersion message, kept here and also parsed to {@link #locale}.
     * Not sent from jsettlers clients older than 2.0.00;
     * if null, should probably assume <tt>en_US</tt>
     * since older versions had all messages in english.
     * Bots always use a {@code null} locale, see {@link #locale} javadoc for details.
     * @see #wantsI18N
     * @since 2.0.00
     */
    public String localeStr;

    /**
     * If this flag is set, client has determined it wants localized strings (I18N),
     * and asked for them early in the connect process by sending a message
     * that had {@link SOCGameOptionGetInfos#hasTokenGetI18nDescs} true.
     * Server can later check this flag to see if responses to various client request
     * messages should include localized strings.
     *<P>
     * Set this flag only if:
     * <UL>
     *  <LI> Client has sent a {@link SOCGameOptionGetInfos} request with
     *     {@link SOCGameOptionGetInfos#hasTokenGetI18nDescs msg.hasTokenGetI18nDescs}
     *  <LI> {@link Connection#getI18NLocale() c.getI18NLocale()} != {@code null}
     *  <LI> {@link Connection#getVersion() c.getVersion()} &gt;= {@link SOCStringManager#VERSION_FOR_I18N};
     *     this is already implied by the client sending a message with {@code hasTokenGetI18nDescs}.
     * </UL>
     * @see #locale
     * @since 2.0.00
     */
    public boolean wantsI18N;

    /**
     * Number of games/channels this client has created, which currently exist (not deleted)
     * @since 1.1.10
     */
    private int currentCreatedGames, currentCreatedChannels;

    /** Synchronization for win-loss count and other counter fields */
    private Object countFieldSync;

    /**
     * Has the server's game list been sent to the client yet?
     * Please synchronize on {@link SOCGameList#takeMonitor()} / releaseMonitor.
     * @since 1.1.06
     */
    private boolean sentGameList;

    /**
     * If true we've called {@link SOCServer#clientHasLocalizedStrs_gameScenarios(Connection)},
     * storing the result in {@link #localeHasScenStrings}.
     * @since 2.0.00
     */
    public boolean checkedLocaleScenStrings;

    /**
     * If true we've called {@link SOCServer#clientHasLocalizedStrs_gameScenarios(Connection)},
     * and this client's locale is not {@code null} and has at least some localized scenario strings
     * (see that method's javadoc for details).
     * @since 2.0.00
     * @see #checkedLocaleScenStrings
     * @see #sentAllScenarioStrings
     */
    public boolean localeHasScenStrings;

    /**
     * True if we've sent localized strings for all {@link soc.game.SOCScenario SOCScenario}s.
     * To reduce network traffic, those large strings aren't sent unless the client is creating a
     * new game and needs the scenario dropdown.
     *<P>
     * Also true if the client's locale doesn't have localized strings for scenarios,
     * or if the client is too old (v1.x.xx) to use i18n localization.
     *
     * @since 2.0.00
     * @see #scenariosInfoSent
     * @see #sentAllScenarioInfo
     * @see #localeHasScenStrings
     */
    public boolean sentAllScenarioStrings;

    /**
     * True if we've sent all updated {@link soc.game.SOCScenario SOCScenario} info.
     * Like {@link #sentAllScenarioStrings}, scenario info messages aren't sent unless needed.
     *<P>
     * Also true if the client is too old (v1.x.xx) to use scenarios.
     *
     * @since 2.0.00
     * @see #scenariosInfoSent
     * @see #sentAllScenarioStrings
     */
    public boolean sentAllScenarioInfo;

    /**
     * For a scenario keyname in {@link #scenariosInfoSent}, value indicating that the client
     * was sent localized scenario strings (not all scenario info fields), or that the client
     * requested them and no localized strings were found for that scenario.
     * @see #SENT_SCEN_INFO
     * @since 2.0.00
     */
    public static final String SENT_SCEN_STRINGS = "S";

    /**
     * For a scenario keyname in {@link #scenariosInfoSent}, value indicating that the client
     * was sent all scenario info fields (not only localized scenario strings).
     * @see #SENT_SCEN_STRINGS
     * @since 2.0.00
     */
    public static final String SENT_SCEN_INFO = "I";

    /**
     * The {@link soc.game.SOCScenario SOCScenario} keynames for which we've
     * sent localized strings or all scenario info fields.
     * To reduce network traffic, those large strings aren't sent unless
     * the client is joining a game with a scenario, or has requested them.
     *<P>
     * For any scenario's keyname here, the value will be either {@link #SENT_SCEN_STRINGS} or {@link #SENT_SCEN_INFO}.
     * If a scenario's key isn't contained in this map, nothing has been sent about it
     * unless the {@link #sentAllScenarioStrings} flag is set.
     *<P>
     * Null if {@link #sentAllScenarioStrings} or if client hasn't requested any
     * or joined any game that has a scenario.
     *<P>
     * {@link soc.game.SOCGameOption SOCGameOption} strings are also localized, but aren't tracked
     * the same way because game option strings are all sent when the client connects.
     *
     * @since 2.0.00
     */
    public Map<String, String> scenariosInfoSent;

    /**
     * Is this connection a robot?
     * @see #isBuiltInRobot
     * @see soc.game.SOCPlayer#isRobot()
     * @since 1.1.07
     */
    public boolean isRobot;

    /**
     * Is this robot connection the built-in robot (not a 3rd-party),
     * with the original AI?
     * @see #robot3rdPartyBrainClass
     * @see #isRobot
     * @see soc.message.SOCImARobot
     * @see soc.game.SOCPlayer#isBuiltInRobot()
     * @since 1.1.09
     */
    public boolean isBuiltInRobot;

    /**
     * For 3rd-party robots, their type (brain class).
     * When {@link #isBuiltInRobot}, this field is null,
     * not {@link soc.message.SOCImARobot#RBCLASS_BUILTIN}.
     * @since 1.1.09
     */
    public String robot3rdPartyBrainClass;

    /**
     * Version of {@link soc.game.SOCScenario}s implemented by this client, or 0;
     * from {@link SOCFeatureSet#CLIENT_SCENARIO_VERSION} reported in {@link #feats}.
     * @since 2.0.00
     */
    public int scenVersion;

    /**
     * Are we considering a request to disconnect this client?
     * If so, the time we sent a ping (and awaiting a reply).
     * Same format as {@link System#currentTimeMillis()}.
     * Value is 0 otherwise.
     * Only client versions 1.1.08 and higher respond to
     * the {@link SOCMessage#SERVERPING} message.
     * @since 1.1.08
     */
    public long disconnectLastPingMillis;

    /**
     * TimerTask for connect-time client-version timer
     * @since 1.1.06
     */
    private SOCCDCliVersionTask cliVersionTask;

    public SOCClientData()
    {
        isRobot = false;
        countFieldSync = new Object();
        wins = 0;
        losses = 0;
        sentGameList = false;
        // other fields get their default java values (0 or null)
    }

    /**
     * Client has won a game; update win-loss count.
     * Thread-safe; synchronizes on an internal object.
     */
    public void wonGame()
    {
        synchronized (countFieldSync)
        {
            ++wins;
        }
    }

    /**
     * Client has lost a game; update win-loss count.
     * Thread-safe; synchronizes on an internal object.
     */
    public void lostGame()
    {
        synchronized (countFieldSync)
        {
            ++losses;
        }
    }

    /**
     * Client has created a game; update the count.
     * Thread-safe; synchronizes on an internal object.
     * @since 1.1.10
     */
    public void createdGame()
    {
        synchronized (countFieldSync)
        {
            ++currentCreatedGames;
        }
    }

    /**
     * Client has created a channel; update the count.
     * Thread-safe; synchronizes on an internal object.
     * @since 1.1.10
     */
    public void createdChannel()
    {
        synchronized (countFieldSync)
        {
            ++currentCreatedChannels;
        }
    }

    /**
     * Client has deleted a game they created; update the count.
     * Thread-safe; synchronizes on an internal object.
     * @since 1.1.10
     */
    public void deletedGame()
    {
        synchronized (countFieldSync)
        {
            --currentCreatedGames;
        }
    }

    /**
     * Client has deleted a channel they created; update the count.
     * Thread-safe; synchronizes on an internal object.
     * @since 1.1.10
     */
    public void deletedChannel()
    {
        synchronized (countFieldSync)
        {
            --currentCreatedChannels;
        }
    }

    /**
     * @return Number of games won by this client in this session
     */
    public int getWins()
    {
        return wins;
    }

    /**
     * @return Number of games lost by this client in this session
     */
    public int getLosses()
    {
        return losses;
    }

    /**
     * @return Number of games this client has created, which currently exist (not deleted)
     * @since 1.1.10
     */
    public int getCurrentCreatedGames()
    {
        return currentCreatedGames;
    }

    /**
     * @return Number of channels this client has created, which currently exist (not deleted)
     * @since 1.1.10
     */
    public int getcurrentCreatedChannels()
    {
        return currentCreatedChannels;
    }

    /**
     * Copy the client's win-loss record from another SOCClientData.
     * ({@link #getWins()}, {@link #getLosses()}).
     *
     * @param source Copy from here
     * @since 1.1.08
     */
    public void copyClientPlayerStats(SOCClientData source)
    {
        wins = source.wins;
        losses = source.losses;
        currentCreatedGames = source.currentCreatedGames;
        currentCreatedChannels = source.currentCreatedChannels;
    }

    /**
     * Has the server's game list been sent to the client yet?
     * Please synchronize on {@link SOCGameList#takeMonitor()} / releaseMonitor.
     * @since 1.1.06
     */
    public boolean hasSentGameList()
    {
        return sentGameList;
    }

    /**
     * Set flag: Has the server's game list been sent to the client yet?
     * Please synchronize on {@link SOCGameList#takeMonitor()} / releaseMonitor.
     * @since 1.1.06
     */
    public void setSentGameList()
    {
        sentGameList = true;
    }

    /**
     * Set up the version timer.
     * It will fire after {@link SOCServer#CLI_VERSION_TIMER_FIRE_MS} milliseconds.
     * @param sr  Our SOCServer
     * @param con Connection for this timer / this clientdata
     * @since 1.1.06
     */
    public void setVersionTimer(SOCServer sr, Connection con)
    {
        cliVersionTask = new SOCCDCliVersionTask (sr, this, con);
        sr.utilTimer.schedule(cliVersionTask, SOCServer.CLI_VERSION_TIMER_FIRE_MS);
    }

    /**
     * Cancel the version timer, don't fire it.
     * @since 1.1.06
     */
    public void clearVersionTimer()
    {
        if (cliVersionTask != null)
        {
            cliVersionTask.cancel();
            cliVersionTask = null;
        }
    }


    /**
     * TimerTask at client connect, to guess the client version
     * if it isn't sent soon enough. (assume it's too old to tell us)
     *<P>
     * When timer fires, assume client's version will not be sent.
     * Set it to {@link SOCServer#CLI_VERSION_ASSUMED_GUESS}.
     * (Don't set the version if cliConn.isVersionKnown() at that point.)
     * Ask server to send the list of games.
     * The version can be corrected later if necessary.
     * @since 1.1.06
     */
    private static class SOCCDCliVersionTask extends TimerTask
    {
        private SOCServer srv;
        private SOCClientData cliData;
        private Connection cliConn;

        public SOCCDCliVersionTask (SOCServer sr, SOCClientData cd, Connection con)
        {
            srv = sr;
            cliData = cd;
            cliConn = con;
        }

        /**
         * Called when timer fires. See class description for action taken.
         */
        public void run()
        {
            cliData.cliVersionTask = null;  // Clear reference to this soon-to-expire obj
            if (! cliConn.isVersionKnown())
            {
                srv.setClientVersSendGamesOrReject(cliConn, SOCServer.CLI_VERSION_ASSUMED_GUESS, null, null, false);
                // will also send game list.
                // if cli vers already known, it's already sent the list.
            }
        }

    }  // SOCCDCliVersionTask

}  // SOCClientData
