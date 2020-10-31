/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2015,2018-2020 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>:
 *     - parameterize types
 * This file's contents were formerly part of SOCPlayerClient.java:
 *     - class GameOptionServerSet created in 2007 by Jeremy Monin for v1.1.07
 *     - class renamed and moved to own file in 2015 for v2.0.00
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

package soc.client;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCScenario;
import soc.game.SOCVersionedItem;
import soc.message.SOCGameOptionInfo;
import soc.message.SOCNewGameWithOptions;

/**
 * Track the server's information about the game type: valid game option set, scenarios, etc.
 * Client has one instance for remote tcp server, one for practice server.
 * For simplicity, getters/setters are not included: Synchronize on the object to set/read its fields.
 *<P>
 * In v2.0.00 and newer, also tracks all {@link SOCScenario}s' i18n localized strings.
 *<P>
 * Interaction with client-server messages at connect:
 *<OL>
 *<LI> First, this object is created; <tt>allOptionsReceived</tt> false,
 *     <tt>newGameWaitingForOpts</tt> false.
 *     <tt>knownOpts</tt> is set at client from {@link SOCGameOptionSet#getAllKnownOptions()}.
 *<LI> At server connect, ask and receive info about options, if our version and the
 *     server's version differ.  Once this is done, <tt>allOptionsReceived</tt> == true.
 *     If server is older than 1.1.07, <tt>knownOpts</tt> becomes null here
 *     because older servers don't support game options.
 *<LI> When user wants to create a new game, <tt>askedDefaultsAlready</tt> is false;
 *     ask server for its defaults (current option values for any new game).
 *     Also set <tt>newGameWaitingForOpts</tt> = true.
 *<LI> Server will respond with its current option values.  This sets
 *     <tt>defaultsReceived</tt> and updates <tt>knownOpts</tt>.
 *     It's possible that the server's defaults contain option names that are
 *     unknown at our version.  If so, <tt>allOptionsReceived</tt> is cleared, and we ask the
 *     server about those specific options.
 *     Otherwise, clear <tt>newGameWaitingForOpts</tt>.
 *<LI> If waiting on option info from defaults above, the server replies with option info.
 *     (They may remain as type {@link SOCGameOption#OTYPE_UNKNOWN}.)
 *     Once these are all received, set <tt>allOptionsReceived</tt> = true,
 *     clear <tt>newGameWaitingForOpts</tt>.
 *<LI> Once  <tt>newGameWaitingForOpts</tt> == false, show the {@link NewGameOptionsFrame}.
 *</OL>
 *<P>
 * Server scenario info is sent on demand, instead of sending all info when the client connects:
 *<UL>
 *<LI> When "Game Info" is clicked for a game, and the game has option "SC" with a scenario
 *     not found in {@link #scenKeys}, client will ask the server for info about that scenario.
 *     Meanwhile it will display game info, and assume the server will reply before the user
 *     clicks "scenario info" in the game info popup.
 *<LI> When client joins a game, it will need scenario info as in the "Game Info" case above.
 *<LI> When "New Game" is clicked to create a new game, the client needs all scenarios' info and
 *     will ask the server for all updated scenario info, just as it does at connect for game options.
 *</UL>
 *<P>
 * Before v2.0.00 this class was <tt>{@link SOCPlayerClient}.GameOptionServerSet</tt>.
 *
 * @author jdmonin
 * @since 1.1.07
 */
/*package*/ class ServerGametypeInfo
{
    /**
     * If true, we know all options on this server,
     * or the server is too old to support options.
     */
    public boolean   allOptionsReceived = false;

    /**
     * If true, we've asked the server about defaults or options because
     * we're about to create a new game.  When all are received,
     * we should create and show a NewGameOptionsFrame.
     */
    public boolean   newGameWaitingForOpts = false;

    /**
     * If non-null, we're waiting to hear about game options because
     * user has clicked 'game info' on a game.  When all are
     * received, we should create and show a NewGameOptionsFrame
     * with that game's options.
     */
    public String    gameInfoWaitingForOpts = null;

    /**
     * Known Options of the connected server; will be null if {@link SOCPlayerClient#sVersion}
     * is less than {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS} (1.1.07).
     * Otherwise, set from {@link SOCGameOptionSet#getAllKnownOptions()}
     * and update from server as needed.
     *<P>
     * May contain {@link SOCGameOption#OTYPE_UNKNOWN} opts sent from server
     * as part of gameopt info synchronization.
     *<P>
     * Before v2.4.50 this field was {@code optionSet}.
     * @see #newGameOpts
     */
    public SOCGameOptionSet knownOpts = null;

    /**
     * Deep copy of {@link #knownOpts} for {@link NewGameOptionsFrame}
     * to remember game option values selected by user for the next new game.
     * Null if {@code knownOpts} is null.
     *<P>
     * {@code NewGameOptionsFrame} may remove any {@link SOCGameOption#OTYPE_UNKNOWN} options
     * from this set, but they will remain in {@code knownOpts}.
     * @since 2.4.50
     */
    public SOCGameOptionSet newGameOpts = null;

    /** Have we asked the server for default values? */
    public boolean   askedDefaultsAlready = false;

    /** Has the server told us defaults? */
    public boolean   defaultsReceived = false;

    /**
     * If {@link #askedDefaultsAlready}, the time it was asked,
     * as returned by {@link System#currentTimeMillis()}.
     */
    public long askedDefaultsTime;

    /**
     * If true, the server sent us all scenarios' i18n strings,
     * has none for our locale, or is too old to support them.
     *<P>
     * The server sends all scenario strings when client has asked
     * for game option defaults for the dialog to create a new game.
     * @see #scenKeys
     * @see #allScenInfoReceived
     * @since 2.0.00
     */
    public boolean allScenStringsReceived = false;

    /**
     * If true, the server sent us information on all added or changed scenarios,
     * and any localized i18n scenario strings ({@link #allScenStringsReceived}).
     * @see #scenKeys
     * @since 2.0.00
     */
    public boolean allScenInfoReceived = false;

    /**
     * Any scenario keynames for which the server has sent us updated info or i18n strings or responded with "unknown".
     * Empty if server hasn't sent any, ignored if {@link #allScenStringsReceived} or {@link #allScenStringsReceived}.
     * @since 2.0.00
     */
    public HashSet<String> scenKeys;

    /**
     * Create a new ServerGametypeInfo, with an {@link #knownOpts} defaulting
     * to our client version's {@link SOCGameOptionSet#getAllKnownOptions()}.
     */
    public ServerGametypeInfo()
    {
        knownOpts = SOCGameOptionSet.getAllKnownOptions();
        scenKeys = new HashSet<String>();
    }

    /**
     * The server doesn't have any more options to send (or none at all, from its version).
     * Set fields as if we've already received the complete set of options, and aren't waiting
     * for any more.
     *<P>
     * Check the server version against {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}
     * (1.1.07). If the server is too old to understand options, right after calling this method
     * you must set {@link #knownOpts} = null.
     * @param askedDefaults Should we also set the askedDefaultsAlready flag? It not, leave it unchanged.
     */
    public void noMoreOptions(boolean askedDefaults)
    {
        allOptionsReceived = true;
        if (askedDefaults)
        {
            defaultsReceived = true;
            askedDefaultsAlready = true;
            askedDefaultsTime = System.currentTimeMillis();
        }
    }

    /**
     * Set of default options has been received from the server, examine them.
     * Sets allOptionsReceived, defaultsReceived, knownOpts.  If we already have non-null knownOpts,
     * use servOpts to replace its {@link SOCGameOption} references instead of creating a new Map.
     *
     * @param servOpts The allowable {@link SOCGameOption}s received from the server.
     *                 Assumes has been parsed already against the locally known opts,
     *                 so any opts that we don't know are {@link SOCGameOption#OTYPE_UNKNOWN}.
     * @return null if all are known, or a list of key names for unknown options.
     * @see #receiveInfo(SOCGameOptionInfo)
     */
    public List<String> receiveDefaults(final Map<String, SOCGameOption> servOpts)
    {
        // Replacing the changed option objects is effectively the same as updating their default values;
        // we already parsed these servOpts for all SGO fields, including current value.
        // Option objects are always accessed by key name, so replacement is OK.

        HashSet<String> prevKnown = null;

        if ((knownOpts == null) || knownOpts.isEmpty())
        {
            knownOpts = new SOCGameOptionSet(servOpts);
        } else {
            prevKnown = new HashSet<>();
            for (String oKey : servOpts.keySet())
            {
                SOCGameOption op = servOpts.get(oKey);
                if (knownOpts.put(op) != null)  // always add, even if OTYPE_UNKNOWN
                    prevKnown.add(oKey);
            }
        }

        List<String> unknowns = SOCVersionedItem.findUnknowns(servOpts, prevKnown);
        allOptionsReceived = (unknowns == null);
        defaultsReceived = true;

        return unknowns;
    }

    /**
     * After calling receiveDefaults, call this as each GAMEOPTIONGETINFO is received.
     * Calls {@link SOCGameOptionSet#addKnownOption(SOCGameOption)}.
     * May update {@code allOptionsReceived} flag.
     * If client already had information about this game option, that old info is discarded
     * but any {@link SOCGameOption.ChangeListener} is copied to the message's new {@link SOCGameOption}.
     *
     * @param gi  Message from server with info on one parameter, or end-of-list marker
     *     {@link SOCGameOptionInfo#OPTINFO_NO_MORE_OPTS}
     * @return true if all are known, false if more are still unknown after this
     *     because {@code gi} isn't the end-of-list marker
     */
    public boolean receiveInfo(SOCGameOptionInfo gi)
    {
        final SOCGameOption oinfo = gi.getOptionInfo();
        final boolean isUnknown = (oinfo.optType == SOCGameOption.OTYPE_UNKNOWN);

        if ((oinfo.key.equals("-")) && isUnknown)
        {
            // end-of-list marker: no more options from server.
            // That is end of srv's response to cli sending GAMEOPTIONGETINFOS("-").

            noMoreOptions(false);

            return true;
        } else {
            // remove old, replace with new from server (if any)

            knownOpts.addKnownOption(oinfo);
            if (isUnknown)
                knownOpts.put(oinfo);  // since addKnownOption won't add an unknown

            return false;
        }
    }

}
