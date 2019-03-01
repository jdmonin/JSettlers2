/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2018 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;


/**
 * Messages used for game data, events, and chatting on a channel.
 *<P>
 * Text announcements ({@link SOCGameServerText} or {@link SOCGameTextMsg})
 * are often sent after data messages. Bots ignore text messages except for
 * a few bot-debug commands.
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
 *<H3>To create and add a new message type:</H3>
 *<UL>
 * <LI> Decide on the message type name.  Add to the end of the constant list in this
 *      class.  Add a comment to note the JSettlers version in which it was introduced, and the date.
 * <LI> If the new message is for something that any kind of game can use,
 *      give it the next available type ID number in the list (1xxx).
 *      If the message is specific to the JSettlers game and its interface,
 *      use a message number above 10000.  The intention is that other kinds of games
 *      can be played eventually within this server framework.
 * <LI> Add it to the switch in {@link #toMsg(String)}.  Again, note the version with a comment.
 *      In the switch you will call <tt>yourMessageType.parseDataStr(data)</tt>.
 *      If your message class extends {@link SOCMessageTemplateMs} or {@link SOCMessageTemplateMi},
 *      instead call <tt>yourMessageType.parseDataStr(multiData)</tt>:
 *      for details see {@link SOCMessageMulti} class javadoc.
 * <LI> If the message contains a game name, your new class must implement {@link SOCMessageForGame}.
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
 *</UL>
 *
 *<H3>Backwards compatibility:</H3>
 * Unknown message types are ignored by client and by server:
 * They are returned as {@code null} from {@link #toMsg(String)} if the local copy
 * (the old version's code) of SOCMessage doesn't know that message type.
 *
 *<H3>Format:</H3>
 * For most messages, at most one {@link #sep} token per message, which separates the messagetype number
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
    public static final int ROLLDICEPROMPT = 1072;     // autoroll, 20071003, sf patch #1812254
    public static final int RESETBOARDREQUEST = 1073;  // resetboard, 20080217, sf patch#tbd
    public static final int RESETBOARDAUTH = 1074;     // resetboard, 20080217, sf patch#tbd
    public static final int RESETBOARDVOTEREQUEST = 1075; // resetboard, 20080223, sf patch#tbd
    public static final int RESETBOARDVOTE = 1076;     // resetboard, 20080223, sf patch#tbd
    public static final int RESETBOARDREJECT = 1077;   // resetboard, 20080223, sf patch#tbd

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

    /** {@link SOCRevealFogHex} - Reveal a hidden hex on the board.
     *  @since 2.0.00 */
    public static final int REVEALFOGHEX = 1097;  // fog hexes, 20121108, v2.0.00

    /** {@link SOCLegalEdges} - Legal road or ship edges for the large sea board.
     *  @since 2.0.00 */
    public static final int LEGALEDGES = 1098;  // large sea board, 20121216, v2.0.00

    /** {@link SOCSVPTextMessage} - Text that a player has been awarded Special Victory Point(s).
     *  The server will also send a {@link SOCPlayerElement} with the SVP total.
     *  @since 2.0.00 */
    public static final int SVPTEXTMSG = 1099;  // SVP text messages, 20121221, v2.0.00

    /** {@link SOCInventoryItemAction} - Add or remove a {@code SOCInventoryItem}
     *  (excluding {@code SOCDevCard}s) from a player's inventory.
     *  Used in some game scenarios.
     * @see #DEVCARDACTION
     * @since 2.0.00 */
    public static final int INVENTORYITEMACTION = 1100;  // player inventory items, 20131126, v2.0.00

    /** {@link SOCSetSpecialItem} - Special Item requests and change announcements.
     *  {@code SOCSpecialItem}s are used in some game scenarios.
     *  @since 2.0.00 */
    public static final int SETSPECIALITEM = 1101;  // Special Items, 20140416, v2.0.00

    /** {@link SOCLocalizedStrings} - Localized i18n strings for items such as game options or scenarios.
     *  @since 2.0.00 */
    public static final int LOCALIZEDSTRINGS = 1102;  // Localized strings, 20150111, v2.0.00

    /** {@link SOCScenarioInfo} - Client's request about available {@link soc.game.SOCScenario SOCScenario}s,
     *  or server's reply about a single scenario.
     * @since 2.0.00
     */
    public static final int SCENARIOINFO = 1103;    // Scenario info, 20150920, v2.0.00


    /////////////////////////////////////////
    // REQUEST FOR FUTURE MESSAGE NUMBERS: //
    /////////////////////////////////////////
    // Gametype-specific messages (jsettlers) above 10000;
    // messages applicable to any game (game options, move piece, etc) in current low-1000s range.
    // Please see class javadoc.
    /////////////////////////////////////////


    /** @since 1.1.00 */
    public static final int VERSION = 9998;   // cli-serv versioning, 20080807, v1.1.00

    public static final int SERVERPING = 9999;  // available in all versions


    /**
     * Token separators. At most one SEP per message; multiple SEP2 are allowed after SEP.
     * For multi-messages, multiple SEP are allowed; see {@link SOCMessageMulti}.
     * SEP is "|".
     * @see #sep_char
     */
    public static final String sep = "|";

    /**
     * secondary separator token SEP2, as string. SEP2 is ",".
     * @see #sep2_char
     */
    public static final String sep2 = ",";

    /** main separator token {@link #sep}, as character. SEP is '|'. */
    public static final char sep_char = '|';

    /** secondary separator token {@link #sep2}, as character. SEP2 is ','. */
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
     * An ID identifying the type of message
     */
    protected int messageType;

    /**
     * @return  the message type
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
     */
    public int getMinimumVersion() { return 1000; }

    /**
     * To identify obsolete message types, give the maximum version where this
     * type is used.  Default (for active messages) returns {@link Integer#MAX_VALUE}.
     * @return Version number, as in 1006 for JSettlers 1.0.06, or {@link Integer#MAX_VALUE}.
     */
    public int getMaximumVersion()
    {
        return Integer.MAX_VALUE;
    }

    /**
     * Converts the contents of this message into
     * a String that can be transferred by a client
     * or server.
     * Your class' required method
     * static SOCMessageSubclass parseDataStr(String)
     * must be able to turn this String
     * back into an instance of the message class.
     *<P>
     * For most message types, at most one {@link #sep} token is allowed,
     * separating the type ID from the rest of the parameters.
     * For multi-messages (@link SOCMessageMulti}, multiple {@link #sep} tokens
     * are allowed.  Multi-messages are parsed with:
     * static SOCMessageSubclass parseDataStr(String[])
     */
    public abstract String toCmd();

    /** Simple human-readable representation, used for debug purposes. */
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
     * Convert a string into a SOCMessage.
     * The string is in the form of "id SEP messagename {SEP2 messagedata}*".
     * If the message type id is unknown, this is printed to System.err.
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

            case REVEALFOGHEX:      // fog hexes, 20121108, v2.0.00
                return SOCRevealFogHex.parseDataStr(data);

            case LEGALEDGES:        // large sea board, 20121216, v2.0.00
                return SOCLegalEdges.parseDataStr(data);

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

}
