/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2009 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.client;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;

import java.awt.Button;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


/**
 * Two-mode panel to display either a short status message, or a
 * resource trade offer (and counter-offer).
 *<P>
 * The status message mode is used for tasks such as:
 *<UL>
 *  <LI> Saying "no thanks" to a trade offer
 *  <LI> Showing vote on a board reset request
 *  <LI> Showing the player is deciding what to discard
 *</UL>
 *<P>
 * To set this panel's position or size, please use {@link #setBounds(int, int, int, int)},
 * because it is overridden to also update a "compact mode" flag for counter-offer layout.
 */
public class TradeOfferPanel extends Panel
{
    public static final String OFFER_MODE = "offer";
    public static final String MESSAGE_MODE = "message";

    /**
     * Typical height of offer panel, when visible. (Includes
     * {@link #OFFER_BUTTONS_HEIGHT}, but not {@link #OFFER_COUNTER_HEIGHT}.)
     * For convenience of other classes' layout calculations.
     * Actual height (buttons' y-positions + height) is set dynamically in OfferPanel.doLayout.
     * @since 1.1.08
     */
    public static final int OFFER_HEIGHT
        = (5 + 32 + (SquaresPanel.HEIGHT + 8)) + 18 + 10;
        // As calculated in OfferPanel.doLayout():
        //    top = between 5 and 18: = (h / (int)(.5 * ColorSquareLarger.HEIGHT_L)) + 5
        //    squaresHeight = squares.getBounds().height + 8
        //    buttonY = top + 32 + squaresHeight
        //    buttonH = 18
        //    inset = 10
        //    HEIGHT = buttonY + buttonH + inset

    /**
     * Additional height of offer (part of {@link #OFFER_HEIGHT})
     * when the "offer"/"accept"/"reject" buttons are showing.
     * That is, when not in counter-offer mode.
     * For convenience of other classes' layout calculations.
     * Based on calculations within OfferPanel.doLayout.
     * @since 1.1.08
     */
    public static final int OFFER_BUTTONS_HEIGHT = 26;

    /**
     * Typical height of counter-offer panel, when visible.
     * For convenience of other classes' layout calculations.
     * Actual height of counter-offer (offerBox) is set dynamically in OfferPanel.doLayout.
     * @since 1.1.08
     */
    public static final int OFFER_COUNTER_HEIGHT
        = SquaresPanel.HEIGHT + 24 + 16 + ColorSquareLarger.HEIGHT_L;
        // As calculated in OfferPanel.doLayout():
        //   squaresHeight = squares.getBounds().height + 24
        //   lineH = ColorSquareLarger.HEIGHT_L
        //   HEIGHT = squaresHeight + 16 + lineH

    protected static final int[] zero = { 0, 0, 0, 0, 0 };
    static final String OFFER = "counter";
    static final String ACCEPT = "accept";
    static final String REJECT = "reject";
    static final String SEND = "send";
    static final String CLEAR = "clear";
    static final String CANCEL = "cancel";
    static final Color insideBGColor = new Color(255, 230, 162);
    int from;
    SOCHandPanel hp;
    SOCPlayerInterface pi;

    String mode;
    CardLayout cardLayout;
    MessagePanel messagePanel;
    OfferPanel offerPanel;

    /**
     * If true, display counter-offer in a "compact mode" layout
     * because the panel's height is too short for the normal arrangement.
     * Calculated using {@link #OFFER_HEIGHT} + {@link #OFFER_COUNTER_HEIGHT}
     *     - {@link #OFFER_BUTTONS_HEIGHT}.
     * @since 1.1.08
     */
    private boolean counterCompactMode;

    /**
     * If true, hide the original offer's balloon point
     * (see {@link SpeechBalloon#setBalloonPoint(boolean)})
     * when the counter-offer is visible.
     * @since 1.1.08 
     */
    private boolean counterHidesBalloonPoint;

    /**
     * Creates a new TradeOfferPanel object.
     */
    public TradeOfferPanel(SOCHandPanel hp, int from)
    {
        this.hp = hp;
        this.from = from;
        pi = hp.getPlayerInterface();

        setBackground(pi.getPlayerColor(from));
        setForeground(Color.black);

        messagePanel = new MessagePanel();
        offerPanel = new OfferPanel();
        
        cardLayout = new CardLayout();
        setLayout(cardLayout);

        add(messagePanel, MESSAGE_MODE); // first added = first shown
        add(offerPanel, OFFER_MODE);
        mode = MESSAGE_MODE;

        counterCompactMode = false;
        counterHidesBalloonPoint = false;
    }
    
    private class MessagePanel extends Panel
    {
        SpeechBalloon balloon;
        Label msg;
        int msgHeight;

        /**
         * Creates a new MessagePanel object.
         */
        public MessagePanel()
        {
            setLayout(null);
            setFont(new Font("Helvetica", Font.PLAIN, 18));
        
            msg = new Label(" ", Label.CENTER);
            msg.setBackground(insideBGColor);
            msgHeight = 0;  // set in doLayout
            add(msg);
        
            balloon = new SpeechBalloon(pi.getPlayerColor(from));
            add(balloon);
        }
        
        /**
         * Update the text shown in this messagepanel.
         * Does not show or hide the panel, only changes the label text.
         * @param message message to display, or null for no text
         */
        public void update(String message)
        {
            if (message != null)
                msg.setText(message);
            else
                msg.setText(" ");
        }
        
        /**
         * Just for the message panel
         */
        public void doLayout()
        {
            Dimension dim = getSize();
            int buttonW = 48;
            int inset = 10;
            if (msgHeight == 0)
                msgHeight = getFontMetrics(msg.getFont()).getHeight() + 4;
            int w = Math.min((2*(inset+5) + 3*buttonW), dim.width);
            int h = Math.min(92 + 2 * ColorSquareLarger.HEIGHT_L, dim.height);
            int msgY = (h - msgHeight) / 2;
            if (msgY < 0)
                msgY = 0;
            if (msgHeight > h)
                msgHeight = h;
            msg.setBounds(inset, msgY, w - (2 * inset), msgHeight);
            balloon.setBounds(0, 0, w, h);
        }
    }

    /** Contains both offer and counter-offer; see {@link #setCounterOfferVisible(boolean)} */
    private class OfferPanel extends Panel implements ActionListener
    {
        /** Balloon to hold offer received */
        SpeechBalloon balloon;

        /** "Offered To" line 1 */
        Label toWhom1;
        /** "Offered To" line 2 for wrapping; usually blank */
        Label toWhom2;
        /** "I Give" */
        Label giveLab;
        /** "I Get" */
        Label getLab;

        /** Offer's resources */
        SquaresPanel squares;
        /** send button for counter-offer */
        Button offerBut;
        Button acceptBut;
        Button rejectBut;

        /** Counter-offer to send */
        ShadowedBox offerBox;
        Label counterOfferToWhom;
        boolean counterOffer_playerInit = false;
        SquaresPanel offerSquares;
        Label giveLab2;
        Label getLab2;
        Button sendBut;
        Button clearBut;
        Button cancelBut;
        boolean offered;
        SOCResourceSet give;
        SOCResourceSet get;
        int[] giveInt = new int[5];
        int[] getInt = new int[5];

        /** is the counter-offer showing? use {@link #setCounterOfferVisible(boolean)} to change. */
        boolean counterOfferMode = false;

        /**
         * Creates a new OfferPanel object.
         * The counter-offer is initially hidden.
         */
        public OfferPanel()
        {
            setLayout(null);
            setFont(new Font("Helvetica", Font.PLAIN, 10));

            /** Offer received */

            toWhom1 = new Label();
            toWhom1.setBackground(insideBGColor);
            add(toWhom1);

            toWhom2 = new Label();
            toWhom2.setBackground(insideBGColor);
            add(toWhom2);

            /** Offer's resources */
            squares = new SquaresPanel(false);
            add(squares);

            giveLab = new Label("I Give: ");
            giveLab.setBackground(insideBGColor);
            add(giveLab);
            new AWTToolTip("Opponent gives to you", giveLab);

            getLab = new Label("I Get: ");
            getLab.setBackground(insideBGColor);
            add(getLab);
            new AWTToolTip("You give to opponent", getLab);

            giveInt = new int[5];
            getInt = new int[5];

            acceptBut = new Button("Accept");
            acceptBut.setActionCommand(ACCEPT);
            acceptBut.addActionListener(this);
            add(acceptBut);

            rejectBut = new Button("Reject");
            rejectBut.setActionCommand(REJECT);
            rejectBut.addActionListener(this);
            add(rejectBut);

            offerBut = new Button("Counter");
            offerBut.setActionCommand(OFFER);
            offerBut.addActionListener(this);
            add(offerBut);

            /** Counter-offer to send */

            counterOfferToWhom = new Label();
            counterOfferToWhom.setVisible(false);
            add(counterOfferToWhom);

            sendBut = new Button("Send");
            sendBut.setActionCommand(SEND);
            sendBut.addActionListener(this);
            sendBut.setVisible(false);
            add(sendBut);

            clearBut = new Button("Clear");
            clearBut.setActionCommand(CLEAR);
            clearBut.addActionListener(this);
            clearBut.setVisible(false);
            add(clearBut);

            cancelBut = new Button("Cancel");
            cancelBut.setActionCommand(CANCEL);
            cancelBut.addActionListener(this);
            cancelBut.setVisible(false);
            add(cancelBut);

            offerSquares = new SquaresPanel(true);
            offerSquares.setVisible(false);
            add(offerSquares);

            giveLab2 = new Label("Give Them: ");
            giveLab2.setVisible(false);
            add(giveLab2);
            new AWTToolTip("Give to opponent", giveLab2);

            getLab2 = new Label("You Get: ");
            getLab2.setVisible(false);
            add(getLab2);
            new AWTToolTip("Opponent gives to you", getLab2);

            // correct the interior when we can get our player color
            offerBox = new ShadowedBox(pi.getPlayerColor(from), Color.white);
            offerBox.setVisible(false);
            add(offerBox);

            /** done with counter-offer */

            balloon = new SpeechBalloon(pi.getPlayerColor(from));
            add(balloon);
        }

        /**
         * Update the displayed offer.
         * Should be called when already in {@link TradeOfferPanel#OFFER_MODE},
         * or about to switch to it via {@link TradeOfferPanel#setOffer(SOCTradeOffer)}.
         *
         * @param  offer  the trade offer, with set of resources being given and asked for
         */
        public void update(SOCTradeOffer offer)
        {
            this.give = offer.getGiveSet();
            this.get = offer.getGetSet();
            boolean[] offerList = offer.getTo();
        
            SOCPlayer player = hp.getGame().getPlayer(hp.getClient().getNickname());

            if (player != null)
            {
                if (! counterOffer_playerInit)
                {
                    // do we have to fill in opponent's name for 1st time,
                    // and set up colors?
                    Color ourPlayerColor = pi.getPlayerColor(player.getPlayerNumber());
                    giveLab2.setBackground(ourPlayerColor);
                    getLab2.setBackground(ourPlayerColor);
                    counterOfferToWhom.setBackground(ourPlayerColor);
                    offerBox.setInterior(ourPlayerColor);
                    counterOfferToWhom.setText
                        ("Counter to " + hp.getPlayer().getName() + ":");

                    counterOffer_playerInit = true;
                }                
                offered = offerList[player.getPlayerNumber()];
            }
            else
            {
                offered = false;
            }
        
            SOCGame ga = hp.getGame();
            String names1 = "Offered to: ";
            String names2 = null;

            int cnt = 0;

            for (; cnt < ga.maxPlayers; cnt++)
            {
                if (offerList[cnt] && ! ga.isSeatVacant(cnt))
                {
                    names1 += ga.getPlayer(cnt).getName();

                    break;
                }
            }

            cnt++;

            int len = names1.length();

            for (; cnt < ga.maxPlayers; cnt++)
            {
                if (offerList[cnt] && ! ga.isSeatVacant(cnt))
                {
                    String name = ga.getPlayer(cnt).getName();
                    len += name.length();  // May be null if vacant
                    
                    if (len < 25)
                    {
                        names1 += ", ";
                        names1 += name;
                    }
                    else
                    {
                        if (names2 == null)
                        {
                            names1 += ",";
                            names2 = new String(name);
                        }
                        else
                        {
                            names2 += ", ";
                            names2 += name;
                        }
                    }
                }
            }
            toWhom1.setText(names1);
            toWhom2.setText(names2);

            /**
             * Note: this only works if SOCResourceConstants.CLAY == 1
             */
            for (int i = 0; i < 5; i++)
            {
                giveInt[i] = give.getAmount(i + 1);
                getInt[i] = get.getAmount(i + 1);
            }
            squares.setValues(giveInt, getInt);

            // enables accept,reject,offer Buttons if 'offered' is true
            setCounterOfferVisible(counterOfferMode);
            validate();
        }

        /**
         * Custom layout for this OfferPanel
         */
        public void doLayout()
        {
            FontMetrics fm = this.getFontMetrics(this.getFont());
            Dimension dim = getSize();
            final int buttonW = 48;
            final int buttonH = 18;
            int inset = 10;

            // At initial call to doLayout: dim.width, .height == 0.
            int w = Math.min((2*(inset+5) + 3*buttonW), dim.width);
            int h = Math.min(92 + 2 * ColorSquareLarger.HEIGHT_L, dim.height);
            int top = (h / (int)(.5 * ColorSquareLarger.HEIGHT_L)) + 5;

            if (counterOfferMode)
            {
                // show the counter offer controls

                final int lineH = ColorSquareLarger.HEIGHT_L;
                h = Math.min(60 + 2 * ColorSquareLarger.HEIGHT_L, h);
                top = (h / (int)(.5 * ColorSquareLarger.HEIGHT_L)) + 10;

                if (counterCompactMode)
                {
                    inset = 2;
                    balloon.setBalloonPoint(false);
                    top -= (h / 8);  // Shift everything up this far, since we
                                     // don't need to leave room for balloon point.
                } else {
                    balloon.setBalloonPoint(! counterHidesBalloonPoint);
                }

                final int giveW = fm.stringWidth(giveLab2.getText()) + 1;

                toWhom1.setBounds(inset, top, w - 20, 14);
                toWhom2.setBounds(inset, top + 14, w - 20, 14);

                giveLab.setBounds(inset, top + 32, giveW, lineH);
                getLab.setBounds(inset, top + 32 + lineH, giveW, lineH);
                squares.setLocation(inset + giveW, top + 32);

                int squaresHeight = squares.getBounds().height + 24;
                counterOfferToWhom.setBounds(inset + 7, top + 28 + squaresHeight, w - 33, 12);
                giveLab2.setBounds(inset, top + 28 + lineH + squaresHeight, giveW, lineH);
                getLab2.setBounds(inset, top + 28 + 2*lineH + squaresHeight, giveW, lineH);
                offerSquares.setLocation(inset + giveW, top + 28 + lineH + squaresHeight);
                offerSquares.doLayout();

                if (counterCompactMode)
                {
                    // Buttons to right of counterOfferToWhom
                    int buttonY = top + 28 + squaresHeight;
                    final int buttonX = inset + giveW + squares.getBounds().width + 2;

                    sendBut.setBounds(buttonX, buttonY, buttonW, buttonH);
                    buttonY += buttonH + 2;
                    clearBut.setBounds(buttonX, buttonY, buttonW, buttonH);
                    buttonY += buttonH + 2;
                    cancelBut.setBounds(buttonX, buttonY, buttonW, buttonH);

                    if (w < (buttonX + buttonW + ShadowedBox.SHADOW_SIZE + 2))
                        w = buttonX + buttonW + ShadowedBox.SHADOW_SIZE + 2;
                } else {
                    // Buttons below giveLab2, offerSquares
                    final int buttonY = top + 8 + (2 * squaresHeight) + lineH;

                    sendBut.setBounds(inset, buttonY, buttonW, buttonH);
                    clearBut.setBounds(inset + 5 + buttonW, buttonY, buttonW, buttonH);
                    cancelBut.setBounds(inset + (2 * (5 + buttonW)), buttonY, buttonW, buttonH);
                }

                if (counterCompactMode)
                {
                    // No balloon point, so top h/8 of its bounding box is empty
                    balloon.setBounds(0, -h/8, w, h - (h/16));
                } else {
                    balloon.setBounds(0, 0, w, h);
                }
                offerBox.setBounds(0, top + 18 + squaresHeight, w, squaresHeight + 16 + lineH);

                // If offerBox height calculation changes, please update OFFER_COUNTER_HEIGHT.
            }
            else
            {
                balloon.setBalloonPoint(true);

                int lineH = ColorSquareLarger.HEIGHT_L;
                int giveW = fm.stringWidth("I Give: ") + 2;

                toWhom1.setBounds(inset, top, w - 20, 14);
                toWhom2.setBounds(inset, top + 14, w - 20, 14);
                giveLab.setBounds(inset, top + 32, giveW, lineH);
                getLab.setBounds(inset, top + 32 + lineH, giveW, lineH);
                squares.setLocation(inset + giveW, top + 32);
                squares.doLayout();
                
                if (offered)
                {
                    int squaresHeight = squares.getBounds().height + 8;
                    int buttonY = top + 32 + squaresHeight;
                    acceptBut.setBounds(inset, buttonY, buttonW, buttonH);
                    rejectBut.setBounds(inset + 5 + buttonW, buttonY, buttonW, buttonH);
                    offerBut.setBounds(inset + (2 * (5 + buttonW)), buttonY, buttonW, buttonH);
                }

                balloon.setBounds(0, 0, w, h);

                // If rejectBut height calculation changes, please update OFFER_HEIGHT.
                // If change in the height dfference of "offered" buttons showing/not showing,
                // please update OFFER_BUTTONS_HEIGHT.
            }
        }

        /**
         * Respond to button-related user input
         *
         * @param e Input event and source
         */
        public void actionPerformed(ActionEvent e)
        {
            try {
            String target = e.getActionCommand();

            if (target == OFFER)
            {
                setCounterOfferVisible(true);
            }
            else if (target == CLEAR)
            {
                offerSquares.setValues(zero, zero);
            }
            else if (target == SEND)
            {
                SOCGame game = hp.getGame();
                SOCPlayer player = game.getPlayer(pi.getClient().getNickname());

                if (game.getGameState() == SOCGame.PLAY1)
                {
                    // slot for each resource, plus one for 'unknown' (remains 0)
                    int[] give = new int[5];
                    int[] get = new int[5];
                    int giveSum = 0;
                    int getSum = 0;
                    offerSquares.getValues(give, get);
                    
                    for (int i = 0; i < 5; i++)
                    {
                        giveSum += give[i];
                        getSum += get[i];
                    }

                    SOCResourceSet giveSet = new SOCResourceSet(give);
                    SOCResourceSet getSet = new SOCResourceSet(get);
                    
                    if (!player.getResources().contains(giveSet))
                    {
                        pi.print("*** You can't offer what you don't have.");
                    }
                    else if ((giveSum == 0) || (getSum == 0))
                    {
                        pi.print("*** A trade must contain at least one resource card from each player.");
                    }
                    else
                    {
                        // arrays of bools are initially false
                        boolean[] to = new boolean[game.maxPlayers];
                        // offer to the player that made the original offer
                        to[from] = true;

                        SOCTradeOffer tradeOffer =
                            new SOCTradeOffer (game.getName(),
                                               player.getPlayerNumber(),
                                               to, giveSet, getSet);
                        hp.getClient().offerTrade(game, tradeOffer);

                        setCounterOfferVisible(true);
                    }
                }
            }

            if (target == CANCEL)
            {
                setCounterOfferVisible(false);
            }

            if (target == REJECT)
            {
                setVisible(false);
                hp.rejectOfferAtClient();
            }

            if (target == ACCEPT)
            {
                //int[] tempGive = new int[5];
                //int[] tempGet = new int[5];
                //squares.getValues(tempGive, tempGet);
                hp.getClient().acceptOffer(hp.getGame(), from);
            }
            } catch (Throwable th) {
                pi.chatPrintStackTrace(th);
            }            
        }

        /** 
         * show or hide our counter-offer panel, below the trade-offer panel.
         * This should be called when in {@link TradeOfferPanel#OFFER_MODE},
         * not in {@link TradeOfferPanel#MESSAGE_MODE}.
         */
        private void setCounterOfferVisible(boolean visible)
        {
            boolean haveResources = true;
            if(offered)
            {
                SOCPlayer player = hp.getGame().getPlayer(hp.getClient().getNickname());
                haveResources = player.getResources().contains(get);
            }

            giveLab2.setVisible(visible);
            getLab2.setVisible(visible);
            counterOfferToWhom.setVisible(visible);
            if (! visible)
            {
                // Clear counteroffer for next use
                offerSquares.setValues(zero, zero);
            }
            offerSquares.setVisible(visible);

            sendBut.setVisible(visible);
            clearBut.setVisible(visible);
            cancelBut.setVisible(visible);
            offerBox.setVisible(visible);

            acceptBut.setVisible(haveResources && offered && ! visible);
            rejectBut.setVisible(offered && ! visible);
            offerBut.setVisible(offered && ! visible);

            counterOfferMode = visible;
            hp.offerCounterOfferVisibleChanged(visible);
            validate();
        }
    }

    /**
     * Switch to the Message from another player, or clear
     * its most recent contents.
     * If <tt>message</tt> is null, only clear the message text,
     * don't change the visibility.
     * Otherwise, set the message text and show the Message.
     * If an offer/counteroffer were visible, they are
     * not lost; call {@link #setOffer(SOCTradeOffer)} to
     * show them again.
     *
     * @param  message  the message message to show, or null.
     *      Null does not show or hide the panel, only clears the label text.
     */
    public void setMessage(String message)
    {
        messagePanel.update(message);
        if (message != null)
        {
            cardLayout.show(this, mode = MESSAGE_MODE);
            validate();
        }
    }

    /**
     * Update to view the of an offer from another player.
     * If counter-offer was previously shown, show it again.
     * This lets us restore the offer view after message mode.
     * To clear values to zero, and hide the counter-offer box,
     * call {@link #clearOffer()}.
     *
     * @param  currentOffer the trade being proposed
     */
    public void setOffer(SOCTradeOffer currentOffer)
    {
        offerPanel.update(currentOffer);
        cardLayout.show(this, mode = OFFER_MODE);
        validate();
    }

    /**
     * Set the offer and counter-offer contents to zero.
     * Clear counteroffer mode.
     */
    public void clearOffer()
    {
        offerPanel.squares.setValues(zero, zero);
        offerPanel.offerSquares.setValues(zero, zero);
        if (offerPanel.counterOfferMode)
        {
            offerPanel.counterOfferMode = false;
            invalidate();
        }
        repaint();
    }

    /**
     * Is this offerpanel in counteroffer mode, with a trade offer
     * and counter-offer showing?
     * @return  true if in counter-offer mode
     * @since 1.1.08
     */
    public boolean isCounterOfferMode()
    {
        return offerPanel.counterOfferMode;
    }

    /**
     * Returns current mode of <code>TradeOfferPanel.OFFER_MODE</code>, or
     * <code>TradeOfferPanel.MESSAGE_MODE</code>, which has been set by using
     * {@link #setOffer} or {@link #setMessage}
     */
    public String getMode() {
        return mode;
    }

    /**
     * If true, hide the original offer's balloon point
     * (see {@link SpeechBalloon#setBalloonPoint(boolean)})
     * when the counter-offer is visible.
     * @since 1.1.08 
     */
    public boolean doesCounterHideBalloonPoint()
    {
        return counterHidesBalloonPoint;
    }

    /**
     * If true, hide the original offer's balloon point
     * (see {@link SpeechBalloon#setBalloonPoint(boolean)})
     * when the counter-offer is visible.
     * @param hide  Hide it during counter-offer?
     * @since 1.1.08 
     */
    public void setCounterHidesBalloonPoint(final boolean hide)
    {
        if (counterHidesBalloonPoint == hide)
            return;
        counterHidesBalloonPoint = hide;
        offerPanel.balloon.setBalloonPoint(! hide);
    }

    /**
     * Move and/or resize this panel.
     * Overriden to also update "compact mode" flag for counter-offer.
     * @since 1.1.08
     */
    public void setBounds(final int x, final int y, final int width, final int height)
    {
        super.setBounds(x, y, width, height);
        counterCompactMode =
            (height < (OFFER_HEIGHT + OFFER_COUNTER_HEIGHT - OFFER_BUTTONS_HEIGHT));
    }

}  // TradeOfferPanel
