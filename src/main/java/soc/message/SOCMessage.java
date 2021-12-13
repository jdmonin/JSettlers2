/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2021 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017-2018 Strategic Conversation (STAC Project) https://www.irit.fr/STAC/
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
package soc.message;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;


/**
 * Messages used for game data, events, and chatting on a channel.
 *<P>
 * Text announcements ({@link SOCGameServerText} or {@link SOCGameTextMsg})
 * are often sent after data messages. Bots ignore text messages except for
 * a few bot-debug commands.
 *<P>
 * A list of basic game actions and their message sequences is in
 * {@code /doc/Message-Sequences-for-Game-Actions.md}.
 *
 *<H3>Implementation:</H3>
 * No {@code Object}s, only strings and integers, are to be sent over the network
 * between servers and clients!  Your game's code must guarantee that no string
 * sent contains a separator character ({@link #sep_char} or {@link #sep2_char}).
 * To help with this, use {@link #isSingleLineAndSafe(String)}.
 * Keeping the network protocol simple helps with interoperability
 * between different versions and implementations.
 *<P>
 * The message data is sent over the network as type ID + data strings
 * built by each SOCMessage subclass's toCmd() method.
 * This is sent over TCP using {@link java.io.DataOutputStream#writeUTF(String)}.
 * Server-launched local AI bots use {@link soc.server.genericServer.StringConnection}
 * instead of TCP, and skip the UTF encoding/decoding overhead.
 *<P>
 * On the remote end, it's reconstructed to a new instance of the
 * appropriate SOCMessage subclass, by the subclass' required method
 * static SOCMessageSubclass parseDataStr(String).
 * parseDataStr is called from {@link #toMsg(String)} in this class.
 * Remote TCP clients receive data using {@link java.io.DataInputStream#readUTF()}.
 *<P>
 * The client receives messages in {@link soc.client.MessageHandler#handle(SOCMessage, boolean)}.
 * The server receives messages in
 * {@link soc.server.SOCMessageDispatcher#dispatch(SOCMessage, soc.server.genericServer.Connection)}.
 *
 *<H3>Human-readable format:</H3>
 * For debugging purposes, {@link #toString()} should include all fields sent over the network
 * in a human-readable form. That's also a useful format if messages are being written to a log
 * that someone might want to read and interpret later. For documentation, we recommend including the current
 * {@link soc.util.Version#versionNumber()} or a {@link SOCVersion} message near the start of such a log.
 *<P>
 * Starting in v2.5.00, the {@code toString()} form must be parsable back into {@code SOCMessage}
 * through {@link #parseMsgStr(String)}. This "round-trip" parsing is useful for third-party projects
 * which wrote human-readable message logs and want to interpret or replay them later.
 * See that method's javadoc for details.
 *
 *<H3>To create and add a new message type:</H3>
 *<UL>
 * <LI> Decide on the message type name.
 *      Make sure the new name has never been used (check {@link #MESSAGE_RENAME_MAP}).
 *      Add to the end of the constant list in this class.
 *      Add a comment to note the JSettlers version in which it was introduced, and the date.
 * <LI> If the new message is for something that any kind of game can use,
 *      give it the next available type ID number in the list (1xxx).
 *      If the message is specific to the JSettlers game and its interface,
 *      use a message number above 10000.  The intention is that other kinds of games
 *      can be played eventually within this server framework.
 *      For message types added during a fork or third-party work, use the 2xxx range;
 *      that range won't be used by the JSettlers core itself.
 * <LI> Add it to the switch in {@link #toMsg(String)}.  Again, note the version with a comment.
 *      In the switch you will call <tt>yourMessageType.parseDataStr(data)</tt>.
 *      If your message class extends {@link SOCMessageTemplateMs} or {@link SOCMessageTemplateMi},
 *      instead call <tt>yourMessageType.parseDataStr(multiData)</tt>:
 *      for details see {@link SOCMessageMulti} class javadoc.
 * <LI> If the message contains a game name, your new class must implement {@link SOCMessageForGame}.
 * <LI> If the message can be sent to server from clients which haven't yet authenticated,
 *      must implement marker {@link SOCMessageFromUnauthClient}.
 * <LI> Extend the SOCMessage class or a template class, including the required parseDataStr method.
 *      ({@link SOCRevealFogHex} and {@link SOCSetTurn} are good example subclasses.)
 *      Template parent-classes can help; the example subclasses extend them.
 *      Be sure to override the minimum version reported in {@link #getMinimumVersion()}.
 *      Set <tt>serialVersionUID</tt> to the version it's added in.
 *      for example, if adding for version 1.1.09:
 *      <code> private static final long serialVersionUID = 1109L;</code>
 * <LI> Add to the switch in SOCPlayerClient.treat and/or SOCServerMessageHandler.dispatch
 *      or its game type's GameMessageHandler.dispatch.  Note the JSettlers version with a comment.
 *      <P>
 *      <em>Note:</em> Most things added to SOCPlayerClient.treat should also be added to
 *      {@link soc.baseclient.SOCDisplaylessPlayerClient#treat(SOCMessage)}. If robots
 *      should react, also add to {@link soc.robot.SOCRobotClient#treat(SOCMessage)}
 *      and maybe also {@link soc.robot.SOCRobotBrain#run()}.
 *      <P>
 *      If the message is player-state related, you might also want to add
 *      it in your game type's <tt>soc.server.GameHandler.sitDown_sendPrivateInfo()</tt>.
 * <LI> If the {@link #toString()} form has fields that can't be automatically parsed by {@link #parseMsgStr(String)},
 *      such as hex values or int constant strings, write a {@code stripAttribNames(..)} method to help: See
 *      {@code parseMsgStr(..)} javadocs for details.
 * <LI> Add it to unit test {@code soctest.message.TestToCmdToStringParse}'s {@code TOCMD_TOSTRING_COMPARES} array
 *</UL>
 *
 *<H3>Backwards compatibility:</H3>
 * Unknown message types are ignored by client and by server:
 * They are returned as {@code null} from {@link #toMsg(String)} if the local copy
 * (the old version's code) of SOCMessage doesn't know that message type.
 *
 *<H3>Changing or adding to a message type:</H3>
 * It's sometimes useful to add new fields to a message, to support new features
 * or optimize the message stream.
 *<P>
 * It's important that previous JSettlers versions are still able to parse the message
 * when it includes its new field(s), unless the addition supports a feature which isn't compatible with
 * those previous versions. Try to avoid adding code to the server/client
 * to send different formats of the same message to different versions.
 * Compatibility is made easier because most messages' {@code parseDataStr} methods will
 * ignore extra fields and/or were designed to be extensible by using field markers, length
 * prefixes, etc.
 *
 *<H3>Renaming a message or improving its {@link #toString()} form:</H3>
 * For debugging purposes, it's sometimes useful to make the output of {@link #toString()} more meaningful:
 * Translating integer field values like {@code pieceType} to their declared constant names, etc.
 *<P>
 * In versions after 2.5.00: If you must make an incompatible change to a message's toString form,
 * and a previous version's {@link #parseMsgStr(String)} wouldn't be able to parse that new form,
 * rename the message class and make sure the old name can still be parsed with its old format
 * (see {@link #MESSAGE_RENAME_MAP}, write a static {@code stripAttribNames(messageTypeName, messageStrParams)}, etc.)
 * Don't change the message {@link #getType()} constant's numeric value.
 *
 *<H3>Format:</H3>
 * For most messages, at most one {@link #sep} token per message, which separates its {@link #getType()} number
 * from the message data; multiple SEP2 are allowed after SEP.
 * For multi-messages, multiple SEP are allowed; see {@link SOCMessageMulti}.
 * Some message types allow blank fields; these must use a token like {@link #EMPTYSTR}
 * to avoid adjacent field separators.
 *
 * @author Robert S Thomas
 */
public abstract class SOCMessage implements Serializable, Cloneable
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Placeholder token to represent a null or empty string over the network
     * to avoid 2 adjacent field-delimiter characters, used in
     * some message types: A single tab {@code "\t"}.
     *<P>
     * If using this token for a new message type's field(s), be sure
     * the token could never be a valid value for the field. To help
     * with this, the token value fails {@link #isSingleLineAndSafe(String)}.
     *
     * @see #GAME_NONE
     * @see SOCMessageTemplateMs#parseData_FindEmptyStrs(java.util.List)
     * @since 2.0.00
     */
    public static final String EMPTYSTR = "\t";

    /**
     * message type IDs.
     * This list of constants does not provide javadocs, instead please see
     * the SOCMessage subclass for the message type.
     * Example: For {@link #DELETEGAME}, see javadocs for {@link SOCDeleteGame}.
     */

    /**
     * Authentication request, to do so without creating or joining a game or channel; see {@link SOCAuthRequest}.
     * @since 1.1.19
     */
    public static final int AUTHREQUEST = 999;

    /**
     * Null message type; {@link #toMsg(String)} returns {@code null} for this type.  There is
     * no constructor for this message type: For a trivial message see {@link SOCServerPing} instead.
     */
    public static final int NULLMESSAGE = 1000;

    /** {@link SOCNewChannel}: A new channel has been created. */
    public static final int NEWCHANNEL = 1001;
    /**
     * {@link SOCChannelMembers}: List of one channel's members.
     * Before v2.0.00 this constant was {@code MEMBERS}.
     */
    public static final int CHANNELMEMBERS = 1002;
    /** {@link SOCChannels}: List of all chat channel names. */
    public static final int CHANNELS = 1003;
    /**
     * {@link SOCJoinChannel}: Join or create a chat channel.
     * Before v2.0.00 this constant was {@code JOIN}.
     */
    public static final int JOINCHANNEL = 1004;
    /**
     * {@link SOCChannelTextMsg}: A text message in a channel.
     * Before v2.0.00 this constant was {@code TEXTMSG}.
     */
    public static final int CHANNELTEXTMSG = 1005;
    /**
     * {@link SOCLeaveChannel}: Leaving a channel.
     * Before v2.0.00 this constant was {@code LEAVE}.
     */
    public static final int LEAVECHANNEL = 1006;
    /** {@link SOCDeleteChannel}: Deleting a channel. */
    public static final int DELETECHANNEL = 1007;
    /** {@link SOCLeaveAll}: Leaving all games and chat channels. */
    public static final int LEAVEALL = 1008;

    public static final int PUTPIECE = 1009;

    /** {@link SOCGameTextMsg} - Game text from players.
     *<P>
     * Before v2.0.00, messages from the server also used this type.
     * In 2.0.00 and later, text from the server is {@link #GAMESERVERTEXT} instead.
     */
    public static final int GAMETEXTMSG = 1010;

    /** {@link SOCLeaveGame}: Leaving a game. */
    public static final int LEAVEGAME = 1011;
    /** {@link SOCSitDown}: Taking a seat at specific position in a game. */
    public static final int SITDOWN = 1012;
    /**
     * {@link SOCJoinGame}: Joining game's members as a player or observer,
     * or requesting new game creation having no game options.
     * See {@link #NEWGAMEWITHOPTIONSREQUEST}.
     */
    public static final int JOINGAME = 1013;
    /** {@link SOCBoardLayout}: Board layout info (classic 4-player board). See {@link #BOARDLAYOUT2}. */
    public static final int BOARDLAYOUT = 1014;
    /** {@link SOCDeleteGame}: Game has been destroyed. */
    public static final int DELETEGAME = 1015;
    /** {@link SOCNewGame}: New game has been created; see {@link #NEWGAMEWITHOPTIONS}. */
    public static final int NEWGAME = 1016;
    /** {@link SOCGameMembers}: List of all members of a game; used as a signal all game details have been sent. */
    public static final int GAMEMEMBERS = 1017;
    public static final int STARTGAME = 1018;
    /** {@link SOCGames}: List of all game names; see {@link #GAMESWITHOPTIONS}. */
    public static final int GAMES = 1019;
    /**
     * {@link SOCJoinChannelAuth}: Your client is authorized to join a channel.
     * Before v2.0.00 this constant was {@code JOINAUTH}.
     */
    public static final int JOINCHANNELAUTH = 1020;
    /** {@link SOCJoinGameAuth}: Your client is authorized to join a game. */
    public static final int JOINGAMEAUTH = 1021;

    public static final int IMAROBOT = 1022;

    /**
     * {@link SOCBotJoinGameRequest}: Ask a robot client to join a game.
     * Was JOINGAMEREQUEST before v2.0.00.
     */
    public static final int BOTJOINGAMEREQUEST = 1023;

    public static final int PLAYERELEMENT = 1024;
    public static final int GAMESTATE = 1025;
    public static final int TURN = 1026;
    // public static final int SETUPDONE = 1027;   // unused; SOCSetupDone removed in v2.0.00 cleanup
    public static final int DICERESULT = 1028;
    public static final int DISCARDREQUEST = 1029;
    public static final int ROLLDICEREQUEST = 1030;
    public static final int ROLLDICE = 1031;
    public static final int ENDTURN = 1032;
    public static final int DISCARD = 1033;
    public static final int MOVEROBBER = 1034;
    public static final int CHOOSEPLAYER = 1035;
    public static final int CHOOSEPLAYERREQUEST = 1036;
    public static final int REJECTOFFER = 1037;
    public static final int CLEAROFFER = 1038;
    public static final int ACCEPTOFFER = 1039;
    public static final int BANKTRADE = 1040;
    public static final int MAKEOFFER = 1041;
    public static final int CLEARTRADEMSG = 1042;
    public static final int BUILDREQUEST = 1043;
    public static final int CANCELBUILDREQUEST = 1044;
    /**
     * {@link SOCBuyDevCardRequest} message. Before v2.0.00
     * this type was {@code BUYCARDREQUEST} (class {@code SOCBuyCardRequest}).
     */
    public static final int BUYDEVCARDREQUEST = 1045;
    /** {@link SOCDevCardAction} message; before v2.0.00, this type was {@code DEVCARD} (class name {@code SOCDevCard}). */
    public static final int DEVCARDACTION = 1046;
    public static final int DEVCARDCOUNT = 1047;
    public static final int SETPLAYEDDEVCARD = 1048;
    public static final int PLAYDEVCARDREQUEST = 1049;
    /** {@link SOCPickResources} message; before v2.0.00, this was {@code DISCOVERYPICK} (class {@code SOCDiscoveryPick)}. */
    public static final int PICKRESOURCES = 1052;
    /**
     * {@link SOCPickResourceType} message. Before v2.0.00
     * this type was {@code MONOPOLYPICK} (class {@code SOCMonopolyPick}).
     */
    public static final int PICKRESOURCETYPE = 1053;
    public static final int FIRSTPLAYER = 1054;
    public static final int SETTURN = 1055;
    public static final int ROBOTDISMISS = 1056;
    public static final int POTENTIALSETTLEMENTS = 1057;
    public static final int CHANGEFACE = 1058;
    public static final int REJECTCONNECTION = 1059;
    public static final int LASTSETTLEMENT = 1060;
    public static final int GAMESTATS = 1061;
    public static final int BCASTTEXTMSG = 1062;
    public static final int RESOURCECOUNT = 1063;
    public static final int ADMINPING = 1064;
    public static final int ADMINRESET = 1065;
    public static final int LONGESTROAD = 1066;
    public static final int LARGESTARMY = 1067;
    public static final int SETSEATLOCK = 1068;
    public static final int STATUSMESSAGE = 1069;
    public static final int CREATEACCOUNT = 1070;
    public static final int UPDATEROBOTPARAMS = 1071;
    public static final int ROLLDICEPROMPT = 1072;     // autoroll, 20071003, sf patch #1812254, v1.1.00
    public static final int RESETBOARDREQUEST = 1073;  // resetboard, 20080217, v1.1.00
    public static final int RESETBOARDAUTH = 1074;     // resetboard, 20080217, v1.1.00
    public static final int RESETBOARDVOTEREQUEST = 1075; // resetboard, 20080223, v1.1.00
    public static final int RESETBOARDVOTE = 1076;     // resetboard, 20080223, v1.1.00
    public static final int RESETBOARDREJECT = 1077;   // resetboard, 20080223, v1.1.00

    /**
     * {@link SOCNewGameWithOptionsRequest}: Requesting creation of a new game
     * having game options; replaces {@link #JOINGAME} for game creation.
     * @since 1.1.07
     */
    public static final int NEWGAMEWITHOPTIONSREQUEST = 1078;  // gameoptions, 20090601

    /**
     * {@link SOCNewGameWithOptions}: New game has been created; see {@link #NEWGAME}.
     * @since 1.1.07
     */
    public static final int NEWGAMEWITHOPTIONS = 1079;  // gameoptions, 20090601

    /** @since 1.1.07 */
    public static final int GAMEOPTIONGETDEFAULTS = 1080;  // gameoptions, 20090601

    /** @since 1.1.07 */
    public static final int GAMEOPTIONGETINFOS = 1081;  // gameoptions, 20090601

    /** @since 1.1.07 */
    public static final int GAMEOPTIONINFO = 1082;  // gameoptions, 20090601

    /**
     * {@link SOCGamesWithOptions}: List of all games and their options; see {@link #GAMES}.
     * @since 1.1.07
     */
    public static final int GAMESWITHOPTIONS = 1083;  // gameoptions, 20090601

    /**
     * {@link SOCBoardLayout2}: Board layout info (6-player or other new layout).
     * See {@link #BOARDLAYOUT}.
     * @since 1.1.08
     */
    public static final int BOARDLAYOUT2 = 1084;  // 6-player board, 20091104

    /** @since 1.1.09 */
    public static final int PLAYERSTATS = 1085;  // per-player statistics, 20100312, v1.1.09

    /** @since 1.1.09 */
    public static final int PLAYERELEMENTS = 1086;  // multiple PLAYERELEMENT, 20100313, v1.1.09

    /** @since 1.1.12 */
    public static final int DEBUGFREEPLACE = 1087;  // debug piece Free Placement, 20110104, v1.1.12

    /** @since 1.1.13 */
    public static final int TIMINGPING = 1088;  // robot timing ping, 20111011, v1.1.13

    /** {@link SOCSimpleRequest} - Generic message type for simple requests by players.
     *  @since 1.1.18 */
    public static final int SIMPLEREQUEST = 1089;  // simple player requests, 20130217, v1.1.18

    /** {@link SOCSimpleAction} - Generic message type for simple actions for players or for the game.
     *  @since 1.1.19 */
    public static final int SIMPLEACTION = 1090;  // simple player actions, 20130904, v1.1.19

    /** {@link SOCGameServerText} - Game text announcements from the server.
     *<P>
     * Before v2.0.00, server text announcements were sent as {@link #GAMETEXTMSG} just like player chat messages.
     * @since 2.0.00
     */
    public static final int GAMESERVERTEXT = 1091;  // game server text, 20130905; v2.0.00

    /**
     * {@link SOCDiceResultResources} - All resources gained by players from a dice roll.
     *<P>
     * Before v2.0.00, these were sent as {@link #PLAYERELEMENT SOCPlayerElement(GAIN)} and {@link #GAMETEXTMSG}.
     * @since 2.0.00
     */
    public static final int DICERESULTRESOURCES = 1092;  // dice roll result resources, 20130920; v2.0.00

    /** {@link SOCMovePiece} - Move a piece to another location.
     *  @since 2.0.00 */
    public static final int MOVEPIECE = 1093;  // move piece, 20111203, v2.0.00

    /** {@link SOCRemovePiece} - Remove a piece from the board; currently used only with ships.
     *  @since 2.0.00 */
    public static final int REMOVEPIECE = 1094;  // pirate islands scenario, 20130218, v2.0.00

    /** {@link SOCPieceValue} - Update the value field(s) of a piece on the board.
     *  @since 2.0.00 */
    public static final int PIECEVALUE = 1095;  // cloth villages scenario, 20121115, v2.0.00

    /** {@link SOCGameElements} - Update the value(s) of game status field(s).
     *  @since 2.0.00 */
    public static final int GAMEELEMENTS = 1096;  // message sequence refactoring for v3 prep, 20171223, v2.0.00

    /** {@link SOCSVPTextMessage} - Text that a player has been awarded Special Victory Point(s).
     *  The server will also send a {@link SOCPlayerElement} with the SVP total.
     *  @since 2.0.00 */
    public static final int SVPTEXTMSG = 1097;  // SVP text messages, 20121221, v2.0.00

    /** {@link SOCInventoryItemAction} - Add or remove a {@code SOCInventoryItem}
     *  (excluding {@code SOCDevCard}s) from a player's inventory.
     *  Used in some game scenarios.
     * @see #DEVCARDACTION
     * @since 2.0.00 */
    public static final int INVENTORYITEMACTION = 1098;  // player inventory items, 20131126, v2.0.00

    /** {@link SOCSetSpecialItem} - Special Item requests and change announcements.
     *  {@code SOCSpecialItem}s are used in some game scenarios.
     *  @since 2.0.00 */
    public static final int SETSPECIALITEM = 1099;  // Special Items, 20140416, v2.0.00

    /** {@link SOCLocalizedStrings} - Localized i18n strings for items such as game options or scenarios.
     *  @since 2.0.00 */
    public static final int LOCALIZEDSTRINGS = 1100;  // Localized strings, 20150111, v2.0.00

    /** {@link SOCScenarioInfo} - Client's request about available {@link soc.game.SOCScenario SOCScenario}s,
     *  or server's reply about a single scenario.
     * @since 2.0.00
     */
    public static final int SCENARIOINFO = 1101;    // Scenario info, 20150920, v2.0.00

    /**
     * {@link SOCRobberyResult} - Info reported from server about a robbery's perpetrator, victim, and what was stolen.
     * @since 2.5.00
     */
    public static final int ROBBERYRESULT = 1102;  // Report robbery result, 20200915, v2.5.00

    /**
     * {@link SOCBotGameDataCheck} - Check if all bots still have an accurate copy of various game data.
     * @since 2.5.00
     */
    public static final int BOTGAMEDATACHECK = 1103;  // Bot game data consistency check, 20210930, v2.5.00

    /**
     * {@link SOCDeclinePlayerRequest} - Decline a player's request or requested action.
     * @since 2.5.00
     */
    public static final int DECLINEPLAYERREQUEST = 1104;  // Decline player's request, 20211208, v2.5.00

    /////////////////////////////////////////
    // REQUEST FOR FUTURE MESSAGE NUMBERS: //
    /////////////////////////////////////////
    // Gametype-specific messages (jsettlers) above 10000;
    // messages applicable to any game (game options, move piece, etc) in current low-1000s range.
    // Third-party/project-fork message types in 2000s range.
    // Please see class javadoc.
    /////////////////////////////////////////

    /** @since 1.1.00 */
    public static final int VERSION = 9998;   // cli-serv versioning, 20080807, v1.1.00

    public static final int SERVERPING = 9999;  // available in all versions

    //////////////////////////////////////////////
    // GAMETYPE-SPECIFIC MESSAGES for JSettlers //
    //////////////////////////////////////////////

    /**
     * {@link SOCRevealFogHex} - Reveal a hidden hex on the board.
     * @since 2.0.00
     */
    public static final int REVEALFOGHEX = 10001;  // fog hexes, 20121108, v2.0.00



    /**
     * Token separators. At most one SEP per message; multiple SEP2 are allowed after SEP.
     * For multi-messages, multiple SEP are allowed; see {@link SOCMessageMulti}.
     * SEP is "|".
     * @see #sep_char
     * @see #sepRE
     */
    public static final String sep = "|";

    /**
     * Main {@link #sep SEP} separator, in regexp form for splits and replacements.
     * @since 2.5.00
     */
    public static final String sepRE = "\\|";

    /**
     * Secondary separator token SEP2, as string. SEP2 is ",".
     * @see #sep2_char
     */
    public static final String sep2 = ",";

    /**
     * Main separator token {@link #sep}, as character. SEP is '|'.
     * @since 1.1.00
     */
    public static final char sep_char = '|';

    /**
     * Secondary separator token {@link #sep2}, as character. SEP2 is ','.
     * @since 1.1.00
     */
    public static final char sep2_char = ',';

    /**
     * "Not for any game" marker, used when any of the {@code SOCMessageTemplate*} message types
     * (which all implement {@link SOCMessageForGame}) are used for convenience for non-game messages
     * such as {@link SOCLocalizedStrings}.
     *<P>
     * No actual game, option, or scenario will ever have the same name as this marker, because the marker fails
     * {@link #isSingleLineAndSafe(String, boolean) isSingleLineAndSafe(String, false)}:
     * Marker is control character {@code ^V (SYN)}: (char) 22.
     *
     * @see #EMPTYSTR
     * @since 2.0.00
     */
    public static final String GAME_NONE = "\026";  // 0x16 ^V (SYN)

    /**
     * An ID identifying the type of message; see {@link #getType()}.
     */
    protected int messageType;

    /**
     * @return  the message type number sent over the network, such as {@link #JOINGAMEAUTH} or {@link #PUTPIECE}
     */
    public int getType()
    {
        return messageType;
    }

    /**
     * To identify new message types, give the minimum version where this
     * type is used.  Default of 1000 (version 1.0.00) unless overridden.
     *<P>
     * When overriding, write the entire method on a single line for easier
     * visibility of the version when searching the source code.
     *
     * @return Version number, as in 1006 for JSettlers 1.0.06.
     * @since 1.1.00
     */
    public int getMinimumVersion() { return 1000; }

    /**
     * To identify obsolete message types, give the maximum version where this
     * type is used.  Default (for active messages) returns {@link Integer#MAX_VALUE}.
     * @return Version number, as in 1006 for JSettlers 1.0.06, or {@link Integer#MAX_VALUE}.
     * @since 1.1.00
     */
    public int getMaximumVersion()
    {
        return Integer.MAX_VALUE;
    }

    /**
     * Converts the contents of this message into
     * a String that can be transferred by a client
     * or server. For a human-readable alternative, see {@link #toString()}.
     *<P>
     * Your message class's required method
     * {@code static SOCMessageSubclass parseDataStr(String)}
     * must be able to turn this String
     * back into an instance of the message class.
     *<P>
     * For most message types, at most one {@link #sep} token is allowed,
     * separating the type ID from the rest of the parameters.
     * For multi-messages (@link SOCMessageMulti}, multiple {@link #sep} tokens
     * are allowed.  Multi-messages are parsed with:
     * static SOCMessageSubclass parseDataStr(String[])
     *<P>
     * Overall conversion from {@code toCmd()} format is handled by {@link #toMsg(String)},
     * which checks message type ID to call the appropriate class's {@code parseDataStr}.
     */
    public abstract String toCmd();

    /**
     * Simple human-readable delimited representation, used for debug purposes:
     * {@code SOCPutPiece:game=test5|playerNumber=3|pieceType=0|coord=40a}
     *<BR>
     * Could also be used by a {@link soc.server.SOCServer#recordGameEvent(String, SOCMessage)} implementation
     * like {@link soc.extra.server.GameEventLog.EventEntry}.
     *<P>
     * Within this representation, message parameters should be in same order used by {@link #toCmd()} and
     * {@code parseDataStr(..)}. Should be parseable by {@link #parseMsgStr(String)} which calls
     * {@link #stripAttribNames(String)}, which your class can override if needed.
     * @see #toCmd()
     * @since 1.1.00
     */
    @Override
    public abstract String toString();

    /**
     * Test whether a string is non-empty and its characters are
     * all 'safe' as a single-line string:
     * No newlines or {@link Character#isISOControl(char) control characters},
     * no {@link Character#isSpaceChar(char) line separators or paragraph separators}.
     * Whitespace character type {@link Character#SPACE_SEPARATOR} is OK.
     * Must not contain {@link #sep_char} or {@link #sep2_char}.
     * @param s   string to test; if null or "", returns false.
     * @return true if all characters are OK, false otherwise.
     *            Null string or 0-length string returns false.
     * @see #isSingleLineAndSafe(String, boolean)
     * @since 1.1.07
     */
    public static final boolean isSingleLineAndSafe(String s)
    {
        return isSingleLineAndSafe(s, false);
    }

    /**
     * Variant of {@link #isSingleLineAndSafe(String)} that can optionally
     * allow {@link #sep_char} or {@link #sep2_char}.
     * See that method for other conditions checked here.
     * @param s  string to test; if null or "", returns false.
     * @param allowSepChars  If true, string can contain {@link #sep_char} or {@link #sep2_char}
     * @return true if all characters are OK, false otherwise.
     *            Null string or 0-length string returns false.
     * @since 2.0.00
     */
    public static final boolean isSingleLineAndSafe(final String s, final boolean allowSepChars)
    {
        if (s == null)
            return false;
        if ((! allowSepChars)
            && ((-1 != s.indexOf(sep_char))
                || (-1 != s.indexOf(sep2_char))))
            return false;

        int i = s.length();
        if (i == 0)
            return false;

        --i;
        for (; i>=0; --i)
        {
            final char c = s.charAt(i);
            if (Character.isISOControl(c) ||
                (Character.isSpaceChar(c) && (Character.getType(c) != Character.SPACE_SEPARATOR)))
                return false;
        }

        return true;
    }

    /**
     * Convert a string from {@link #toCmd()} into a SOCMessage.
     * The string is in the form of "id SEP messagename { SEP2 messagedata }*".
     * If the message type id is unknown, that is printed to System.err.
     * Otherwise calls message type's static {@code parseDataStr} method.
     *
     * @param s  String to convert
     * @return   converted String to a SOCMessage, or null if the string is garbled,
     *           or is an unknown command id
     */
    public static SOCMessage toMsg(String s)
    {
        try
        {
            StringTokenizer st = new StringTokenizer(s, sep);

            /**
             * get the id that identifies the type of message
             */
            int msgId = Integer.parseInt(st.nextToken());

            /**
             * get the rest of the data
             */
            String data;

            /**
             * to handle {@link SOCMessageMulti} subclasses -
             * multiple parameters with sub-fields.
             * If only 1 param is seen, {@code multiData} will be null; pass {@code data} to your parseDataStr too.
             *<P>
             * Note that if you passed a non-null gamename to the
             * {@link SOCMessageTemplateMs} or {@link SOCMessageTemplateMi} constructor,
             * then multiData[0] here will be gamename,
             * and multiData[1] == param[0] as passed to that constructor.
             *<P>
             *<H5>If your message never needs to handle exactly 1 parameter:</H5>
             *<pre>
             *     case GAMESWITHOPTIONS:
             *         return SOCGamesWithOptions.parseDataStr(multiData);
             *</pre>
             *
             *<H5>If your message might be valid with 1 parameter:</H5>
             *<pre>
             *     case GAMESWITHOPTIONS:
             *         return SOCGamesWithOptions.parseDataStr(data, multiData);
             *</pre>
             */
            ArrayList<String> multiData = null;

            try
            {
                data = st.nextToken();
                if (st.hasMoreTokens())
                {
                        // SOCMessageMulti

                        int n = st.countTokens();  // remaining (== number of parameters after "data")
                        multiData = new ArrayList<String>(n + 1);
                        multiData.add(data);
                        while (st.hasMoreTokens())
                        {
                                try {
                                        multiData.add(st.nextToken());
                                } catch (NoSuchElementException e)
                                {
                                        multiData.add(null);
                                }
                        }
                }
            }
            catch (NoSuchElementException e)
            {
                data = "";
            }

            /**
             * convert the data part and create the message
             */
            switch (msgId)
            {
            case AUTHREQUEST:        // authentication request, 20141106, v1.1.19
                return SOCAuthRequest.parseDataStr(data);

            case NULLMESSAGE:
                return null;

            case NEWCHANNEL:
                return SOCNewChannel.parseDataStr(data);

            case CHANNELMEMBERS:
                return SOCChannelMembers.parseDataStr(data);

            case CHANNELS:
                return SOCChannels.parseDataStr(data);

            case JOINCHANNEL:
                return SOCJoinChannel.parseDataStr(data);

            case CHANNELTEXTMSG:
                return SOCChannelTextMsg.parseDataStr(data);

            case LEAVECHANNEL:
                return SOCLeaveChannel.parseDataStr(data);

            case DELETECHANNEL:
                return SOCDeleteChannel.parseDataStr(data);

            case LEAVEALL:
                return SOCLeaveAll.parseDataStr(data);

            case PUTPIECE:
                return SOCPutPiece.parseDataStr(data);

            case GAMETEXTMSG:
                return SOCGameTextMsg.parseDataStr(data);

            case LEAVEGAME:
                return SOCLeaveGame.parseDataStr(data);

            case SITDOWN:
                return SOCSitDown.parseDataStr(data);

            case JOINGAME:
                return SOCJoinGame.parseDataStr(data);

            case BOARDLAYOUT:
                return SOCBoardLayout.parseDataStr(data);

            case GAMES:
                return SOCGames.parseDataStr(data);

            case DELETEGAME:
                return SOCDeleteGame.parseDataStr(data);

            case NEWGAME:
                return SOCNewGame.parseDataStr(data);

            case GAMEMEMBERS:
                return SOCGameMembers.parseDataStr(data);

            case STARTGAME:
                return SOCStartGame.parseDataStr(data);

            case JOINCHANNELAUTH:
                return SOCJoinChannelAuth.parseDataStr(data);

            case JOINGAMEAUTH:
                return SOCJoinGameAuth.parseDataStr(data);

            case IMAROBOT:
                return SOCImARobot.parseDataStr(data);

            case BOTJOINGAMEREQUEST:
                return SOCBotJoinGameRequest.parseDataStr(data);

            case PLAYERELEMENT:
                return SOCPlayerElement.parseDataStr(data);

            case GAMESTATE:
                return SOCGameState.parseDataStr(data);

            case TURN:
                return SOCTurn.parseDataStr(data);

            case DICERESULT:
                return SOCDiceResult.parseDataStr(data);

            case DISCARDREQUEST:
                return SOCDiscardRequest.parseDataStr(data);

            case ROLLDICEREQUEST:
                return SOCRollDiceRequest.parseDataStr(data);

            case ROLLDICE:
                return SOCRollDice.parseDataStr(data);

            case ENDTURN:
                return SOCEndTurn.parseDataStr(data);

            case DISCARD:
                return SOCDiscard.parseDataStr(data);

            case MOVEROBBER:
                return SOCMoveRobber.parseDataStr(data);

            case CHOOSEPLAYER:
                return SOCChoosePlayer.parseDataStr(data);

            case CHOOSEPLAYERREQUEST:
                return SOCChoosePlayerRequest.parseDataStr(data);

            case REJECTOFFER:
                return SOCRejectOffer.parseDataStr(data);

            case CLEAROFFER:
                return SOCClearOffer.parseDataStr(data);

            case ACCEPTOFFER:
                return SOCAcceptOffer.parseDataStr(data);

            case BANKTRADE:
                return SOCBankTrade.parseDataStr(data);

            case MAKEOFFER:
                return SOCMakeOffer.parseDataStr(data);

            case CLEARTRADEMSG:
                return SOCClearTradeMsg.parseDataStr(data);

            case BUILDREQUEST:
                return SOCBuildRequest.parseDataStr(data);

            case CANCELBUILDREQUEST:
                return SOCCancelBuildRequest.parseDataStr(data);

            case BUYDEVCARDREQUEST:
                return SOCBuyDevCardRequest.parseDataStr(data);

            case DEVCARDACTION:
                return SOCDevCardAction.parseDataStr(data);

            case DEVCARDCOUNT:
                return SOCDevCardCount.parseDataStr(data);

            case SETPLAYEDDEVCARD:
                return SOCSetPlayedDevCard.parseDataStr(data);

            case PLAYDEVCARDREQUEST:
                return SOCPlayDevCardRequest.parseDataStr(data);

            case PICKRESOURCES:  // Discovery/Year of Plenty, or v2.0.00 gold hex resources
                return SOCPickResources.parseDataStr(data);

            case PICKRESOURCETYPE:  // Monopoly
                return SOCPickResourceType.parseDataStr(data);

            case FIRSTPLAYER:
                return SOCFirstPlayer.parseDataStr(data);

            case SETTURN:
                return SOCSetTurn.parseDataStr(data);

            case ROBOTDISMISS:
                return SOCRobotDismiss.parseDataStr(data);

            case POTENTIALSETTLEMENTS:
                return SOCPotentialSettlements.parseDataStr(data);

            case CHANGEFACE:
                return SOCChangeFace.parseDataStr(data);

            case REJECTCONNECTION:
                return SOCRejectConnection.parseDataStr(data);

            case LASTSETTLEMENT:
                return SOCLastSettlement.parseDataStr(data);

            case GAMESTATS:
                return SOCGameStats.parseDataStr(data);

            case BCASTTEXTMSG:
                return SOCBCastTextMsg.parseDataStr(data);

            case RESOURCECOUNT:
                return SOCResourceCount.parseDataStr(data);

            case ADMINPING:
                return SOCAdminPing.parseDataStr(data);

            case ADMINRESET:
                return SOCAdminReset.parseDataStr(data);

            case LONGESTROAD:
                return SOCLongestRoad.parseDataStr(data);

            case LARGESTARMY:
                return SOCLargestArmy.parseDataStr(data);

            case SETSEATLOCK:
                return SOCSetSeatLock.parseDataStr(data);

            case STATUSMESSAGE:
                return SOCStatusMessage.parseDataStr(data);

            case CREATEACCOUNT:
                return SOCCreateAccount.parseDataStr(data);

            case UPDATEROBOTPARAMS:
                return SOCUpdateRobotParams.parseDataStr(data);

            case SERVERPING:
                return SOCServerPing.parseDataStr(data);

            case ROLLDICEPROMPT:     // autoroll, 20071003, sf patch #1812254
                return SOCRollDicePrompt.parseDataStr(data);

            case RESETBOARDREQUEST:  // resetboard, 20080217, v1.1.00
                return SOCResetBoardRequest.parseDataStr(data);

            case RESETBOARDAUTH:     // resetboard, 20080217, v1.1.00
                return SOCResetBoardAuth.parseDataStr(data);

            case RESETBOARDVOTEREQUEST:  // resetboard, 20080223, v1.1.00
                return SOCResetBoardVoteRequest.parseDataStr(data);

            case RESETBOARDVOTE:     // resetboard, 20080223, v1.1.00
                return SOCResetBoardVote.parseDataStr(data);

            case RESETBOARDREJECT:   // resetboard, 20080223, v1.1.00
                return SOCResetBoardReject.parseDataStr(data);

            case VERSION:            // cli-serv versioning, 20080807, v1.1.00
                return SOCVersion.parseDataStr(data);

            case NEWGAMEWITHOPTIONS:     // per-game options, 20090601, v1.1.07
                return SOCNewGameWithOptions.parseDataStr(data);

            case NEWGAMEWITHOPTIONSREQUEST:  // per-game options, 20090601, v1.1.07
                return SOCNewGameWithOptionsRequest.parseDataStr(data);

            case GAMEOPTIONGETDEFAULTS:  // per-game options, 20090601, v1.1.07
                return SOCGameOptionGetDefaults.parseDataStr(data);

            case GAMEOPTIONGETINFOS:     // per-game options, 20090601, v1.1.07
                return SOCGameOptionGetInfos.parseDataStr(data);

            case GAMEOPTIONINFO:         // per-game options, 20090601, v1.1.07
                return SOCGameOptionInfo.parseDataStr(multiData);

            case GAMESWITHOPTIONS:       // per-game options, 20090601, v1.1.07
                return SOCGamesWithOptions.parseDataStr(multiData);

            case BOARDLAYOUT2:      // 6-player board, 20091104, v1.1.08
                return SOCBoardLayout2.parseDataStr(data);

            case PLAYERSTATS:       // per-player statistics, 20100312, v1.1.09
                return SOCPlayerStats.parseDataStr(multiData);

            case PLAYERELEMENTS:    // multiple PLAYERELEMENT, 20100313, v1.1.09
                return SOCPlayerElements.parseDataStr(multiData);

            case DEBUGFREEPLACE:    // debug piece Free Placement, 20110104, v1.1.12
                return SOCDebugFreePlace.parseDataStr(data);

            case TIMINGPING:        // robot timing ping, 20111011, v1.1.13
                return SOCTimingPing.parseDataStr(data);

            case SIMPLEREQUEST:     // simple player requests, 20130217, v1.1.18
                return SOCSimpleRequest.parseDataStr(data);

            case SIMPLEACTION:     // simple actions for players, 20130904, v1.1.19
                return SOCSimpleAction.parseDataStr(data);

            case GAMESERVERTEXT:    // game server text, 20130905; v2.0.00
                return SOCGameServerText.parseDataStr(data);

            case DICERESULTRESOURCES:  // dice roll result resources, 20130920; v2.0.00
                return SOCDiceResultResources.parseDataStr(multiData);

            case MOVEPIECE:         // move piece announcement, 20111203, v2.0.00
                return SOCMovePiece.parseDataStr(data);

            case REMOVEPIECE:       // pirate islands scenario, 20130218, v2.0.00
                return SOCRemovePiece.parseDataStr(data);

            case PIECEVALUE:        // cloth villages scenario, 20121115, v2.0.00
                return SOCPieceValue.parseDataStr(data);

            case GAMEELEMENTS:      // game status fields, 20171223, v2.0.00
                return SOCGameElements.parseDataStr(multiData);

            case SVPTEXTMSG:        // SVP text messages, 20121221, v2.0.00
                return SOCSVPTextMessage.parseDataStr(data);

            case INVENTORYITEMACTION:         // player inventory items, 20131126, v2.0.00
                return SOCInventoryItemAction.parseDataStr(data);

            case SETSPECIALITEM:       // Special Items, 20140416, v2.0.00
                return SOCSetSpecialItem.parseDataStr(data);

            case LOCALIZEDSTRINGS:     // Localized strings, 20150111, v2.0.00
                return SOCLocalizedStrings.parseDataStr(multiData);

            case SCENARIOINFO:         // Scenario info, 20150920, v2.0.00
                return SOCScenarioInfo.parseDataStr(multiData, data);

            case ROBBERYRESULT:        // Report robbery result, 20200915, v2.5.00
                return SOCRobberyResult.parseDataStr(data);

            case BOTGAMEDATACHECK:      // Bot game data consistency check, 20210930, v2.5.00
                return SOCBotGameDataCheck.parseDataStr(multiData);

            case DECLINEPLAYERREQUEST:  // Decline player's request, 20211208, v2.5.00
                return SOCDeclinePlayerRequest.parseDataStr(data);

            // gametype-specific messages:

            case REVEALFOGHEX:      // fog hexes, 20121108, v2.0.00
                return SOCRevealFogHex.parseDataStr(data);

            default:
                System.err.println("Unhandled message type in SOCMessage.toMsg: " + msgId);
                return null;
            }
        }
        catch (Exception e)
        {
            System.err.println("toMsg ERROR - " + e);
            e.printStackTrace();

            return null;
        }
    }

    /**
     * Map of renamed classes for backwards compatibility in {@link #parseMsgStr(String)}:
     * Key is old name of message type, value is new name (SOCMessage subclass).
     * See {@code parseMsgStr(..)} javadoc for more details, including expected
     * static {@code stripAttribNames(messageTypeName, messageStrParams)} method.
     * @since 2.5.00
     */
    public static final Map<String, String> MESSAGE_RENAME_MAP = new HashMap<>();
    static
    {
        for (final String[] fromTo : new String[][]
            {
                {"SOCBuyCardRequest", "SOCBuyDevCardRequest"},
                {"SOCDevCard", "SOCDevCardAction"},
                {"SOCDiscoveryPick", "SOCPickResources"},
                {"SOCJoin", "SOCJoinChannel"},
                {"SOCJoinAuth", "SOCJoinChannelAuth"},
                {"SOCJoinGameRequest", "SOCBotJoinGameRequest"},
                {"SOCLeave", "SOCLeaveChannel"},
                {"SOCMembers", "SOCChannelMembers"},
                {"SOCMonopolyPick", "SOCPickResourceType"},
                {"SOCTextMsg", "SOCChannelTextMsg"},
            })
            MESSAGE_RENAME_MAP.put(fromTo[0], fromTo[1]);
    }

    /**
     * Parse a delimited message in {@link SOCMessage#toString()} format:
     * {@code SOCPutPiece:game=test5|playerNumber=3|pieceType=0|coord=40a}
     *<BR>
     * Strips parameter/attribute names from values to get the format expected by
     * message types' {@code parseDataStr(..)}, calls that.
     *<P>
     * If {@code toString()} format has fields that can't be automatically parsed here,
     * such as hex values or int constant strings, message class should have a static {@code stripAttribNames(String)}
     * method to do so. See {@link SOCPutPiece#stripAttribNames(String)}
     * and {@link SOCVersion#stripAttribNames(String)} for simple examples.
     *<P>
     * Message types which have been renamed and noted in {@link #MESSAGE_RENAME_MAP},
     * and the old name used an incompatibly different toString format,
     * should declare a static {@code stripAttribNames(String messageTypeName, String messageStrParams)} method
     * to know whether to parse using the old or new format.
     *<P>
     * Uses reflection to call the message type's static {@code stripAttribNames(String)}
     * and {@code parseDataStr(String or List<String>)} methods
     * if available, otherwise {@link SOCMessage#stripAttribNames(String)}.
     * @param messageStr  Message as delimited string from {@link #toString()}; not null
     * @return parsed message if successful, throws exception otherwise; not null
     * @throws InputMismatchException if message can't be parsed and
     *     {@link #parseMsgStr(String)} returned null
     * @throws ParseException if message class name not parsed or class not found,
     *     reflection error (method not static, etc), or if message's
     *     {@link #stripAttribNames(String)} or {@link #parseMsgStr(String)} threw an exception
     * @since 2.5.00
     */
    public static SOCMessage parseMsgStr(final String messageStr)
        throws ParseException, InputMismatchException
    {
        // pieces[0] = classname
        // pieces[1] = params

        if (messageStr == null)
            throw new ParseException("null messageStr", 0);
        final int colonIdx = messageStr.indexOf(':');
        if (colonIdx < 1)
            throw new ParseException("Missing \"SomeMsgClassName:\" prefix", 0);
        String className = null;
        String currentCall = null;

        try
        {
            className = messageStr.substring(0, colonIdx);
            String msgBody = messageStr.substring(colonIdx+1);

            final String origClassName = className,
                renamedClassName = MESSAGE_RENAME_MAP.get(className);
            if (renamedClassName != null)
                className = renamedClassName;

            @SuppressWarnings("unchecked")
            Class<SOCMessage> c = (Class<SOCMessage>) Class.forName("soc.message." + className);

            Method m;

            // if SOCMessageMulti, look for stripAttribsToList first
            if (SOCMessageMulti.class.isAssignableFrom(c))
            {
                try
                {
                    m = c.getMethod("stripAttribsToList", String.class);
                    if (! Modifier.isStatic(m.getModifiers()))
                        throw new ParseException
                            (className + ".stripAttribsToList must be static", 0);
                    currentCall = m.getDeclaringClass().getName() + "." + "stripAttribsToList";
                    @SuppressWarnings("unchecked")
                    List<String> treatedAttribs = (List<String>) m.invoke(null, msgBody);
                    if (treatedAttribs == null)
                        throw new InputMismatchException
                            ("Unparsable message: stripAttribsToList rets null: " + messageStr);

                    try
                    {
                        m = c.getMethod("parseDataStr", List.class);
                    } catch (NoSuchMethodException e) {
                        throw new ParseException
                            (className + ".parseDataStr(List) not found", 0);
                    }
                    if (! Modifier.isStatic(m.getModifiers()))
                        throw new ParseException
                            (className + ".parseDataStr(List) must be static", 0);
                    currentCall = m.getDeclaringClass().getName() + "." + "parseDataStr";
                    Object o = m.invoke(null, treatedAttribs);
                    if (o == null)
                        throw new InputMismatchException
                            ("Unparsable message: parseDataStr(List) rets null: " + messageStr);

                    return (SOCMessage) o;
                } catch (NoSuchMethodException e) {}
            }

            String treatedAttribs = null;  // output from stripAttribNames

            try
            {
                // in case this is a renamed message type's old or new name,
                // look first for stripAttribNames(String,String) to pass message type name

                m = c.getMethod("stripAttribNames", String.class, String.class);
                currentCall = m.getDeclaringClass().getName() + "." + "stripAttribNames(String,String)";
                if (! Modifier.isStatic(m.getModifiers()))
                    throw new ParseException
                        (currentCall + " must be static", 0);
                if (! m.getReturnType().equals(String.class))
                    throw new ParseException
                        (currentCall + " must return String", 0);

                treatedAttribs = (String) m.invoke
                    (null, (origClassName != null) ? origClassName : className, msgBody);
                if (treatedAttribs == null)
                    throw new InputMismatchException
                        ("Unparsable message: stripAttribNames(String,String) rets null: " + messageStr);
            } catch (NoSuchMethodException e) {}

            if (treatedAttribs == null)
            {
                // call message class's or SOCMessage's stripAttribNames(String)

                m = c.getMethod("stripAttribNames", String.class);
                currentCall = m.getDeclaringClass().getName() + "." + "stripAttribNames(String)";
                if (! Modifier.isStatic(m.getModifiers()))
                    throw new ParseException
                        (currentCall + " must be static", 0);
                // no need to check return type, because it's declared in SOCMessage as String
                // and any change in a subclass is a syntax error

                treatedAttribs = (String) m.invoke(null,  msgBody);
            }
            if (treatedAttribs == null)
                throw new InputMismatchException
                    ("Unparsable message: stripAttribNames rets null: " + messageStr);

            m = c.getMethod("parseDataStr", String.class);
            currentCall = m.getDeclaringClass().getName() + "." + "parseDataStr";
            if (! Modifier.isStatic(m.getModifiers()))
                throw new ParseException
                    (currentCall + " must be static", 0);

            Object o = m.invoke(null, treatedAttribs);
            if (o == null)
            {
                // This occurs when a message can't be parsed.  Likely means stripAttribNames
                //  needs to be overridden.  Doesn't seem to happen for any replay-relevant messages.
                // Generally a good idea to put a breakpoint here and debug any time you handle a new
                //  log file, just in case.

                throw new InputMismatchException
                    ("Unparsable message: parseDataStr rets null: " + messageStr);
            }

            return (SOCMessage) o;
        } catch (ClassNotFoundException ex) {
            throw new ParseException
                ("Class not found" + ((className != null) ? ": " + className : "") + ": " + messageStr, 0);
        } catch (InvocationTargetException ex) {
            throw new ParseException
                ("Exception from " + currentCall + ": " + ex.getCause(), 0);
        } catch (NoSuchMethodException | SecurityException
            | IllegalAccessException | ExceptionInInitializerError ex) {
            throw new ParseException
                ("Reflection error calling " + currentCall + ": " + ex, 0);
        } catch (InputMismatchException ex) {
            throw ex;  // probably from recursive or "super" call of this method
        } catch (Exception ex) {
            throw new ParseException
                ("Exception from " + currentCall + ": " + ex, 0);
        }
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link #parseMsgStr(String)}
     * to pass to the message class's {@code parseDataStr(String)}.
     *<P>
     * Some messages will need to override this (new game with options,
     * any of the types which use hex encoding of coordinates).
     * Message types which have been renamed, and the old name used an incompatibly different toString format,
     * should instead declare a static {@code stripAttribNames(String messageTypeName, String messageStrParams)} method:
     * See {@link #parseMsgStr(String)} javadoc.
     *<P>
     * For {@link SOCMessageMulti} subclasses, use or override {@link #stripAttribsToList(String)} instead.
     *
     * @param messageStrParams Params part of a message string formatted by {@link #toString()},
     *     typically delimited by {@code '|'}; not {@code null}
     * @return Comma-delimited message parameters ({@link #sep2_char}) without attribute names,
     *     or {@code null} if params are malformed.
     *     If {@code messageStrParams} is "", returns "".
     * @see #stripAttribsToList(String)
     * @since 2.5.00
     */
    public static String stripAttribNames(String messageStrParams)
    {
        StringBuilder sb = new StringBuilder();

        for (String s : messageStrParams.split(sepRE))
        {
            int eqIdx = s.indexOf('=');
            // if no '=', appends entire string
            sb.append(s.substring(eqIdx + 1)).append(sep2_char);
        }

        return sb.substring(0, sb.length() - 1);  // omit final ','
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a list for further parsing or for
     * {@link #parseMsgStr(String)} to pass to a {@link SOCMessageMulti} message class's {@code parseDataStr(List)}.
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters to finish parsing into a SOCMessage, or {@code null} if malformed.
     *     If {@code messageStrParams} is "", returns a list with "" as its sole element.
     *     The returned List might not support optional methods like {@link List#add(int, Object)}.
     * @see #stripAttribNames(String)
     * @since 2.5.00
     */
    public static List<String> stripAttribsToList(String messageStrParams)
    {
        String[] params = messageStrParams.split(sepRE);
        for (int i = 0; i < params.length; ++i)
        {
            int eqIdx = params[i].indexOf('=');
            if (eqIdx > 0)
                params[i] = params[i].substring(eqIdx + 1);
        }

        return Arrays.asList(params);
    }

}
