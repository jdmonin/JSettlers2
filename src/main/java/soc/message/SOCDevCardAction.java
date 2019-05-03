/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2012-2014,2017-2019 Jeremy D Monin <jeremy@nand.net>
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
import java.util.StringTokenizer;

import soc.game.SOCDevCardConstants;  // for javadocs only


/**
 * This message from the server means that a player is
 * {@link #DRAW drawing} or {@link #PLAY playing}
 * a development card; server's response to {@link SOCPlayDevCardRequest}.
 * Sometimes sent to a specific player, sometimes to all game members.
 *<P>
 * If a robot asks to play a dev card that they can't right now,
 * the server replies to that bot with DevCardAction(-1, {@link #CANNOT_PLAY}, cardtype).
 *<P>
 * Not sent from client, which sends {@link SOCBuyDevCardRequest} or {@link SOCPlayDevCardRequest} instead.
 *<P>
 * At end of game (state {@link soc.game.SOCGame#OVER OVER}), server v2.0.00 reveals players'
 * hidden Victory Point cards by announcing a DevCardAction(pn, {@link #ADD_OLD}, cardtype [, cardtype, ...])
 * for each player that has them. Older server versions used {@link SOCGameTextMsg} instead.
 * Is sent to all game members; a client player should ignore messages about their own cards
 * in state {@code OVER} by checking {@link #getPlayerNumber()}.
 *<P>
 * The multiple-cardtype form ({@link #getCardTypes()} != {@code null}) is currently used only at end of game.
 *<P>
 * Before v2.0.00, this message type was {@code DEVCARD} (class name {@code SOCDevCard}).
 *
 * @author Robert S Thomas
 * @see SOCInventoryItemAction
 */
public class SOCDevCardAction extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Minimum client version which can send multiple card types in 1 message: v2.0.00.
     * Same version as {@link SOCPlayerElement#VERSION_FOR_CARD_ELEMENTS}.
     * @since 2.0.00
     */
    public static final int VERSION_FOR_MULTIPLE = 2000;

    /**
     * Maximum number of cards to send in a reasonable message: 100.
     * @since 2.0.00
     */
    public static final int MAX_MULTIPLE = 100;

    /** dev card action DRAW (Buy): Add as new to player's hand */
    public static final int DRAW = 0;

    /** dev card action PLAY: remove as old from player's hand */
    public static final int PLAY = 1;

    /**
     * dev card action ADD_NEW: Add as new to player's hand.
     *<P>
     * Before v2.0.00 this was {@code ADDNEW}.
     */
    public static final int ADD_NEW = 2;

    /**
     * dev card action ADD_OLD: Add as old to player's hand.
     *<P>
     * Before v2.0.00 this was {@code ADDOLD}.
     */
    public static final int ADD_OLD = 3;

    /**
     * dev card action CANNOT_PLAY: The bot can't play the requested card at this time.
     * This is sent only to the requesting robot, so playerNumber is always -1 in this message.
     * @since 1.1.17
     */
    public static final int CANNOT_PLAY = 4;

    /**
     * Name of game
     */
    private String game;

    /**
     * Player number
     */
    private int playerNumber;

    /**
     * The type of development card, like {@link SOCDevCardConstants#ROADS} or {@link SOCDevCardConstants#UNKNOWN}.
     * If {@link #cardTypes} != {@code null}, this field is not used.
     */
    private final int cardType;

    /**
     * When sending multiple cards, each card's type; otherwise {@code null}, uses {@link #cardType} instead.
     * Not usable with action {@link #PLAY} or {@link #CANNOT_PLAY}.
     * @since 2.0.00
     */
    private final List<Integer> cardTypes;

    /**
     * Action type
     */
    private int actionType;

    /**
     * Create a DevCardAction message about 1 card.
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for action type {@link #CANNOT_PLAY}
     * @param ac  the type of action
     * @param ct  the type of card, like {@link SOCDevCardConstants#ROADS}
     *     or {@link SOCDevCardConstants#UNKNOWN}
     * @see #SOCDevCardAction(String, int, int, List)
     */
    public SOCDevCardAction(String ga, int pn, int ac, int ct)
    {
        messageType = DEVCARDACTION;
        game = ga;
        playerNumber = pn;
        actionType = ac;
        cardType = ct;
        cardTypes = null;
    }

    /**
     * Create a DevCardAction message about multiple cards.
     * This form is currently used only at end of game (state {@link soc.game.SOCGame#OVER OVER})
     * to reveal hidden Victory Point cards. So, bots ignore it.
     *
     * @param ga  name of the game
     * @param pn  the player number; cannot be &lt; 0
     * @param ac  the type of action; cannot be {@link #PLAY} or {@link #CANNOT_PLAY}
     * @param ct  the types of card, like {@link SOCDevCardConstants#ROADS}
     *     or {@link SOCDevCardConstants#UNKNOWN}; cannot be {@code null} or empty,
     *     or longer than {@link #MAX_MULTIPLE}
     * @throws IllegalArgumentException  if problem with {@code pn}, {@code ac}, or {@code ct}
     * @see #SOCDevCardAction(String, int, int, int)
     * @since 2.0.00
     */
    public SOCDevCardAction(String ga, int pn, int ac, List<Integer> ct)
        throws IllegalArgumentException
    {
        if (pn < 0)
            throw new IllegalArgumentException("pn: " + pn);
        if ((ac == PLAY) || (ac == CANNOT_PLAY))
            throw new IllegalArgumentException("action: " + pn);
        if (ct == null)
            throw new IllegalArgumentException("ct: null");
        final int S = ct.size();
        if ((S == 0) || (S > MAX_MULTIPLE))
            throw new IllegalArgumentException("ct size: " + S);

        messageType = DEVCARDACTION;
        game = ga;
        playerNumber = pn;
        actionType = ac;
        cardType = 0;
        cardTypes = ct;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the player number, or -1 for action type {@link #CANNOT_PLAY}
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the action type, like {@link #DRAW}
     */
    public int getAction()
    {
        return actionType;
    }

    /**
     * Get the card type, if message is about one card.
     * If about multiple cards, {@link #getCardTypes()} will be non-{@code null}.
     * @return the card type, like {@link SOCDevCardConstants#ROADS} or {@link SOCDevCardConstants#UNKNOWN}
     */
    public int getCardType()
    {
        return cardType;
    }

    /**
     * Get the card types, if message is about multiple cards.
     * @return list of card types, like {@link SOCDevCardConstants#ROADS} or {@link SOCDevCardConstants#UNKNOWN},
     *     or {@code null} if should use {@link #getCardType()} instead
     * @since 2.0.00
     */
    public List<Integer> getCardTypes()
    {
        return cardTypes;
    }

    /**
     * DEVCARDACTION sep game sep2 playerNumber sep2 actionType sep2 cardType [sep2 cardType ...]
     *
     * @return the command String
     */
    public String toCmd()
    {
        StringBuilder sb = new StringBuilder
            (DEVCARDACTION + sep + game + sep2 + playerNumber + sep2 + actionType);
        if (cardTypes == null)
        {
            sb.append(sep2);
            sb.append(cardType);
        } else {
            for (Integer ctype : cardTypes)
            {
                sb.append(sep2);
                sb.append(ctype);
            }
        }

        return sb.toString();
    }

    /**
     * Parse the command String into a DEVCARDACTION message.
     *
     * @param s   the String to parse
     * @return    a DEVCARDACTION message, or null if the data is garbled
     */
    public static SOCDevCardAction parseDataStr(String s)
    {
        String ga;
        int pn;
        int ac;
        int ct;
        List<Integer> ctypes = null;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            ac = Integer.parseInt(st.nextToken());
            ct = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens())
            {
                ctypes = new ArrayList<Integer>();
                ctypes.add(ct);
                for (int i = 2; st.hasMoreTokens() && (i <= MAX_MULTIPLE); ++i)
                    ctypes.add(Integer.parseInt(st.nextToken()));
                if (st.hasMoreTokens())
                    return null;  // more than MAX_MULTIPLE not allowed (possible DoS)
            }
        }
        catch (Exception e)
        {
            return null;
        }

        if (ctypes != null)
            return new SOCDevCardAction(ga, pn, ac, ctypes);
        else
            return new SOCDevCardAction(ga, pn, ac, ct);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        final String act;
        switch (actionType)
        {
        case DRAW:    act = "DRAW";  break;
        case PLAY:    act = "PLAY"; break;
        case ADD_NEW: act = "ADD_NEW"; break;
        case ADD_OLD: act = "ADD_OLD"; break;
        case CANNOT_PLAY: act = "CANNOT_PLAY"; break;
        default:      act = Integer.toString(actionType);
        }

        return "SOCDevCardAction:game=" + game + "|playerNum=" + playerNumber
            + "|actionType=" + act +
            ((cardTypes != null)
             ? "|cardTypes=" + cardTypes.toString()  // "[1, 5, 7]"
             : "|cardType=" + cardType);
    }

}
