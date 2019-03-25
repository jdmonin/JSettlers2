/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
 * Extracted in 2019 from SOCPlayerClient.java, so:
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
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

import java.awt.Container;
import java.util.Collection;
import java.util.Map;
import java.util.Timer;

import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.message.SOCGames;
import soc.util.SOCFeatureSet;

/**
 * A facade for the SOCPlayerClient to use to invoke actions in the main GUI
 * (as opposed to in-game {@link PlayerClientListener}):
 * Connect, list games and chat channels, etc.
 * Classic implementation is {@link SwingMainDisplay}.
 * @since 2.0.00
 */
public interface MainDisplay
{
    /** Returns the overall Client. */
    SOCPlayerClient getClient();

    /** Returns this client's GameMessageMaker. */
    GameMessageMaker getGameMessageMaker();

    /**
     * Returns this display's top-level GUI element: Panel, JPanel, etc.
     * Not necessarily a Frame or JFrame.
     */
    Container getGUIContainer();

    /**
     * For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * Use for fonts, components, and window sizes.
     * @return Display scaling factor, or 1 if none or unknown
     */
    int getDisplayScaleFactor();

    /**
     * Init the visual elements.  Done before connecting to server,
     * so we don't know its version or active {@link SOCFeatureSet}.
     * So, most of the Main Panel elements are initialized here but not
     * laid out or made visible until a later call to
     * {@link #showVersion(int, String, String, SOCFeatureSet)}
     * when the version and features are known.
     */
    void initVisualElements();

    /**
     * Prepare to connect and give feedback by showing a message panel.
     * Stores the given username and password in the user interface.
     *<P>
     * Does not make a network connection.
     * Call {@link ClientNetwork#connect(String, int)} when ready to make the connection.
     *<P>
     * User login and authentication don't occur until a game or channel join is requested;
     * at that time, the user interface will read the name and password stored here.
     *
     * @param cpass Password text to put into that TextField (obscured)
     * @param cuser User nickname text to put into that TextField
     */
    void connect(String cpass, String cuser);

    /**
     * Setup for locally hosting a TCP server.
     * If needed, a {@link ClientNetwork#localTCPServer local server} and robots are started, and client
     * connects to it, then visually indicate we are in Server Mode and port number.
     * If the {@link ClientNetwork#localTCPServer} is already created, does nothing.
     * If {@link ClientNetwork#connected connected} already, does nothing.
     *
     * @param tport  TCP port number to host on
     * @throws IllegalArgumentException If port is 0 or negative
     * @throws IllegalStateException  if already connected to a server
     */
    void startLocalTCPServer(final int tport)
        throws IllegalArgumentException, IllegalStateException;

    /**
     * Read and validate username and password GUI fields into client's data fields.
     * If username is invalid or empty, or we aren't ready to connect in some other way,
     * let the user know what to change.
     * @return true if OK, false if blank or not ready
     * @see #askStartGameWithOptions(String, boolean, Map, Map)
     */
    boolean readValidNicknameAndPassword();

    /**
     * Ask server to start a game with options.
     * If it's practice, will call {@link #startPracticeGame(String, Map, boolean)}.
     * Otherwise, ask tcp server, and also set {@code WAIT_CURSOR} and status line ("Talking to server...").
     *<P>
     * Assumes we're already connected to server, and that nickname, password, hostname are already validated.
     *
     * @param gmName Game name; for practice, null is allowed
     * @param forPracticeServer Is this for a new game on the practice (not tcp) server?
     * @param opts Set of {@link SOCGameOption game options} to use, or null
     * @param localPrefs Set of per-game local preferences to pass to {@link SOCPlayerInterface} constructor, or null
     * @see #readValidNicknameAndPassword()
     */
    void askStartGameWithOptions
        (final String gmName, final boolean forPracticeServer,
         final Map<String, SOCGameOption> opts, final Map<String, Object> localPrefs);

    /**
     * Clear any visual indicators that we are waiting for the network or other action, like {@code WAIT_CURSOR}.
     * @param clearStatus  If true, also clear any text out of the status line.
     */
    void clearWaitingStatus(final boolean clearStatus);

    /**
     * Act as if the "practice game" button has been clicked.
     * Assumes the dialog panels are all initialized.
     */
    void clickPracticeButton();
    void practiceGameStarting();

    void setMessage(String string);

    /**
     * Show an error dialog which has one button.
     * @param errMessage  Error message to show
     * @param buttonText  Button text, or null for "OK"
     */
    void showErrorDialog(String errMessage, String buttonText);

    /**
     * After network trouble, show a panel with the error message
     * instead of the main user/password/games/channels panel.
     *<P>
     * If we have the startup panel (started as JAR client app, not applet) with buttons to connect
     * to a server or practice, and {@code canPractice} is true, shows that panel instead of the
     * simpler practice-only message panel.
     *
     * @param err  Error message to show
     * @param canPractice  In current state of client, can we start a practice game?
     * @since 1.1.16
     */
    void showErrorPanel(String err, boolean canPractice);

    void enableOptions();

    /**
     * After connecting, display the remote server's version on main panel,
     * and update display based on its active {@link SOCFeatureSet}.
     * Not called for practice server.
     * If we're running a server, display its listening port # instead.
     * @param versionNumber  Version number, like 1119, from server's {@link soc.util.Version#versionNumber()}
     * @param versionString  Version string, like "1.1.19", from server's {@link soc.util.Version#version()}
     * @param buildString  Build number, from server's {@link soc.util.Version#buildnum()}
     * @param feats  Active optional server features; never null. If server is older than v1.1.19, use the
     *        {@link SOCFeatureSet#SOCFeatureSet(boolean, boolean) SOCFeatureSet(true, true)} constructor.
     */
    void showVersion
        (final int versionNumber, final String versionString, final String buildString, final SOCFeatureSet feats);

    /**
     * Show server welcome banner or status text.
     * If status during initial connect includes warning that the server's in Debug Mode, show that.
     * @param statusText  Status message text from server
     * @param debugWarn   True if server has Debug Mode active
     */
    void showStatus(String statusText, boolean debugWarn);

    /** Set the contents of the nickname field. */
    void setNickname(final String nm);

    /** If the password field is currently visible, focus the cursor there for the user to type something. */
    void focusPassword();

    /** Set the contents of the password field. */
    void setPassword(final String pw);

    void channelJoined(String channelName);
    void channelJoined(String channelName, String nickname);
    void channelMemberList(String channelName, Collection<String> members);
    void channelCreated(String channelName);
    void channelLeft(String channelName);
    void channelLeft(String channelName, String nickname);

    /**
     * Server has sent its list of chat channels (or an empty list), so
     * populate and show that list along with the rest of the UI.
     * The server sends the list when the client successfully connects.
     * @param channelNames  List of server's chat channels, from server message
     * @param isPractice  True if this is the practice server, not a TCP server
     */
    void channelList(Collection<String> channelNames, boolean isPractice);

    void channelDeleted(String channelName);
    void channelsClosed(String message);

    /**
     * Send a text message to a channel on the server,
     * or perform a local command like \ignore or \&shy;unignore.
     *
     * @param chName   the name of the channel
     * @param mes  the message text or local command
     * @see GameMessageMaker#sendText(SOCGame, String)
     */
    void sendToChannel(String chName, String mes);

    /** Print contents of the current chat ignorelist into a playerinterface's chat window. */
    void printIgnoreList(SOCPlayerInterface pi);

    /**
     * Print a broadcast message into all chat channel windows.
     * @param message  the message text
     * @see PlayerClientListener#messageBroadcast(String)
     */
    void chatMessageBroadcast(String message);

    /**
     * For a chat channel, print a received message into that channel's window.
     * @param channelName  the name of the channel
     * @param nickname  nickname of user sending the message,
     *     or {@code ":"} for server messages which should appear in the chat area (recap, etc).
     *     For {@code ":"}, the message text will probably end with " ::" because the original client would
     *     begin the text line with ":: " from {@code nickname + ": "}.
     * @param message  the message text
     * @see PlayerClientListener#messageReceived(String, String)
     */
    void chatMessageReceived(String channelName, String nickname, String message);

    /**
     * Callback for when a {@link NewGameOptionsFrame} is closed, to clear any reference to it here.
     * For example, the "New Game..." button might create and show an NGOF, and keep such a reference
     * to use if that button is clicked again instead of creating another NGOf and having both visible.
     */
    void dialogClosed(NewGameOptionsFrame ngof);

    PlayerClientListener gameJoined(SOCGame game, Map<String, Object> localPrefs);

    /**
     * Want to start a new game, on a server which supports options.
     * Do we know the valid options already?  If so, bring up the options window.
     * If not, ask the server for them. If a {@link NewGameOptionsFrame} is already
     * showing, give it focus instead of creating a new one.
     *<P>
     * For a summary of the flags and variables involved with game options,
     * and the client/server interaction about their values, see
     * {@link ServerGametypeInfo}.
     *
     * @param forPracticeServer  Ask {@link ClientNetwork#practiceServer}, instead of TCP server?
     * @param didAuth  If true, the server has authenticated our username and password;
     *     set those input fields read-only.
     */
    void gameWithOptionsBeginSetup(final boolean forPracticeServer, final boolean didAuth);

    void optionsRequested();

    /**
     * Server has sent its game option default values for new games.
     * Called when {@link ServerGametypeInfo#newGameWaitingForOpts} flag was set and
     * has just been cleared.  Client should show dialog to create a new game which
     * will have game options.
     *
     * @param opts  Client's game option info, tracking the TCP or local practice server
     * @param isPractice  True if received from {@link ClientNetwork#practiceServer}, instead of TCP server
     */
    void optionsReceived(ServerGametypeInfo opts, boolean isPractice);

    /**
     * Server has sent info about a single game option.  If {@code hasAllNow},
     * client should check {@link ServerGametypeInfo#newGameWaitingForOpts} and
     * {@link ServerGametypeInfo#gameInfoWaitingForOpts}, and if either of these
     * were waiting, show a game info/options dialog for a new game or existing game.
     *
     * @param opts  Client's game option info, tracking the TCP or local practice server
     * @param isPractice  True if received from {@link ClientNetwork#practiceServer}, instead of TCP server
     * @param isDash  True if the game option was {@code "-"}, indicating the end of the list.
     *     If so, no further options will be sent and any running timeout task related to the
     *     game options can be cancelled.
     * @param hasAllNow  If true, all game option info has now been received by the client
     */
    void optionsReceived(ServerGametypeInfo opts, boolean isPractice, boolean isDash, boolean hasAllNow);

    /**
     * Callback for when the client player leaves a game and closes its {@link SOCPlayerInterface}.
     * Remove that game from list of our PlayerInterfaces, do any other needed cleanup.
     */
    void leaveGame(SOCGame game);

    /**
     * Add a new game to the initial window's list of games.
     * If client can't join, makes sure the game is marked as unjoinable
     * in the {@link SOCPlayerClient#serverGames} list.
     *
     * @param cannotJoin Can we not join this game?
     * @param gameName the game name to add to the list;
     *            must not have the prefix {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     * @param gameOptsStr String of packed {@link SOCGameOption game options}, or null
     * @param addToSrvList Should this game be added to the list of remote-server games?
     *            True except for practice games, which should not be added.
     * @see SOCPlayerClient#addToGameList(String, String, boolean)
     * @see #deleteFromGameList(String, boolean, boolean)
     */
    void addToGameList(final boolean cannotJoin, String gameName, String gameOptsStr, final boolean addToSrvList);

    /**
     * Delete a game from the list.
     * If it's on the {@link SOCPlayerClient#serverGames} list, also remove from there.
     *
     * @param gameName  the game to remove
     * @param isPractice   Game is practice, not at tcp server?
     * @param withUnjoinablePrefix  True if game's display name starts with
     *     {@link SOCPlayerClient#GAMENAME_PREFIX_CANNOT_JOIN};
     *     {@code gameName} should not include this prefix
     * @return true if deleted, false if not found in list
     * @see #addToGameList(boolean, String, String, boolean)
     */
    boolean deleteFromGameList(String gameName, final boolean isPractice, final boolean withUnjoinablePrefix);

    /**
     * Utility for time-driven events in the client.
     * For some users, see where-used of this and of {@link SOCPlayerInterface#getEventTimer()}.
     * @return the timer
     */
    Timer getEventTimer();

}