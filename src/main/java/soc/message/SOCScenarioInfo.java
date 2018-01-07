/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2015,2017-2018 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;

import java.util.ArrayList;
import java.util.List;

import soc.game.SOCGameOption;
import soc.game.SOCVersionedItem;
import soc.game.SOCScenario;

/**
 * A <B>client's request</B> for updated {@link SOCScenario} info,
 * or <B>server's reply</B> with information on one available {@link SOCScenario}
 * (including localization). This is so clients can get localization;
 * or find out about scenarios which were introduced in versions newer than the client's version,
 * but which may be usable at their version or all versions; or ask an older server what it knows
 * about scenario(s) changed since the server's version.
 *<P>
 * This message is about the server or client scenarios and not about any particular game:
 * It extends {@link SOCMessageTemplateMs} for convenient encoding and parsing,
 * but {@link #getGame()} returns {@code null}.
 *
 *<H4>Timing:</H4>
 *
 * <B>When client connects,</B> it doesn't yet need any scenario information.
 * At that point the client will request any added, changed, or localized {@link SOCGameOption}s.
 * This early game option sync guarantees that when the client later needs any scenario info,
 * it already knows about any options listed in {@link SOCScenario#scOpts}.
 * The server will also know from this if the client wants localized strings in general.
 *<P>
 * <B>Get Game Info:</B> If the user clicks the Game Info button for a game with a scenario,
 * at that point the client can send a {@code SOCScenarioInfo} message to request info about that scenario.
 *<P>
 * <B>Join Game:</B> If the user joins a game with a scenario, before {@link SOCJoinGameAuth} the server can send
 * a {@code SOCScenarioInfo} or {@link SOCLocalizedStrings} message with info about that scenario if needed.
 *<P>
 * <B>New Game:</B> When the user wants to create a new game, the client should send a {@code SOCScenarioInfo}
 * to request all updated info for scenarios; the server and client synchronize {@code SOCScenario} info
 * at this point.
 *
 * <H4>Client request to Server:</H4>
 *
 * The client can request information about a single scenario (for the Game Info button),
 * or request all new or changed scenarios when client and server are different versions or locales.
 *<UL>
 * <LI> For a single scenario, the client request's parameter list has 1 item,
 *      the scenario's {@link SOCVersionedItem#key SOCScenario.key}.
 * <LI> If client is older than server, the request's list has 1 item, {@link #MARKER_ANY_CHANGED}.
 *      Server should calculate and send all scenarios changed/added since the client's version.
 * <LI> If client is newer than server, client should calculate any scenarios it knows about
 *      which are new or changed since the older server's version. The parameter list
 *      will have all of those, and end with {@link #MARKER_ANY_CHANGED} so the server
 *      knows to end its reply sequence with a message having the {@link #noMoreScens} flag.
 *      The server's reply sequence will have a message about each scenario key in the client's request.
 * <LI> If client and server are the same version, and client only wants localized i18n scenario strings,
 *      client sends {@code SOCScenarioInfo} with 1 item, {@link #MARKER_ANY_CHANGED}.
 *</UL>
 * This scenario info sync protocol for different versions is very similar to that done for {@link SOCGameOption}s
 * at client connect with {@link SOCGameOptionGetInfos}/{@link SOCGameOptionInfo}. The list can be calculated with
 * {@link SOCVersionedItem#itemsNewerThanVersion(int, boolean, java.util.Map)}.
 *<P>
 * <B>I18N:</B> Because client has previously sent {@link SOCGameOptionGetInfos} if needed,
 * the server knows whether the client wants localized strings, so {@code SOCScenarioInfo} has
 * no i18n flag like {@link SOCGameOptionGetInfos#OPTKEY_GET_I18N_DESCS}.
 *
 * <H4>Server reply to client:</H4>
 *
 * There are two message types which may send scenario info to the client:
 *<UL>
 * <LI> If the client is a different version than the server,
 *   a <B>sequence of {@code SOCScenarioInfo} messages</B> sends all info about new or changed scenarios,
 *   including those scenarios' localized text.
 *   The sequence ends with a message which has the {@link #noMoreScens} flag.
 *  <P>
 *   Any scenarios too new for the client (per {@link SOCVersionedItem#minVersion sc.minVersion})
 *   are included in the sequence with the {@link #isKeyUnknown} flag, in case the client
 *   encounters them as a listed game's scenario. This is consistent with how too-new {@link SOCGameOption}s
 *   are sent to clients.
 *  <P>
 *   Separately, <B>a single {@code SOCScenarioInfo}</B> can be sent when the client requests info on that
 *   scenario or asks to join a game with a scenario not previously sent to the client.
 * <LI> If the client is a different locale than the server,
 *   a single <B>{@link SOCLocalizedStrings}</B> message can send all scenarios' localized text if available.
 *   If the client was sent {@code SOCScenarioInfo} about a given scenario,
 *   it won't also get {@code SOCLocalizedStrings} for the same scenario.
 *   See {@link SOCLocalizedStrings} javadoc for more information about that message type.
 *</UL>
 * The server's {@code SOCScenarioInfo} reply message provides the scenario's information,
 * including public fields with game options and description localized for the client,
 * and sets these flags/fields:
 *<UL>
 *  <LI> {@link #getScenario()}, all scenario details if available
 *  <LI> {@link #getScenarioKey()}, the key for a scenario requested by the client
 *  <LI> {@link #isKeyUnknown} if the server doesn't know a scenario requested by the client
 *  <LI> {@link #noMoreScens} if this is an empty message marking the end of the reply sequence
 *</UL>
 * Special case: If the client is asking for any new or changed scenarios but there aren't any,
 * server responds with a single {@code SOCScenarioInfo} with the {@link #noMoreScens} flag.
 *<P>
 * Introduced in 2.0.00; check receiver's version against {@link SOCScenario#VERSION_FOR_SCENARIOS}
 * before sending this message.
 *<P>
 * Robot clients don't need to know about or handle this message type, because
 * they are always the server's version and thus know about the same scenarios.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCScenarioInfo extends SOCMessageTemplateMs
{
    private static final long serialVersionUID = 2000L;

    /**
     * If an older client is asking for any changed/new scenarios,
     * server responds with set of SCENARIOINFOs. Mark end of this list with a
     * SCENARIOINFO named "-". At the client this sets the {@link #noMoreScens} flag.
     */
    public static final SOCScenarioInfo SCENINFO_NO_MORE_SCENS
        = new SOCScenarioInfo(null, null, null);

    /**
     * {@link #scKey} marker {@code "?"} from client to ask for any new or changed scenarios
     * between the client and server versions.  When present, this is the last item in the parameter list.
     * The server will reply with a sequence of messages with scenario info, and a sequence-ending empty message
     * with only the {@link #noMoreScens} flag.
     */
    public static final String MARKER_ANY_CHANGED = "?";

    /**
     * {@link #scKey} marker {@code "-"} from server to indicate this is the end of the list of SCENARIOINFOs.
     */
    public static final String MARKER_NO_MORE_SCENS = "-";

    /**
     * Version marker to indicate the requested scenario key is unknown.
     * Sent from server as -2 in the reply's {@code lastModVersion} field.
     */
    public static final int MARKER_KEY_UNKNOWN = -2;  // skip -1 which is valid for sc.lastModVersion

    /**
     * Parsed scenario from server ({@link #getScenario()}),
     * or {@code null} if {@link #isKeyUnknown} or {@link #noMoreScens}
     * or if this message is from client to server.
     * When {@code null}, see field {@link #scKey} for scenario name.
     */
    private SOCScenario sc;

    /**
     * The scenario key in a reply from server.
     * If {@link #isKeyUnknown}, use this field because {@link #sc} is null.
     */
    private final String scKey;

    /**
     * If true, this requested {@link #getScenarioKey()} is unknown; used in reply from server to client.
     * This can occur if the client's version is newer than the server.
     * A message with this flag set will have null {@link #getScenario()}.
     */
    public final boolean isKeyUnknown;

    /**
     * If true, there are no more scenarios to send; used in reply from server to client.
     * A message with this flag set will have null {@link #getScenario()}.
     */
    public final boolean noMoreScens;

    /**
     * Constructor for server to tell client about a scenario, or mark the end of the list of scenarios.
     *
     * @param sc  Scenario to send, or {@code null} to send the end-of-list marker {@link #SCENINFO_NO_MORE_SCENS}.
     *     Scenario key isn't checked here for {@link SOCMessage#isSingleLineAndSafe(String)}
     *     because the {@link SOCScenario} constructor already checked it against
     *     more restrictive {@link SOCVersionedItem#isAlphanumericUpcaseAscii(String)}.
     * @param localDesc  i18n localized brief description, or {@code null} to use
     *     {@link SOCVersionedItem#getDesc() SOCScenario.getDesc()}
     * @param localLongDesc  i18n localized long description, or {@code null} to use
     *     {@link SOCScenario#getLongDesc()}
     * @see #SOCScenarioInfo(String, boolean)
     * @see #SOCScenarioInfo(List, boolean)
     */
    public SOCScenarioInfo(final SOCScenario sc, String localDesc, String localLongDesc)
    {
        super(SCENARIOINFO, null, new ArrayList<String>());

        isKeyUnknown = false;
        noMoreScens = (sc != null);

        this.sc = sc;
        if (sc != null)
        {
            scKey = sc.key;
            String opts = sc.scOpts;  // never null or "", per scOpts javadoc
            if (localDesc == null)
                localDesc = sc.getDesc();

            /* [0] */ pa.add(sc.key);
            /* [1] */ pa.add(Integer.toString(sc.minVersion));
            /* [2] */ pa.add(Integer.toString(sc.lastModVersion));
            /* [3] */ pa.add(opts);
            /* [4] */ pa.add(localDesc);

            if (localLongDesc == null)
                localLongDesc = sc.getLongDesc();
            if ((localLongDesc != null) && (localLongDesc.length() > 0))
                /* [5] */ pa.add(localLongDesc);
        } else {
            scKey = MARKER_NO_MORE_SCENS;
            /* [0] */ pa.add(MARKER_NO_MORE_SCENS);
            for (int i = 0; i < 4; ++i)
                /* [1]-[4] */ pa.add(EMPTYSTR);
        }
    }

    /**
     * Constructor for client to ask the server for info about a single scenario,
     * or for server to tell client that a requested scenario is unknown.
     *
     * @param scKey  Keyname of a scenario, requested at client or unknown at server
     * @param isServerReply  True if replying from server, false if requesting from client
     * @throws IllegalArgumentException  if {@code scKey} fails {@link SOCMessage#isSingleLineAndSafe(String)}
     * @see #SOCScenarioInfo(List, boolean)
     * @see #SOCScenarioInfo(SOCScenario, String, String)
     */
    public SOCScenarioInfo(final String scKey, final boolean isServerReply)
        throws IllegalArgumentException
    {
        super(SCENARIOINFO, ((isServerReply) ? null : SOCMessage.GAME_NONE), new ArrayList<String>());

        if (! isSingleLineAndSafe(scKey))
            throw new IllegalArgumentException("scKey: " + scKey);

        noMoreScens = false;
        isKeyUnknown = isServerReply;
        this.scKey = scKey;

        /* [0] */ pa.add(scKey);
        if (! isServerReply)
            return;  // <--- Early return: Sending request from client ---

        /* [1] */ pa.add("0");  // minVersion
        /* [2] */ pa.add(Integer.toString(MARKER_KEY_UNKNOWN));  // lastModVersion
        /* [3] */ pa.add(EMPTYSTR);  // opts
        /* [4] */ pa.add(EMPTYSTR);  // desc
    }

    /**
     * Constructor for client to ask a server for info about any new or changed scenarios
     * and/or about specific scenario keys.
     *
     * @param scKeys  List of scenarios to ask about, or {@code null} for an empty list
     * @param addMarkerAnyChanged  If true, append {@link #MARKER_ANY_CHANGED} to the sent list
     * @throws IllegalArgumentException if ! {@code addMarkerAnyChanged} and {@code scKeys} is {@code null} or empty
     *     (this would be an empty message), or if any element of {@code scKeys} fails
     *     {@link SOCMessage#isSingleLineAndSafe(String)}
     * @see #SOCScenarioInfo(String, boolean)
     * @see #SOCScenarioInfo(SOCScenario, String, String)
     */
    public SOCScenarioInfo(final List<String> scKeys, final boolean addMarkerAnyChanged)
        throws IllegalArgumentException
    {
        super(SCENARIOINFO, SOCMessage.GAME_NONE,
              (scKeys != null) ? scKeys : new ArrayList<String>());

        if ((scKeys == null) || scKeys.isEmpty())
        {
            if (! addMarkerAnyChanged)
                throw new IllegalArgumentException("empty message");
        } else {
            for (final String sc : scKeys)
                if (! SOCMessage.isSingleLineAndSafe(sc))
                    throw new IllegalArgumentException();
        }

        if (addMarkerAnyChanged)
            pa.add(MARKER_ANY_CHANGED);

        scKey = null;
        isKeyUnknown = false;
        noMoreScens = false;
    }

    /**
     * Constructor to parse an incoming message; see {@link #parseDataStr(List)} for expected {@code pa} format.
     * If message is from client, removes the {@link SOCMessage#GAME_NONE} first element.
     *
     * @throws IllegalArgumentException if pa length &lt; 5 from server or &lt; 2 from client,
     *    or if message is from server and any field fails the
     *    {@link SOCScenario#SOCScenario(String, int, int, String, String, String)}
     *    constructor's requirements for it
     * @throws IndexOutOfBoundsException if {@code pa} is empty or its only element is {@link SOCMessage#GAME_NONE}
     * @throws NumberFormatException    if any {@code pa} integer field's contents are incorrectly formatted.
     */
    private SOCScenarioInfo(List<String> pa)
        throws IllegalArgumentException, IndexOutOfBoundsException, NumberFormatException
    {
        super(SCENARIOINFO, null, parseData_FindEmptyStrs(pa));
            // Transforms EMPTYSTR -> "" for sanitation;
            // won't find any EMPTYSTR unless data was malformed when passed to toCmd() at server

	final int L = pa.size();
	final boolean isFromClient = (pa.get(0).equals(SOCMessage.GAME_NONE));  // may throw IndexOutOfBoundsException
	if (isFromClient)
	{
	    // remove GAME_NONE marker from param list, set game field to it:
	    // non-null game required for server message handler
	    game = pa.remove(0);
	    if (pa.isEmpty())
	        throw new IndexOutOfBoundsException();

	    scKey = null;
	    isKeyUnknown = false;
	    noMoreScens = false;
	} else {
	    if (L < 5)
	        throw new IllegalArgumentException("pa.size");

	    scKey = pa.get(0);
            noMoreScens = (scKey.equals(MARKER_NO_MORE_SCENS));
            if (! noMoreScens)
            {
                final int minVers = Integer.parseInt(pa.get(1));
                final int lastModVers = Integer.parseInt(pa.get(2));
                final String longDesc = (L >= 6) ? pa.get(5) : null;

                isKeyUnknown = (lastModVers == MARKER_KEY_UNKNOWN);
                if (! isKeyUnknown)
                    sc = new SOCScenario(scKey, minVers, lastModVers, pa.get(4), longDesc, pa.get(3));
            } else {
                isKeyUnknown = false;
            }
	}
    }

    /**
     * Minimum version where this message type is used.
     * SCENARIOINFO introduced in 2.0.00.
     * @return Version number, 2000 for JSettlers 2.0.00
     */
    public int getMinimumVersion() { return 2000; }

    /**
     * The scenario info, if any. Used in replies from server to client.
     * If the {@link #isKeyUnknown} or {@link #noMoreScens} flag is set, this is {@code null}.
     * {@link #getScenarioKey()} will name the scenario and won't be null.
     * @return the parsed scenario from server, or {@code null} if this message is coming from
     *     a client or if either flag is set.
     */
    public SOCScenario getScenario()
    {
        return sc;
    }

    /**
     * The scenario keyname this message is about. Used in replies from server to client.
     * If {@link #isKeyUnknown} flag is true, this field is set but {@link #getScenario()} is {@code null}.
     * @return  Key name of a scenario, from {@link SOCVersionedItem#key sc.key}
     */
    public String getScenarioKey()
    {
        return scKey;
    }

    /**
     * Parse the parameter list into a SOCScenarioInfo message.
     *
     *<H4>From Client:</H4>
     * {@code pa} is a list of scenario keynames the client is requesting info about.
     * {@code pa[0]} is the marker {@link SOCMessage#GAME_NONE}.
     * List might end with {@link #MARKER_ANY_CHANGED}.
     *
     *<H4>From Server:</H4>
     *<UL>
     * <LI> pa[0] = key (name of the scenario)
     * <LI> pa[1] = minimum version integer
     * <LI> pa[2] = last-modified version integer
     * <LI> pa[3] = game options if any, or "-", from {@link SOCScenario#scOpts}
     * <LI> pa[4] = one-line description (displayed text), localized to client if localized text is available
     * <LI> pa[5] if present = long description (paragraph of displayed text) if any, localized to client if available
     *</UL>
     *<P>
     * {@link SOCMessageTemplateMs} has an optional {@link #getGame()} field in the message,
     * which if non-{@code null} appears here as the first parameter in the string list.
     * To determine if we're parsing a message from the client or from the server, note that messages
     * from the client have {@code getGame()} == {@link SOCMessage#GAME_NONE}, those from the server have {@code null}
     * and their first parameter is a scenario keyname instead. {@code GAME_NONE} is guaranteed to not be
     * a valid scenario keyname; it contains a character not valid for scenario keys.
     *
     * @param pa  the String parameters; any {@link SOCMessage#EMPTYSTR} will be parsed as ""
     * @return  a SOCScenarioInfo message, or null if parsing errors
     */
    public static SOCScenarioInfo parseDataStr(List<String> pa)
    {
        if ((pa == null) || (pa.size() < 2))
            return null;

        try
        {
            return new SOCScenarioInfo(pa);  // calls parseData_FindEmptyStrs
        } catch (Throwable e) {
            return null;
        }
    }

}
