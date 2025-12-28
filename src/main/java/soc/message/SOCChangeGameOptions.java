/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2025 Jeremy D Monin <jeremy@nand.net>
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
import java.util.Map;
import java.util.SortedMap;

import soc.game.SOCGameOption;

/**
 * Message from server about changes to a game's set of options.
 * Currently used only to remove {@link soc.game.SOCGameOption#FLAG_OPPORTUNISTIC Opportunistic} options
 * for graceful fallback with older player clients.
 *
 *<UL>
 * <LI> Not sent after game has started (so, state is {@link soc.game.SOCGame#START1A} or earlier)
 * <LI> Can remove options, but no reason yet to change or add them
 *</UL>
 *
 * Introduced in 2.7.00; check client version against {@link #VERSION_FOR_REMOVE}
 * before sending this message.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.7.00
 */
public class SOCChangeGameOptions extends SOCMessageTemplateMs
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2700L;

    /** Minimum version (2.7.00) of server and client where game options can be removed ({@link #OP_REMOVE}). */
    public static final int VERSION_FOR_REMOVE = 2700;

    /** {@link #operation} constant to remove game options from a game's set. */
    public static final char OP_REMOVE = 'R';

    /** Field marker: Start of {@link #optsChanges} list. */
    protected static final String FIELD_OPTS_CHANGES = "O";

    /** Field marker: Start of {@link #changeCauseClientNamesVersions} list. */
    protected static final String FIELD_OPTS_CLIENT_NAMES_VERSIONS = "C";

    /** Game name */
    public final String gaName;

    /**
     * Which type of change: Currently only Remove ({@link #OP_REMOVE}).
     */
    public char operation;

    /**
     * List of options being changed; format depends on {@link #operation}.
     *<UL>
     * <LI> {@link #OP_REMOVE}: Option names
     *</UL>
     */
    public List<String> optsChanges;
 
    /**
     * Optionally, the client names and versions which caused the change or removal, or {@code null} (never empty):
     * Pairs of elements clientName, clientVersionNumber in same format as {@link soc.util.Version#versionNumber()}.
     */
    public List<String> changeCauseClientNamesVersions;

    /**
     * Constructor for server to send message to client(s).
     * @param gaName  Game name
     * @param operation  Operation, such as {@link #OP_REMOVE}, listed in {@link #operation}
     * @param optsChanged  Info on changed options, in format of {@link #optsChanges}
     * @param changeCauseClientNamesVersions  Optional client info,
     *     in same format as {@link soc.game.SOCGameOptionSet.RemoveOpportunisticResults#olderCliNamesVersions}
     * @throws IllegalArgumentException if a parameter doesn't match its requirements documented here
     */
    public SOCChangeGameOptions
        (String gaName, char operation, SortedMap<String, SOCGameOption> optsChanged,
         SortedMap<String, Integer> changeCauseClientNamesVersions)
        throws IllegalArgumentException
    {
        super(CHANGEGAMEOPTIONS, new ArrayList<String>());
        if ((gaName == null) || gaName.isEmpty())
            throw new IllegalArgumentException("gaName");
        if (operation != OP_REMOVE)
            throw new IllegalArgumentException("unknown op " + operation);
        if ((optsChanged == null) || optsChanged.isEmpty())
            throw new IllegalArgumentException("optsChanged");
        if ((changeCauseClientNamesVersions != null) && changeCauseClientNamesVersions.isEmpty())
            throw new IllegalArgumentException("changeCauseClientNamesVersions");

        this.gaName = gaName;
        this.operation = operation;
        this.optsChanges = new ArrayList<String>(optsChanged.keySet());
        if (changeCauseClientNamesVersions != null)
        {
            ArrayList<String> clis = new ArrayList<>();
            for (Map.Entry<String, Integer> cliPl: changeCauseClientNamesVersions.entrySet())
            {
                clis.add(cliPl.getKey());
                clis.add(cliPl.getValue().toString());
            }
            this.changeCauseClientNamesVersions = clis;
        }

        pa.add(gaName);
        pa.add(Character.toString(operation));
        encodeList(FIELD_OPTS_CHANGES, optsChanges, pa);
        if (this.changeCauseClientNamesVersions != null)
            encodeList(FIELD_OPTS_CLIENT_NAMES_VERSIONS, this.changeCauseClientNamesVersions, pa);
    }

    /**
     * Constructor for client to parse server's message.
     * @param pal  The option's parameters; see {@link #parseDataStr(List)} for format.
     * @throws IllegalArgumentException if pal's length &lt; 4, gameName empty, field parsing fails,
     *     or doesn't have a valid {@link #operation}
     * @throws NumberFormatException    if a field marker's length can't be parsed as a nonnegative integer
     */
    protected SOCChangeGameOptions(List<String> pal)
        throws IllegalArgumentException, NumberFormatException
    {
        super(CHANGEGAMEOPTIONS, pal);
        final int L = pal.size();
        if (L < 4)
            throw new IllegalArgumentException("pal.size");

        parseData_FindEmptyStrs(pal);  // EMPTYSTR -> ""

        gaName = pal.get(0);
        String optStr = pal.get(1);
        if ((optStr.length() != 1) || (optStr.charAt(0) != OP_REMOVE))
            throw new IllegalArgumentException("operation");
        operation = optStr.charAt(0);

        // the rest is field markers and field contents
        for (int i = 2; i < L; )
        {
            // expecting a field marker here: field name, space, length in parameters
            String fieldEncodedMarker = pal.get(i);
            final int sepPos = fieldEncodedMarker.indexOf(' ');
            if (sepPos < 1)
                throw new IllegalArgumentException("pal(" + i + ")");
            String fieldName = fieldEncodedMarker.substring(0, sepPos);
            int fieldParamLen = Integer.parseInt(fieldEncodedMarker.substring(sepPos + 1));
            if (fieldParamLen < 0)
                throw new NumberFormatException("pal(" + i + ")");
            if (i + fieldParamLen > L)
                throw new IllegalArgumentException("pal(" + i + ") length");

            // copy fieldParamLen items
            List<String> fieldItems = new ArrayList<>();
            ++i;
            for (; fieldParamLen > 0; --fieldParamLen, ++i)
                fieldItems.add(pal.get(i));

            boolean dupe = false;
            switch (fieldName)
            {
            case FIELD_OPTS_CHANGES:
                if (optsChanges != null)
                    dupe = true;
                else
                    optsChanges = fieldItems;
                break;

            case FIELD_OPTS_CLIENT_NAMES_VERSIONS:
                if (changeCauseClientNamesVersions != null)
                    dupe = true;
                else
                    changeCauseClientNamesVersions = fieldItems;
                break;

            default:
                throw new IllegalArgumentException("pal(" + i + ") unrecognized field");
            }
            if (dupe)
                throw new IllegalArgumentException("dupe field: " + fieldName);
        }

        if (optsChanges == null)
            throw new IllegalArgumentException("missing optsChanges");
    }

    /**
     * Minimum version where this message type is used ({@link #VERSION_FOR_REMOVE}).
     * @return Version number, 2700 for JSettlers 2.7.00.
     */
    public int getMinimumVersion() { return VERSION_FOR_REMOVE; }

    // inherits javadoc from SOCMessageForGame
    public String getGame()
    {
        return gaName;
    }

    /**
     * Parse the command String array into a SOCChangeGameOptions message.
     * @param pa  The String parameters:
     *<UL>
     * <LI> {@link #gaName}
     * <LI> {@link #operation}
     * <LI> Followed by 1 or more fields, each of which is a marker and the field contents:
     * <LI> A field marker (type and number of parameters/items)
     * <LI> Contents of that field
     *</UL>
     * Any parameter which is {@link SOCMessage#EMPTYSTR} is changed to "" in place in {@code pa}.
     *
     * @param pa   the String parameters
     * @return    a SOCChangeGameOptions message, or null if parsing errors
     */
    public static SOCChangeGameOptions parseDataStr(List<String> pa)
    {
        if ((pa == null) || (pa.size() < 4))
            return null;

        try
        {
            return new SOCChangeGameOptions(pa);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Encode a field's list of items into the message parameter list:
     * a field marker string (type + " " + length) followed by 1 item for each element of the list.
     * @param fieldMarker  Field marker, a short case-sensitive identifier such as {@code "O"} ({@code FIELD_OPTS_CHANGES}); not null or empty
     * @param fieldItems  Items of the field's list; can be empty or {@code null}
     * @param intoMessageParamList  Message parameter list into which to encode; not null
     * @throws IllegalArgumentException  if {@code fieldMarker} is null or empty, or {@code intoMessageParamList} null
     */
    public static void encodeList
        (final String fieldMarker, final List<String> fieldItems, final List<String> intoMessageParamList)
        throws IllegalArgumentException
    {
        if ((fieldMarker == null) || fieldMarker.isEmpty())
            throw new IllegalArgumentException("fieldMarker");
        if (intoMessageParamList == null)
            throw new IllegalArgumentException("intoMessageParamList");

        final int length = (fieldItems != null) ? fieldItems.size() : 0;
        intoMessageParamList.add(fieldMarker + " " + length);
        if (fieldItems != null)
            intoMessageParamList.addAll(fieldItems);
    }

}
