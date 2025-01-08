/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2024-2025 Jeremy D Monin <jeremy@nand.net>
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

import java.util.StringTokenizer;

import soc.game.SOCScenario;


/**
 * Message from server to game's clients, to let them know that the
 * current player's most recent action can't be undone because of an uncommon situation or scenario.
 * Example: in the {@link SOCScenario#K_SC_FTRI SC_FTRI} scenario the player can
 * build or move ships to certain locations to receive dev cards or a "gift port" for trading, but
 * we currently disallow undoing the action if it gave a "gift port" because that's difficult to undo
 * and there was a confirmation dialog before the action.
 *
 *<H4>I18N:</H4>
 * Like any {@link SOCKeyedMessage} type, server code uses a string key when
 * constructing this message, then the server's net code localizes that key
 * when sending to each client. This allows new reason explanations without client changes.
 *
 * @see SOCUndoPutPiece
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.7.00
 */
public class SOCUndoNotAllowedReasonText extends SOCMessage
    implements SOCKeyedMessage, SOCMessageForGame
{
    private static final long serialVersionUID = 2700L;

    /**
     * Name of the game.
     */
    public final String game;

    /**
     * Is undo not allowed for the most recent action?
     * Likely always true, or the server wouldn't need to send this message type.
     * This field is in the message as a placeholder for future expansion if needed,
     * since the rest of the message is used unparsed as the string value.
     */
    public final boolean isNotAllowed;

    /**
     * The reason the action can't be undone ({@link #isNotAllowed}), or {@code null} if it can.
     * At the server this is an I18N string key, at the client it's text which was localized by and sent from
     * the server: see {@link #isLocalized}.
     * Constructor checks this against {@link SOCMessage#isSingleLineAndSafe(String, boolean)}.
     */
    public final String reason;

    /**
     * True if this message's {@link #reason} is localized text (for a specific client/locale),
     * false if it's an I18N string key (for server, to be localized to a game's member clients).
     */
    public final boolean isLocalized;

    /**
     * Create a new non-localized {@link SOCUndoNotAllowedReasonText} message at the server.
     *
     * @param ga  the game name
     * @param isNotAllowed  Is undo not allowed for the most recent action?
     *     Likely always true when sending or receiving this message type.
     * @param reason  Reason the action can't be undone; can be {@code null} if {@code ! isNotAllowed}.
     *     At the server this is an I18N string key which the server will localize before sending,
     *     at the client it's text which was localized by and sent from the server.
     *     This allows new reason explanations without client changes.
     * @throws IllegalArgumentException if {@code reason} is not null but {@link String#isEmpty() reason.isEmpty()} or
     *     fails {@link SOCMessage#isSingleLineAndSafe(String, boolean) SOCMessage.isSingleLineAndSafe(reason, true)}
     */
    public SOCUndoNotAllowedReasonText(final String ga, final boolean isNotAllowed, final String reason)
        throws IllegalArgumentException
    {
        this(ga, isNotAllowed, reason, false);
    }

    /**
     * Create a new localized or non-localized {@link SOCUndoNotAllowedReasonText} message.
     *
     * @param ga  the game name
     * @param isNotAllowed  Is undo not allowed for the most recent action?
     *     Likely always true when sending or receiving this message type.
     * @param reason  Reason the action can't be undone; can be {@code null} if {@code ! isNotAllowed}.
     *     At the server this is an I18N string key which the server must localize before sending,
     *     at the client it's localized text sent from the server.
     *     This allows new reason explanations without client changes.
     * @param isLocal  True if {@code reason} is localized, false if not: {@link #isLocalized}
     * @throws IllegalArgumentException if {@code reason} is not null but {@link String#isEmpty() reason.isEmpty()} or
     *     fails {@link SOCMessage#isSingleLineAndSafe(String, boolean) SOCMessage.isSingleLineAndSafe(reason, true)}
     */
    public SOCUndoNotAllowedReasonText(final String ga, final boolean isNotAllowed, final String reason, final boolean isLocal)
        throws IllegalArgumentException
    {
        if ((reason != null) && (reason.isEmpty() || ! isSingleLineAndSafe(reason, true)))
            throw new IllegalArgumentException("reason");

        messageType = UNDONOTALLOWEDREASONTEXT;
        game = ga;
        this.isNotAllowed = isNotAllowed;
        this.reason = reason;
        isLocalized = isLocal;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * UNDONOTALLOWEDREASONTEXT sep game sep2 isNotAllowedInt(0 or 1) [sep2 reason]
     *
     * @return the command String
     */
    public String toCmd()
    {
        return Integer.toString(messageType) + sep + game + sep2 + (isNotAllowed ? 1 : 0)
            + ((reason != null) ? sep2 + reason : "");
    }

    /**
     * {@inheritDoc}
     *<P>
     * This message type's key field is {@link #reason}. May be null.
     */
    public String getKey()
    {
        return reason;
    }

    // javadoc inherited from SOCKeyedMessage
    public SOCMessage localize(final String localizedText)
    {
        return new SOCUndoNotAllowedReasonText(game, isNotAllowed, localizedText, true);
    }

    /**
     * Parse the command string into a localized SOCUndoNotAllowedReasonText message at the client.
     *
     * @param s   the String to parse; format: game sep2 pn sep2 isNotAllowedInt sep2 reason
     * @return    a SOCUndoNotAllowedReasonText message, or null if parsing errors
     */
    public static SOCUndoNotAllowedReasonText parseDataStr(final String s)
    {
        String ga; // the game name
        boolean isNotAllowed;
        String reason;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            int isNotAllowedInt = Integer.parseInt(st.nextToken());
            if ((isNotAllowedInt < 0) || (isNotAllowedInt > 1))
                return null;
            else
                isNotAllowed = (isNotAllowedInt == 1);
            // get all of the line for reason,
            //  by choosing a separator character
            //  that can't appear in reason
            if (st.hasMoreTokens())
            {
                reason = st.nextToken(Character.toString( (char) 1 )).trim();
                if (reason.startsWith(SOCMessage.sep2))
                    reason = reason.substring(1);
            } else {
                reason = null;
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCUndoNotAllowedReasonText(ga, isNotAllowed, reason, true);
    }

    /**
     * @return a human readable form of the message; isNotAllowed is rendered as 1 or 0 in case of future expansion.
     *    Reason is omitted if {@code null}.
     */
    public String toString()
    {
        return getClass().getSimpleName() + ":game=" + game
            + "|isNotAllowed=" + (isNotAllowed ? 1 : 0)
            + (reason != null ? "|reason=" + reason : "");
    }

}
