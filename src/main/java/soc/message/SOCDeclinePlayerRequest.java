/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2021 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;
import soc.game.SOCPlayingPiece;


/**
 * This reply message from the server to a client means that client player's request or requested action
 * has been declined by the server, is not possible at this time.
 * Includes a {@link #reasonCode}, optional {@link #reasonText} and current {@link #gameState},
 * and optional {@link #detailValue1} and {@link #detailValue2} which may be used by some reason codes
 * if mentioned in their javadocs.
 *<P>
 * Clients older than v2.5.00 ({@link #MIN_VERSION}) are instead sent {@link SOCGameServerText},
 * optionally followed by {@link SOCGameState}.
 *
 *<H4>I18N:</H4>
 * Text is localized by server when sending to the client.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.5.00
 */
public class SOCDeclinePlayerRequest extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2500L;  // last structural change v2.5.00

    /**
     * Version number (2.5.00) where this message type was introduced.
     */
    public static final int MIN_VERSION = 2500;

    /**
     * Reason code if not covered by other available reason codes.
     */
    public static final int REASON_OTHER = 0;

    /**
     * Reason code when game rules or conditions prevent the request,
     * and will continue to do so for rest of game.
     *<P>
     * Examples:
     *<UL>
     * <LI> Ask to buy a dev card, but none are left to buy
     * <LI> Ask for Special Build, but not enough players in game
     *</UL>
     */
    public static final int REASON_NOT_THIS_GAME = 1;

    /**
     * Reason code when it's not the player's turn.
     * Should not send optional {@link #gameState} with this reason.
     */
    public static final int REASON_NOT_YOUR_TURN = 2;

    /**
     * Reason code when player is current but
     * can't take the requested action right now, probably because of game state.
     */
    public static final int REASON_NOT_NOW = 3;

    /**
     * Reason code when the requested location/coordinate isn't permitted ("can't build here").
     *<P>
     * {@link #detailValue1} = piece type like {@link SOCPlayingPiece#ROAD}, or -1 if not applicable<BR>
     * {@link #detailValue2} = requested placement coordinate
     */
    public static final int REASON_LOCATION = 4;

    /**
     * Reason code when it's the player's turn and the right game state,
     * but must decline because of other specifics like wrong number of resources requested by Year of Plenty,
     * or asking to rob a player who's not a possible victim.
     *<P>
     * Server may choose to instead send less-specific {@link #REASON_NOT_NOW}.
     */
    public static final int REASON_SPECIFICS = 5;

    /**
     * Name of the game.
     */
    public final String gameName;

    /**
     * Optional current game state like {@link SOCGame#WAITING_FOR_DISCARDS}, or 0.
     * Can help prompt robots to take action if needed.
     */
    public final int gameState;

    /**
     * Reason the request was declined: {@link #REASON_NOT_NOW}, {@link #REASON_NOT_YOUR_TURN}, etc.
     * If value not defined at client, client should treat it as {@link #REASON_OTHER}.
     * Future versions may use negative values.
     */
    public final int reasonCode;

    /**
     * Optional information related to the {@link #reasonCode}, or 0.
     */
    public final int detailValue1;

    /**
     * Optional information related to the {@link #reasonCode}, or 0.
     */
    public final int detailValue2;

    /**
     * Optional localized reason text, or {@code null} if
     * client should choose text based on {@link #reasonCode}.
     */
    public final String reasonText;

    /**
     * Create a {@link SOCDeclinePlayerRequest} message.
     *
     * @param gameName  name of the game
     * @param gameState  current game state for {@link #gameState} if needed, or 0
     * @param reasonCode  Reason to decline the request:
     *     {@link SOCDeclinePlayerRequest#REASON_NOT_NOW}, {@link SOCDeclinePlayerRequest#REASON_NOT_YOUR_TURN}, etc
     * @param detailValue1  message's {@link #detailValue1} if needed, or 0
     * @param detailValue2  message's {@link #detailValue2} if needed, or 0
     * @param reasonText  message's {@link #reasonText} if needed, or {@code null}
     */
    public SOCDeclinePlayerRequest
        (final String gameName, final int gameState, final int reasonCode,
         final int detailValue1, final int detailValue2, final String reasonText)
    {
        messageType = DECLINEPLAYERREQUEST;
        this.gameName = gameName;
        this.gameState = gameState;
        this.reasonCode = reasonCode;
        this.detailValue1 = detailValue1;
        this.detailValue2 = detailValue2;
        this.reasonText = reasonText;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return gameName;
    }

    /**
     * Minimum version where this message type is used.
     * Introduced in v2.5.00 ({@link #MIN_VERSION}).
     * @return Version number, 2500 for JSettlers 2.5.00
     */
    @Override
    public final int getMinimumVersion() { return MIN_VERSION; }

    /**
     * {@link #DECLINEPLAYERREQUEST} sep gameName sep2 reasonCode
     * [sep2 detailValue1 sep2 detailValue2 [sep2 reasonText]]
     *
     * @return the command string
     */
    public String toCmd()
    {
        StringBuilder sb = new StringBuilder
            (DECLINEPLAYERREQUEST + sep + gameName + sep2 + gameState + sep2 + reasonCode);
        if ((detailValue1 != 0) || (detailValue2 != 0) || (reasonText != null))
        {
            sb.append(sep2_char).append(detailValue1)
              .append(sep2_char).append(detailValue2);
            if (reasonText != null)
                sb.append(sep2_char).append(reasonText);
        }

        return sb.toString();
    }

    /**
     * Parse the command String into a {@link SOCDeclinePlayerRequest} message.
     *
     * @param cmd   the String to parse, from {@link #toCmd()}
     * @return    a SOCDeclinePlayerRequest message, or {@code null} if parsing errors
     */
    public static SOCDeclinePlayerRequest parseDataStr(final String cmd)
    {
        StringTokenizer st = new StringTokenizer(cmd, sep2);

        try
        {
            final String ga;
            final int gaState, rcode;
            int detail1 = 0, detail2 = 0;
            String rtext = null;

            ga = st.nextToken();
            gaState = Integer.parseInt(st.nextToken());
            rcode = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens())
            {
                detail1 = Integer.parseInt(st.nextToken());
                detail2 = Integer.parseInt(st.nextToken());
                if (st.hasMoreTokens())
                {
                    // get all of the rest for rtext, by choosing an unlikely delimiter character
                    rtext = st.nextToken(Character.toString( (char) 1 )).trim();
                    if (rtext.startsWith(SOCMessage.sep2))
                        rtext = rtext.substring(1);  // started with sep2, since it isn't delimiter anymore
                }
            }

            return new SOCDeclinePlayerRequest(ga, gaState, rcode, detail1, detail2, rtext);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder
            ("SOCDeclinePlayerRequest:game=" + gameName + "|state=" + gameState + "|reason=" + reasonCode);
        if ((detailValue1 != 0) || (detailValue2 != 0) || (reasonText != null))
        {
            sb.append("|detail1=").append(detailValue1)
              .append("|detail2=").append(detailValue2);
            if (reasonText != null)
                sb.append("|text=").append(reasonText);
        }

        return sb.toString();
    }

}
