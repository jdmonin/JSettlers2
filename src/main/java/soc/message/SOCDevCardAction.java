/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2012-2014,2017-2018 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCDevCardConstants;  // for javadoc's use
import soc.proto.Data.DevCardValue;
import soc.proto.GameMessage;
import soc.proto.Message;
import soc.proto.GameMessage.InventoryItemAction;


/**
 * This message from the server means that a player is
 * {@link #DRAW drawing} or {@link #PLAY playing}
 * a development card; response to {@link SOCPlayDevCardRequest}.
 *<P>
 * If a robot asks to play a dev card that they can't right now,
 * the server sends that bot {@code DEVCARDACTION}(-1, {@link #CANNOT_PLAY}, cardtype).
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
     * The type of development card, like {@link SOCDevCardConstants#ROADS}
     */
    private int cardType;

    /**
     * Action type
     */
    private int actionType;

    /**
     * Create a DevCardAction message.
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for action type {@link #CANNOT_PLAY}
     * @param ac  the type of action
     * @param ct  the type of card, like {@link SOCDevCardConstants#ROADS}
     */
    public SOCDevCardAction(String ga, int pn, int ac, int ct)
    {
        messageType = DEVCARDACTION;
        game = ga;
        playerNumber = pn;
        actionType = ac;
        cardType = ct;
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
     * @return the card type, like {@link SOCDevCardConstants#ROADS}
     */
    public int getCardType()
    {
        return cardType;
    }

    /**
     * DEVCARDACTION sep game sep2 playerNumber sep2 actionType sep2 cardType
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, actionType, cardType);
    }

    /**
     * DEVCARDACTION sep game sep2 playerNumber sep2 actionType sep2 cardType
     *
     * @param ga  the game name
     * @param pn  the player number
     * @param ac  the type of action
     * @param ct  the type of card
     * @return    the command string
     */
    public static String toCmd(String ga, int pn, int ac, int ct)
    {
        return DEVCARDACTION + sep + ga + sep2 + pn + sep2 + ac + sep2 + ct;
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

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            ac = Integer.parseInt(st.nextToken());
            ct = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCDevCardAction(ga, pn, ac, ct);
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        final DevCardValue cvalue = ProtoMessageBuildHelper.toDevCardValue(cardType);

        GameMessage.InventoryItemAction.Builder b
            = GameMessage.InventoryItemAction.newBuilder()
                .setPlayerNumber(playerNumber);
        if (cvalue != null)
            b.setDevCardValue(cvalue);

        final InventoryItemAction._ActionType act;

        switch (actionType)
        {
        case DRAW:     act = InventoryItemAction._ActionType.DRAW;  break;
        case PLAY:     act = InventoryItemAction._ActionType.PLAY;  break;
        case ADD_NEW:  act = InventoryItemAction._ActionType.ADD_NEW;  break;
        case ADD_OLD:  act = InventoryItemAction._ActionType.ADD_OLD;  break;
        case CANNOT_PLAY: act = InventoryItemAction._ActionType.CANNOT_PLAY;  break;
        default:
            act = null;
        }
        if (act != null)
            b.setActionType(act);

        if ((cvalue != null) && (actionType != PLAY) && (actionType != CANNOT_PLAY))
        {
            final boolean isVP = ProtoMessageBuildHelper.isDevCardVP(cvalue);
            b.setIsVP(isVP);
            b.setIsKept(isVP);
            b.setIsPlayable(! isVP);
            b.setCanCancelPlay(! isVP);
        }

        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGaName(game).setInvItemAction(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
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
            + "|actionType=" + act + "|cardType=" + cardType;
    }
}
