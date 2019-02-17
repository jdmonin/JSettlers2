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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import soc.baseclient.SOCDisplaylessPlayerClient;
import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCDevCardConstants;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCInventory;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCScenario;
import soc.game.SOCSettlement;
import soc.game.SOCTradeOffer;
import soc.game.SOCVillage;

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
 * Once connected, messages from the server are processed in {@link MessageTreater#treat(SOCMessage, boolean)}.
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
     * The classic JSettlers green background color; green tone #61AF71.
     * Typically used with foreground color {@link Color#BLACK},
     * like in {@link SwingMainDisplay}'s main panel.
     * Occasionally used with {@link #MISC_LABEL_FG_OFF_WHITE}.
     * @since 2.0.00
     * @see SOCPlayerInterface#DIALOG_BG_GOLDENROD
     */
    public static final Color JSETTLERS_BG_GREEN = new Color(97, 175, 113);

    /**
     * For miscellaneous labels, off-white foreground color #FCFBF3.
     * Typically used on {@link #JSETTLERS_BG_GREEN}.
     * @since 2.0.00
     */
    public static final Color MISC_LABEL_FG_OFF_WHITE = new Color(252, 251, 243);

    /**
     * i18n text strings in our {@link #cliLocale}.
     * @since 2.0.00
     */
    final soc.util.SOCStringManager strings;

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
     * @since 1.1.07
     */
    public static final boolean isJavaOnOSX =
        System.getProperty("os.name").toLowerCase().startsWith("mac os x");
    static
    {
        if (isJavaOnOSX)
        {
            // Must set "OSX look and feel" items before calling any AWT code
            // or setting a platform look and feel.

            System.setProperty("apple.awt.application.name", "JSettlers");
                // Required on OSX 10.7 or so and newer; works for java 6
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
    private final MessageTreater treater;

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
     * and/or {@link MessageTreater#handleVERSION(boolean, SOCVersion)}.
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
    protected String nickname = null;

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
     * Accessed from GUI thread and network {@link MessageTreater} thread.
     */
    protected Hashtable<String, SOCGame> games = new Hashtable<String, SOCGame>();

    /**
     * all announced game names on the remote server, including games which we can't
     * join due to limitations of the client.
     * May also contain options for all announced games on the server (not just ones
     * we're in) which we can join (version is not higher than our version).
     *<P>
     * Key is the game name, without the UNJOINABLE prefix.
     * This field is null until {@link SOCPlayerClient.MessageTreater#handleGAMES(SOCGames, boolean) handleGAMES},
     *   {@link SOCPlayerClient.MessageTreater#handleGAMESWITHOPTIONS(SOCGamesWithOptions, boolean) handleGAMESWITHOPTIONS},
     *   {@link SOCPlayerClient.MessageTreater#handleNEWGAME(SOCNewGame, boolean) handleNEWGAME}
     *   or {@link SOCPlayerClient.MessageTreater#handleNEWGAMEWITHOPTIONS(SOCNewGameWithOptions, boolean) handleNEWGAMEWITHOPTIONS}
     *   is called.
     * @since 1.1.07
     */
    protected SOCGameList serverGames = null;

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
        treater = new MessageTreater(this);
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
    public ClientNetwork getNet()
    {
        return net;
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
     * Get this client's MessageTreater.
     * @since 2.0.00
     */
    MessageTreater getMessageTreater() { return treater; }

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
     * Nested class for processing incoming messages (treating).
     * {@link #treat(SOCMessage, boolean)} dispatches messages to their
     * handler methods (such as {@link #handleBANKTRADE(SOCBankTrade)}).
     *<P>
     * Before v2.0.00, most of these fields and methods were part of the main {@link SOCPlayerClient} class.
     * @author paulbilnoski
     * @since 2.0.00
     */
    /*private*/ class MessageTreater   // TODO extract to pkg-visible top-level class
    {
        private final SOCPlayerClient client;
        private final GameMessageMaker gmm;

        public MessageTreater(SOCPlayerClient client)
        {
            if (client == null)
                throw new IllegalArgumentException("client is null");
            this.client = client;
            gmm = client.getGameMessageMaker();

            if (gmm == null)
                throw new IllegalArgumentException("client game message maker is null");
        }

    /**
     * Treat the incoming messages.
     * Messages of unknown type are ignored
     * ({@code mes} will be null from {@link SOCMessage#toMsg(String)}).
     *
     * @param mes    the message
     * @param isPractice  Message is coming from {@link ClientNetwork#practiceServer}, not a TCP server
     */
    public void treat(SOCMessage mes, final boolean isPractice)
    {
        if (mes == null)
            return;  // Parsing error

        if (client.debugTraffic || D.ebugIsEnabled())
            soc.debug.D.ebugPrintln(mes.toString());

        try
        {
            final String gaName;
            final SOCGame ga;
            if (mes instanceof SOCMessageForGame)
            {
                gaName = ((SOCMessageForGame) mes).getGame();
                ga = (gaName != null) ? games.get(gaName) : null;
                    // Allows null gaName, for the few message types (like SOCScenarioInfo) which
                    // for convenience use something like SOCTemplateMs which extends SOCMessageForGame
                    // but aren't actually game-specific messages.
            } else {
                gaName = null;
                ga = null;
            }

            switch (mes.getType())
            {

            /**
             * echo the server ping, to ensure we're still connected.
             * (ignored before version 1.1.08)
             */
            case SOCMessage.SERVERPING:
                handleSERVERPING((SOCServerPing) mes, isPractice);
                break;

            /**
             * server's version message
             */
            case SOCMessage.VERSION:
                handleVERSION(isPractice, (SOCVersion) mes);

                break;

            /**
             * status message
             */
            case SOCMessage.STATUSMESSAGE:
                handleSTATUSMESSAGE((SOCStatusMessage) mes, isPractice);

                break;

            /**
             * join channel authorization
             */
            case SOCMessage.JOINCHANNELAUTH:
                handleJOINCHANNELAUTH((SOCJoinChannelAuth) mes);

                break;

            /**
             * someone joined a channel
             */
            case SOCMessage.JOINCHANNEL:
                handleJOINCHANNEL((SOCJoinChannel) mes);

                break;

            /**
             * list of members for a chat channel
             */
            case SOCMessage.CHANNELMEMBERS:
                handleCHANNELMEMBERS((SOCChannelMembers) mes);

                break;

            /**
             * a new chat channel has been created
             */
            case SOCMessage.NEWCHANNEL:
                handleNEWCHANNEL((SOCNewChannel) mes);

                break;

            /**
             * List of chat channels on the server: Server connection is complete.
             * (sent at connect after VERSION, even if no channels)
             * Show main panel if not already showing; see handleCHANNELS javadoc.
             */
            case SOCMessage.CHANNELS:
                handleCHANNELS((SOCChannels) mes, isPractice);

                break;

            /**
             * text message to a chat channel
             */
            case SOCMessage.CHANNELTEXTMSG:
                handleCHANNELTEXTMSG((SOCChannelTextMsg) mes);

                break;

            /**
             * someone left the chat channel
             */
            case SOCMessage.LEAVECHANNEL:
                handleLEAVECHANNEL((SOCLeaveChannel) mes);

                break;

            /**
             * delete a chat channel
             */
            case SOCMessage.DELETECHANNEL:
                handleDELETECHANNEL((SOCDeleteChannel) mes);

                break;

            /**
             * list of games on the server
             */
            case SOCMessage.GAMES:
                handleGAMES((SOCGames) mes, isPractice);

                break;

            /**
             * join game authorization
             */
            case SOCMessage.JOINGAMEAUTH:
                handleJOINGAMEAUTH((SOCJoinGameAuth) mes, isPractice);

                break;

            /**
             * someone joined a game
             */
            case SOCMessage.JOINGAME:
                handleJOINGAME((SOCJoinGame) mes);

                break;

            /**
             * someone left a game
             */
            case SOCMessage.LEAVEGAME:
                handleLEAVEGAME((SOCLeaveGame) mes);

                break;

            /**
             * new game has been created
             */
            case SOCMessage.NEWGAME:
                handleNEWGAME((SOCNewGame) mes, isPractice);

                break;

            /**
             * game has been destroyed
             */
            case SOCMessage.DELETEGAME:
                handleDELETEGAME((SOCDeleteGame) mes, isPractice);

                break;

            /**
             * list of game members
             */
            case SOCMessage.GAMEMEMBERS:
                handleGAMEMEMBERS((SOCGameMembers) mes);

                break;

            /**
             * game stats
             */
            case SOCMessage.GAMESTATS:
                handleGAMESTATS((SOCGameStats) mes);

                break;

            /**
             * game text message
             */
            case SOCMessage.GAMETEXTMSG:
                handleGAMETEXTMSG((SOCGameTextMsg) mes);
                break;

            /**
             * broadcast text message
             */
            case SOCMessage.BCASTTEXTMSG:
                handleBCASTTEXTMSG((SOCBCastTextMsg) mes);
                break;

            /**
             * someone is sitting down
             */
            case SOCMessage.SITDOWN:
                handleSITDOWN((SOCSitDown) mes);

                break;

            /**
             * receive a board layout
             */
            case SOCMessage.BOARDLAYOUT:
                handleBOARDLAYOUT((SOCBoardLayout) mes);

                break;

            /**
             * receive a board layout (new format, as of 20091104 (v 1.1.08))
             */
            case SOCMessage.BOARDLAYOUT2:
                handleBOARDLAYOUT2((SOCBoardLayout2) mes);
                break;

            /**
             * message that the game is starting
             */
            case SOCMessage.STARTGAME:
                handleSTARTGAME((SOCStartGame) mes);
                break;

            /**
             * update the state of the game
             */
            case SOCMessage.GAMESTATE:
                handleGAMESTATE((SOCGameState) mes);
                break;

            /**
             * set the current turn
             */
            case SOCMessage.SETTURN:
                handleGAMEELEMENT(ga, SOCGameElements.CURRENT_PLAYER, ((SOCSetTurn) mes).getPlayerNumber());
                break;

            /**
             * set who the first player is
             */
            case SOCMessage.FIRSTPLAYER:
                handleGAMEELEMENT(ga, SOCGameElements.FIRST_PLAYER, ((SOCFirstPlayer) mes).getPlayerNumber());
                break;

            /**
             * update who's turn it is
             */
            case SOCMessage.TURN:
                handleTURN((SOCTurn) mes);
                break;

            /**
             * receive player information
             */
            case SOCMessage.PLAYERELEMENT:
                handlePLAYERELEMENT((SOCPlayerElement) mes);
                break;

            /**
             * receive player information.
             * Added 2017-12-10 for v2.0.00.
             */
            case SOCMessage.PLAYERELEMENTS:
                handlePLAYERELEMENTS((SOCPlayerElements) mes);
                break;

            /**
             * update game element information.
             * Added 2017-12-24 for v2.0.00.
             */
            case SOCMessage.GAMEELEMENTS:
                handleGAMEELEMENTS((SOCGameElements) mes);
                break;

            /**
             * receive resource count
             */
            case SOCMessage.RESOURCECOUNT:
                handleRESOURCECOUNT((SOCResourceCount) mes);
                break;

            /**
             * receive player's last settlement location.
             * Added 2017-12-23 for v2.0.00.
             */
            case SOCMessage.LASTSETTLEMENT:
                SOCDisplaylessPlayerClient.handleLASTSETTLEMENT
                    ((SOCLastSettlement) mes, games.get(((SOCLastSettlement) mes).getGame()));
                break;

            /**
             * the latest dice result
             */
            case SOCMessage.DICERESULT:
                handleDICERESULT((SOCDiceResult) mes);

                break;

            /**
             * a player built something
             */
            case SOCMessage.PUTPIECE:
                handlePUTPIECE((SOCPutPiece) mes);

                break;

            /**
             * the current player has cancelled an initial settlement,
             * or has tried to place a piece illegally.
             */
            case SOCMessage.CANCELBUILDREQUEST:
                handleCANCELBUILDREQUEST((SOCCancelBuildRequest) mes);

                break;

            /**
             * the robber or pirate moved
             */
            case SOCMessage.MOVEROBBER:
                handleMOVEROBBER((SOCMoveRobber) mes);

                break;

            /**
             * the server wants this player to discard
             */
            case SOCMessage.DISCARDREQUEST:
                handleDISCARDREQUEST((SOCDiscardRequest) mes);

                break;

            /**
             * the server wants this player to choose a player to rob
             */
            case SOCMessage.CHOOSEPLAYERREQUEST:
                handleCHOOSEPLAYERREQUEST((SOCChoosePlayerRequest) mes);

                break;

            /**
             * The server wants this player to choose to rob cloth or rob resources.
             * Added 2012-11-17 for v2.0.00.
             */
            case SOCMessage.CHOOSEPLAYER:
                handleCHOOSEPLAYER((SOCChoosePlayer) mes);
                break;

            /**
             * a player has made a bank/port trade
             */
            case SOCMessage.BANKTRADE:
                handleBANKTRADE((SOCBankTrade) mes);
                break;

            /**
             * a player has made an offer
             */
            case SOCMessage.MAKEOFFER:
                handleMAKEOFFER((SOCMakeOffer) mes);

                break;

            /**
             * a player has cleared her offer
             */
            case SOCMessage.CLEAROFFER:
                handleCLEAROFFER((SOCClearOffer) mes);

                break;

            /**
             * a player has rejected an offer
             */
            case SOCMessage.REJECTOFFER:
                handleREJECTOFFER((SOCRejectOffer) mes);

                break;

            /**
             * a player has accepted a trade offer
             */
            case SOCMessage.ACCEPTOFFER:
                handleACCEPTOFFER((SOCAcceptOffer) mes);
                break;

            /**
             * the trade message needs to be cleared
             */
            case SOCMessage.CLEARTRADEMSG:
                handleCLEARTRADEMSG((SOCClearTradeMsg) mes);

                break;

            /**
             * the current number of development cards
             */
            case SOCMessage.DEVCARDCOUNT:
                handleGAMEELEMENT(ga, SOCGameElements.DEV_CARD_COUNT, ((SOCDevCardCount) mes).getNumDevCards());
                break;

            /**
             * a dev card action, either draw, play, or add to hand
             */
            case SOCMessage.DEVCARDACTION:
                handleDEVCARDACTION(isPractice, (SOCDevCardAction) mes);
                break;

            /**
             * set the flag that tells if a player has played a
             * development card this turn
             */
            case SOCMessage.SETPLAYEDDEVCARD:
                handleSETPLAYEDDEVCARD((SOCSetPlayedDevCard) mes);
                break;

            /**
             * get a list of all the potential settlements for a player
             */
            case SOCMessage.POTENTIALSETTLEMENTS:
                handlePOTENTIALSETTLEMENTS((SOCPotentialSettlements) mes);

                break;

            /**
             * handle the change face message
             */
            case SOCMessage.CHANGEFACE:
                handleCHANGEFACE((SOCChangeFace) mes);

                break;

            /**
             * handle the reject connection message
             */
            case SOCMessage.REJECTCONNECTION:
                handleREJECTCONNECTION((SOCRejectConnection) mes);

                break;

            /**
             * handle the longest road message
             */
            case SOCMessage.LONGESTROAD:
                handleGAMEELEMENT(ga, SOCGameElements.LONGEST_ROAD_PLAYER, ((SOCLongestRoad) mes).getPlayerNumber());
                break;

            /**
             * handle the largest army message
             */
            case SOCMessage.LARGESTARMY:
                handleGAMEELEMENT(ga, SOCGameElements.LARGEST_ARMY_PLAYER, ((SOCLargestArmy) mes).getPlayerNumber());
                break;

            /**
             * handle the seat lock state message
             */
            case SOCMessage.SETSEATLOCK:
                handleSETSEATLOCK((SOCSetSeatLock) mes);

                break;

            /**
             * handle the roll dice prompt message
             * (it is now x's turn to roll the dice)
             */
            case SOCMessage.ROLLDICEPROMPT:
                handleROLLDICEPROMPT((SOCRollDicePrompt) mes);

                break;

            /**
             * handle board reset (new game with same players, same game name, new layout).
             */
            case SOCMessage.RESETBOARDAUTH:
                handleRESETBOARDAUTH((SOCResetBoardAuth) mes);

                break;

            /**
             * another player is requesting a board reset: we must vote
             */
            case SOCMessage.RESETBOARDVOTEREQUEST:
                handleRESETBOARDVOTEREQUEST((SOCResetBoardVoteRequest) mes);

                break;

            /**
             * another player has voted on a board reset request
             */
            case SOCMessage.RESETBOARDVOTE:
                handleRESETBOARDVOTE((SOCResetBoardVote) mes);

                break;

            /**
             * voting complete, board reset request rejected
             */
            case SOCMessage.RESETBOARDREJECT:
                handleRESETBOARDREJECT((SOCResetBoardReject) mes);

                break;

            /**
             * for game options (1.1.07)
             */
            case SOCMessage.GAMEOPTIONGETDEFAULTS:
                handleGAMEOPTIONGETDEFAULTS((SOCGameOptionGetDefaults) mes, isPractice);
                break;

            case SOCMessage.GAMEOPTIONINFO:
                handleGAMEOPTIONINFO((SOCGameOptionInfo) mes, isPractice);
                break;

            case SOCMessage.NEWGAMEWITHOPTIONS:
                handleNEWGAMEWITHOPTIONS((SOCNewGameWithOptions) mes, isPractice);
                break;

            case SOCMessage.GAMESWITHOPTIONS:
                handleGAMESWITHOPTIONS((SOCGamesWithOptions) mes, isPractice);
                break;

            /**
             * player stats (as of 20100312 (v 1.1.09))
             */
            case SOCMessage.PLAYERSTATS:
                handlePLAYERSTATS((SOCPlayerStats) mes);
                break;

            /**
             * debug piece Free Placement (as of 20110104 (v 1.1.12))
             */
            case SOCMessage.DEBUGFREEPLACE:
                handleDEBUGFREEPLACE((SOCDebugFreePlace) mes);
                break;

            /**
             * generic 'simple request' response from the server.
             * Added 2013-02-19 for v1.1.18.
             */
            case SOCMessage.SIMPLEREQUEST:
                handleSIMPLEREQUEST((SOCSimpleRequest) mes);
                break;

            /**
             * generic "simple action" announcements from the server.
             * Added 2013-09-04 for v1.1.19.
             */
            case SOCMessage.SIMPLEACTION:
                handleSIMPLEACTION((SOCSimpleAction) mes);
                break;

            /**
             * game server text and announcements.
             * Added 2013-09-05 for v2.0.00.
             */
            case SOCMessage.GAMESERVERTEXT:
                handleGAMESERVERTEXT((SOCGameServerText) mes);
                break;

            /**
             * All players' dice roll result resources.
             * Added 2013-09-20 for v2.0.00.
             */
            case SOCMessage.DICERESULTRESOURCES:
                handleDICERESULTRESOURCES((SOCDiceResultResources) mes);
                break;

            /**
             * move a previous piece (a ship) somewhere else on the board.
             * Added 2011-12-05 for v2.0.00.
             */
            case SOCMessage.MOVEPIECE:
                handleMOVEPIECE((SOCMovePiece) mes);
                break;

            /**
             * remove a piece (a ship) from the board in certain scenarios.
             * Added 2013-02-19 for v2.0.00.
             */
            case SOCMessage.REMOVEPIECE:
                handleREMOVEPIECE((SOCRemovePiece) mes);
                break;

            /**
             * reveal a hidden hex on the board.
             * Added 2012-11-08 for v2.0.00.
             */
            case SOCMessage.REVEALFOGHEX:
                handleREVEALFOGHEX((SOCRevealFogHex) mes);
                break;

            /**
             * update a village piece's value on the board (cloth remaining).
             * Added 2012-11-16 for v2.0.00.
             */
            case SOCMessage.PIECEVALUE:
                handlePIECEVALUE((SOCPieceValue) mes);
                break;

            /**
             * Text that a player has been awarded Special Victory Point(s).
             * Added 2012-12-21 for v2.0.00.
             */
            case SOCMessage.SVPTEXTMSG:
                handleSVPTEXTMSG((SOCSVPTextMessage) mes);
                break;

            /**
             * a special inventory item action: either add or remove,
             * or we cannot play our requested item.
             * Added 2013-11-26 for v2.0.00.
             */
            case SOCMessage.INVENTORYITEMACTION:
                handleINVENTORYITEMACTION((SOCInventoryItemAction) mes);
                break;

            /**
             * Special Item change announcements.
             * Added 2014-04-16 for v2.0.00.
             */
            case SOCMessage.SETSPECIALITEM:
                handleSETSPECIALITEM(games, (SOCSetSpecialItem) mes);
                break;

            /**
             * Localized i18n strings for game items.
             * Added 2015-01-11 for v2.0.00.
             */
            case SOCMessage.LOCALIZEDSTRINGS:
                handleLOCALIZEDSTRINGS((SOCLocalizedStrings) mes, isPractice);
                break;

            /**
             * Updated scenario info.
             * Added 2015-09-21 for v2.0.00.
             */
            case SOCMessage.SCENARIOINFO:
                handleSCENARIOINFO((SOCScenarioInfo) mes, isPractice);
                break;

            }  // switch (mes.getType())
        }
        catch (Throwable th)
        {
            System.out.println("SOCPlayerClient treat ERROR - " + th.getMessage());
            th.printStackTrace();
            System.out.println("  For message: " + mes);
        }

    }  // treat

    /**
     * Handle the "version" message, server's version report.
     * Ask server for game-option info if client's version differs.
     * If remote, store the server's version for {@link #getServerVersion(SOCGame)}
     * and display the version on the main panel.
     * (Local server's version is always {@link Version#versionNumber()}.)
     *
     * @param isPractice Is the server {@link ClientNetwork#practiceServer}, not remote?  Client can be connected
     *                only to one at a time.
     * @param mes  the message
     */
    private void handleVERSION(final boolean isPractice, SOCVersion mes)
    {
        D.ebugPrintln("handleVERSION: " + mes);
        int vers = mes.getVersionNumber();

        if (! isPractice)
        {
            sVersion = vers;
            sFeatures = (vers >= SOCFeatureSet.VERSION_FOR_SERVERFEATURES)
                ? new SOCFeatureSet(mes.feats)
                : new SOCFeatureSet(true, true);

            mainDisplay.showVersion(vers, mes.getVersionString(), mes.getBuild(), sFeatures);
        }

        // If we ever require a minimum server version, would check that here.

        // Pre-1.1.06 versions would reply here with our client version.
        // That's been sent to server already in connect() in 1.1.06 and later.

        // Check known game options vs server's version. (added in 1.1.07)
        // Server's responses will add, remove or change our "known options".
        // In v2.0.00 and later, also checks for game option localized descriptions.
        final int cliVersion = Version.versionNumber();
        final boolean sameVersion = (sVersion == cliVersion);
        final boolean withTokenI18n =
            (cliLocale != null) && (isPractice || (sVersion >= SOCStringManager.VERSION_FOR_I18N))
            && ! ("en".equals(cliLocale.getLanguage()) && "US".equals(cliLocale.getCountry()));

        if ( ((! isPractice) && (sVersion > cliVersion))
            || (withTokenI18n && (isPractice || sameVersion)))
        {
            // Newer server: Ask it to list any options we don't know about yet.
            // Same version: Ask for all localized option descs if available.
            if (! isPractice)
                mainDisplay.optionsRequested();
            gmm.put(SOCGameOptionGetInfos.toCmd(null, withTokenI18n, withTokenI18n && sameVersion), isPractice);
                // sends "-" and/or "?I18N"
        }
        else if ((sVersion < cliVersion) && ! isPractice)
        {
            if (sVersion >= SOCNewGameWithOptions.VERSION_FOR_NEWGAMEWITHOPTIONS)
            {
                // Older server: Look for options created or changed since server's version.
                // Ask it what it knows about them.
                List<SOCGameOption> tooNewOpts = SOCGameOption.optionsNewerThanVersion(sVersion, false, false, null);
                if ((tooNewOpts != null) && (sVersion < SOCGameOption.VERSION_FOR_LONGER_OPTNAMES) && ! isPractice)
                {
                    // Server is older than 2.0.00; we can't send it any long option names.
                    // Remove them from our set of options for games at this server.
                    if (tcpServGameOpts.optionSet == null)
                        tcpServGameOpts.optionSet = SOCGameOption.getAllKnownOptions();

                    Iterator<SOCGameOption> opi = tooNewOpts.iterator();
                    while (opi.hasNext())
                    {
                        final SOCGameOption op = opi.next();
                        //TODO i18n how to?
                        if ((op.key.length() > 3) || op.key.contains("_"))
                        {
                            tcpServGameOpts.optionSet.remove(op.key);
                            opi.remove();
                        }
                    }
                    if (tooNewOpts.isEmpty())
                        tooNewOpts = null;
                }

                if (tooNewOpts != null)
                {
                    if (! isPractice)
                        mainDisplay.optionsRequested();
                    gmm.put(SOCGameOptionGetInfos.toCmd(tooNewOpts, withTokenI18n, false), isPractice);
                }
                else if (withTokenI18n && ! isPractice)
                {
                    // server is older than client but understands i18n: request gameopt localized strings

                    gmm.put(SOCGameOptionGetInfos.toCmd(null, true, false), false);  // sends opt list "-,?I18N"
                }
            } else {
                // server is too old to understand options. Can't happen with local practice srv,
                // because that's our version (it runs from our own JAR file).

                if (! isPractice)
                {
                    tcpServGameOpts.noMoreOptions(true);
                    tcpServGameOpts.optionSet = null;
                }
            }
        } else {
            // sVersion == cliVersion, so we have same code as server for getAllKnownOptions.
            // For practice games, optionSet may already be initialized, so check vs null.
            ServerGametypeInfo opts = (isPractice ? practiceServGameOpts : tcpServGameOpts);
            if (opts.optionSet == null)
                opts.optionSet = SOCGameOption.getAllKnownOptions();
            opts.noMoreOptions(isPractice);  // defaults not known unless it's practice
        }
    }

    /**
     * handle the {@link SOCStatusMessage "status"} message.
     * Used for server events, also used if player tries to join a game
     * but their nickname is not OK.
     *<P>
     * Also used (v1.1.19 and newer) as a reply to {@link SOCAuthRequest} sent
     * before showing {@link NewGameOptionsFrame}, so check whether the
     * {@link SOCPlayerClient#isNGOFWaitingForAuthStatus isNGOFWaitingForAuthStatus client.isNGOFWaitingForAuthStatus}
     * flag is set.
     *
     * @param mes  the message
     * @param isPractice from practice server, not remote server?
     */
    protected void handleSTATUSMESSAGE(SOCStatusMessage mes, final boolean isPractice)
    {
        System.err.println("L2045 statusmsg at " + System.currentTimeMillis());
        int sv = mes.getStatusValue();
        String statusText = mes.getStatus();

        if ((sv == SOCStatusMessage.SV_OK_SET_NICKNAME))
        {
            sv = SOCStatusMessage.SV_OK;

            final int i = statusText.indexOf(SOCMessage.sep2_char);
            if (i > 0)
            {
                client.nickname = statusText.substring(0, i);
                statusText = statusText.substring(i + 1);
                mainDisplay.setNickname(client.nickname);
            }
        }

        final boolean srvDebugMode;
        if (isPractice || (sVersion >= 2000))
            srvDebugMode = (sv == SOCStatusMessage.SV_OK_DEBUG_MODE_ON);
        else
            srvDebugMode = statusText.toLowerCase().contains("debug");

        mainDisplay.showStatus(statusText, srvDebugMode);

        // Are we waiting for auth response in order to show NGOF?
        if ((! isPractice) && client.isNGOFWaitingForAuthStatus)
        {
            client.isNGOFWaitingForAuthStatus = false;

            if (sv == SOCStatusMessage.SV_OK)
            {
                client.gotPassword = true;

                EventQueue.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        mainDisplay.gameWithOptionsBeginSetup(false, true);
                    }
                });
            }
        }

        switch (sv)
        {
        case SOCStatusMessage.SV_PW_WRONG:
            mainDisplay.focusPassword();
            break;

        case SOCStatusMessage.SV_NEWGAME_OPTION_VALUE_TOONEW:
            {
                // Extract game name and failing game-opt keynames,
                // and pop up an error message window.
                String errMsg;
                StringTokenizer st = new StringTokenizer(statusText, SOCMessage.sep2);
                try
                {
                    String gameName = null;
                    ArrayList<String> optNames = new ArrayList<String>();
                    errMsg = st.nextToken();
                    gameName = st.nextToken();
                    while (st.hasMoreTokens())
                        optNames.add(st.nextToken());

                    StringBuffer opts = new StringBuffer();
                    final Map<String, SOCGameOption> knowns =
                        isPractice ? practiceServGameOpts.optionSet : tcpServGameOpts.optionSet;
                    for (String oname : optNames)
                    {
                        opts.append('\n');
                        SOCGameOption oinfo = null;
                        if (knowns != null)
                            oinfo = knowns.get(oname);
                        if (oinfo != null)
                            oname = oinfo.getDesc();
                        opts.append(strings.get("options.error.valuesproblem.which", oname));
                    }
                    errMsg = strings.get("options.error.valuesproblem", gameName, errMsg, opts.toString());
                }
                catch (Throwable t)
                {
                    errMsg = statusText;  // fallback, not expected to happen
                }

                mainDisplay.showErrorDialog(errMsg, strings.get("base.cancel"));
            }
            break;

        case SOCStatusMessage.SV_GAME_CLIENT_FEATURES_NEEDED:
            {
                // Extract game name and missing client feature keynames,
                // and pop up an error message window.
                String errMsg;
                StringTokenizer st = new StringTokenizer(statusText, SOCMessage.sep2);
                try
                {
                    errMsg = st.nextToken();
                    final String gameName = st.nextToken();
                    final String featsList = (st.hasMoreTokens()) ? st.nextToken() : "?";
                    final String msgKey = (doesGameExist(gameName, true))
                        ? "pcli.gamelist.client_feats.cannot_join"
                            // "Cannot create game {0}\nThis client does not have required feature(s): {1}"
                        : "pcli.gamelist.client_feats.cannot_create";
                            // "Cannot join game {0}\nThis client does not have required feature(s): {1}"
                    errMsg = strings.get(msgKey, gameName, featsList);
                }
                catch (Throwable t)
                {
                    errMsg = statusText;  // fallback, not expected to happen
                }

                mainDisplay.showErrorDialog(errMsg, strings.get("base.cancel"));
            }
            break;
        }
    }

    /**
     * handle the "join channel authorization" message
     * @param mes  the message
     */
    protected void handleJOINCHANNELAUTH(SOCJoinChannelAuth mes)
    {
        gotPassword = true;
        mainDisplay.channelJoined(mes.getChannel());
    }

    /**
     * handle the "join channel" message
     * @param mes  the message
     */
    protected void handleJOINCHANNEL(SOCJoinChannel mes)
    {
        mainDisplay.channelJoined(mes.getChannel(), mes.getNickname());
    }

    /**
     * handle the "channel members" message
     * @param mes  the message
     */
    protected void handleCHANNELMEMBERS(SOCChannelMembers mes)
    {
        mainDisplay.channelMemberList(mes.getChannel(), mes.getMembers());
    }

    /**
     * handle the "new channel" message
     * @param mes  the message
     */
    protected void handleNEWCHANNEL(SOCNewChannel mes)
    {
        mainDisplay.channelCreated(mes.getChannel());
    }

    /**
     * handle the "list of channels" message; this message indicates that
     * we're newly connected to the server, and is sent even if the server
     * isn't using {@link SOCFeatureSet#SERVER_CHANNELS}: Server connection is complete.
     * Unless {@code isPractice}, show {@link #MAIN_PANEL}.
     * @param mes  the message
     * @param isPractice is the server actually {@link ClientNetwork#practiceServer} (practice game)?
     */
    protected void handleCHANNELS(SOCChannels mes, final boolean isPractice)
    {
        mainDisplay.channelList(mes.getChannels(), isPractice);
    }

    /**
     * handle a broadcast text message
     * @param mes  the message
     */
    protected void handleBCASTTEXTMSG(SOCBCastTextMsg mes)
    {
        mainDisplay.chatMessageBroadcast(mes.getText());

        for (PlayerClientListener pcl : clientListeners.values())
        {
            pcl.messageBroadcast(mes.getText());
        }
    }

    /**
     * handle a text message received in a channel
     * @param mes  the message
     * @see #handleGAMETEXTMSG(SOCGameTextMsg)
     */
    protected void handleCHANNELTEXTMSG(SOCChannelTextMsg mes)
    {
        mainDisplay.chatMessageReceived(mes.getChannel(), mes.getNickname(), mes.getText());
    }

    /**
     * handle the "leave channel" message
     * @param mes  the message
     */
    protected void handleLEAVECHANNEL(SOCLeaveChannel mes)
    {
        mainDisplay.channelLeft(mes.getChannel(), mes.getNickname());
    }

    /**
     * handle the "delete channel" message
     * @param mes  the message
     */
    protected void handleDELETECHANNEL(SOCDeleteChannel mes)
    {
        mainDisplay.channelDeleted(mes.getChannel());
    }

    /**
     * handle the "list of games" message
     * @param mes  the message
     */
    protected void handleGAMES(SOCGames mes, final boolean isPractice)
    {
        // Any game's name in this msg may start with the "unjoinable" prefix
        // SOCGames.MARKER_THIS_GAME_UNJOINABLE.
        // We'll recognize and remove it in methods called from here.

        List<String> gameNames = mes.getGames();

        if (! isPractice)  // practiceServer's gameoption data is set up in handleVERSION
        {
            if (serverGames == null)
                serverGames = new SOCGameList();
            serverGames.addGames(gameNames, Version.versionNumber());

            // No more game-option info will be received,
            // because that's always sent before game names are sent.
            // We may still ask for GAMEOPTIONGETDEFAULTS if asking to create a game,
            // but that will happen when user clicks that button, not yet.
            tcpServGameOpts.noMoreOptions(false);

            // Reset enum for addToGameList call; serverGames.addGames has consumed it.
            gameNames = mes.getGames();
        }

        for (String gn : gameNames)
        {
            addToGameList(gn, null, false);
        }
    }

    /**
     * handle the "join game authorization" message: create new {@link SOCGame} and
     * {@link SOCPlayerInterface} so user can join the game
     * @param mes  the message
     * @param isPractice server is practiceServer (not normal tcp network)
     */
    protected void handleJOINGAMEAUTH(SOCJoinGameAuth mes, final boolean isPractice)
    {
        System.err.println("L2299 joingameauth at " + System.currentTimeMillis());
        gotPassword = true;

        final String gaName = mes.getGame();
        Map<String,SOCGameOption> gameOpts;
        if (isPractice)
        {
            gameOpts = net.practiceServer.getGameOptions(gaName);
            if (gameOpts != null)
                gameOpts = new HashMap<String,SOCGameOption>(gameOpts);  // changes here shouldn't change practiceServ's copy
        } else {
            if (serverGames != null)
                gameOpts = serverGames.parseGameOptions(gaName);
            else
                gameOpts = null;
        }
        System.err.println("L2318 past opts at " + System.currentTimeMillis());

        SOCGame ga = new SOCGame(gaName, gameOpts);
        if (ga != null)
        {
            ga.isPractice = isPractice;
            PlayerClientListener clientListener = mainDisplay.gameJoined(ga, gameReqLocalPrefs.get(gaName));
            clientListeners.put(gaName, clientListener);
            games.put(gaName, ga);
        }
        System.err.println("L2332 handlejoin done at " + System.currentTimeMillis());
    }

    /**
     * handle the "join game" message
     * @param mes  the message
     */
    protected void handleJOINGAME(SOCJoinGame mes)
    {
        final String gn = mes.getGame();
        final String name = mes.getNickname();
        if (name == null)
            return;

        PlayerClientListener pcl = clientListeners.get(gn);
        pcl.playerJoined(name);
    }

    /**
     * handle the "leave game" message
     * @param mes  the message
     */
    protected void handleLEAVEGAME(SOCLeaveGame mes)
    {
        String gn = mes.getGame();
        SOCGame ga = games.get(gn);

        if (ga != null)
        {
            final String name = mes.getNickname();
            final SOCPlayer player = ga.getPlayer(name);

            // Give the listener a chance to clean up while the player is still in the game
            PlayerClientListener pcl = clientListeners.get(gn);
            pcl.playerLeft(name, player);

            if (player != null)
            {
                //
                //  This user was not a spectator.
                //  Remove first from listener, then from game data.
                //
                ga.removePlayer(name);
            }
        }
    }

    /**
     * handle the "new game" message
     * @param mes  the message
     */
    protected void handleNEWGAME(SOCNewGame mes, final boolean isPractice)
    {
        addToGameList(mes.getGame(), null, ! isPractice);
    }

    /**
     * handle the "delete game" message
     * @param mes  the message
     */
    protected void handleDELETEGAME(SOCDeleteGame mes, final boolean isPractice)
    {
        final String gaName = mes.getGame();

        if (! mainDisplay.deleteFromGameList(gaName, isPractice, false))
            mainDisplay.deleteFromGameList(gaName, isPractice, true);

        PlayerClientListener pcl = clientListeners.get(gaName);
        if (pcl != null)
            pcl.gameDisconnected(true, null);
    }

    /**
     * handle the "game members" message, the server's hint that it's almost
     * done sending us the complete game state in response to JOINGAME.
     * @param mes  the message
     */
    protected void handleGAMEMEMBERS(final SOCGameMembers mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.membersListed(mes.getMembers());
    }

    /**
     * handle the "game stats" message
     */
    protected void handleGAMESTATS(SOCGameStats mes)
    {
        String ga = mes.getGame();
        int[] scores = mes.getScores();

        // If we're playing in a game, update the scores. (SOCPlayerInterface)
        // This is used to show the true scores, including hidden
        // victory-point cards, at the game's end.
        updateGameEndStats(ga, scores);
    }

    /**
     * handle the "game text message" message.
     * Messages not from Server go to the chat area.
     * Messages from Server go to the game text window.
     * Urgent messages from Server (starting with ">>>") also go to the chat area,
     * which has less activity, so they are harder to miss.
     *
     * @param mes  the message
     * @see #handleGAMESERVERTEXT(SOCGameServerText)
     * @see #handleCHANNELTEXTMSG(SOCChannelTextMsg)
     */
    protected void handleGAMETEXTMSG(SOCGameTextMsg mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;

        String fromNickname = mes.getNickname();
        if (fromNickname.equals(SOCGameTextMsg.SERVERNAME))  // for pre-2.0.00 servers not using SOCGameServerText
            fromNickname = null;
        pcl.messageReceived(fromNickname, mes.getText());
    }

    /**
     * handle the "player sitting down" message
     * @param mes  the message
     */
    protected void handleSITDOWN(final SOCSitDown mes)
    {
        /**
         * tell the game that a player is sitting
         */
        final SOCGame ga = games.get(mes.getGame());
        if (ga != null)
        {
            final int mesPN = mes.getPlayerNumber();

            ga.takeMonitor();
            SOCPlayer player = null;
            try
            {
                ga.addPlayer(mes.getNickname(), mesPN);

                player = ga.getPlayer(mesPN);
                /**
                 * set the robot flag
                 */
                player.setRobotFlag(mes.isRobot(), false);
            }
            catch (Exception e)
            {
                System.out.println("Exception caught - " + e);
                e.printStackTrace();

                return;
            }
            finally
            {
                ga.releaseMonitor();
            }

            /**
             * tell the GUI that a player is sitting
             */
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.playerSitdown(mesPN, mes.getNickname());

            /**
             * let the board panel & building panel find our player object if we sat down
             */
            if (nickname.equals(mes.getNickname()))
            {
                /**
                 * change the face (this is so that old faces don't 'stick')
                 */
                if (! ga.isBoardReset() && (ga.getGameState() < SOCGame.START1A))
                {
                    ga.getPlayer(mesPN).setFaceId(lastFaceChange);
                    gmm.changeFace(ga, lastFaceChange);
                }
            }
        }
    }

    /**
     * Handle the old "board layout" message (original 4-player board, no options).
     * Most game boards will call {@link #handleBOARDLAYOUT2(SOCBoardLayout2)} instead.
     * @param mes  the message
     */
    protected void handleBOARDLAYOUT(SOCBoardLayout mes)
    {
        System.err.println("L2561 boardlayout at " + System.currentTimeMillis());
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            // BOARDLAYOUT is always the v1 board encoding (oldest format)
            SOCBoard bd = ga.getBoard();
            bd.setHexLayout(mes.getHexLayout());
            bd.setNumberLayout(mes.getNumberLayout());
            bd.setRobberHex(mes.getRobberHex(), false);
            ga.updateAtBoardLayout();

            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.boardLayoutUpdated();
        }
    }

    /**
     * echo the server ping, to ensure we're still connected.
     * Ping may be a keepalive check or an attempt to kick by another
     * client with the same nickname; may call
     * {@link SOCPlayerClient#shutdownFromNetwork()} if so.
     *<P>
     * (message ignored before v1.1.08)
     * @since 1.1.08
     */
    private void handleSERVERPING(SOCServerPing mes, final boolean isPractice)
    {
        int timeval = mes.getSleepTime();
        if (timeval != -1)
        {
            gmm.put(mes.toCmd(), isPractice);
        } else {
            net.ex = new RuntimeException(strings.get("pcli.error.kicked.samename"));  // "Kicked by player with same name."
            client.shutdownFromNetwork();
        }
    }

    /**
     * Handle the "board layout" message, in its usual format.
     * (Some simple games can use the old {@link #handleBOARDLAYOUT(SOCBoardLayout)} instead.)
     * @param mes  the message
     * @since 1.1.08
     */
    protected void handleBOARDLAYOUT2(SOCBoardLayout2 mes)
    {
        System.err.println("L2602 boardlayout2 at " + System.currentTimeMillis());
        if (SOCDisplaylessPlayerClient.handleBOARDLAYOUT2(games, mes))
        {
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.boardLayoutUpdated();
        }
    }

    /**
     * handle the "start game" message
     * @param mes  the message
     */
    protected void handleSTARTGAME(SOCStartGame mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        final SOCGame ga = games.get(mes.getGame());
        if ((pcl == null) || (ga == null))
            return;

        if (ga.getGameState() == SOCGame.NEW)
            // skip gameStarted call if handleGAMESTATE already called it
            pcl.gameStarted();
    }

    /**
     * Handle the "game state" message; calls {@link #handleGAMESTATE(SOCGame, int)}.
     * @param mes  the message
     */
    protected void handleGAMESTATE(SOCGameState mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        handleGAMESTATE(ga, mes.getState());
    }

    /**
     * Handle game state message: Update {@link SOCGame} and {@link PlayerClientListener} if any.
     * Call for any message type which contains a Game State field.
     *<P>
     * Checks current {@link SOCGame#getGameState()}; if current state is {@link SOCGame#NEW NEW}
     * and {@code newState != NEW}, calls {@link PlayerClientListener#gameStarted()} before
     * its usual {@link PlayerClientListener#gameStateChanged(int)} call.
     *
     * @param ga  Game to update state; not null
     * @param newState  New state from message, like {@link SOCGame#ROLL_OR_CARD}, or 0. Does nothing if 0.
     * @see #handleGAMESTATE(SOCGameState)
     * @since 2.0.00
     */
    protected void handleGAMESTATE(final SOCGame ga, final int newState)
    {
        if (newState == 0)
            return;

        final boolean gameStarted = (ga.getGameState() == SOCGame.NEW) && (newState != SOCGame.NEW);

        ga.setGameState(newState);

        PlayerClientListener pcl = clientListeners.get(ga.getName());
        if (pcl == null)
            return;

        if (gameStarted)
        {
            // call here, not just in handleSTARTGAME, in case we joined a game in progress
            pcl.gameStarted();
        }
        pcl.gameStateChanged(newState);
    }

    /**
     * handle the "turn" message
     * @param mes  the message
     */
    protected void handleTURN(SOCTurn mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;

        handleGAMESTATE(ga, mes.getGameState());

        final int pnum = mes.getPlayerNumber();
        ga.setCurrentPlayerNumber(pnum);
        ga.updateAtTurn();
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.playerTurnSet(pnum);
    }

    /**
     * handle the "player information" message: Finds game and its {@link PlayerClientListener} by name
     * and calls {@link #handlePLAYERELEMENT(PlayerClientListener, SOCGame, SOCPlayer, int, int, int, int, boolean)}.
     * @param mes  the message
     * @since 2.0.00
     */
    protected void handlePLAYERELEMENTS(SOCPlayerElements mes)
    {
        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final PlayerClientListener pcl = clientListeners.get(mes.getGame());
        final int pn = mes.getPlayerNumber();
        final SOCPlayer pl = (pn != -1) ? ga.getPlayer(pn) : null;
        final int action = mes.getAction();
        final int[] etypes = mes.getElementTypes(), amounts = mes.getAmounts();

        for (int i = 0; i < etypes.length; ++i)
            handlePLAYERELEMENT
                (pcl, ga, pl, pn, action, etypes[i], amounts[i], false);
    }

    /**
     * handle the "player information" message: Finds game and its {@link PlayerClientListener} by name
     * and calls {@link #handlePLAYERELEMENT(PlayerClientListener, SOCGame, SOCPlayer, int, int, int, int, boolean)}.
     * @param mes  the message
     */
    protected void handlePLAYERELEMENT(SOCPlayerElement mes)
    {
        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final int pn = mes.getPlayerNumber();
        final int action = mes.getAction(), amount = mes.getAmount();
        final int etype = mes.getElementType();

        handlePLAYERELEMENT
            (clientListeners.get(mes.getGame()), ga, null, pn, action, etype, amount, mes.isNews());
    }

    /**
     * Handle a player information update from a {@link SOCPlayerElement} or {@link SOCPlayerElements} message.
     * Update game information, then update {@code pcl} display if appropriate.
     *<P>
     * To update game information, defaults to calling
     * {@link SOCDisplaylessPlayerClient#handlePLAYERELEMENT_simple(SOCGame, SOCPlayer, int, int, int, int, String)}
     * for elements that don't need special handling for this client class.
     *
     * @param pcl  PlayerClientListener for {@code ga}, to update display if not null
     * @param ga   Game to update; does nothing if null
     * @param pl   Player to update; some elements take null. If null and {@code pn != -1}, will find {@code pl}
     *     using {@link SOCGame#getPlayer(int) ga.getPlayer(pn)}.
     * @param pn   Player number from message (sometimes -1 for none or all)
     * @param action   {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN GAIN},
     *     or {@link SOCPlayerElement#LOSE LOSE}
     * @param etype  Element type, such as {@link SOCPlayerElement#SETTLEMENTS} or {@link SOCPlayerElement#NUMKNIGHTS}
     * @param amount  The new value to set, or the delta to gain/lose
     * @param isNews  True if message's isNews() flag is set; used when calling
     *     {@link PlayerClientListener#playerElementUpdated(SOCPlayer, soc.client.PlayerClientListener.UpdateType, boolean, boolean)}
     * @since 2.0.00
     */
    private void handlePLAYERELEMENT
        (final PlayerClientListener pcl, final SOCGame ga, SOCPlayer pl, final int pn,
         final int action, final int etype, final int amount, final boolean isNews)
    {
        if (ga == null)
            return;

        if ((pl == null) && (pn != -1))
            pl = ga.getPlayer(pn);

        PlayerClientListener.UpdateType utype = null;  // If not null, update this type's amount display

        switch (etype)
        {
        case SOCPlayerElement.ROADS:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.ROAD, amount);
            utype = PlayerClientListener.UpdateType.Road;
            break;

        case SOCPlayerElement.SETTLEMENTS:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.SETTLEMENT, amount);
            utype = PlayerClientListener.UpdateType.Settlement;
            break;

        case SOCPlayerElement.CITIES:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.CITY, amount);
            utype = PlayerClientListener.UpdateType.City;
            break;

        case SOCPlayerElement.SHIPS:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numPieces
                (pl, action, SOCPlayingPiece.SHIP, amount);
            utype = PlayerClientListener.UpdateType.Ship;
            break;

        case SOCPlayerElement.NUMKNIGHTS:
            // PLAYERELEMENT(NUMKNIGHTS) is sent after a Soldier card is played.
            {
                final SOCPlayer oldLargestArmyPlayer = ga.getPlayerWithLargestArmy();
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numKnights
                    (ga, pl, action, amount);
                utype = PlayerClientListener.UpdateType.Knight;

                // Check for change in largest-army player; update handpanels'
                // LARGESTARMY and VICTORYPOINTS counters if so, and
                // announce with text message.
                pcl.largestArmyRefresh(oldLargestArmyPlayer, ga.getPlayerWithLargestArmy());
            }

            break;

        case SOCPlayerElement.CLAY:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.CLAY, amount);
            utype = PlayerClientListener.UpdateType.Clay;
            break;

        case SOCPlayerElement.ORE:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.ORE, amount);
            utype = PlayerClientListener.UpdateType.Ore;
            break;

        case SOCPlayerElement.SHEEP:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.SHEEP, amount);
            utype = PlayerClientListener.UpdateType.Sheep;
            break;

        case SOCPlayerElement.WHEAT:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.WHEAT, amount);
            utype = PlayerClientListener.UpdateType.Wheat;
            break;

        case SOCPlayerElement.WOOD:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.WOOD, amount);
            utype = PlayerClientListener.UpdateType.Wood;
            break;

        case SOCPlayerElement.UNKNOWN:
            /**
             * Note: if losing unknown resources, we first
             * convert player's known resources to unknown resources,
             * then remove mes's unknown resources from player.
             */
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_numRsrc
                (pl, action, SOCResourceConstants.UNKNOWN, amount);
            utype = PlayerClientListener.UpdateType.Unknown;
            break;

        case SOCPlayerElement.ASK_SPECIAL_BUILD:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                (ga, pl, pn, action, etype, amount, nickname);
            // This case is not really an element update, so route as a 'request'
            pcl.requestedSpecialBuild(pl);
            break;

        case SOCPlayerElement.RESOURCE_COUNT:
            if (amount != pl.getResources().getTotal())
            {
                SOCResourceSet rsrcs = pl.getResources();

                if (D.ebugOn)
                {
                    //pi.print(">>> RESOURCE COUNT ERROR: "+mes.getCount()+ " != "+rsrcs.getTotal());
                }

                boolean isClientPlayer = pl.getName().equals(client.getNickname());

                //
                //  fix it
                //

                if (! isClientPlayer)
                {
                    rsrcs.clear();
                    rsrcs.setAmount(amount, SOCResourceConstants.UNKNOWN);
                    pcl.playerResourcesUpdated(pl);
                }
            }
            break;

        case SOCPlayerElement.NUM_PICK_GOLD_HEX_RESOURCES:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                (ga, pl, pn, action, etype, amount, nickname);
            pcl.requestedGoldResourceCountUpdated(pl, 0);
            break;

        case SOCPlayerElement.SCENARIO_SVP:
            pl.setSpecialVP(amount);
            utype = PlayerClientListener.UpdateType.SpecialVictoryPoints;
            break;

        case SOCPlayerElement.SCENARIO_CLOTH_COUNT:
            if (pn != -1)
            {
                pl.setCloth(amount);
            } else {
                ((SOCBoardLarge) (ga.getBoard())).setCloth(amount);
            }
            utype = PlayerClientListener.UpdateType.Cloth;
            break;

        case SOCPlayerElement.SCENARIO_WARSHIP_COUNT:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                (ga, pl, pn, action, etype, amount, nickname);
            utype = PlayerClientListener.UpdateType.Warship;
            break;

        default:
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                (ga, pl, pn, action, etype, amount, nickname);
        }

        if ((pcl != null) && (utype != null))
        {
            if (! isNews)
                pcl.playerElementUpdated(pl, utype, false, false);
            else if (action == SOCPlayerElement.GAIN)
                pcl.playerElementUpdated(pl, utype, true, false);
            else
                pcl.playerElementUpdated(pl, utype, false, true);
        }
    }

    /**
     * Handle the GameElements message: Finds game by name, and loops calling
     * {@link #handleGAMEELEMENT(SOCGame, int, int)}.
     * @param mes  the message
     * @since 2.0.00
     */
    protected void handleGAMEELEMENTS(final SOCGameElements mes)
    {
        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final int[] etypes = mes.getElementTypes(), values = mes.getValues();
        for (int i = 0; i < etypes.length; ++i)
            handleGAMEELEMENT(ga, etypes[i], values[i]);
    }

    /**
     * Update one game element field from a {@link SOCGameElements} message,
     * then update game's {@link PlayerClientListener} display if appropriate.
     *<P>
     * To update game information, calls
     * {@link SOCDisplaylessPlayerClient#handleGAMEELEMENT(SOCGame, int, int)}.
     *
     * @param ga   Game to update; does nothing if null
     * @param etype  Element type, such as {@link SOCGameElements#ROUND_COUNT} or {@link SOCGameElements#DEV_CARD_COUNT}
     * @param value  The new value to set
     * @since 2.0.00
     */
    protected void handleGAMEELEMENT
        (final SOCGame ga, final int etype, final int value)
    {
        if (ga == null)
            return;

        final PlayerClientListener pcl = clientListeners.get(ga.getName());

        // A few etypes need to give PCL the old and new values.
        // For those, update game state and PCL together and return.
        if (pcl != null)
        {
            switch (etype)
            {
            // SOCGameElements.ROUND_COUNT:
                // Doesn't need a case here because it's sent only during joingame;
                // SOCBoardPanel will check ga.getRoundCount() as part of joingame

            case SOCGameElements.LARGEST_ARMY_PLAYER:
                {
                    SOCPlayer oldLargestArmyPlayer = ga.getPlayerWithLargestArmy();
                    SOCDisplaylessPlayerClient.handleGAMEELEMENT(ga, etype, value);
                    SOCPlayer newLargestArmyPlayer = ga.getPlayerWithLargestArmy();

                    // Update player victory points; check for and announce change in largest army
                    pcl.largestArmyRefresh(oldLargestArmyPlayer, newLargestArmyPlayer);
                }
                return;

            case SOCGameElements.LONGEST_ROAD_PLAYER:
                {
                    SOCPlayer oldLongestRoadPlayer = ga.getPlayerWithLongestRoad();
                    SOCDisplaylessPlayerClient.handleGAMEELEMENT(ga, etype, value);
                    SOCPlayer newLongestRoadPlayer = ga.getPlayerWithLongestRoad();

                    // Update player victory points; check for and announce change in longest road
                    pcl.longestRoadRefresh(oldLongestRoadPlayer, newLongestRoadPlayer);
                }
                return;
            }
        }

        SOCDisplaylessPlayerClient.handleGAMEELEMENT(ga, etype, value);

        if (pcl == null)
            return;

        switch (etype)
        {
        case SOCGameElements.DEV_CARD_COUNT:
            pcl.devCardDeckUpdated();
            break;

        case SOCGameElements.CURRENT_PLAYER:
            pcl.playerTurnSet(value);
            break;
        }
    }

    /**
     * handle "resource count" message
     * @param mes  the message
     */
    protected void handleRESOURCECOUNT(SOCResourceCount mes)
    {
        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        handlePLAYERELEMENT
            (clientListeners.get(mes.getGame()), ga, null, mes.getPlayerNumber(),
             SOCPlayerElement.SET, SOCPlayerElement.RESOURCE_COUNT, mes.getCount(), false);
    }

    /**
     * handle the "dice result" message
     * @param mes  the message
     */
    protected void handleDICERESULT(SOCDiceResult mes)
    {
        final String gameName = mes.getGame();
        SOCGame ga = games.get(gameName);
        if (ga == null)
            throw new IllegalStateException("No game found for name '"+gameName+"'");

        final int cpn = ga.getCurrentPlayerNumber();
        SOCPlayer p = null;
        if (cpn >= 0)
            p = ga.getPlayer(cpn);

        final int roll = mes.getResult();
        final SOCPlayer player = p;

        // update game state
        ga.setCurrentDice(roll);

        // notify listener
        PlayerClientListener listener = clientListeners.get(gameName);
        listener.diceRolled(player, roll);
    }

    /**
     * handle the "put piece" message
     * @param mes  the message
     */
    protected void handlePUTPIECE(SOCPutPiece mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        final int coord = mes.getCoordinates();
        final int ptype = mes.getPieceType();

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;
        pcl.playerPiecePlaced(player, coord, ptype);
    }

    /**
     * handle the rare "cancel build request" message; usually not sent from
     * server to client.
     *<P>
     * - When sent from client to server, CANCELBUILDREQUEST means the player has changed
     *   their mind about spending resources to build a piece.  Only allowed during normal
     *   game play (PLACING_ROAD, PLACING_SETTLEMENT, or PLACING_CITY).
     *<P>
     *  When sent from server to client:
     *<P>
     * - During game startup (START1B or START2B): <BR>
     *       Sent from server, CANCELBUILDREQUEST means the current player
     *       wants to undo the placement of their initial settlement.
     *<P>
     * - During piece placement (PLACING_ROAD, PLACING_CITY, PLACING_SETTLEMENT,
     *                           PLACING_FREE_ROAD1 or PLACING_FREE_ROAD2):
     *<P>
     *      Sent from server, CANCELBUILDREQUEST means the player has sent
     *      an illegal PUTPIECE (bad building location). Humans can probably
     *      decide a better place to put their road, but robots must cancel
     *      the build request and decide on a new plan.
     *<P>
     *      Our client can ignore this case, because the server also sends a text
     *      message that the human player is capable of reading and acting on.
     *      For convenience, during initial placement
     *      {@link PlayerClientListener#buildRequestCanceled(SOCPlayer)}
     *      is called to reset things like {@link SOCBoardPanel} hovering pieces.
     *
     * @param mes  the message
     * @since 1.1.00
     */
    protected void handleCANCELBUILDREQUEST(SOCCancelBuildRequest mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final int ptype = mes.getPieceType();
        final SOCPlayer pl = ga.getPlayer(ga.getCurrentPlayerNumber());

        if (ptype >= SOCPlayingPiece.SETTLEMENT)
        {
            final int sta = ga.getGameState();
            if ((sta != SOCGame.START1B) && (sta != SOCGame.START2B) && (sta != SOCGame.START3B))
            {
                // The human player gets a text message from the server informing
                // about the bad piece placement.  So, we can ignore this message type.
                return;
            }

            if (ptype == SOCPlayingPiece.SETTLEMENT)
            {
                SOCSettlement pp = new SOCSettlement(pl, pl.getLastSettlementCoord(), null);
                ga.undoPutInitSettlement(pp);
            }
        } else {
            // ptype is -3 (SOCCancelBuildRequest.INV_ITEM_PLACE_CANCEL)
        }

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.buildRequestCanceled(pl);
    }

    /**
     * handle the "robber moved" or "pirate moved" message.
     * @param mes  the message
     */
    protected void handleMOVEROBBER(SOCMoveRobber mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            /**
             * Note: Don't call ga.moveRobber() because that will call the
             * functions to do the stealing.  We just want to say where
             * the robber moved without seeing if something was stolen.
             */
            int newHex = mes.getCoordinates();
            final boolean isPirate = (newHex <= 0);
            if (! isPirate)
            {
                ga.getBoard().setRobberHex(newHex, true);
            } else {
                newHex = -newHex;
                ((SOCBoardLarge) ga.getBoard()).setPirateHex(newHex, true);
            }

            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.robberMoved(newHex, isPirate);
        }
    }

    /**
     * handle the "discard request" message
     * @param mes  the message
     */
    protected void handleDISCARDREQUEST(SOCDiscardRequest mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.requestedDiscard(mes.getNumberOfDiscards());
    }

    /**
     * handle the "choose player request" message
     * @param mes  the message
     */
    protected void handleCHOOSEPLAYERREQUEST(SOCChoosePlayerRequest mes)
    {
        SOCGame game = games.get(mes.getGame());
        final int maxPl = game.maxPlayers;
        final boolean[] ch = mes.getChoices();

        List<SOCPlayer> choices = new ArrayList<SOCPlayer>();
        for (int i = 0; i < maxPl; i++)
        {
            if (ch[i])
            {
                SOCPlayer p = game.getPlayer(i);
                choices.add(p);
            }
        }

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.requestedChoosePlayer(choices, mes.canChooseNone());
    }

    /**
     * The server wants this player to choose to rob cloth or rob resources,
     * after moving the pirate ship.  Added 2012-11-17 for v2.0.00.
     */
    protected void handleCHOOSEPLAYER(SOCChoosePlayer mes)
    {
        SOCGame ga = games.get(mes.getGame());
        int victimPlayerNumber = mes.getChoice();
        SOCPlayer player = ga.getPlayer(victimPlayerNumber);

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.requestedChooseRobResourceType(player);
    }

    /**
     * handle the "bank trade" message from a v2.0.00 or newer server.
     * @param mes  the message
     * @since 2.0.00
     */
    protected void handleBANKTRADE(final SOCBankTrade mes)
    {
        final String gaName = mes.getGame();
        final SOCGame ga = games.get(gaName);
        if (ga == null)
            return;
        PlayerClientListener pcl = clientListeners.get(gaName);
        if (pcl == null)
            return;

        pcl.playerBankTrade(ga.getPlayer(mes.getPlayerNumber()), mes.getGiveSet(), mes.getGetSet());
    }

    /**
     * handle the "make offer" message
     * @param mes  the message
     */
    protected void handleMAKEOFFER(final SOCMakeOffer mes)
    {
        final String gaName = mes.getGame();
        final SOCGame ga = games.get(gaName);
        if (ga == null)
            return;

        SOCTradeOffer offer = mes.getOffer();
        SOCPlayer from = ga.getPlayer(offer.getFrom());
        from.setCurrentOffer(offer);

        PlayerClientListener pcl = clientListeners.get(gaName);
        pcl.requestedTrade(from);
    }

    /**
     * handle the "clear offer" message
     * @param mes  the message
     */
    protected void handleCLEAROFFER(final SOCClearOffer mes)
    {
        final SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            final int pn = mes.getPlayerNumber();
            SOCPlayer player = null;
            if (pn != -1)
                player = ga.getPlayer(pn);

            if (pn != -1)
            {
                ga.getPlayer(pn).setCurrentOffer(null);
            } else {
                for (int i = 0; i < ga.maxPlayers; ++i)
                {
                    ga.getPlayer(i).setCurrentOffer(null);
                }
            }

            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.requestedTradeClear(player, false);
        }
    }

    /**
     * handle the "reject offer" message
     * @param mes  the message
     */
    protected void handleREJECTOFFER(SOCRejectOffer mes)
    {
        SOCGame ga = games.get(mes.getGame());
        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.requestedTradeRejection(player);
    }

    /**
     * handle the "accept offer" message
     * @param mes  the message
     * @since 2.0.00
     */
    protected void handleACCEPTOFFER(final SOCAcceptOffer mes)
    {
        final String gaName = mes.getGame();
        final SOCGame ga = games.get(gaName);
        if (ga == null)
            return;
        PlayerClientListener pcl = clientListeners.get(gaName);
        if (pcl == null)
            return;

        pcl.playerTradeAccepted
            (ga.getPlayer(mes.getOfferingNumber()), ga.getPlayer(mes.getAcceptingNumber()));
    }

    /**
     * handle the "clear trade message" message
     * @param mes  the message
     */
    protected void handleCLEARTRADEMSG(SOCClearTradeMsg mes)
    {
        SOCGame ga = games.get(mes.getGame());
        int pn = mes.getPlayerNumber();
        SOCPlayer player = null;
        if (pn != -1)
            player = ga.getPlayer(pn);

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        pcl.requestedTradeReset(player);
    }

    /**
     * handle the "development card action" message
     * @param mes  the message
     */
    protected void handleDEVCARDACTION(final boolean isPractice, final SOCDevCardAction mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            final int mesPN = mes.getPlayerNumber();
            SOCPlayer player = ga.getPlayer(mesPN);

            int ctype = mes.getCardType();
            if ((! isPractice) && (sVersion < SOCDevCardConstants.VERSION_FOR_NEW_TYPES))
            {
                if (ctype == SOCDevCardConstants.KNIGHT_FOR_VERS_1_X)
                    ctype = SOCDevCardConstants.KNIGHT;
                else if (ctype == SOCDevCardConstants.UNKNOWN_FOR_VERS_1_X)
                    ctype = SOCDevCardConstants.UNKNOWN;
            }

            final int act = mes.getAction();
            switch (act)
            {
            case SOCDevCardAction.DRAW:
                player.getInventory().addDevCard(1, SOCInventory.NEW, ctype);
                break;

            case SOCDevCardAction.PLAY:
                player.getInventory().removeDevCard(SOCInventory.OLD, ctype);
                // JM temp debug:
                if (ctype != mes.getCardType())
                    System.out.println("L3947: play dev card type " + ctype + "; srv has " + mes.getCardType());
                break;

            case SOCDevCardAction.ADD_OLD:
                player.getInventory().addDevCard(1, SOCInventory.OLD, ctype);
                break;

            case SOCDevCardAction.ADD_NEW:
                player.getInventory().addDevCard(1, SOCInventory.NEW, ctype);
                break;
            }

            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            pcl.playerDevCardUpdated(player, (act == SOCDevCardAction.ADD_OLD));
        }
    }

    /**
     * handle the "set played dev card flag" message
     * @param mes  the message
     */
    protected void handleSETPLAYEDDEVCARD(SOCSetPlayedDevCard mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
            SOCDisplaylessPlayerClient.handlePLAYERELEMENT_simple
                (ga, null, mes.getPlayerNumber(), SOCPlayerElement.SET,
                 SOCPlayerElement.PLAYED_DEV_CARD_FLAG, mes.hasPlayedDevCard() ? 1 : 0, null);
    }

    /**
     * handle the "list of potential settlements" message
     * @param mes  the message
     * @throws IllegalStateException if the board has
     *     {@link SOCBoardLarge#getAddedLayoutPart(String) SOCBoardLarge.getAddedLayoutPart("AL")} != {@code null} but
     *     badly formed (node list number 0, or a node list number not followed by a land area number).
     *     This Added Layout Part is rarely used, and this would be discovered quickly while testing
     *     the board layout that contained it.
     */
    protected void handlePOTENTIALSETTLEMENTS(SOCPotentialSettlements mes)
        throws IllegalStateException
    {
        System.err.println("L3292 potentialsettles at " + System.currentTimeMillis());
        SOCDisplaylessPlayerClient.handlePOTENTIALSETTLEMENTS(mes, games);

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl != null)
            pcl.boardPotentialsUpdated();
    }

    /**
     * handle the "change face" message
     * @param mes  the message
     */
    protected void handleCHANGEFACE(SOCChangeFace mes)
    {
        SOCGame ga = games.get(mes.getGame());

        if (ga != null)
        {
            SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
            PlayerClientListener pcl = clientListeners.get(mes.getGame());
            player.setFaceId(mes.getFaceId());
            pcl.playerFaceChanged(player, mes.getFaceId());
        }
    }

    /**
     * handle the "reject connection" message
     * @param mes  the message
     */
    protected void handleREJECTCONNECTION(SOCRejectConnection mes)
    {
        net.disconnect();

        mainDisplay.showErrorPanel(mes.getText(), (net.ex_P == null));
    }

    /**
     * handle the "set seat lock" message
     * @param mes  the message
     */
    protected void handleSETSEATLOCK(SOCSetSeatLock mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;

        final SOCGame.SeatLockState[] sls = mes.getLockStates();
        if (sls == null)
            ga.setSeatLock(mes.getPlayerNumber(), mes.getLockState());
        else
            ga.setSeatLocks(sls);

        PlayerClientListener pcl = clientListeners.get(gaName);
        pcl.seatLockUpdated();
    }

    /**
     * handle the "roll dice prompt" message;
     *   if we're in a game and we're the dice roller,
     *   either set the auto-roll timer, or prompt to roll or choose card.
     *
     * @param mes  the message
     */
    protected void handleROLLDICEPROMPT(SOCRollDicePrompt mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        pcl.requestedDiceRoll(mes.getPlayerNumber());
    }

    /**
     * handle board reset
     * (new game with same players, same game name, new layout).
     * Create new Game object, destroy old one.
     * For human players, the reset message will be followed
     * with others which will fill in the game state.
     * For robots, they must discard game state and ask to re-join.
     *
     * @param mes  the message
     *
     * @see soc.server.SOCServer#resetBoardAndNotify(String, int)
     * @see soc.game.SOCGame#resetAsCopy()
     */
    protected void handleRESETBOARDAUTH(SOCResetBoardAuth mes)
    {
        String gname = mes.getGame();
        SOCGame ga = games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        SOCGame greset = ga.resetAsCopy();
        greset.isPractice = ga.isPractice;
        games.put(gname, greset);
        pcl.boardReset(greset, mes.getRejoinPlayerNumber(), mes.getRequestingPlayerNumber());
        ga.destroyGame();
    }

    /**
     * a player is requesting a board reset: we must update
     * local game state, and vote unless we are the requester.
     *
     * @param mes  the message
     */
    protected void handleRESETBOARDVOTEREQUEST(SOCResetBoardVoteRequest mes)
    {
        String gname = mes.getGame();
        SOCGame ga = games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        SOCPlayer player = ga.getPlayer(mes.getRequestingPlayer());
        pcl.boardResetVoteRequested(player);
    }

    /**
     * another player has voted on a board reset request: display the vote.
     *
     * @param mes  the message
     */
    protected void handleRESETBOARDVOTE(SOCResetBoardVote mes)
    {
        String gname = mes.getGame();
        SOCGame ga = games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        pcl.boardResetVoteCast(player, mes.getPlayerVote());
    }

    /**
     * voting complete, board reset request rejected
     *
     * @param mes  the message
     */
    protected void handleRESETBOARDREJECT(SOCResetBoardReject mes)
    {
        String gname = mes.getGame();
        SOCGame ga = games.get(gname);
        if (ga == null)
            return;  // Not one of our games
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        pcl.boardResetVoteRejected();
    }

    /**
     * process the "game option get defaults" message.
     * If any default option's keyname is unknown, ask the server.
     * @see ServerGametypeInfo
     * @since 1.1.07
     */
    private void handleGAMEOPTIONGETDEFAULTS(SOCGameOptionGetDefaults mes, final boolean isPractice)
    {
        ServerGametypeInfo opts;
        if (isPractice)
            opts = practiceServGameOpts;
        else
            opts = tcpServGameOpts;

        final List<String> unknowns;
        synchronized(opts)
        {
            // receiveDefaults sets opts.defaultsReceived, may set opts.allOptionsReceived
            unknowns = opts.receiveDefaults
                (SOCGameOption.parseOptionsToMap((mes.getOpts())));
        }

        if (unknowns != null)
        {
            if (! isPractice)
                mainDisplay.optionsRequested();

            gmm.put(SOCGameOptionGetInfos.toCmd(unknowns, wantsI18nStrings(isPractice), false), isPractice);
        } else {
            opts.newGameWaitingForOpts = false;
            mainDisplay.optionsReceived(opts, isPractice);
        }
    }

    /**
     * process the "game option info" message
     * by calling {@link ServerGametypeInfo#receiveInfo(SOCGameOptionInfo)}.
     * If all are now received, possibly show game info/options window for new game or existing game.
     *<P>
     * For a summary of the flags and variables involved with game options,
     * and the client/server interaction about their values, see
     * {@link ServerGametypeInfo}.
     *<P>
     * When first connected to a server having a different version, the client negotiates available options.
     * To avoid hanging on this process because of a very slow network or bug,
     * {@link SwingMainDisplay.GameOptionsTimeoutTask} can eventually call this
     * method to signal that all options have been received.
     *
     * @since 1.1.07
     */
    /*package*/ void handleGAMEOPTIONINFO(SOCGameOptionInfo mes, final boolean isPractice)
    {
        ServerGametypeInfo opts;
        if (isPractice)
            opts = practiceServGameOpts;
        else
            opts = tcpServGameOpts;

        boolean hasAllNow;
        synchronized(opts)
        {
            hasAllNow = opts.receiveInfo(mes);
        }

        boolean isDash = mes.getOptionNameKey().equals("-");  // I18N: do not localize "-" keyname
        mainDisplay.optionsReceived(opts, isPractice, isDash, hasAllNow);
    }

    /**
     * process the "new game with options" message
     * @since 1.1.07
     */
    private void handleNEWGAMEWITHOPTIONS(SOCNewGameWithOptions mes, final boolean isPractice)
    {
        System.err.println("L3609 newgamewithopts at " + System.currentTimeMillis());
        String gname = mes.getGame();
        String opts = mes.getOptionsString();
        boolean canJoin = (mes.getMinVersion() <= Version.versionNumber());
        if (gname.charAt(0) == SOCGames.MARKER_THIS_GAME_UNJOINABLE)
        {
            gname = gname.substring(1);
            canJoin = false;
        }
        mainDisplay.addToGameList(! canJoin, gname, opts, ! isPractice);
    }

    /**
     * handle the "list of games with options" message
     * @since 1.1.07
     */
    private void handleGAMESWITHOPTIONS(SOCGamesWithOptions mes, final boolean isPractice)
    {
        // Any game's name in this msg may start with the "unjoinable" prefix
        // SOCGames.MARKER_THIS_GAME_UNJOINABLE.
        // This is recognized and removed in mes.getGameList.

        SOCGameList msgGames = mes.getGameList();
        if (msgGames == null)
            return;

        if (! isPractice)  // practice gameoption data is set up in handleVERSION;
        {                  // practice srv's gamelist is reached through practiceServer obj.
            if (serverGames == null)
                serverGames = msgGames;
            else
                serverGames.addGames(msgGames, Version.versionNumber());

            // No more game-option info will be received,
            // because that's always sent before game names are sent.
            // We may still ask for GAMEOPTIONGETDEFAULTS if asking to create a game,
            // but that will happen when user clicks that button, not yet.
            tcpServGameOpts.noMoreOptions(false);
        }

        for (String gaName : msgGames.getGameNames())
        {
            mainDisplay.addToGameList(msgGames.isUnjoinableGame(gaName), gaName, msgGames.getGameOptionsString(gaName), false);
        }
    }

    /**
     * Localized i18n strings for game items.
     * Added 2015-01-11 for v2.0.00.
     * @param isPractice  Is the server {@link ClientNetwork#practiceServer}, not remote?
     */
    private void handleLOCALIZEDSTRINGS(final SOCLocalizedStrings mes, final boolean isPractice)
    {
        final List<String> strs = mes.getParams();
        final String type = strs.get(0);

        if (type.equals(SOCLocalizedStrings.TYPE_GAMEOPT))
        {
            final int L = strs.size();
            for (int i = 1; i < L; i += 2)
            {
                SOCGameOption opt = SOCGameOption.getOption(strs.get(i), false);
                if (opt != null)
                {
                    final String desc = strs.get(i + 1);
                    if ((desc != null) && (desc.length() > 0))
                        opt.setDesc(desc);
                }
            }

        }
        else if (type.equals(SOCLocalizedStrings.TYPE_SCENARIO))
        {
            localizeGameScenarios
                (strs, true, mes.isFlagSet(SOCLocalizedStrings.FLAG_SENT_ALL), isPractice);
        }
        else
        {
            System.err.println("L4916: Unknown localized string type " + type);
        }
    }

    /**
     * Updated scenario info.
     * Added 2015-09-21 for v2.0.00.
     * @param isPractice  Is the server {@link ClientNetwork#practiceServer}, not remote?
     */
    private void handleSCENARIOINFO(final SOCScenarioInfo mes, final boolean isPractice)
    {
        ServerGametypeInfo opts;
        if (isPractice)
            opts = practiceServGameOpts;
        else
            opts = tcpServGameOpts;

        if (mes.noMoreScens)
        {
            synchronized (opts)
            {
                opts.allScenStringsReceived = true;
                opts.allScenInfoReceived = true;
            }
        } else {
            final String scKey = mes.getScenarioKey();

            if (mes.isKeyUnknown)
                SOCScenario.removeUnknownScenario(scKey);
            else
                SOCScenario.addKnownScenario(mes.getScenario());

            synchronized (opts)
            {
                opts.scenKeys.add(scKey);  // OK if was already present from received localized strings
            }
        }
    }

    /**
     * handle the "player stats" message
     * @since 1.1.09
     */
    private void handlePLAYERSTATS(SOCPlayerStats mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        final int stype = mes.getStatType();
        if (stype != SOCPlayerStats.STYPE_RES_ROLL)
            return;  // not recognized in this version

        final int[] rstat = mes.getParams();

        EnumMap<PlayerClientListener.UpdateType, Integer> stats
            = new EnumMap<PlayerClientListener.UpdateType, Integer>(PlayerClientListener.UpdateType.class);
        stats.put(PlayerClientListener.UpdateType.Clay, Integer.valueOf(rstat[SOCResourceConstants.CLAY]));
        stats.put(PlayerClientListener.UpdateType.Ore, Integer.valueOf(rstat[SOCResourceConstants.ORE]));
        stats.put(PlayerClientListener.UpdateType.Sheep, Integer.valueOf(rstat[SOCResourceConstants.SHEEP]));
        stats.put(PlayerClientListener.UpdateType.Wheat, Integer.valueOf(rstat[SOCResourceConstants.WHEAT]));
        stats.put(PlayerClientListener.UpdateType.Wood, Integer.valueOf(rstat[SOCResourceConstants.WOOD]));
        if (rstat.length > SOCResourceConstants.GOLD_LOCAL)
        {
            final int n = rstat[SOCResourceConstants.GOLD_LOCAL];
            if (n != 0)
                stats.put(PlayerClientListener.UpdateType.GoldGains, Integer.valueOf(n));
        }
        pcl.playerStats(stats);
    }

    /**
     * Handle the server's debug piece placement on/off message.
     * @since 1.1.12
     */
    private final void handleDEBUGFREEPLACE(SOCDebugFreePlace mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        pcl.debugFreePlaceModeToggled(mes.getCoordinates() == 1);
    }

    /**
     * Handle server responses from the "simple request" handler.
     * @since 1.1.18
     */
    private final void handleSIMPLEREQUEST(SOCSimpleRequest mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;  // Not one of our games

        SOCDisplaylessPlayerClient.handleSIMPLEREQUEST(games, mes);  // update any game state
        pcl.simpleRequest(mes.getPlayerNumber(), mes.getRequestType(), mes.getValue1(), mes.getValue2());
    }

    /**
     * Handle "simple action" announcements from the server.
     * @since 1.1.19
     */
    private final void handleSIMPLEACTION(final SOCSimpleAction mes)
    {
        final String gaName = mes.getGame();
        PlayerClientListener pcl = clientListeners.get(gaName);
        if (pcl == null)
            return;  // Not one of our games

        final int atype = mes.getActionType();
        switch (atype)
        {
        case SOCSimpleAction.SC_PIRI_FORT_ATTACK_RESULT:
            // present the server's response to a Pirate Fortress Attack request
            pcl.scen_SC_PIRI_pirateFortressAttackResult(false, mes.getValue1(), mes.getValue2());
            break;

        case SOCSimpleAction.BOARD_EDGE_SET_SPECIAL:
            // fall through: displayless sets game data, pcl.simpleAction displays updated board layout

        case SOCSimpleAction.TRADE_PORT_REMOVED:
            SOCDisplaylessPlayerClient.handleSIMPLEACTION(games, mes);  // calls ga.removePort(..)
            // fall through so pcl.simpleAction updates displayed board

        case SOCSimpleAction.DEVCARD_BOUGHT:
            // fall through
        case SOCSimpleAction.RSRC_TYPE_MONOPOLIZED:
            pcl.simpleAction(mes.getPlayerNumber(), atype, mes.getValue1(), mes.getValue2());
            break;

        default:
            // ignore unknown types
            System.err.println
                ("handleSIMPLEACTION: Unknown type ignored: " + atype + " in game " + gaName);
        }
    }

    /**
     * Handle game server text and announcements.
     * @see #handleGAMETEXTMSG(SOCGameTextMsg)
     * @since 2.0.00
     */
    protected void handleGAMESERVERTEXT(SOCGameServerText mes)
    {
        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;

        pcl.messageReceived(null, mes.getText());
    }

    /**
     * Handle all players' dice roll result resources.  Looks up the game,
     * players gain resources, and announces results.
     * @since 2.0.00
     */
    protected void handleDICERESULTRESOURCES(final SOCDiceResultResources mes)
    {
        SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;

        SOCDisplaylessPlayerClient.handleDICERESULTRESOURCES(mes, ga, nickname, true);
        pcl.diceRolledResources(mes.playerNum, mes.playerRsrc);

        // handle total counts here, visually updating any discrepancies
        final int n = mes.playerNum.size();
        for (int i = 0; i < n; ++i)
            handlePLAYERELEMENT
                (clientListeners.get(mes.getGame()), ga, null, mes.playerNum.get(i),
                 SOCPlayerElement.SET, SOCPlayerElement.RESOURCE_COUNT, mes.playerResTotal.get(i), false);
    }

    /**
     * Handle moving a piece (a ship) around on the board.
     * @since 2.0.00
     */
    private final void handleMOVEPIECE(SOCMovePiece mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;
        SOCPlayer player = ga.getPlayer(mes.getPlayerNumber());
        pcl.playerPieceMoved(player, mes.getFromCoord(), mes.getToCoord(), mes.getPieceType());
    }

    /**
     * Handle removing a piece (a ship) from the board in certain scenarios.
     * @since 2.0.00
     */
    private final void handleREMOVEPIECE(SOCRemovePiece mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;
        SOCPlayer player = ga.getPlayer(mes.getParam1());
        pcl.playerPieceRemoved(player, mes.getParam3(), mes.getParam2());
    }

    /**
     * Reveal a hidden hex on the board.
     * @since 2.0.00
     */
    protected void handleREVEALFOGHEX(final SOCRevealFogHex mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games
        if (! ga.hasSeaBoard)
            return;  // should not happen

        ga.revealFogHiddenHex(mes.getParam1(), mes.getParam2(), mes.getParam3());

        PlayerClientListener pcl = clientListeners.get(gaName);
        if (pcl == null)
            return;  // Not one of our games
        pcl.boardUpdated();
    }

    /**
     * Update a village piece's value on the board (cloth remaining) in _SC_CLVI,
     * or a pirate fortress's strength in _SC_PIRI.
     * @since 2.0.00
     */
    protected void handlePIECEVALUE(final SOCPieceValue mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games
        if (! ga.hasSeaBoard)
            return;  // should not happen

        final int coord = mes.getParam2();
        final int pv = mes.getParam3();
        SOCPlayingPiece updatePiece = null;  // if not null, call pcl.pieceValueUpdated

        if (ga.isGameOptionSet(SOCGameOption.K_SC_CLVI))
        {
            SOCVillage vi = ((SOCBoardLarge) (ga.getBoard())).getVillageAtNode(coord);
            if (vi != null)
            {
                vi.setCloth(pv);
                updatePiece = vi;
            }
        }
        else if (ga.isGameOptionSet(SOCGameOption.K_SC_PIRI))
        {
            SOCFortress fort = ga.getFortress(coord);
            if (fort != null)
            {
                fort.setStrength(pv);
                updatePiece = fort;
            }
        }

        if (updatePiece != null)
        {
            PlayerClientListener pcl = clientListeners.get(gaName);
            if (pcl != null)
                pcl.pieceValueUpdated(updatePiece);
        }
    }

    /**
     * Text that a player has been awarded Special Victory Point(s).
     * The server will also send a {@link SOCPlayerElement} with the SVP total.
     * Also sent for each player's SVPs when client is joining a game in progress.
     * @since 2.0.00
     */
    protected void handleSVPTEXTMSG(final SOCSVPTextMessage mes)
    {
        final String gaName = mes.getGame();
        SOCGame ga = games.get(gaName);
        if (ga == null)
            return;  // Not one of our games
        final SOCPlayer pl = ga.getPlayer(mes.pn);
        if (pl == null)
            return;
        pl.addSpecialVPInfo(mes.svp, mes.desc);
        PlayerClientListener pcl = clientListeners.get(gaName);
        if (pcl == null)
            return;
        pcl.playerSVPAwarded(pl, mes.svp, mes.desc);
    }

    /**
     * Update player inventory. Refresh our display. If it's a reject message, give feedback to the user.
     * @since 2.0.00
     */
    private void handleINVENTORYITEMACTION(final SOCInventoryItemAction mes)
    {
        final boolean isReject = SOCDisplaylessPlayerClient.handleINVENTORYITEMACTION
            (games, (SOCInventoryItemAction) mes);

        PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;

        if (isReject)
        {
            pcl.invItemPlayRejected(mes.itemType, mes.reasonCode);
        } else {
            SOCGame ga = games.get(mes.getGame());
            if (ga != null)
            {
                final SOCPlayer pl = ga.getPlayer(mes.playerNumber);
                pcl.playerDevCardUpdated
                    (pl, (mes.action == SOCInventoryItemAction.ADD_PLAYABLE));
                if (mes.action == SOCInventoryItemAction.PLAYED)
                    pcl.playerCanCancelInvItemPlay(pl, mes.canCancelPlay);
            }
        }
    }

    /**
     * Handle the "set special item" message.
     * Calls {@link SOCDisplaylessPlayerClient#handleSETSPECIALITEM(Map, SOCSetSpecialItem)},
     * then calls {@link PlayerClientListener} to update the game display.
     *
     * @param games  Games the client is playing
     * @param mes  the message
     * @since 2.0.00
     */
    private void handleSETSPECIALITEM(final Map<String, SOCGame> games, SOCSetSpecialItem mes)
    {
        SOCDisplaylessPlayerClient.handleSETSPECIALITEM(games, (SOCSetSpecialItem) mes);

        final PlayerClientListener pcl = clientListeners.get(mes.getGame());
        if (pcl == null)
            return;

        final SOCGame ga = games.get(mes.getGame());
        if (ga == null)
            return;

        final String typeKey = mes.typeKey;
        final int gi = mes.gameItemIndex, pi = mes.playerItemIndex, pn = mes.playerNumber;
        final SOCPlayer pl = ((pn != -1) && (pi != -1)) ? ga.getPlayer(pn) : null;

        switch (mes.op)
        {
        case SOCSetSpecialItem.OP_SET:
            // fall through
        case SOCSetSpecialItem.OP_CLEAR:
            pcl.playerSetSpecialItem(typeKey, ga, pl, gi, pi, (mes.op == SOCSetSpecialItem.OP_SET));
            break;

        case SOCSetSpecialItem.OP_PICK:
            // fall through
        case SOCSetSpecialItem.OP_DECLINE:
            pcl.playerPickSpecialItem(typeKey, ga, pl, gi, pi, (mes.op == SOCSetSpecialItem.OP_PICK),
                mes.coord, mes.level, mes.sv);
            break;
        }
    }

    }  // nested class MessageTreater


    /**
     * for stand-alones
     */
    public static void usage()
    {
        System.err.println("usage: java soc.client.SOCPlayerClient [<host> <port>]");
    }

    /**
     * for stand-alones
     */
    public static void main(String[] args)
    {
        SwingMainDisplay mainDisplay = null;
        SOCPlayerClient client = null;

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
        mainDisplay = new SwingMainDisplay((args.length == 0), client);
        client.setMainDisplay(mainDisplay);

        JFrame frame = new JFrame(client.strings.get("pcli.main.title", Version.version()));  // "JSettlers client {0}"
        frame.setBackground(JSETTLERS_BG_GREEN);
        frame.setForeground(Color.black);
        // Add a listener for the close event
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(mainDisplay.createWindowAdapter());

        mainDisplay.initVisualElements(); // after the background is set

        frame.add(mainDisplay, BorderLayout.CENTER);
        frame.setLocationByPlatform(true);
        frame.setSize(650, 400);
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
