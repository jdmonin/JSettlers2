/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2015,2017-2020 Jeremy D Monin <jeremy@nand.net>
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
import soc.util.SOCFeatureSet;  // for javadocs only

/**
 * A <B>client's request</B> for updated info on {@link SOCScenario}s,
 * or <B>server's reply</B> with information on one available {@link SOCScenario}
 * (including localization). This message type is for clients to find out about scenarios which
 * were introduced in versions newer than the client's version, but which may be usable at their version or all versions;
 * or ask an older server what it knows about scenario(s) changed since that server's version.
 * Server replies with scenario keynames and details.
 * Server can also reply with localization strings for one or many scenarios.
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
 *      client instead sends {@link SOCLocalizedStrings}({@link SOCLocalizedStrings#TYPE_SCENARIO TYPE_SCENARIO}):
 *      See that class's javadoc.
 *</UL>
 * This scenario info sync protocol for different versions is very similar to that done for {@link SOCGameOption}s
 * at client connect with {@link SOCGameOptionGetInfos}/{@link SOCGameOptionInfo}. The list can be calculated with
 * {@link SOCVersionedItem#itemsNewerThanVersion(int, boolean, java.util.Map)}.
 *
 * <H5>I18N:</H5>
 * Because client has previously sent {@link SOCGameOptionGetInfos} if needed,
 * the server knows whether the client wants localized strings, so {@code SOCScenarioInfo}
 * doesn't need an i18n flag like {@link SOCGameOptionGetInfos#OPTKEY_GET_I18N_DESCS}.
 *
 * <H4>Server reply to client:</H4>
 *
 * There are two message types which may send scenario info to the client:
 *<UL>
 * <LI> If the client is a different version than the server,
 *   a <B>sequence of {@code SOCScenarioInfo} messages</B> sends all info about each new or changed scenario,
 *   including those scenarios' localized text (or if none, its {@link SOCScenario} hardcoded name and description text).
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
 * Note that to help implement third-party clients, the client version used in this delta calculation
 * is the value reported in {@link SOCFeatureSet#CLIENT_SCENARIO_VERSION}, which might be lower than
 * the client's actual version number.
 *<P>
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
    implements SOCMessageFromUnauthClient
{
    private static final long serialVersionUID = 2450L;  // last structural change v2.4.50

    /**
     * If an older client is asking for any changed/new scenarios,
     * server responds with set of SCENARIOINFOs. Mark end of this list with a
     * SCENARIOINFO named "-". At the client this sets the {@link #noMoreScens} flag.
     */
    public static final SOCScenarioInfo SCENINFO_NO_MORE_SCENS
        = new SOCScenarioInfo(null, null, null);

    /**
     * {@link #scKey} marker {@code "?"} from client to ask for any new or changed scenarios
     * between the client and server versions. When present, this must be the last item in the parameter list.
     * The server will reply with a sequence of messages with scenario info, and a sequence-ending empty message
     * with only the {@link #noMoreScens} flag.
     */
    public static final String MARKER_ANY_CHANGED = "?";

    /**
     * Marker {@code "["} sent as first field when a scenario keyname list is sent from client:
     * Indicates to parser that this message's contents are the client's list of requested
     * {@link SOCScenario} key names, not the server's reply about a single scenario.
     * Can omit if client is sending {@link #MARKER_ANY_CHANGED} as the sole field.
     *<P>
     * Added by client constructor, removed at server by parseDataStr/constructor.
     */
    public static final String MARKER_SCEN_NAME_LIST = "[";

    /**
     * {@link #scKey} marker {@code "-"} from server to indicate this is the end of the list of SCENARIOINFOs.
     */
    public static final String MARKER_NO_MORE_SCENS = "-";

    /**
     * Version marker to indicate the requested scenario key is unknown.
     * Sent from server as -2 in the reply's {@code lastModVersion} field.
     */
    public static final int MARKER_KEY_UNKNOWN = -2;  // not -1, which is valid for sc.lastModVersion

    /**
     * Rendered {@link #MARKER_KEY_UNKNOWN} for known value in {@link #toString()}.
     * @since 2.4.50
     */
    private static final String STR_MARKER_KEY_UNKNOWN = Integer.toString(MARKER_KEY_UNKNOWN);

    /** True if this message is scenario info from server, not a request from client. */
    public final boolean isFromServer;

    /**
     * Parsed scenario from server ({@link #getScenario()}),
     * or {@code null} if {@link #isKeyUnknown} or {@link #noMoreScens}
     * or if this message is from client to server.
     * When {@code null}, see field {@link #scKey} for scenario name.
     *<P>
     * Before v2.4.50, this field was {@code sc}.
     */
    private SOCScenario scen;

    /**
     * The scenario key in a reply from server.
     * If {@link #isKeyUnknown}, use this field because {@link #scen} is null.
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
     * @param localDesc  i18n localized brief description/title, or {@code null} to use
     *     {@link SOCVersionedItem#getDesc() SOCScenario.getDesc()}
     * @param localLongDesc  i18n localized long description, or {@code null} to use
     *     {@link SOCScenario#getLongDesc()}
     * @see #SOCScenarioInfo(String, boolean)
     * @see #SOCScenarioInfo(List, boolean)
     */
    public SOCScenarioInfo(final SOCScenario sc, String localDesc, String localLongDesc)
    {
        super(SCENARIOINFO, new ArrayList<String>());

        isFromServer = true;
        isKeyUnknown = false;
        noMoreScens = (sc != null);

        scen = sc;
        if (sc != null)
        {
            scKey = sc.key;
            String opts = sc.scOpts;
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
        super(SCENARIOINFO, new ArrayList<String>());

        if (! isSingleLineAndSafe(scKey))
            throw new IllegalArgumentException("scKey: " + scKey);

        isFromServer = isServerReply;
        noMoreScens = false;
        isKeyUnknown = isServerReply;
        this.scKey = scKey;

        if (! isServerReply)
            pa.add(MARKER_SCEN_NAME_LIST);

        /* [0] */ pa.add(scKey);
        if (! isServerReply)
            return;  // <--- Early return: Sending request from client ---

        /* [1] */ pa.add("0");  // minVersion
        /* [2] */ pa.add(STR_MARKER_KEY_UNKNOWN);  // lastModVersion: Integer.toString(MARKER_KEY_UNKNOWN)
    }

    /**
     * Constructor for client to ask a server for info about any new or changed scenarios
     * and/or about specific scenario keys.
     *
     * @param scKeys  List of scenarios to ask about, or {@code null} for an empty list.
     *     Items may be added by this constructor.
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
        super(SCENARIOINFO, (scKeys != null) ? scKeys : new ArrayList<String>());

        isFromServer = false;

        if ((scKeys == null) || scKeys.isEmpty())
        {
            if (! addMarkerAnyChanged)
                throw new IllegalArgumentException("empty message");
        } else {
            for (final String sc : scKeys)
                if (! SOCMessage.isSingleLineAndSafe(sc))
                    throw new IllegalArgumentException();
            pa.add(0, MARKER_SCEN_NAME_LIST);  // required at start of non-empty list
        }

        if (addMarkerAnyChanged)
            pa.add(MARKER_ANY_CHANGED);

        scKey = null;
        isKeyUnknown = false;
        noMoreScens = false;
    }

    /**
     * Constructor to parse an incoming message; see {@link #parseDataStr(List)} for expected {@code pa} format.
     * If message is from client, removes the {@link #MARKER_SCEN_NAME_LIST} first element if present.
     *
     * @throws IllegalArgumentException if message is from server and any field fails the
     *    {@link SOCScenario#SOCScenario(String, int, int, String, String, String)}
     *    constructor's requirements for it
     * @throws IndexOutOfBoundsException if {@code pa} is empty or too short (missing expected fields)
     * @throws NumberFormatException    if any {@code pa} integer field's contents are incorrectly formatted.
     */
    private SOCScenarioInfo(List<String> pa)
        throws IllegalArgumentException, IndexOutOfBoundsException, NumberFormatException
    {
        super(SCENARIOINFO, parseData_FindEmptyStrs(pa));
            // Transforms EMPTYSTR -> "" to sanitize;
            // won't find any EMPTYSTR unless data was malformed when passed to toCmd() at server

        final int L = pa.size();
        final String s = pa.get(0);  // may throw IndexOutOfBoundsException if empty
        final boolean startswithCliListMarker = s.equals(MARKER_SCEN_NAME_LIST);

        isFromServer = ! (startswithCliListMarker || s.equals(MARKER_ANY_CHANGED));

        if (! isFromServer)
        {
            // remove MARKER_SCEN_NAME_LIST marker from param list
            if (startswithCliListMarker)
            {
                pa.remove(0);
                if (pa.isEmpty())
                    throw new IndexOutOfBoundsException();
            }

            scKey = null;
            isKeyUnknown = false;
            noMoreScens = false;
        } else {
            scKey = s;
            noMoreScens = (scKey.equals(MARKER_NO_MORE_SCENS));
            if (! noMoreScens)
            {
                final int minVers = Integer.parseInt(pa.get(1));
                final int lastModVers = Integer.parseInt(pa.get(2));
                isKeyUnknown = (lastModVers == MARKER_KEY_UNKNOWN);
                if (! isKeyUnknown)
                {
                    final String longDesc = (L >= 6) ? pa.get(5) : null;
                    scen = new SOCScenario(scKey, minVers, lastModVers, pa.get(4), longDesc, pa.get(3));
                }
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
        return scen;
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
     * {@code pa[0]} is the marker {@link #MARKER_SCEN_NAME_LIST} unless list is empty.
     * List can be followed with {@link #MARKER_ANY_CHANGED}.
     *
     *<H4>From Server:</H4>
     *<UL>
     * <LI> pa[0] = key (name of the scenario)
     * <LI> pa[1] = minimum version integer
     * <LI> pa[2] = last-modified version integer, or {@link #MARKER_KEY_UNKNOWN}
     * <LI> If not {@code MARKER_KEY_UNKNOWN}, also contains these fields:
     * <LI> pa[3] = game options if any, or "-", from {@link SOCScenario#scOpts}
     * <LI> pa[4] = one-line description (displayed text), localized to client if localized text is available
     * <LI> pa[5] if present = long description (paragraph of displayed text) if any, localized to client if available
     *</UL>
     *<P>
     * If we're parsing a message from the client (not the server),
     * {@code pa[0]} will be {@link #MARKER_SCEN_NAME_LIST} or {@link #MARKER_ANY_CHANGED}.
     *
     * @param pa  the String parameters; any {@link SOCMessage#EMPTYSTR} will be parsed as ""
     * @param soleParam  The single String parameter from parser if list contains only 1 parameter;
     *     ignored unless {@code pa} is {@code null}
     * @return  a SOCScenarioInfo message, or null if parsing errors
     */
    public static SOCScenarioInfo parseDataStr(List<String> pa, final String soleParam)
    {
        if (pa == null)
        {
            if (soleParam == null)
                return null;

            pa = new ArrayList<String>();
            pa.add(soleParam);
        }
        else if (pa.isEmpty())
        {
            return null;
        }

        try
        {
            return new SOCScenarioInfo(pa);  // calls parseData_FindEmptyStrs
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Field names for {@link #toString()} when {@link #isFromServer}.
     * @since 2.4.50
     */
    private final String[] FIELD_NAMES = {"key", "minVers", "lastModVers", "opts", "title", "desc"};

    /**
     * Build a human-readable form of the message.
     *<UL>
     * <LI> When {@link #isFromServer}, this is info about 1 scenario, and fields are named/labeled.
     *    {@code lastModVers} will show {@code "MARKER_KEY_UNKNOWN"} if {@link #isKeyUnknown}
     *    (field value sent as {@link #MARKER_KEY_UNKNOWN}).
     * <LI> Otherwise, is from client and this is a list of keys, possibly with a leading or trailing marker.
     *    No field labeling or change to contents is done here; field "labels" are generic {@code "p="}.
     *</UL>
     * @return a human readable form of the message
     * @since 2.4.50
     */
    @Override
    public String toString()
    {
        List<String> fields = pa;
        if (isFromServer && (fields.size() > 2)
            && (isKeyUnknown || fields.get(2).equals(STR_MARKER_KEY_UNKNOWN)))
        {
            fields = new ArrayList<>(fields);
            fields.set(2, "MARKER_KEY_UNKNOWN");
        }

        return toString(fields, isFromServer ? FIELD_NAMES : null);
    }

}
