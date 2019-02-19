/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2009,2011-2013,2015,2017-2019 Jeremy D Monin <jeremy@nand.net>
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

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.TimerTask;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;


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
 * This panel is written for use in {@link SOCHandPanel}, so its layout conventions are nonstandard.
 * To help determine {@link #getPreferredSize()}, call {@link #setAvailableSpace(int, int)} when known.
 *<P>
 * To set this panel's position or size, please use {@link #setBounds(int, int, int, int)},
 * because it is overridden to also update a "compact mode" flag for counter-offer layout.
 *
 * <H3>TODO:</H3>
 *<UL>
 * <LI> Consider separating offerpanel, messagepanel to 2 separate components
 *      that handpanel shows/hides/manages separately
 * <LI> Consider combine ShadowedBox, SpeechBalloon: They look the same except for that balloon point
 * <LI> Consider rework ShadowedBox, SpeechBalloon as JPanels with a custom-drawn border
 *</UL>
 */
@SuppressWarnings("serial")
/*package*/ class TradeOfferPanel extends JPanel
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
     * Typical button height, for doLayouts.
     * @since 2.0.00
     */
    private static final int BUTTON_HEIGHT = 18;

    /**
     * Typical button width, for doLayouts.
     * @since 2.0.00
     */
    private static final int BUTTON_WIDTH = 48;

    /**
     * Height of a single-line text label in pixels,
     * including the auto-reject timer countdown when visible.
     * For convenience of other classes' layout calculations.
     * @see OfferPanel#wantsRejectCountdown(boolean)
     * @since 1.2.00
     */
    public static final int LABEL_LINE_HEIGHT = 14;

    /**
     * Typical height of offer panel when visible. Includes {@link #OFFER_BUTTONS_ADDED_HEIGHT}
     * and speech balloon's protruding point, but not {@link #OFFER_COUNTER_HEIGHT}.
     * Also doesn't include {@link #LABEL_LINE_HEIGHT} + 5 needed when {@link OfferPanel#wantsRejectCountdown(boolean)}.
     *<P>
     * For convenience of other classes' layout calculations.
     * Actual height (buttons' y-positions + height) is set dynamically in OfferPanel.doLayout.
     * @since 1.1.08
     */
    public static final int OFFER_HEIGHT
        = SpeechBalloon.BALLOON_POINT_SIZE + 3
        + (2 * LABEL_LINE_HEIGHT + 4) + (SquaresPanel.HEIGHT + 5) + BUTTON_HEIGHT + 5
        + SpeechBalloon.SHADOW_SIZE;
        // same formula as OfferPanel.doLayout()

    /**
     * Additional height of offer (part of {@link #OFFER_HEIGHT})
     * when the "offer"/"accept"/"reject" buttons are showing.
     * That is, when not in counter-offer mode.
     * For convenience of other classes' layout calculations.
     * Based on calculations within OfferPanel.doLayout.
     *<P>
     * Before v2.0.00 this field was {@code OFFER_BUTTONS_HEIGHT}.
     *
     * @since 1.1.08
     */
    public static final int OFFER_BUTTONS_ADDED_HEIGHT = 26;

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

    /**
     * Offer panel minimum width, based on {@link OfferPanel#doLayout()}
     * @since 2.0.00
     */
    private static final int OFFER_MIN_WIDTH = (2 * (10+5) + 3 * BUTTON_WIDTH);

    /**
     * Initial size, to avoid (0,0)-sized JPanel during parent panels' construction.
     * @since 2.0.00
     */
    private static final Dimension INITIAL_SIZE = new Dimension(OFFER_MIN_WIDTH, OFFER_HEIGHT);

    protected static final int[] zero = { 0, 0, 0, 0, 0 };
    static final String OFFER = "counter";
    static final String ACCEPT = "accept";
    static final String REJECT = "reject";
    static final String SEND = "send";
    static final String CLEAR = "clear";
    static final String CANCEL = "cancel";

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
     * Available width and height in handpanel. Used for determining {@link #getPreferredSize()},
     * overall shape of which changes when a counter-offer needs to use {@link #counterCompactMode}.
     * Is 0 (unused) until {@link #setAvailableSpace(int, int)} is called.
     * @since 2.0.00
     */
    private int availableWidth, availableHeight;

    /**
     * If true, display counter-offer in a "compact mode" layout
     * because the panel's height is too short for the normal arrangement.
     * Buttons (width {@link #BUTTON_WIDTH}) will be to the right of colorsquares, not below them.
     * Calculated using {@link #OFFER_HEIGHT} + {@link #OFFER_COUNTER_HEIGHT}
     *     - {@link #OFFER_BUTTONS_ADDED_HEIGHT}.
     * Ignored unless {@link OfferPanel#counterOfferMode}.
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

        // without these calls, parent JPanel layout is incomplete even when this panel overrides get*Size
        setSize(INITIAL_SIZE);
        setMinimumSize(INITIAL_SIZE);
        setPreferredSize(INITIAL_SIZE);  // will be updated when setAvailableSpace is called
    }

    /**
     * Set the size of the largest space available for this panel in our {@link SOCHandPanel}.
     * If space's size has changed since the last call, calls {@link #recalcPreferredSize()}.
     *
     * @param width  Available width
     * @param height  Available height
     * @since 2.0.00
     */
    public void setAvailableSpace(final int width, final int height)
    {
        if ((width == availableWidth) && (height == availableHeight))
            return;

        availableWidth = width;
        availableHeight = height;

        recalcPreferredSize();
    }

    /**
     * Recalculate our panel's {@link #getPreferredSize()}.
     * Useful when counter-offer is being shown or hidden, which might need a "compact mode".
     * with a different width than otherwise. So, also updates that flag if panel is showing a counter-offer.
     * Does not call {@link #invalidate()}, so call that afterwards if needed.
     *
     * @see #setAvailableSpace(int, int)
     * @since 2.0.00
     */
    public void recalcPreferredSize()
    {
        int prefW, prefH;

        if (mode.equals(MESSAGE_MODE))
        {
            prefW = OFFER_MIN_WIDTH;
            prefH = messagePanel.calcLabelMinHeightFields(true);
        } else {
            prefW = OFFER_MIN_WIDTH;
            if (! offerPanel.counterOfferMode)
            {
                prefH = OFFER_HEIGHT;
            } else {
                final boolean wasCompact = counterCompactMode;

                prefH = OFFER_HEIGHT - OFFER_BUTTONS_ADDED_HEIGHT + OFFER_COUNTER_HEIGHT;
                if (availableHeight >= prefH)
                {
                    counterCompactMode = false;
                } else {
                    counterCompactMode = true;
                    prefW += (BUTTON_WIDTH + 2);
                    prefH -= (BUTTON_HEIGHT + 2);
                }

                if (wasCompact != counterCompactMode)
                    repaint();
            }

            if (! (offerPanel.counterOfferMode && counterCompactMode))
            {
                if (offerPanel.wantsRejectCountdown(true))
                    prefH += LABEL_LINE_HEIGHT + 5;
            } else {
                prefH -= SpeechBalloon.BALLOON_POINT_SIZE;  // TODO already accounted for in offer_height?
            }
        }

        if ((availableWidth != 0) && (availableWidth < prefW))
            prefW = availableWidth;
        if ((availableHeight != 0) && (availableHeight < prefH))
            prefH = availableHeight;
        setPreferredSize(new Dimension(prefW, prefH));
    }

    /**
     * Panel to show when in {@link TradeOfferPanel#MESSAGE_MODE MESSAGE_MODE},
     * not {@link TradeOfferPanel#OFFER_MODE OFFER_MODE}.
     * @see OfferPanel
     */
    private class MessagePanel extends JPanel
    {
        private SpeechBalloon balloon;

        /**
         * For 1 line of text, {@link #msg} contains the entire text.
         * For 2 lines separated by <tt>\n</tt>, {@link #msg} and {@link #msg2} are used.
         * @see #msgLines
         */
        private JLabel msg, msg2;

        /**
         * Height of the text in one label, from <tt>getFontMetrics({@link #msg}.getFont()).getHeight().
         * Should be less than {@link #msgHeight}.
         */
        private int oneLineHeight;

        /**
         * Height of each label ({@link #msg}, {@link #msg2}).
         * Should be {@link #oneLineHeight} + insets.
         */
        private int msgHeight;

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
            super(null);  // custom doLayout

            setForeground(Color.BLACK);
            setBackground(SOCPlayerInterface.DIALOG_BG_GOLDENROD);

            final Font msgFont = new Font("SansSerif", Font.PLAIN, 18);

            msg = new JLabel(" ", SwingConstants.CENTER);
            msg.setFont(msgFont);
            msg.setForeground(null);

            msg2 = new JLabel(" ", SwingConstants.CENTER);
            msg2.setVisible(false);
            msg2.setFont(msgFont);
            msg2.setForeground(null);

            oneLineHeight = 0;  // set once in doLayout
            msgHeight = 0;  // set in doLayout
            msgLines = 1;
            add(msg);
            add(msg2);

            balloon = new SpeechBalloon(pi.getPlayerColor(from));
            add(balloon);

            setSize(INITIAL_SIZE);
            setMinimumSize(INITIAL_SIZE);
            setPreferredSize(INITIAL_SIZE);
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
         * Calculate some fields for this panel's minimum height based on {@link #msgHeight}.
         * Ignores getSize() and {@link TradeOfferPanel#availableHeight}.
         *<P>
         * Used by {@link #doLayout()} which wants those field calcs, and
         * {@link TradeOfferPanel#setAvailableSpace(int, int)} which also wants
         * an overall minimum height.
         *<P>
         * If not yet done (0), first calculate the values for {@link #oneLineHeight} and {@link #msgHeight}
         * based on {@link #msgLines} and getFontMetrics({@link #msg}.getFont()).
         *
         * @return  Minimum panel height if {@code wantHeight}, otherwise 0
         * @since 2.0.00
         */
        int calcLabelMinHeightFields(final boolean wantHeight)
        {
            if (oneLineHeight == 0)
                oneLineHeight = getFontMetrics(msg.getFont()).getHeight();
            if (msgHeight == 0)
                msgHeight = oneLineHeight + 4;

            if (! wantHeight)
                return 0;

            return 3 * msgHeight + 4 + SpeechBalloon.BALLOON_POINT_SIZE + SpeechBalloon.SHADOW_SIZE;
                // actual minimum needs 2 * msgHeight; add another msgHeight for margins
        }

        /**
         * Custom layout for just the message panel.
         * To center {@link #msg} and {@link #msg2} vertically after changing {@link #msgLines},
         * set {@link #msgHeight} to 0 before calling.
         */
        public void doLayout()
        {
            final Dimension dim = getSize();
            final int inset = 2 * SpeechBalloon.SHADOW_SIZE;

            calcLabelMinHeightFields(false);  // if 0, set oneLineHeight, msgHeight

            int w = Math.min((2*(inset+5) + 3*BUTTON_WIDTH), dim.width);
            int h = Math.min(92 + 2 * ColorSquareLarger.HEIGHT_L, dim.height);
            if ((msgHeight * msgLines) > h)
                msgHeight = h / msgLines;
            int msgY = ((h - msgHeight - SpeechBalloon.SHADOW_SIZE - SpeechBalloon.BALLOON_POINT_SIZE) / 2)
                        + SpeechBalloon.BALLOON_POINT_SIZE;
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
    class OfferPanel extends JPanel implements ActionListener
    {
        /**
         * Balloon to hold offer received visually, not as a layout container.
         * Fill color is {@link TradeOfferPanel#insideBGColor}.
         * @see #offerBox
         */
        SpeechBalloon balloon;

        /** "Offered To" line 1 */
        JLabel toWhom1;
        /** "Offered To" line 2 for wrapping; usually blank */
        JLabel toWhom2;

        /**
         * Top row "Gives You:". Client player {@link SOCHandPanel} has "I Give" on this row.
         *<P>
         * Before v1.2.00 this label field was {@code giveLab}.
         */
        JLabel givesYouLab;

        /**
         * Bottom row "They Get:". Client player {@link SOCHandPanel} has "I Get" on this row.
         *<P>
         * Before v1.2.00 this label field was {@code getLab}.
         */
        JLabel theyGetLab;

        /** Offer's resources; counter-offer is {@link #counterOfferSquares}. */
        SquaresPanel squares;

        /** send button for counter-offer */
        JButton offerBut;
        JButton acceptBut;
        JButton rejectBut;

        /**
         * Counter-offer to send; groups counter-offer elements visually, not as a layout container.
         * @see #balloon
         */
        ShadowedBox offerBox;

        JLabel counterOfferToWhom;

        /** Have we set prompt to include opponent name? Is set true by first call to {@link #update(SOCTradeOffer)}. */
        boolean counterOffer_playerInit = false;

        /** Counter-offer's resources; the main offer is {@link #squares}. */
        SquaresPanel counterOfferSquares;

        /**
         * Top row "They Get:". Same as main offer's bottom row.
         *<P>
         * Before v1.2.00 this label field was {@code giveLab2}.
         */
        JLabel theyGetLab2;

        /**
         * Bottom row "Gives You:". Same as main offer's top row.
         *<P>
         * Before v1.2.00 this label field was {@code getLab2}.
         */
        JLabel givesYouLab2;

        JButton sendBut;
        JButton clearBut;
        JButton cancelBut;

        /** True if the current offer's "offered to" includes the client player. */
        boolean offered;

        /**
         * Auto-reject countdown timer text below offer panel, or {@code null}.
         * Used for bots only. Visible only if {@link #offered} and
         * {@link TradeOfferPanel#isFromRobot}.
         * Visibility is updated in {@link #update(SOCTradeOffer)}.
         * If counter-offer panel is shown, this label is hidden and the countdown
         * is canceled because client player might take action on the offer.
         * When canceling the timer and hiding this label, should also call setText("").
         * @see #rejTimerTask
         * @since 1.2.00
         */
        private JLabel rejCountdownLab;

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

        /**
         * Is the counter-offer showing? use {@link #setCounterOfferVisible(boolean)} to change.
         * @see TradeOfferPanel#counterCompactMode
         */
        boolean counterOfferMode = false;

        /**
         * Creates a new OfferPanel. Shows an opponent's offer (not the client player's)
         * and any counter-offer. The counter-offer is initially hidden.
         */
        public OfferPanel()
        {
            super(null);   // custom doLayout

            final Font offerFont = new Font("SansSerif", Font.PLAIN, 10);
            setFont(offerFont);
            setForeground(Color.BLACK);
            setBackground(SOCPlayerInterface.DIALOG_BG_GOLDENROD);

            /** The offer received */

            toWhom1 = new JLabel();
            add(toWhom1);

            toWhom2 = new JLabel();
            add(toWhom2);

            /** Offer's resources */
            squares = new SquaresPanel(false);
            add(squares);

            givesYouLab = new JLabel(strings.get("trade.gives.you"));  // "Gives You:"
            givesYouLab.setToolTipText(strings.get("trade.opponent.gives"));  // "Opponent gives to you"
            add(givesYouLab);

            theyGetLab = new JLabel(strings.get("trade.they.get"));  // "They Get:"
            theyGetLab.setToolTipText(strings.get("trade.you.give"));  // "You give to opponent"
            add(theyGetLab);

            giveInt = new int[5];
            getInt = new int[5];

            final Insets minButtonMargin = new Insets(2, 2, 2, 2);  // avoid text cutoff on win32 JButtons

            acceptBut = new JButton(strings.get("trade.accept"));  // "Accept"
            acceptBut.setActionCommand(ACCEPT);
            acceptBut.addActionListener(this);
            acceptBut.setFont(offerFont);
            acceptBut.setMargin(minButtonMargin);
            add(acceptBut);

            rejectBut = new JButton(strings.get("trade.reject"));  // "Reject"
            rejectBut.setActionCommand(REJECT);
            rejectBut.addActionListener(this);
            rejectBut.setFont(offerFont);
            rejectBut.setMargin(minButtonMargin);
            add(rejectBut);

            offerBut = new JButton(strings.get("trade.counter"));  // "Counter"
            offerBut.setActionCommand(OFFER);
            offerBut.addActionListener(this);
            offerBut.setFont(offerFont);
            offerBut.setMargin(minButtonMargin);
            add(offerBut);

            // Skip rejCountdownLab setup for now, because isFromRobot is false when constructed.
            // TradeOfferPanel constructor will soon call addPlayer() to set it up if needed.

            /** The counter-offer to send */

            counterOfferToWhom = new JLabel();
            counterOfferToWhom.setVisible(false);
            add(counterOfferToWhom);

            sendBut = new JButton(strings.get("base.send"));  // "Send"
            sendBut.setActionCommand(SEND);
            sendBut.addActionListener(this);
            sendBut.setVisible(false);
            sendBut.setFont(offerFont);
            sendBut.setMargin(minButtonMargin);
            add(sendBut);

            clearBut = new JButton(strings.get("base.clear"));  // "Clear"
            clearBut.setActionCommand(CLEAR);
            clearBut.addActionListener(this);
            clearBut.setVisible(false);
            clearBut.setFont(offerFont);
            clearBut.setMargin(minButtonMargin);
            add(clearBut);

            cancelBut = new JButton(strings.get("base.cancel"));  // "Cancel"
            cancelBut.setActionCommand(CANCEL);
            cancelBut.addActionListener(this);
            cancelBut.setVisible(false);
            cancelBut.setFont(offerFont);
            cancelBut.setMargin(minButtonMargin);
            add(cancelBut);

            counterOfferSquares = new SquaresPanel(true);
            counterOfferSquares.setVisible(false);
            add(counterOfferSquares);

            theyGetLab2 = new JLabel(strings.get("trade.they.get"));  // "They Get:"
            theyGetLab2.setVisible(false);
            theyGetLab2.setToolTipText(strings.get("trade.give.to.opponent"));  // "Give to opponent"
            add(theyGetLab2);

            givesYouLab2 = new JLabel(strings.get("trade.gives.you"));  // "Gives You:"
            givesYouLab2.setVisible(false);
            givesYouLab2.setToolTipText(strings.get("trade.opponent.gives"));  // "Opponent gives to you"
            add(givesYouLab2);

            offerBox = new ShadowedBox(pi.getPlayerColor(from), SOCPlayerInterface.DIALOG_BG_GOLDENROD);
            offerBox.setVisible(false);
            add(offerBox);

            /** done with counter-offer */

            balloon = new SpeechBalloon(pi.getPlayerColor(from));
            add(balloon);

            /** adjust JLabel and JButton appearance */
            SOCDialog.styleButtonsAndLabels(this);

            setSize(INITIAL_SIZE);
            setMinimumSize(INITIAL_SIZE);
            setPreferredSize(INITIAL_SIZE);
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
                    // do we have to fill in opponent's name for 1st time?
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

                final int sec = pi.getBotTradeRejectSec();
                if ((sec > 0) && offered && isFromRobot && ! counterOfferMode)
                {
                    rejCountdownLab.setText(" ");  // clear any previous; not entirely blank, for other status checks
                    rejCountdownLab.setVisible(true);
                    rejTimerTask = new AutoRejectTask(sec);
                    pi.getEventTimer().scheduleAtFixedRate(rejTimerTask, 300 /* ms */, 1000 /* ms */ );
                        // initial 300ms delay, so OfferPanel should be visible at first AutoRejectTask.run()
                } else {
                    rejCountdownLab.setVisible(false);
                    rejCountdownLab.setText("");
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
                    rejCountdownLab = new JLabel("");  // rejTimerTask.run() will set countdown text
                    rejCountdownLab.setForeground(null);  // inherit from panel
                    rejCountdownLab.setFont(getFont());
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
            int inset = 10;
            final boolean isUsingRejCountdownLab =
                offered && (! counterOfferMode) && (rejCountdownLab != null)
                && (rejCountdownLab.getText().length() != 0);
            final int countdownLabHeight =
                (isUsingRejCountdownLab) ? LABEL_LINE_HEIGHT : 0;
                // If shown, use same height as toWhom1, toWhom2;
                // layout already gives extra padding above/below, so no more is needed in this calc.

            // At initial call to doLayout: dim.width, .height == 0.
            int w = Math.min((2*(inset+5) + 3*BUTTON_WIDTH), dim.width);
            int h = Math.min(64 + 2 * LABEL_LINE_HEIGHT + 2 * ColorSquareLarger.HEIGHT_L + countdownLabHeight, dim.height);
            // top of toWhom1 label:
            int top = SpeechBalloon.BALLOON_POINT_SIZE + 3;

            if (counterOfferMode)
            {
                // show the counter offer controls

                final int lineH = ColorSquareLarger.HEIGHT_L;
                h = Math.min(60 + 2 * ColorSquareLarger.HEIGHT_L, h);

                if (counterCompactMode)
                {
                    inset = 2;
                    balloon.setBalloonPoint(false);
                    top -= SpeechBalloon.BALLOON_POINT_SIZE;
                        // Shift everything up this far, since we don't need to leave room for balloon point.
                } else {
                    balloon.setBalloonPoint(! counterHidesBalloonPoint);
                }

                final int giveW =    // +6 for padding before ColorSquares
                    Math.max(fm.stringWidth(theyGetLab.getText()), fm.stringWidth(givesYouLab.getText())) + 6;

                toWhom1.setBounds(inset, top, w - 20, LABEL_LINE_HEIGHT);
                toWhom2.setBounds(inset, top + LABEL_LINE_HEIGHT, w - 20, LABEL_LINE_HEIGHT);

                givesYouLab.setBounds(inset, top + 32, giveW, lineH);
                theyGetLab.setBounds(inset, top + 32 + lineH, giveW, lineH);
                squares.setLocation(inset + giveW, top + (2 * LABEL_LINE_HEIGHT) + 2);

                int squaresHeight = SquaresPanel.HEIGHT + 24;
                counterOfferToWhom.setBounds(inset, top + 23 + squaresHeight, w - 33, 12);
                theyGetLab2.setBounds(inset, top + 26 + lineH + squaresHeight, giveW, lineH);
                givesYouLab2.setBounds(inset, top + 26 + 2*lineH + squaresHeight, giveW, lineH);
                counterOfferSquares.setLocation(inset + giveW, top + 24 + lineH + squaresHeight);
                counterOfferSquares.doLayout();

                if (counterCompactMode)
                {
                    // Buttons to right of counterOfferToWhom
                    int buttonY = top + 28 + squaresHeight;
                    final int buttonX = inset + giveW + squares.getBounds().width + 2;

                    sendBut.setBounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
                    buttonY += BUTTON_HEIGHT + 2;
                    clearBut.setBounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
                    buttonY += BUTTON_HEIGHT + 2;
                    cancelBut.setBounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);

                    if (w < (buttonX + BUTTON_WIDTH + ShadowedBox.SHADOW_SIZE + 2))
                        w = buttonX + BUTTON_WIDTH + ShadowedBox.SHADOW_SIZE + 2;
                } else {
                    // Buttons below givesYouLab2, counterOfferSquares
                    final int buttonY = top + 6 + (2 * squaresHeight) + lineH;

                    sendBut.setBounds(inset, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
                    clearBut.setBounds(inset + 5 + BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
                    cancelBut.setBounds(inset + (2 * (5 + BUTTON_WIDTH)), buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
                }

                if (counterCompactMode)
                {
                    // No balloon point, so top few pixels of its bounding box is empty: move it up
                    balloon.setBounds(0, -SpeechBalloon.BALLOON_POINT_SIZE, w, h - SpeechBalloon.BALLOON_POINT_SIZE / 2);
                } else {
                    balloon.setBounds(0, 0, w, h);
                }
                offerBox.setBounds(0, top + 18 + squaresHeight, w, squaresHeight + 16 + lineH);

                // If offerBox height calculation changes, please update OFFER_COUNTER_HEIGHT.

                if (rejCountdownLab != null)
                    rejCountdownLab.setVisible(false);
            }
            else
            {
                // if need auto-reject countdown label but balloon is not tall enough,
                // don't waste space showing its point (happens in 6-player mode
                // on same side of window as client player)
                int balloonTop = 0;
                int buttonY = (offered) ? top + (2 * LABEL_LINE_HEIGHT) + 4 + SquaresPanel.HEIGHT + 5 : 0;
                if (isUsingRejCountdownLab)
                {
                    int htWithLab = buttonY + BUTTON_HEIGHT + 5 + LABEL_LINE_HEIGHT + 3 + SpeechBalloon.SHADOW_SIZE;
                    boolean tooTall = isUsingRejCountdownLab && (h < htWithLab);
                    if (tooTall)
                    {
                        final int dh = SpeechBalloon.BALLOON_POINT_SIZE;
                        top -= dh;
                        balloonTop -= (dh + 2);
                        buttonY -= dh;
                        h = htWithLab;
                    }
                    balloon.setBalloonPoint(! tooTall);
                } else {
                    balloon.setBalloonPoint(true);
                    if (rejCountdownLab != null)
                        rejCountdownLab.setVisible(false);  // needed after a counter-offer canceled
                }

                int lineH = ColorSquareLarger.HEIGHT_L;
                int giveW =    // +6 for padding before ColorSquares
                    Math.max(fm.stringWidth(givesYouLab.getText()), fm.stringWidth(theyGetLab.getText())) + 6;

                toWhom1.setBounds(inset, top, w - 20, LABEL_LINE_HEIGHT);
                toWhom2.setBounds(inset, top + LABEL_LINE_HEIGHT, w - 20, LABEL_LINE_HEIGHT);
                int y = top + (2 * LABEL_LINE_HEIGHT) + 4;
                givesYouLab.setBounds(inset, y, giveW, lineH);
                theyGetLab.setBounds(inset, y + lineH, giveW, lineH);
                squares.setLocation(inset + giveW, y);
                squares.doLayout();

                if (offered)
                {
                    acceptBut.setBounds(inset, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
                    rejectBut.setBounds(inset + 5 + BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
                    offerBut.setBounds(inset + (2 * (5 + BUTTON_WIDTH)), buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);

                    if (isUsingRejCountdownLab)
                        rejCountdownLab.setBounds
                            (inset, buttonY + BUTTON_HEIGHT + 5, w - 2 * inset, LABEL_LINE_HEIGHT);
                }

                balloon.setBounds(0, balloonTop, w, h);

                // If rejectBut height calculation changes, please update OFFER_HEIGHT.
                // If change in the height difference of "offered" buttons showing/not showing,
                // please update OFFER_BUTTONS_ADDED_HEIGHT.
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
                cancelRejectCountdown();
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
                        hp.getClient().getGameMessageMaker().offerTrade(game, tradeOffer);

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
                hp.getClient().getGameMessageMaker().acceptOffer(hp.getGame(), from);
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

            theyGetLab2.setVisible(visible);
            givesYouLab2.setVisible(visible);
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
                if (offered && isFromRobot && (! visible) && (pi.getBotTradeRejectSec() > 0))
                    rejCountdownLab.setVisible(true);
                else
                    cancelRejectCountdown();
            }

            counterOfferMode = visible;
            hp.offerCounterOfferVisibleChanged(visible);
            recalcPreferredSize();
            invalidate();
        }

        /**
         * Will the Auto-Reject Countdown timer text be shown for this bot's offer?
         * (from {@link SOCPlayerInterface#getBotTradeRejectSec()})
         *<P>
         * If visible, this countdown's height is {@link #LABEL_LINE_HEIGHT}.
         * Even when returns true, the label may not yet be visible but space should be reserved
         * for it in {@link #doLayout()}.
         *
         * @param checkCurrentStatus  If true, don't only check the SOCPlayerInterface preference,
         *     also check whether {@link #isCounterOfferMode()} and whether the reject-countdown
         *     label is visible and not blank.
         * @return True if the current offer is from a bot, is offered to client player,
         *     is not counter-offer mode, and the Auto-Reject Countdown Timer label contains text.
         * @since 1.2.00
         */
        public boolean wantsRejectCountdown(final boolean checkCurrentStatus)
        {
            boolean wants = isFromRobot && (pi.getBotTradeRejectSec() > 0);
            if ((! wants) || (! checkCurrentStatus))
                return wants;

            // wants is true, but caller asks to check current status
            return (! isCounterOfferMode()) && (rejCountdownLab != null)
                && (rejCountdownLab.getText().length() != 0);
        }

        /**
         * If running, cancel {@link #rejTimerTask}.
         * If showing, hide {@link #rejCountdownLab}.
         * @since 1.2.00
         */
        private void cancelRejectCountdown()
        {
            if (rejTimerTask != null)
                rejTimerTask.cancel();

            if (rejCountdownLab != null)
            {
                rejCountdownLab.setVisible(false);
                rejCountdownLab.setText("");
            }
        }

        /**
         * Event timer task to display the countdown and then reject bot's offer.
         * Started from {@link TradeOfferPanel#setOffer(SOCTradeOffer)}
         * if {@link SOCPlayerInterface#getBotTradeRejectSec()} &gt; 0.
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
            public int secRemain;

            /**
             * @param sec  Initial value (seconds); should be &gt; 0
             */
            public AutoRejectTask(final int sec)
            {
                secRemain = sec;
            }

            public void run()
            {
                if ((mode != OFFER_MODE)
                    || ! (rejCountdownLab.isVisible() && TradeOfferPanel.OfferPanel.this.isVisible()))
                {
                    rejCountdownLab.setText("");
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
            recalcPreferredSize();
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
     * @see #isOfferToClientPlayer()
     */
    public void setOffer(SOCTradeOffer currentOffer)
    {
        offerPanel.update(currentOffer);
        cardLayout.show(this, mode = OFFER_MODE);
        recalcPreferredSize();
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
            recalcPreferredSize();
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
     * Is panel in offer mode and is its current offer made to the client player?
     * @return  True only if {@link #isVisible()} in {@link #OFFER_MODE} and current offer's "made to" players list
     *     includes the client player, if any.
     * @since 1.2.01
     */
    public boolean isOfferToClientPlayer()
    {
        return isVisible() && mode.equals(OFFER_MODE) && offerPanel.offered;
    }

    /**
     * Returns current mode, which has been set by using
     * {@link #setOffer} or {@link #setMessage}.
     * @return {@link #OFFER_MODE} or {@link #MESSAGE_MODE}
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
    @Override
    public void setBounds(final int x, final int y, final int width, final int height)
    {
        super.setBounds(x, y, width, height);

        final int hpHeight = hp.getHeight();
        int counterBottomY = offerPanel.offerBox.getHeight();
        if (counterBottomY > 0)
            counterBottomY += offerPanel.offerBox.getY() + y + 3;
        counterCompactMode =
            (height < (OFFER_HEIGHT + OFFER_COUNTER_HEIGHT - OFFER_BUTTONS_ADDED_HEIGHT))
            || ((hpHeight > 0) &&
                (((y + height + 3 > hpHeight))
                 || ((counterBottomY > 0) && (counterBottomY >= hpHeight))));
    }

}  // TradeOfferPanel
