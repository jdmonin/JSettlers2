/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2015 Jeremy D Monin <jeremy@nand.net>
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
 *     <tt>optionSet</tt> is set at client from {@link SOCGameOption#getAllKnownOptions()}.
 *<LI> At server connect, ask and receive info about options, if our version and the
 *     server's version differ.  Once this is done, <tt>allOptionsReceived</tt> == true.
 *     If server is older than 1.1.07, <tt>optionSet</tt> becomes null here
 *     because older servers don't support game options.
 *<LI> When user wants to create a new game, <tt>askedDefaultsAlready</tt> is false;
 *     ask server for its defaults (current option values for any new game).
 *     Also set <tt>newGameWaitingForOpts</tt> = true.
 *<LI> Server will respond with its current option values.  This sets
 *     <tt>defaultsReceived</tt> and updates <tt>optionSet</tt>.
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
 * Before v2.0.00 this class was <tt>{@link SOCPlayerClient}.GameOptionServerSet</tt>.
 *
 * @author jdmonin
 * @since 1.1.07
 */
class ServerGametypeInfo
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
     * Options will be null if {@link SOCPlayerClient#sVersion}
     * is less than {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS} (1.1.07).
     * Otherwise, set from {@link SOCGameOption#getAllKnownOptions()}
     * and update from server as needed.
     */
    public Map<String,SOCGameOption> optionSet = null;

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
     * @since 2.0.00
     */
    public boolean allScenStringsReceived = false;

    /**
     * Any scenario keynames for which the server has sent us i18n strings or responded with "unknown".
     * Empty if server hasn't sent any, ignored if {@link #allScenStringsReceived}.
     * @since 2.0.00
     */
    public HashSet<String> scenKeys;

    /**
     * Create a new ServerGametypeInfo, with an {@link #optionSet} defaulting
     * to our client version's {@link SOCGameOption#getAllKnownOptions()}.
     */
    public ServerGametypeInfo()
    {
        optionSet = SOCGameOption.getAllKnownOptions();
        scenKeys = new HashSet<String>();
    }

    /**
     * The server doesn't have any more options to send (or none at all, from its version).
     * Set fields as if we've already received the complete set of options, and aren't waiting
     * for any more.
     *<P>
     * Check the server version against {@link SOCNewGameWithOptions#VERSION_FOR_NEWGAMEWITHOPTIONS}
     * (1.1.07). If the server is too old to understand options, right after calling this method
     * you must set {@link #optionSet} = null.
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
     * Sets allOptionsReceived, defaultsReceived, optionSet.  If we already have non-null optionSet,
     * merge (update the values) instead of replacing the entire set with servOpts.
     *
     * @param servOpts The allowable {@link SOCGameOption} received from the server.
     *                 Assumes has been parsed already against the locally known opts,
     *                 so ones that we don't know are {@link SOCGameOption#OTYPE_UNKNOWN}.
     * @return null if all are known, or a Vector of key names for unknown options.
     */
    public List<String> receiveDefaults(final Map<String,SOCGameOption> servOpts)
    {
        // Although javadoc says "update the values", replacing the option objects does the
        // same thing; we already have parsed servOpts for all obj fields, including current value.
        // Option objects are always accessed by key name, so replacement is OK.

        if ((optionSet == null) || optionSet.isEmpty())
        {
            optionSet = servOpts;
        } else {
            for (String oKey : servOpts.keySet())
            {
                SOCGameOption op = servOpts.get(oKey);
                SOCGameOption oldcopy = optionSet.get(oKey);
                if (oldcopy != null)
                    optionSet.remove(oKey);
                optionSet.put(oKey, op);  // Even OTYPE_UNKNOWN are added
            }
        }

        List<String> unknowns = SOCVersionedItem.findUnknowns(servOpts);
        allOptionsReceived = (unknowns == null);
        defaultsReceived = true;
        return unknowns;
    }

    /**
     * After calling receiveDefaults, call this as each GAMEOPTIONGETINFO is received.
     * Updates allOptionsReceived.
     *
     * @param gi  Message from server with info on one parameter
     * @return true if all are known, false if more are unknown after this one
     */
    public boolean receiveInfo(SOCGameOptionInfo gi)
    {
        String oKey = gi.getOptionNameKey();
        SOCGameOption oinfo = gi.getOptionInfo();
        SOCGameOption oldcopy = optionSet.get(oKey);

        if ((oinfo.key.equals("-")) && (oinfo.optType == SOCGameOption.OTYPE_UNKNOWN))
        {
            // end-of-list marker: no more options from server.
            // That is end of srv's response to cli sending GAMEOPTIONGETINFOS("-").
            noMoreOptions(false);
            return true;
        } else {
            // remove old, replace with new from server (if any)
            if (oldcopy != null)
            {
                optionSet.remove(oKey);
                final SOCGameOption.ChangeListener cl = oldcopy.getChangeListener();
                if (cl != null)
                    oinfo.addChangeListener(cl);
            }
            SOCGameOption.addKnownOption(oinfo);
            if (oinfo.optType != SOCGameOption.OTYPE_UNKNOWN)
                optionSet.put(oKey, oinfo);
            return false;
        }
    }

}
