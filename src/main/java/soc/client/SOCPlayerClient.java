/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2019 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
 *     - UI layer refactoring, GameStatistics, nested class refactoring, parameterize types
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import soc.baseclient.SOCDisplaylessPlayerClient;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCScenario;

import soc.message.*;

import soc.util.I18n;
import soc.util.SOCFeatureSet;
import soc.util.SOCGameList;
import soc.util.SOCStringManager;
import soc.util.Version;

/**
 * Standalone client for connecting to the SOCServer. (For applet see {@link SOCApplet}.)
 * The main user interface class {@link SwingMainDisplay} prompts for name and password,
 * then connects and displays the lists of games and channels available.
 * An actual game is played in a separate {@link SOCPlayerInterface} window.
 *<P>
 * If you want another connection port, you have to specify it as the "port"
 * argument in the html source. If you run this as a stand-alone, the port can be
 * specified on the command line or typed into {@code SwingMainDisplay}'s connect dialog.
 *<P>
 * At startup or init, will try to connect to server via {@link ClientNetwork#connect(String, int)}.
 * See that method for more details.
 *<P>
 * There are three possible servers to which a client can be connected:
 *<UL>
 *  <LI>  A remote server, running on the other end of a TCP connection
 *  <LI>  A local TCP server, for hosting games, launched by this client:
 *        {@link ClientNetwork#localTCPServer}
 *  <LI>  A "practice game" server, not bound to any TCP port, for practicing
 *        locally against robots: {@link ClientNetwork#practiceServer}
 *</UL>
 * A running client can be connected to at most one TCP server at a time, plus the practice server.
 * Its single shared list of games shows those on the server and any practice games.
 * Each game's {@link SOCGame#isPractice} flag determines which connection to use.
 *<P>
 * Once connected, messages from the server are processed in {@link MessageHandler#handle(SOCMessage, boolean)}.
 *<P>
 * Messages to the server are formed and sent using {@link GameMessageMaker}.
 *<P>
 * If network trouble or applet shutdown occurs, calls {@link #shutdownFromNetwork()};
 * may still be able to play practice games locally.
 *
 * @author Robert S Thomas
 */
public class SOCPlayerClient
{
    /**
     * String property {@code jsettlers.debug.clear_prefs} to support testing and debugging:
     * When present, at startup this list of persistent client {@link Preferences} keys will be removed:
     * The {@code SOCPlayerClient} constructor will call {@link UserPreferences#clear(String)}.
     *<P>
     * Format: String of comma-separated preference key names: {@link #PREF_PI__WIDTH}, {@link #PREF_SOUND_ON},
     * {@link #PREF_BOT_TRADE_REJECT_SEC}, etc.
     * @since 1.2.00
     */
    public static final String PROP_JSETTLERS_DEBUG_CLEAR__PREFS = "jsettlers.debug.clear_prefs";

    /**
     * String property {@code jsettlers.debug.client.features} to support server testing and debugging:
     * When present, client will report these Client Features to server in its {@code Version} message
     * instead of its actual features from <tt>{@link SOCFeatureSet}.CLIENT_*</tt>.
     *<P>
     * Format: Empty for none, or string of semicolon-surrounded client features: <tt>";6pl;sb;"</tt><BR>
     * Same format as {@link SOCFeatureSet#getEncodedList()}.
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_DEBUG_CLIENT_FEATURES = "jsettlers.debug.client.features";

    /**
     * Integer persistent {@link Preferences} key for width of a {@link SOCPlayerInterface} window frame,
     * based on most recent resizing by user. Used with {@link #PREF_PI__HEIGHT}.
     *<P>
     * For games with 6 players and/or the sea board, a scaling factor is applied to this preference
     * to keep consistent window sizes and width/height ratios.
     * @since 1.2.00
     */
    public static final String PREF_PI__WIDTH = "PI_width";

    /**
     * Integer persistent {@link Preferences} key for height of a {@link SOCPlayerInterface} window frame;
     * see {@link #PREF_PI__WIDTH} for details.
     * @since 1.2.00
     */
    public static final String PREF_PI__HEIGHT = "PI_height";

    /**
     * Boolean persistent {@link Preferences} key for sound effects.
     * Default value is {@code true}.
     *<P>
     * To set this value for a new {@link SOCPlayerInterface}, use
     * {@link SOCPlayerInterface#PREF_SOUND_MUTE} as the key within
     * its {@code localPrefs} map.
     * @see UserPreferences#getPref(String, boolean)
     * @see SOCPlayerInterface#isSoundMuted()
     * @since 1.2.00
     */
    public static final String PREF_SOUND_ON = "soundOn";

    /**
     * Integer persistent {@link Preferences} key for starting value of the countdown to auto-reject bot trades,
     * in seconds. Negative if auto-reject is disabled, to keep the setting's value for {@link NewGameOptionsFrame}
     * and "Options" dialogs without also having a separate enabled/disabled flag.
     *<P>
     * Default value is -8. Do not store 0.
     *<P>
     * This key name can be used with the {@link SOCPlayerInterface} constructor's {@code localPrefs} map
     * during game setup. If negative there, auto-reject will be disabled until turned on from that {@code PI}'s
     * "Options" button.
     * @see UserPreferences#getPref(String, int)
     * @see SOCPlayerInterface#getBotTradeRejectSec()
     * @since 1.2.00
     */
    public static final String PREF_BOT_TRADE_REJECT_SEC = "botTradeRejectSec";

    /**
     * i18n text strings in our {@link #cliLocale}.
     * @since 2.0.00
     */
    final soc.util.SOCStringManager strings; // TODO: not a very good name

    /**
     * Prefix text to indicate a game this client cannot join: "(cannot join) "<BR>
     * Non-final for localization. Localize before calling {@link SwingMainDisplay.JoinableListItem#toString()}.
     * @since 1.1.06
     */
    protected static String GAMENAME_PREFIX_CANNOT_JOIN = "(cannot join) ";

    /**
     * For use in password fields, and possibly by other places, detect if we're running on
     * Mac OS X.  To identify osx from within java, see technote TN2110:
     * http://developer.apple.com/technotes/tn2002/tn2110.html
     *<P>
     * Before v2.0.00 this field was {@code isJavaOnOSX}.
     *
     * @since 1.1.07
     */
    public static final boolean IS_PLATFORM_MAC_OSX;

    /**
     * Is this a windows platform, according to {@link System#getProperty(String) System.getProperty("os.name")}?
     *<P>
     * Before v2.0.00 this field was {@code SOCPlayerInterface.SOCPI_isPlatformWindows}.
     *
     * @see #IS_PLATFORM_MAC_OSX
     * @since 1.1.08
     */
    /*package*/ static final boolean IS_PLATFORM_WINDOWS;

    static {
        String osName = System.getProperty("os.name");
        IS_PLATFORM_WINDOWS = (osName != null) && (osName.toLowerCase().indexOf("windows") != -1);
        IS_PLATFORM_MAC_OSX = (osName != null) && osName.toLowerCase().startsWith("mac os x");
    }

    static
    {
        if (IS_PLATFORM_MAC_OSX)
        {
            // Must set "OSX look and feel" items before calling any AWT code
            // or setting a platform look and feel.

            System.setProperty("apple.awt.application.name", "JSettlers");
                // Required on OSX 10.7 or so and newer; works for apple java 6
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "JSettlers");
                // Works for earlier OSX versions
        }
    }

    /**
     * Locale for i18n message lookups used for {@link #strings}.  Also sent to server while connecting.
     * Override if needed in the constructor by reading the
     * {@link I18n#PROP_JSETTLERS_LOCALE PROP_JSETTLERS_LOCALE} system property {@code "jsettlers.locale"}.
     * @since 2.0.00
     */
    final Locale cliLocale;

    /**
     * Helper object to deal with network connectivity.
     * @since 2.0.00
     */
    private ClientNetwork net;

    /**
     * Helper object to receive incoming network traffic from the server.
     */
    private final MessageHandler messageHandler;

    /**
     * Helper object to form and send outgoing network traffic to the server.
     * @since 2.0.00
     */
    private final GameMessageMaker gameMessageMaker;

    /**
     * Display for the main user interface, including and beyond the list of games and chat channels.
     * Individual games are {@link PlayerClientListener}s / {@link SOCPlayerInterface}s.
     */
    private MainDisplay mainDisplay;

    /**
     *  Server version number for remote server, sent soon after connect, 0 if no server, or -1 if version unknown.
     *  A local practice server's version is always {@link Version#versionNumber()}, not {@code sVersion},
     *  so always check {@link SOCGame#isPractice} before checking this field.
     */
    protected int sVersion;

    /**
     * Server's active optional features, sent soon after connect, or null if unknown.
     * Not used with a local practice server, so always check {@link SOCGame#isPractice} before checking this field.
     * @see #tcpServGameOpts
     * @since 1.1.19
     */
    protected SOCFeatureSet sFeatures;

    /**
     * Track the game options available at the remote server, at the practice server.
     * Initialized by {@link SwingMainDisplay#gameWithOptionsBeginSetup(boolean, boolean)}
     * and/or {@link MessageHandler#handleVERSION(boolean, SOCVersion)}.
     * These fields are never null, even if the respective server is not connected or not running.
     *<P>
     * For a summary of the flags and variables involved with game options,
     * and the client/server interaction about their values, see
     * {@link ServerGametypeInfo}'s javadoc.
     *<P>
     * Scenario strings are localized by {@link #localizeGameScenarios(List, boolean, boolean, boolean)}.
     *
     * @see #sFeatures
     * @since 1.1.07
     */
    protected ServerGametypeInfo tcpServGameOpts = new ServerGametypeInfo(),
        practiceServGameOpts = new ServerGametypeInfo();

    /**
     * For practice games, default game name ("Practice").
     * Set in constructor using i18n {@link #strings} lookup.
     */
    public final String DEFAULT_PRACTICE_GAMENAME;

    /**
     * the nickname; null until validated and set by
     * {@link SwingMainDisplay#getValidNickname(boolean) getValidNickname(true)}
     */
    protected String nickname = null; // TODO: private

    /**
     * the password for {@link #nickname} from {@link #pass}, or {@code null} if no valid password yet.
     * May be empty (""). If server has authenticated this password, the {@link #gotPassword} flag is set.
     */
    protected String password = null;

    /**
     * true if we've stored the {@link #password} and the server's replied that it's correct.
     * @see #isNGOFWaitingForAuthStatus
     */
    protected boolean gotPassword;

    /**
     * true if user clicked "new game" and, before showing {@link NewGameOptionsFrame}, we've
     * sent the nickname (username) and password to the server and are waiting for a response.
     *<P>
     * Used only with TCP servers, not with {@link ClientNetwork#practiceServer}.
     * @since 1.1.19
     */
    protected boolean isNGOFWaitingForAuthStatus;

    /**
     * True if contents of incoming and outgoing network message traffic should be debug-printed.
     * Set if optional system property {@link SOCDisplaylessPlayerClient#PROP_JSETTLERS_DEBUG_TRAFFIC} is set.
     *<P>
     * Versions earlier than 1.1.20 always printed this debug output; 1.1.20 never prints it.
     * @since 1.2.00
     */
    boolean debugTraffic;

    /**
     * face ID chosen most recently (for use in new games)
     */
    protected int lastFaceChange;

    /**
     * The games we're currently playing.
     * Accessed from GUI thread and network {@link MessageHandler} thread.
     */
    protected Hashtable<String, SOCGame> games = new Hashtable<String, SOCGame>(); // TODO: make private

    /**
     * all announced game names on the remote server, including games which we can't
     * join due to limitations of the client.
     * May also contain options for all announced games on the server (not just ones
     * we're in) which we can join (version is not higher than our version).
     *<P>
     * Key is the game name, without the UNJOINABLE prefix.
     * This field is null until {@link MessageHandler#handleGAMES(SOCGames, boolean) handleGAMES},
     *   {@link MessageHandler#handleGAMESWITHOPTIONS(SOCGamesWithOptions, boolean) handleGAMESWITHOPTIONS},
     *   {@link MessageHandler#handleNEWGAME(SOCNewGame, boolean) handleNEWGAME}
     *   or {@link MessageHandler#handleNEWGAMEWITHOPTIONS(SOCNewGameWithOptions, boolean) handleNEWGAMEWITHOPTIONS}
     *   is called.
     * @since 1.1.07
     */
    protected SOCGameList serverGames = null; // TODO: make private

    /**
     * the unjoinable game names from {@link #serverGames} that player has asked to join,
     * and been told they can't.  If they click again, try to connect.
     * (This is a failsafe against bugs in server or client version-recognition.)
     * Both key and value are the game name, without the {@link #GAMENAME_PREFIX_CANNOT_JOIN} prefix.
     * @since 1.1.06
     */
    protected Map<String,String> gamesUnjoinableOverride = new HashMap<String,String>();

    /**
     * Map from game-name to the listener for that game.
     */
    private final Map<String, PlayerClientListener> clientListeners = new HashMap<String, PlayerClientListener>();

    /**
     * For new-game requests, map of game names to per-game local preference maps to pass to
     * that new game's {@link SOCPlayerInterface} constructor. See {@link #putGameReqLocalPrefs(String, Map)}
     * for details.
     *
     * @since 1.2.00
     */
    private final HashMap<String, Map<String, Object>> gameReqLocalPrefs = new HashMap<String, Map<String, Object>>();

    /**
     * the ignore list
     */
    protected Vector<String> ignoreList = new Vector<String>();

    /**
     * Number of practice games started; used for naming practice games
     */
    protected int numPracticeGames = 0;

    /**
     * Create a SOCPlayerClient connecting to localhost port {@link ClientNetwork#SOC_PORT_DEFAULT}.
     * Initializes helper objects (except {@link MainDisplay}), locale, {@link SOCStringManager}.
     * The locale will be the current user's default locale, unless overridden by setting the
     * {@link I18n#PROP_JSETTLERS_LOCALE PROP_JSETTLERS_LOCALE} system property {@code "jsettlers.locale"}.
     *<P>
     * Must call {@link SOCApplet#init()}, or {@link #setMainDisplay(MainDisplay)} and then
     * {@link MainDisplay#initVisualElements()}, to start up and do layout.
     *<P>
     * Must then call {@link #connect(String, int, String, String)} or {@link ClientNetwork#connect(String, int)}
     * to join a TCP server, or {@link MainDisplay#clickPracticeButton()}
     * or {@link MainDisplay#startLocalTCPServer(int)} to start a server locally.
     */
    public SOCPlayerClient()
    {
        gotPassword = false;
        lastFaceChange = 1;  // Default human face

        if (null != System.getProperty(SOCDisplaylessPlayerClient.PROP_JSETTLERS_DEBUG_TRAFFIC))
            debugTraffic = true;  // set flag if debug prop has any value at all

        String jsLocale = System.getProperty(I18n.PROP_JSETTLERS_LOCALE);
        Locale lo = null;
        if (jsLocale != null)
        {
            try
            {
                lo = I18n.parseLocale(jsLocale.trim());
            } catch (IllegalArgumentException e) {
                System.err.println("Could not parse locale " + jsLocale);
            }
        }
        if (lo != null)
            cliLocale = lo;
        else
            cliLocale = Locale.getDefault();

        strings = soc.util.SOCStringManager.getClientManager(cliLocale);
        DEFAULT_PRACTICE_GAMENAME = strings.get("default.name.practice.game");
        GAMENAME_PREFIX_CANNOT_JOIN = strings.get("pcli.gamelist.cannot_join.parens") + ' ';  // "(cannot join) "

        String debug_clearPrefs = System.getProperty(PROP_JSETTLERS_DEBUG_CLEAR__PREFS);
        if (debug_clearPrefs != null)
            UserPreferences.clear(debug_clearPrefs);

        net = new ClientNetwork(this);
        gameMessageMaker = new GameMessageMaker(this, clientListeners);
        messageHandler = new MessageHandler(this);
    }

    /**
     * Set our main display interface.
     * Before using the client, caller must also call {@link MainDisplay#initVisualElements()}.
     * @throws IllegalArgumentException if {@code md} is {@code null}
     * @since 2.0.00
     */
    public void setMainDisplay(final MainDisplay md)
        throws IllegalArgumentException
    {
        if (md == null)
            throw new IllegalArgumentException("null");
        mainDisplay = md;
        net.setMainDisplay(md);
    }

    /**
     * Connect and give feedback by showing MESSAGE_PANEL.
     * Calls {@link MainDisplay#connect(String, String)} to set username and password,
     * then {@link ClientNetwork#connect(String, int)} to make the connection.
     *
     * @param chost Hostname to connect to, or null for localhost
     * @param cport Port number to connect to
     * @param cuser User nickname
     * @param cpass User optional password
     */
    public void connect(final String chost, final int cport, final String cuser, final String cpass)
    {
        mainDisplay.connect(cpass, cuser);

        // TODO don't do net connect attempt on UI thread
        // Meanwhile: To ensure the UI repaints before starting net connect:
        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                net.connect(chost, cport);
            }
        });
    }

    /**
     * @return the nickname of this user
     * @see SwingMainDisplay#getValidNickname(boolean)
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * Get this client's MainDisplay.
     * @since 2.0.00
     */
    MainDisplay getMainDisplay()
    {
        return mainDisplay;
    }

    /**
     * Get this client's ClientNetwork.
     * @since 2.0.00
     */
    /*package*/ ClientNetwork getNet()
    {
        return net;
    }

    /**
     * @return the client listener of this SOCPlayerClient object based on the name
     */
    /*package*/ PlayerClientListener getClientListener(String name)
    {
        return clientListeners.get(name);
    }

    /**
     * @return the client listeners of this SOCPlayerClient object.
     */
    /*package*/ Map<String, PlayerClientListener> getClientListeners()
    {
        return clientListeners;
    }

    /**
     * @return the local game preferences of this SOCPlayerClient object.
     */
    /*package*/ HashMap<String, Map<String, Object>> getGameReqLocalPrefs()
    {
        return gameReqLocalPrefs;
    }


    /**
     * Get this client's GameMessageMaker.
     * @since 2.0.00
     */
    public GameMessageMaker getGameMessageMaker()
    {
        return gameMessageMaker;
    }

    /**
     * Get this client's MessageHandler.
     * @since 2.0.00
     */
    final MessageHandler getMessageHandler() {
        return messageHandler;
    }

    /**
     * Should this client request localized strings (I18N) from the server if available?
     * Checks server version, checks whether client locale differs from the fallback {@code "en_US"}.
     * @param isPractice  True if checking for local practice server, not a remote server
     * @return  True if client should request localized strings
     * @since 2.0.00
     */
    final boolean wantsI18nStrings(final boolean isPractice)
    {
        return (isPractice || (sVersion >= SOCStringManager.VERSION_FOR_I18N))
            && (cliLocale != null)
            && ! ("en".equals(cliLocale.getLanguage()) && "US".equals(cliLocale.getCountry()));
    }

    /**
     * Check these game options to see if they contain a scenario we don't yet have full info about.
     * If the options include a scenario and we don't have that scenario's info or localized strings,
     * ask the server for that but don't wait here for a reply.
     *<P>
     * <B>Do not call for practice games:</B> Assumes this is the TCP server, because for practice games
     * we already have full info about scenarios and their strings.
     *
     * @param opts  Game options to check for {@code "SC"}, or {@code null}
     * @since 2.0.00
     */
    protected void checkGameoptsForUnknownScenario(final Map<String,SOCGameOption> opts)
    {
        if ((opts == null) || tcpServGameOpts.allScenInfoReceived || ! opts.containsKey("SC"))
            return;

        final String scKey = opts.get("SC").getStringValue();
        if ((scKey.length() == 0) || tcpServGameOpts.scenKeys.contains(scKey))
            return;

        net.putNet(new SOCScenarioInfo(scKey, false).toCmd());
    }

    /**
     * Localize {@link SOCScenario} names and descriptions with strings from the server.
     * Updates scenario data in {@link #practiceServGameOpts} or {@link #tcpServGameOpts}.
     *
     * @param scStrs  Scenario localized strings, same format as {@link SOCLocalizedStrings} params.
     * @param skipFirst  If true skip the first element of {@code scStrs}, it isn't a scenario keyname.
     * @param sentAll  True if no further strings will be received; is {@link SOCLocalizedStrings#FLAG_SENT_ALL} set?
     *     If true, sets {@link ServerGametypeInfo#allScenStringsReceived}.
     * @param isPractice  Is the server {@link ClientNetwork#practiceServer}, not remote?
     * @since 2.0.00
     */
    protected void localizeGameScenarios
        (final List<String> scStrs, final boolean skipFirst, final boolean sentAll, final boolean isPractice)
    {
        ServerGametypeInfo opts = (isPractice ? practiceServGameOpts : tcpServGameOpts);

        final int L = scStrs.size();
        int i = (skipFirst) ? 1 : 0;
        while (i < L)
        {
            final String scKey = scStrs.get(i);
            ++i;
            opts.scenKeys.add(scKey);

            final String nm = scStrs.get(i);
            ++i;

            if (nm.equals(SOCLocalizedStrings.MARKER_KEY_UNKNOWN))
                continue;

            String desc = scStrs.get(i);
            ++i;

            SOCScenario sc = SOCScenario.getScenario(scKey);
            if ((sc != null) && (nm.length() > 0))
            {
                if ((desc != null) && (desc.length() == 0))
                    desc = null;

                sc.setDesc(nm, desc);
            }
        }

        if (sentAll)
            opts.allScenStringsReceived = true;
    }

    /**
     * Does a game with this name exist, either at the remote server or our Practice Server (if one is running)?
     * @param gameName  Game name to check. Should not have the prefix {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}.
     * @param checkPractice  True if should also check list of practice games, false to ignore practice games.
     *     It's safe to use {@code true} even when the practice server isn't running.
     * @return  True if game exists in client's practice server or remote server game lists
     * @since 2.0.00
     */
    public boolean doesGameExist(final String gameName, final boolean checkPractice)
    {
        boolean gameExists = (checkPractice)
            ? ((net.practiceServer != null) && (-1 != net.practiceServer.getGameState(gameName)))
            : false;
        if ((! gameExists) && (serverGames != null))
            gameExists = gameExists || serverGames.isGame(gameName);

        return gameExists;
    }

    /**
     * Add a new game to the initial window's list of games, and possibly
     * to the {@link #serverGames server games list}.
     *
     * @param gameName the game name to add to the list;
     *            may have the prefix {@link SOCGames#MARKER_THIS_GAME_UNJOINABLE}
     * @param gameOptsStr String of packed {@link SOCGameOption game options}, or null
     * @param addToSrvList Should this game be added to the list of remote-server games?
     *            Practice games should not be added.
     *            The {@link #serverGames} list also has a flag for cannotJoin.
     * @see #doesGameExist(String, boolean)
     * @see MainDisplay#addToGameList(boolean, String, String, boolean)
     */
    public void addToGameList(String gameName, String gameOptsStr, final boolean addToSrvList)
    {
        boolean hasUnjoinMarker = (gameName.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE);
        if (hasUnjoinMarker)
        {
            gameName = gameName.substring(1);
        }
        mainDisplay.addToGameList(hasUnjoinMarker, gameName, gameOptsStr, addToSrvList);
    }

    /** If we're playing in a game that's just finished, update the scores.
     *  This is used to show the true scores, including hidden
     *  victory-point cards, at the game's end.
     *  @since 1.1.00
     */
    public void updateGameEndStats(String game, final int[] scores)
    {
        SOCGame ga = games.get(game);
        if (ga == null)
            return;  // Not playing in that game
        if (ga.getGameState() != SOCGame.OVER)
        {
            System.err.println("L4044: pcli.updateGameEndStats called at state " + ga.getGameState());
            return;  // Should not have been sent; game is not yet over.
        }

        PlayerClientListener pcl = clientListeners.get(game);
        if (pcl == null)
            return;
        Map<SOCPlayer, Integer> scoresMap = new HashMap<SOCPlayer, Integer>();
        for (int i=0; i<scores.length; ++i)
        {
            scoresMap.put(ga.getPlayer(i), Integer.valueOf(scores[i]));
        }
        pcl.gameEnded(scoresMap);
    }

    /**
     * the user leaves the given chat channel
     *
     * @param ch  the name of the channel
     */
    public void leaveChannel(String ch)
    {
        mainDisplay.channelLeft(ch);
        net.putNet(SOCLeaveChannel.toCmd(nickname, net.getHost(), ch));
    }

    /**
     * @return true if name is on the ignore list
     */
    protected boolean onIgnoreList(String name)
    {
        boolean result = false;

        for (String s : ignoreList)
        {
            if (s.equals(name))
            {
                result = true;

                break;
            }
        }

        return result;
    }

    /**
     * add this name to the ignore list
     *
     * @param name the name to add
     * @see #removeFromIgnoreList(String)
     */
    protected void addToIgnoreList(String name)
    {
        name = name.trim();

        if (! onIgnoreList(name))
        {
            ignoreList.addElement(name);
        }
    }

    /**
     * remove this name from the ignore list
     *
     * @param name  the name to remove
     * @see #addToIgnoreList(String)
     */
    protected void removeFromIgnoreList(String name)
    {
        name = name.trim();
        ignoreList.removeElement(name);
    }

    /**
     * Create a game name, and start a practice game.
     * Assumes {@link SwingMainDisplay#MAIN_PANEL} is initialized.
     * See {@link #startPracticeGame(String, Map, boolean)} for details.
     * @return True if the practice game request was sent, false if there was a problem
     *         starting the practice server or client
     * @since 1.1.00
     */
    public boolean startPracticeGame()
    {
        return startPracticeGame(null, null, true);
    }

    /**
     * Setup for practice game (on the non-tcp server).
     * If needed, a (stringport, not tcp) {@link ClientNetwork#practiceServer}, client, and robots are started.
     *
     * @param practiceGameName Unique name to give practice game; if name unknown, call
     *         {@link #startPracticeGame()} instead
     * @param gameOpts Set of {@link SOCGameOption game options} to use, or null
     * @param mainPanelIsActive Is the SOCPlayerClient main panel active?
     *         False if we're being called from elsewhere, such as
     *         {@link SOCConnectOrPracticePanel}.
     * @return True if the practice game request was sent, false if there was a problem
     *         starting the practice server or client
     * @since 1.1.00
     */
    public boolean startPracticeGame
        (String practiceGameName, final Map<String, SOCGameOption> gameOpts, final boolean mainPanelIsActive)
    {
        ++numPracticeGames;

        if (practiceGameName == null)
            practiceGameName = DEFAULT_PRACTICE_GAMENAME + " " + (numPracticeGames);  // "Practice 3"

        // May take a while to start server & game.
        // The new-game window will clear this cursor.
        mainDisplay.practiceGameStarting();

        return net.startPracticeGame(practiceGameName, gameOpts);
    }

    /**
     * For new-game requests, a per-game local preference map from {@link NewGameOptionsFrame} to pass to
     * that new game's {@link SOCPlayerInterface} constructor.
     *<P>
     * Preference name keys are {@link SOCPlayerInterface#PREF_SOUND_MUTE}, etc.
     * Values for boolean prefs should be {@link Boolean#TRUE} or {@code .FALSE}.  
     *<P>
     * The {@link HashMap} of game names permits a {@code null} value instead of a Map,
     * but there is no guarantee that preference values can be {@code null} within a game's Map.
     *
     * @param gaName  Game name
     * @param localPrefs  Local prefs to store for {@code gaName}
     * @since 2.0.00
     */
    void putGameReqLocalPrefs(final String gaName, final Map<String, Object> localPrefs)
    {
        gameReqLocalPrefs.put(gaName, localPrefs);
    }

    /**
     * Server version, for checking feature availability.
     * Returns -1 if unknown. Checks {@link SOCGame#isPractice}:
     * practice games always return this client's own {@link soc.util.Version#versionNumber()}.
     *<P>
     * Instead of calling this method, some client code checks a game's version like:<BR>
     * {@code (game.isPractice || (client.sVersion >= VERSION_FOR_AUTHREQUEST))}
     *
     * @param  game  Game being played on a practice or tcp server.
     * @return Server version, in same format as {@link soc.util.Version#versionNumber()},
     *         or 0 or -1.
     * @since 1.1.00
     */
    public int getServerVersion(SOCGame game)
    {
        if (game.isPractice)
            return Version.versionNumber();
        else
            return sVersion;
    }

    /**
     * network trouble; if possible, ask if they want to play locally (practiceServer vs. robots).
     * Otherwise, go ahead and shut down. Either way, calls {@link MainDisplay#showErrorPanel(String, boolean)}
     * to show an error message or network exception detail.
     *<P>
     * "If possible" is determined from return value of {@link ClientNetwork#putLeaveAll()}.
     *<P>
     * Before v1.2.01 this method was {@code destroy()}.
     */
    public void shutdownFromNetwork()
    {
        final boolean canPractice = net.putLeaveAll(); // Can we still start a practice game?

        String err;
        if (canPractice)
        {
            err = strings.get("pcli.error.networktrouble");  // "Sorry, network trouble has occurred."
        } else {
            err = strings.get("pcli.error.clientshutdown");  // "Sorry, the client has been shut down."
        }
        err = err + " " + ((net.ex == null) ? strings.get("pcli.error.loadpageagain") : net.ex.toString());
            // "Load the page again."

        mainDisplay.channelsClosed(err);

        // Stop network games; continue Practice games if possible.
        for (Map.Entry<String, PlayerClientListener> e : clientListeners.entrySet())
        {
            String gameName = e.getKey();
            SOCGame game = games.get(gameName);
            boolean isPractice = canPractice && (game != null) && game.isPractice;
            if (! isPractice)
                e.getValue().gameDisconnected(false, err);
        }

        net.dispose();

        mainDisplay.showErrorPanel(err, canPractice);
    }

    /**
     * for stand-alones
     */
    public static void usage()
    {
        System.err.println("usage: java [-D ...=...] -jar JSettlers.jar [<host> <port>]");
    }

    /**
     * for stand-alones
     */
    public static void main(String[] args)
    {
        final SOCPlayerClient client;
        final SwingMainDisplay mainDisplay;

        String host = null;  // from args, if not empty
        int port = -1;

        Version.printVersionText(System.out, "Java Settlers Client ");

        if (args.length != 0)
        {
            if (args.length != 2)
            {
                usage();
                System.exit(1);
            }

            try {
                host = args[0];
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException x) {
                usage();
                System.err.println("Invalid port: " + args[1]);
                System.exit(1);
            }
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}

        client = new SOCPlayerClient();
        JFrame frame = new JFrame(client.strings.get("pcli.main.title", Version.version()));  // "JSettlers client {0}"

        final int displayScale = SwingMainDisplay.checkDisplayScaleFactor(frame);
        SwingMainDisplay.scaleUIManagerFonts(displayScale);

        final Color[] colors = SwingMainDisplay.getForegroundBackgroundColors(false, false);
        if (colors != null)
        {
            frame.setBackground(colors[2]);  // SwingMainDisplay.JSETTLERS_BG_GREEN
            frame.setForeground(colors[0]);  // Color.BLACK
        }

        mainDisplay = new SwingMainDisplay((args.length == 0), client, displayScale);
        client.setMainDisplay(mainDisplay);

        // Add a listener for the close event
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(mainDisplay.createWindowAdapter());

        mainDisplay.initVisualElements(); // after the background is set

        frame.add(mainDisplay, BorderLayout.CENTER);
        frame.setLocationByPlatform(true);
        frame.setSize(650 * displayScale, 400 * displayScale);
        frame.setVisible(true);

        if (Version.versionNumber() == 0)
        {
            client.mainDisplay.showErrorPanel("Packaging error: Cannot determine JSettlers version", false);
                // I18N: Can't localize this, the i18n files are provided by the same packaging steps
                // which would create /resources/version.info
            return;
        }

        if ((host != null) && (port != -1))
            client.net.connect(host, port);
    }


}  // public class SOCPlayerClient
