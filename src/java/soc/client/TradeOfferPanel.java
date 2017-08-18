/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2009,2011-2013,2015,2017 Jeremy D Monin <jeremy@nand.net>
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
import java.util.TimerTask;


/**
 * Two-mode panel to display either a short status message, or a
 * resource trade offer (and counter-offer) from another player.
 *<P>
 * The status message mode is used for tasks such as:
 *<UL>
 *  <LI> Saying "no thanks" to a trade offer
 *  <LI> Showing vote on a board reset request
 *  <LI> Showing the player is deciding what to discard
 *</UL>
 *<P>
 * To use message mode, call {@link #setMessage(String)}.
 * To use trade offer mode, show {@link #setOffer(SOCTradeOffer)}.
 * To show or hide the panel in either mode, call {@link #setVisible(boolean)}.
 *<P>
 * To set this panel's position or size, please use {@link #setBounds(int, int, int, int)},
 * because it is overridden to also update a "compact mode" flag for counter-offer layout.
 */
@SuppressWarnings("serial")
public class TradeOfferPanel extends Panel
{
    /** i18n text strings; will use same locale as SOCPlayerClient's string manager.
     *  @since 2.0.00 */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /**
     * Mode to show a trade offer, not a message.
     * Made visible via {@link #setOffer(SOCTradeOffer)}.
     * @see #MESSAGE_MODE
     */
    public static final String OFFER_MODE = "offer";  // shows OfferPanel

    /**
     * Mode to show a message, not a trade offer.
     * Made visible via {@link #setMessage(String)}.
     * @see #OFFER_MODE
     */
    public static final String MESSAGE_MODE = "message";  // shows MessagePanel

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

    /** This panel's player number */
    private final int from;

    /**
     * True if {@link #from} is a robot player.
     * @since 1.2.00
     */
    private boolean isFromRobot;

    /** This TradeOfferPanel's parent hand panel, for action callbacks from buttons */
    private final SOCHandPanel hp;

    /** {@link #hp}'s parent player interface */
    private final SOCPlayerInterface pi;

    /**
     * Current mode: {@link #MESSAGE_MODE} to show {@link #messagePanel},
     * or {@link #OFFER_MODE} to show {@link #offerPanel}.
     */
    String mode;

    /** Layout which shows either {@link #messagePanel} or {@link #offerPanel}. */
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
     * @param hp  New TradeOfferPanel's parent hand panel, for action callbacks from trade buttons
     * @param from  {@code hp}'s player number
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

        addPlayer();  // set isFromRobot, etc

        counterCompactMode = false;
        counterHidesBalloonPoint = false;
    }

    /**
     * Panel to show when in {@link TradeOfferPanel#MESSAGE_MODE MESSAGE_MODE},
     * not {@link TradeOfferPanel#OFFER_MODE OFFER_MODE}.
     * @see OfferPanel
     */
    private class MessagePanel extends Panel
    {
        private SpeechBalloon balloon;
        /**
         * For 1 line of text, {@link #msg} contains the entire text.
         * For 2 lines separated by <tt>\n</tt>, {@link #msg} and {@link #msg2} are used.
         * (AWT Label is described as 1 line of text, although OSX respects \n in the text.)
         */
        private Label msg, msg2;
        private int oneLineHeight, msgHeight;

        /**
         * Number of lines of text; 1, or 2 if text contains <tt>\n</tt>.
         * After changing this, set {@link #msgHeight} = 0 and call {@link #validate()}.
         * @since 2.0.00
         */
        private int msgLines;

        /**
         * Creates a new MessagePanel object.
         * Give room for 1 or 2 centered lines of text.
         */
        public MessagePanel()
        {
            setLayout(null);
            setFont(new Font("SansSerif", Font.PLAIN, 18));

            msg = new Label(" ", Label.CENTER);
            msg.setBackground(insideBGColor);
            msg2 = new Label(" ", Label.CENTER);
            msg2.setBackground(insideBGColor);
            msg2.setVisible(false);
            oneLineHeight = 0;  // set once in doLayout
            msgHeight = 0;  // set in doLayout
            msgLines = 1;
            add(msg);
            add(msg2);

            balloon = new SpeechBalloon(pi.getPlayerColor(from));
            add(balloon);
        }

        /**
         * Update the text shown in this messagepanel.
         * Does not show or hide the panel, only changes the label text.
         * @param message message to display, or null for no text
         */
        public void update(final String message)
        {
            String newText;
            int newMsgLines, newlineIndex;
            if (message != null)
            {
                newText = message;
                newlineIndex = message.indexOf('\n');
                newMsgLines = (newlineIndex >= 0) ? 2 : 1;
            } else {
                newText = " ";
                newlineIndex = -1;
                newMsgLines = 1;
            }
            if (newMsgLines == 1)
            {
                msg.setText(newText);
                msg2.setText(" ");
            } else {
                msg.setText(newText.substring(0, newlineIndex));
                msg2.setText(newText.substring(newlineIndex + 1));
            }
            if (msgLines != newMsgLines)
            {
                msgLines = newMsgLines;
                msgHeight = 0;
                msg2.setVisible(newMsgLines != 1);
                validate();
            }
        }

        /**
         * Custom layout for just the message panel.
         * To center {@link #msg} and {@link #msg2} vertically after changing {@link #msgLines},
         * set {@link #msgHeight} to 0 before calling.
         */
        public void doLayout()
        {
            final Dimension dim = getSize();
            final int buttonW = 48;
            final int inset = 2 * SpeechBalloon.SHADOW_SIZE;

            if (oneLineHeight == 0)
                oneLineHeight = getFontMetrics(msg.getFont()).getHeight();
            if (msgHeight == 0)
                msgHeight = oneLineHeight + 4;
            int w = Math.min((2*(inset+5) + 3*buttonW), dim.width);
            int h = Math.min(92 + 2 * ColorSquareLarger.HEIGHT_L, dim.height);
            if ((msgHeight * msgLines) > h)
                msgHeight = h / msgLines;
            int msgY = ((h - msgHeight - SpeechBalloon.SHADOW_SIZE - (h / 8)) / 2)
                        + (h / 8);
            if (msgLines != 1)
                msgY -= (oneLineHeight / 2);  // move up to make room for msg2
            if (msgY < 0)
                msgY = 0;

            msg.setBounds
                (inset, msgY, w - (2 * inset) - (SpeechBalloon.SHADOW_SIZE / 2), msgHeight);
            if (msgLines != 1)
            {
                msgY += oneLineHeight;
                msg2.setBounds
                    (inset, msgY, w - (2 * inset) - (SpeechBalloon.SHADOW_SIZE / 2), msgHeight);
            }
            balloon.setBounds(0, 0, w, h);
        }
    }

    /**
     * Panel to show a trade offer when in {@link TradeOfferPanel#OFFER_MODE OFFER_MODE},
     * not {@link TradeOfferPanel#MESSAGE_MODE}.
     * Contains both offer and counter-offer; see {@link #setCounterOfferVisible(boolean)}
     * @see MessagePanel
     */
    private class OfferPanel extends Panel implements ActionListener
    {
        /**
         * Balloon to hold offer received visually, not as a layout container.
         * Fill color is {@link TradeOfferPanel#insideBGColor}.
         * @see #offerBox
         */
        SpeechBalloon balloon;

        /** "Offered To" line 1 */
        Label toWhom1;
        /** "Offered To" line 2 for wrapping; usually blank */
        Label toWhom2;
        /** "I Give" */
        Label giveLab;
        /** "I Get" */
        Label getLab;

        /** Offer's resources; counter-offer is {@link #counterOfferSquares}. */
        SquaresPanel squares;

        /** send button for counter-offer */
        Button offerBut;
        Button acceptBut;
        Button rejectBut;

        /**
         * Counter-offer to send; groups counter-offer elements visually, not as a layout container.
         * @see #balloon
         */
        ShadowedBox offerBox;

        Label counterOfferToWhom;
        boolean counterOffer_playerInit = false;
        /** Counter-offer's resources; the main offer is {@link #squares}. */
        SquaresPanel counterOfferSquares;
        Label giveLab2;
        Label getLab2;
        Button sendBut;
        Button clearBut;
        Button cancelBut;
        /** True if the current offer's "offered to" includes the client player. */
        boolean offered;

        /**
         * Auto-reject countdown timer text below offer panel, or {@code null}.
         * Used for bots only. Visible only if {@link #offered} and
         * {@link TradeOfferPanel#isFromRobot}.
         * Visibility is updated in {@link #update(SOCTradeOffer)}.
         * If counter-offer panel is shown, this label is hidden and the countdown
         * is canceled because client player might take action on the offer.
         * @see #rejTimerTask
         * @since 1.2.00
         */
        private Label rejCountdownLab;

        /**
         * Countdown timer to auto-reject offers from bots. Uses {@link #rejCountdownLab}.
         * Created when countdown needed in {@link #update(SOCTradeOffer)}.
         * See {@link AutoRejectTask} javadoc for details.
         * @since 1.2.00
         */
        private AutoRejectTask rejTimerTask;

        SOCResourceSet give;
        SOCResourceSet get;
        int[] giveInt = new int[5];
        int[] getInt = new int[5];

        /** is the counter-offer showing? use {@link #setCounterOfferVisible(boolean)} to change. */
        boolean counterOfferMode = false;

        /**
         * Creates a new OfferPanel. Shows an opponent's offer (not the client player's)
         * and any counter-offer. The counter-offer is initially hidden.
         */
        public OfferPanel()
        {
            setLayout(null);
            setFont(new Font("SansSerif", Font.PLAIN, 10));

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

            giveLab = new Label(strings.get("trade.i.give"));  // "I Give:"
            giveLab.setBackground(insideBGColor);
            add(giveLab);
            new AWTToolTip(strings.get("trade.opponent.gives"), giveLab);  // "Opponent gives to you"

            getLab = new Label(strings.get("trade.i.get"));  // "I Get:"
            getLab.setBackground(insideBGColor);
            add(getLab);
            new AWTToolTip(strings.get("trade.you.give"), getLab);  // "You give to opponent"

            giveInt = new int[5];
            getInt = new int[5];

            acceptBut = new Button(strings.get("trade.accept"));  // "Accept"
            acceptBut.setActionCommand(ACCEPT);
            acceptBut.addActionListener(this);
            add(acceptBut);

            rejectBut = new Button(strings.get("trade.reject"));  // "Reject"
            rejectBut.setActionCommand(REJECT);
            rejectBut.addActionListener(this);
            add(rejectBut);

            offerBut = new Button(strings.get("trade.counter"));  // "Counter"
            offerBut.setActionCommand(OFFER);
            offerBut.addActionListener(this);
            add(offerBut);

            // Skip rejCountdownLab setup for now, because isFromRobot is false when constructed.
            // TradeOfferPanel constructor will soon call addPlayer() to set it up if needed.

            /** Counter-offer to send */

            counterOfferToWhom = new Label();
            counterOfferToWhom.setVisible(false);
            add(counterOfferToWhom);

            sendBut = new Button(strings.get("base.send"));  // "Send"
            sendBut.setActionCommand(SEND);
            sendBut.addActionListener(this);
            sendBut.setVisible(false);
            add(sendBut);

            clearBut = new Button(strings.get("base.clear"));  // "Clear"
            clearBut.setActionCommand(CLEAR);
            clearBut.addActionListener(this);
            clearBut.setVisible(false);
            add(clearBut);

            cancelBut = new Button(strings.get("base.cancel"));  // "Cancel"
            cancelBut.setActionCommand(CANCEL);
            cancelBut.addActionListener(this);
            cancelBut.setVisible(false);
            add(cancelBut);

            counterOfferSquares = new SquaresPanel(true);
            counterOfferSquares.setVisible(false);
            add(counterOfferSquares);

            giveLab2 = new Label(strings.get("trade.give.them"));  // "Give Them:"
            giveLab2.setVisible(false);
            add(giveLab2);
            new AWTToolTip(strings.get("trade.give.to.opponent"), giveLab2);  // "Give to opponent"

            getLab2 = new Label(strings.get("trade.you.get"));  // "You Get:"
            getLab2.setVisible(false);
            add(getLab2);
            new AWTToolTip(strings.get("trade.opponent.gives"), getLab2);  // "Opponent gives to you"

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
                        (strings.get("trade.counter.to.x", hp.getPlayer().getName()));  // "Counter to {0}:"

                    counterOffer_playerInit = true;
                }
                offered = offerList[player.getPlayerNumber()];
            }
            else
            {
                offered = false;
            }

            SOCGame ga = hp.getGame();

            /**
             * Build the list of player names, retrieve i18n-localized, then wrap at maxChars.
             */
            StringBuilder names = new StringBuilder();

            int cnt = 0;
            for (; cnt < ga.maxPlayers; cnt++)
            {
                if (offerList[cnt] && ! ga.isSeatVacant(cnt))
                {
                    names.append(ga.getPlayer(cnt).getName());
                    break;
                }
            }

            cnt++;

            for (; cnt < ga.maxPlayers; cnt++)
            {
                if (offerList[cnt] && ! ga.isSeatVacant(cnt))
                {
                    names.append(", ");
                    names.append(ga.getPlayer(cnt).getName());
                }
            }

            final int maxChars = ((ga.maxPlayers > 4) || ga.hasSeaBoard) ? 30 : 25;
            String names1 = strings.get("trade.offered.to", names);  // "Offered to: p1, p2, p3"
            String names2 = null;
            if (names1.length() > maxChars)
            {
                // wrap into names2
                int i = names1.lastIndexOf(", ", maxChars);
                if (i != -1)
                {
                    ++i;  // +1 to keep ','
                    names2 = names1.substring(i).trim();
                    names1 = names1.substring(0, i).trim();
                }
            }

            toWhom1.setText(names1);
            toWhom2.setText(names2 != null ? names2 : "");

            /**
             * Note: this only works if SOCResourceConstants.CLAY == 1
             */
            for (int i = 0; i < 5; i++)
            {
                giveInt[i] = give.getAmount(i + 1);
                getInt[i] = get.getAmount(i + 1);
            }
            squares.setValues(giveInt, getInt);

            if (rejCountdownLab != null)
            {
                if (rejTimerTask != null)
                    rejTimerTask.cancel();  // cancel any previous

                final boolean wantVis = offered && ! counterOfferMode;
                rejCountdownLab.setText("");  // clear any previous
                rejCountdownLab.setVisible(wantVis);
                if (wantVis)
                {
                    rejTimerTask = new AutoRejectTask();
                    pi.getEventTimer().scheduleAtFixedRate(rejTimerTask, 300 /* ms */, 1000 /* ms */ );
                        // initial 300ms delay, so OfferPanel should be visible at first AutoRejectTask.run()
                }
            }

            // enables accept,reject,offer Buttons if 'offered' is true
            setCounterOfferVisible(counterOfferMode);

            validate();
        }

        /**
         * Update fields when a human or robot player sits down in our {@link SOCHandPanel}'s position.
         * Must update {@link TradeOfferPanel#isFromRobot} before calling this method.
         * @since 1.2.00
         */
        void addPlayer()
        {
            if (isFromRobot)
            {
                if (rejCountdownLab == null)
                {
                    rejCountdownLab = new Label("");  // rejTimerTask.run() will set countdown text
                    rejCountdownLab.setBackground(insideBGColor);
                    add(rejCountdownLab, null, 0);  // add at index 0 to paint in front of balloon (z-order)
                }
            }

            if (rejCountdownLab != null)
                rejCountdownLab.setVisible(false);
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
            final int countdownLabHeight =
                (offered && (! counterOfferMode) && (rejCountdownLab != null) && rejCountdownLab.isVisible())
                ? 14 : 0;
                // If shown, use same height (14) as toWhom1, toWhom2;
                // layout already gives extra padding above/below, so no more is needed in this calc.

            // At initial call to doLayout: dim.width, .height == 0.
            int w = Math.min((2*(inset+5) + 3*buttonW), dim.width);
            int h = Math.min(92 + 2 * ColorSquareLarger.HEIGHT_L + countdownLabHeight, dim.height);
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

                final int giveW =    // +5 for padding before ColorSquares
                    Math.max(fm.stringWidth(giveLab2.getText()), fm.stringWidth(getLab2.getText())) + 5;

                toWhom1.setBounds(inset, top, w - 20, 14);
                toWhom2.setBounds(inset, top + 14, w - 20, 14);

                giveLab.setBounds(inset, top + 32, giveW, lineH);
                getLab.setBounds(inset, top + 32 + lineH, giveW, lineH);
                squares.setLocation(inset + giveW, top + 32);

                int squaresHeight = squares.getBounds().height + 24;
                counterOfferToWhom.setBounds(inset + 7, top + 28 + squaresHeight, w - 33, 12);
                giveLab2.setBounds(inset, top + 28 + lineH + squaresHeight, giveW, lineH);
                getLab2.setBounds(inset, top + 28 + 2*lineH + squaresHeight, giveW, lineH);
                counterOfferSquares.setLocation(inset + giveW, top + 28 + lineH + squaresHeight);
                counterOfferSquares.doLayout();

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
                int giveW =    // +5 for padding before ColorSquares
                    Math.max(fm.stringWidth(giveLab.getText()), fm.stringWidth(getLab.getText())) + 5;

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

                    if ((rejCountdownLab != null) && rejCountdownLab.isVisible())
                        rejCountdownLab.setBounds(inset, buttonY + buttonH + 5, w - 2 * inset, 14);
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
                counterOfferSquares.setValues(zero, zero);
            }
            else if (target == SEND)
            {
                cancelRejectCountdown();

                SOCGame game = hp.getGame();
                SOCPlayer player = game.getPlayer(pi.getClient().getNickname());

                if (game.getGameState() == SOCGame.PLAY1)
                {
                    // slot for each resource, plus one for 'unknown' (remains 0)
                    int[] give = new int[5];
                    int[] get = new int[5];
                    int giveSum = 0;
                    int getSum = 0;
                    counterOfferSquares.getValues(give, get);

                    for (int i = 0; i < 5; i++)
                    {
                        giveSum += give[i];
                        getSum += get[i];
                    }

                    SOCResourceSet giveSet = new SOCResourceSet(give);
                    SOCResourceSet getSet = new SOCResourceSet(get);

                    if (! player.getResources().contains(giveSet))
                    {
                        pi.print("*** " + strings.get("trade.msg.cant.offer"));  // "You can't offer what you don't have."
                    }
                    else if ((giveSum == 0) || (getSum == 0))
                    {
                        pi.print("*** " + strings.get("trade.msg.must.contain"));
                            // "A trade must contain at least one resource from each player." (v1.x.xx: ... resource card ...)
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
                        hp.getClient().getGameManager().offerTrade(game, tradeOffer);

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
                clickRejectButton();
            }

            if (target == ACCEPT)
            {
                //int[] tempGive = new int[5];
                //int[] tempGet = new int[5];
                //squares.getValues(tempGive, tempGet);
                cancelRejectCountdown();
                hp.getClient().getGameManager().acceptOffer(hp.getGame(), from);
                hp.disableBankUndoButton();
            }
            } catch (Throwable th) {
                pi.chatPrintStackTrace(th);
            }
        }

        /**
         * Handle a click of the Reject button ({@link #rejectBut}):
         * Hide this panel, call {@link SOCHandPanel#rejectOfferAtClient()}.
         * @since 1.2.00
         */
        private void clickRejectButton()
        {
            setVisible(false);
            cancelRejectCountdown();
            hp.rejectOfferAtClient();
        }

        /**
         * Show or hide the Accept button, based on client player resources
         * and whether this offer is offered to client player.
         *<P>
         * This should be called when in {@link TradeOfferPanel#OFFER_MODE},
         * not in {@link TradeOfferPanel#MESSAGE_MODE}.
         * @since 1.1.20
         */
        public void updateOfferButtons()
        {
            final boolean haveResources;
            if (! offered)
            {
                haveResources = false;
            } else {
                final int cpn = hp.getPlayerInterface().getClientPlayerNumber();
                if (cpn == -1)
                    return;
                SOCPlayer player = hp.getGame().getPlayer(cpn);
                haveResources = player.getResources().contains(get);
            }

            acceptBut.setVisible(haveResources);
        }

        /**
         * show or hide our counter-offer panel, below the trade-offer panel.
         * Also shows or hides {@link #acceptBut} based on client player resources,
         * {@link #offered}, and ! {@code visible}; see also {@link #updateOfferButtons()}.
         *<P>
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
                counterOfferSquares.setValues(zero, zero);
            }
            counterOfferSquares.setVisible(visible);

            sendBut.setVisible(visible);
            clearBut.setVisible(visible);
            cancelBut.setVisible(visible);
            offerBox.setVisible(visible);

            acceptBut.setVisible(haveResources && offered && ! visible);
            rejectBut.setVisible(offered && ! visible);
            offerBut.setVisible(offered && ! visible);
            if (rejCountdownLab != null)
            {
                if (offered && ! visible)
                    rejCountdownLab.setVisible(true);
                else
                    cancelRejectCountdown();
            }

            counterOfferMode = visible;
            hp.offerCounterOfferVisibleChanged(visible);
            validate();
        }

        /**
         * If showing, hide {@link #rejCountdownLab}.
         * If running, cancel {@link #rejTimerTask}.
         * @since 1.2.00
         */
        private void cancelRejectCountdown()
        {
            if (rejCountdownLab != null)
                rejCountdownLab.setVisible(false);
            if (rejTimerTask != null)
                rejTimerTask.cancel();
        }

        /**
         * Event timer task to display the countdown and then reject bot's offer.
         * Started from {@link TradeOfferPanel#setOffer(SOCTradeOffer)}.
         * Event timer calls {@link #run()} once per second.
         * Cancels itself after reaching 0, or if OfferPanel or
         * {@link TradeOfferPanel.OfferPanel#rejCountdownLab rejCountdownLab} is hidden.
         *<P>
         * Instead of calling {@link TimerTask#cancel()}, most places
         * should call {@link TradeOfferPanel.OfferPanel#cancelRejectCountdown()}.
         * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
         * @since 1.2.00
         */
        private class AutoRejectTask extends TimerTask
        {
            public int secRemain = 5;  // TODO make configurable

            public void run()
            {
                if ((mode != OFFER_MODE)
                    || ! (rejCountdownLab.isVisible() && TradeOfferPanel.OfferPanel.this.isVisible()))
                {
                    cancel();
                    return;
                }

                if (secRemain > 0)
                {
                    rejCountdownLab.setText
                        (strings.get("hpan.trade.auto_reject_countdown", Integer.valueOf(secRemain)));
                        // "Auto-Reject in: 5"
                    --secRemain;
                } else {
                    clickRejectButton();
                    cancel();  // End of countdown for this timer
                }
            }
        }
    }

    /**
     * Update offer panel fields when a new player (human or robot) sits down in our {@link SOCHandPanel}'s position.
     * @since 1.2.00
     */
    public void addPlayer()
    {
        isFromRobot = pi.getGame().getPlayer(from).isRobot();
        offerPanel.addPlayer();
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
     *      Message can be 1 line, or 2 lines with <tt>'\n'</tt>;
     *      will not automatically wrap based on message length.
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
     *<P>
     * To update buttons after {@code setOffer} if the client player's
     * resources change, call {@link #updateOfferButtons()}.
     *<P>
     * To clear values to zero, and hide the counter-offer box,
     * call {@link #clearOffer()}.
     *
     * @param  currentOffer the trade being proposed
     * @see #setMessage(String)
     */
    public void setOffer(SOCTradeOffer currentOffer)
    {
        offerPanel.update(currentOffer);
        cardLayout.show(this, mode = OFFER_MODE);
        validate();
    }

    /**
     * If an offer is currently showing, show or hide Accept button based on the
     * client player's current resources.  Call this after client player receives,
     * loses, or trades resources.
     *
     * @since 1.1.20
     */
    public void updateOfferButtons()
    {
        if (! (isVisible() && mode.equals(OFFER_MODE)))
            return;

        offerPanel.updateOfferButtons();
    }

    /**
     * Set the offer and counter-offer contents to zero.
     * Clear counteroffer mode.
     */
    public void clearOffer()
    {
        offerPanel.squares.setValues(zero, zero);
        offerPanel.counterOfferSquares.setValues(zero, zero);
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
     * Returns current mode of {@link #OFFER_MODE}, or {@link #MESSAGE_MODE},
     * which has been set by using
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
