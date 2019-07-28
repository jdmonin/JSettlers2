/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.util.SOCStringManager;

/**
 * Panel for a trade offer or counter-offer in a non-client player's {@link SOCHandPanel}.
 *<P>
 * Contains:
 *<UL>
 * <LI> Text above the trading squares (1 or 2 lines)
 * <LI> A 2-row grid of trading squares, with labels to the left of each row such as "They Get",
 *      updated by {@link #setTradeOffer(SOCTradeOffer)} or {@link #setTradeResources(SOCResourceSet, SOCResourceSet)}
 * <LI> 3 buttons, appearing below the trading squares, or to their right if no room below ("compact mode")
 * <LI> Optional text below the trading squares
 *</UL>
 *<P>
 * The parent SOCHandPanel controls this panel's size, position, and visibility.
 * TradePanel determines whether to use "compact mode" layout based on its height:
 * If not tall enough to include the row of buttons, compact mode moves them to the right.
 * See {@link #getCompactPreferredSize()}.
 *<P>
 * Any reference to "unscaled pixels" means a dimension not yet multiplied by {@link SOCPlayerInterface#displayScale}.
 *<P>
 * Before v2.0.00 the trade interface combined offer and counter-offer in class {@code TradeOfferPanel}.
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TradePanel extends JPanel
    implements ActionListener
{
    private static final long serialVersionUID = 2000L;

    /** i18n text strings. */
    private static final SOCStringManager strings = SOCStringManager.getClientManager();

    /**
     * Margin at left and right side of panel, in unscaled pixels.
     * Not scaled by {@link #displayScale}.
     */
    private static final int PANEL_MARGIN_HORIZ = 8;

    /**
     * Typical button height for doLayout, in unscaled pixels.
     */
    private static final int BUTTON_HEIGHT = 18;

    /**
     * Typical button width, for doLayout, in unscaled pixels.
     */
    private static final int BUTTON_WIDTH = 55;

    /**
     * Margin around buttons in compact mode, in unscaled pixels:
     * Vertically between buttons, and to their left (between squares panel and buttons).
     */
    private static final int BUTTON_MARGIN_COMPACT = 2;

    /**
     * Height of a single-line text label, in unscaled pixels,
     * including the auto-reject timer countdown when visible.
     * For convenience of other classes' layout calculations.
     * Not scaled by {@link #displayScale}.
     * @see #LAYOUT_LINE_SPACE
     */
    private static final int LABEL_LINE_HEIGHT = 14;

    /**
     * Vertical empty space between layout rows/lines, in unscaled pixels.
     * @see #LABEL_LINE_HEIGHT
     */
    private static final int LAYOUT_LINE_SPACE = 3;

    /**
     * Minimum width for trading square labels, for fallback if FontMetrics not available yet.
     * Not scaled by {@link #displayScale}.
     */
    private static final int SQUARES_LAB_MIN_WIDTH = 49;

    /**
     * Margin between trading square labels and trading squares in unscaled pixels.
     * Not scaled by {@link #displayScale}.
     */
    private static final int SQUARES_LAB_MARGIN_RIGHT = 6;

    /**
     * Optional player for {@link #canPlayerGiveTradeResources()}, or null.
     * Current convention sets this field to client player, if any.
     * @see #setPlayer(SOCPlayer)
     * @see #playerIsRow1
     * @see #offeredToPlayer
     */
    private SOCPlayer player;

    /**
     * For Offer panel (not counter-offer): True if the current offer's "offered to" includes {@link #player}.
     * False if no such player. Updated in {@link #setTradeOffer(SOCTradeOffer)}.
     */
    private boolean isOfferToPlayer;

    /**
     * True if {@link #player}'s resources in the trade would be shown in row 1
     * of the {@link #squares} panel, false if row 2.
     */
    private final boolean isPlayerRow1;

    /** Listener to use for callbacks when buttons are clicked. */
    private final TPListener listener;

    /** Our parent handpanel, for game data callbacks. */
    private final SOCHandPanel hpan;

    /**
     * For high-DPI displays, what scaling factor to use? Unscaled is 1.
     */
    private final int displayScale;

    /**
     * Line 1 of text appearing above the trading squares. Text is "" if empty.
     * May or may not be a {@link #line2} below this line.
     */
    private final JLabel line1;

    /**
     * Line 2 of text appearing above the trading squares below {@link #line1}, or {@code null}.
     * Text is "" if empty.
     */
    private final JLabel line2;

    /**
     * Optional line of text below the trading squares, or {@code null}. Text is "" if empty.
     */
    private final JLabel lineBelow;

    /**
     * Buttons with user-specified text.
     * Some or all can be hidden.
     * When clicked by user, callback methods are called.
     */
    private final JButton btn1, btn2, btn3;

    /**
     * Is the button row visible? Panel is less tall when hidden.
     * Visible by default, changed by {@link #setButtonRowVisible(boolean)}.
     */
    private boolean buttonRowVis;

    /**
     * Visibility of {@link #btn1}. Visible by default, changed by {@link #setButton1Visible(boolean)}.
     * Field is needed because {@link #setButtonRowVisible(boolean)} will hide all buttons.
     */
    private boolean button1Vis;

    /**
     * Trade offer's resources.
     * Labeled with {@link #sqLabRow1} and {@link #sqLabRow2}.
     * Updated by {@link #setTradeResources(SOCResourceSet, SOCResourceSet)}.
     * @see #isPlayerRow1
     */
    private final SquaresPanel squares;

    /**
     * Text to label row 1 and row 2 of the trading {@link #squares}.
     * Width {@link #sqLabWidth} is calculated during {@link #doLayout()}.
     */
    private final JLabel sqLabRow1, sqLabRow2;

    /**
     * Cached width of {@link #sqLabRow1} and {@link #sqLabRow2}, or 0.
     * Calculated by {@link #calcLabelWidth()} for {@link #doLayout()}.
     */
    private int sqLabWidth;

    /**
     * Make a new TradePanel to hold a trade offer or counter-offer.
     *
     * @param buttonTexts  Array with 3 strings: Text for button 1, button 2, button 3
     * @param sqLabelTexts  Array with 2 or 4 strings: Label for row 1 of the trading squares, for row 2,
     *     and those labels' optional tooltips
     * @param isPlayerRow1  True if player's own resources in the trade would be shown in row 1, false if row 2.
     *     For more info see {@link #canPlayerGiveTradeResources()}.
     * @param hasLine2  True if text above trading squares has a second line
     * @param hasLineBelow  True if the layout should leave room for text below the trading squares
     * @param hpan  Parent handpanel, for game data callbacks
     * @param listener   Listener for callbacks when buttons pressed; not {@code null}
     * @param displayScale  For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @throws IllegalArgumentException  if {@code buttonTexts} is null or length != 3,
     *     or {@code hpan} or {@code listener} or {@ {@code sqLabel1} or {@link sqLabel2} is null
     */
    public TradePanel
        (final String[] buttonTexts, final String[] sqLabelTexts,
         boolean isPlayerRow1, boolean hasLine2, boolean hasLineBelow,
         final SOCHandPanel hpan, TPListener listener, final int displayScale)
        throws IllegalArgumentException
    {
        if (hpan == null)
            throw new IllegalArgumentException("hpan");
        if ((buttonTexts == null) || (buttonTexts.length != 3))
            throw new IllegalArgumentException("buttonTexts");
        if (listener == null)
            throw new IllegalArgumentException("listener");
        if ((sqLabelTexts == null) || ((sqLabelTexts.length != 2) && (sqLabelTexts.length != 4)))
            throw new IllegalArgumentException("sqLabelTexts");

        this.hpan = hpan;
        this.listener = listener;

        final Font panelFont = new Font("SansSerif", Font.PLAIN, 10 * displayScale);
        setFont(panelFont);
        final Color[] colors = SwingMainDisplay.getForegroundBackgroundColors(true, false);
        if (colors != null)
        {
            setForeground(colors[0]);  // Color.BLACK
            setBackground(colors[2]);  // DIALOG_BG_GOLDENROD
            setOpaque(true);
        }

        line1 = new JLabel();
        line1.setFont(panelFont);
        line2 = (hasLine2) ? new JLabel() : null;
        lineBelow = (hasLineBelow) ? new JLabel() : null;
        squares = new SquaresPanel(true, displayScale);
        sqLabRow1 = new JLabel(sqLabelTexts[0]);
        sqLabRow2 = new JLabel(sqLabelTexts[1]);
        sqLabRow1.setFont(panelFont);
        sqLabRow2.setFont(panelFont);
        add(sqLabRow1);
        add(sqLabRow2);
        if (sqLabelTexts.length == 4)
        {
            sqLabRow1.setToolTipText(sqLabelTexts[2]);
            sqLabRow2.setToolTipText(sqLabelTexts[3]);
        }
        this.isPlayerRow1 = isPlayerRow1;
        this.displayScale = displayScale;

        buttonRowVis = true;
        button1Vis = true;

        setLayout(null);
        add(line1);
        if (hasLine2)
        {
            line2.setFont(panelFont);
            add(line2);
        }
        if (hasLineBelow)
        {
            lineBelow.setFont(panelFont);
            add(lineBelow);
        }
        add(squares);

        JButton[] bs = new JButton[3];
        final int pix2 = 2 * displayScale;
        final Insets minButtonMargin = new Insets(pix2, pix2, pix2, pix2);  // avoid text cutoff on win32 JButtons
        for (int i = 0; i < 3; ++i)
        {
            bs[i] = new JButton(buttonTexts[i]);
            bs[i].setFont(panelFont);
            bs[i].setMargin(minButtonMargin);
            add(bs[i]);
            bs[i].addActionListener(this);
        }
        btn1 = bs[0];
        btn2 = bs[1];
        btn3 = bs[2];
    }

    /**
     * Get resource sets for each of the 2 resource square lines.
     * Does not validate that total &gt; 0 or that give/get totals are equal.
     * @return Array with a resource set for line 1, and a set for line 2,
     *     from contents of this panel's resource squares
     * @see #setTradeResources(SOCResourceSet, SOCResourceSet)
     * @see #canPlayerGiveTradeResources()
     */
    public SOCResourceSet[] getTradeResources()
    {
        int[] res1 = new int[5], res2 = new int[5];
        squares.getValues(res1, res2);

        return new SOCResourceSet[] { new SOCResourceSet(res1), new SOCResourceSet(res2) };
    }

    /**
     * Set trade panel's resource contents, or clear to 0.
     *<P>
     * To also set the "Offered To" text, call {@link #setTradeOffer(SOCTradeOffer)} instead.
     *
     * @param line1  Trade resources to use in Line 1; will clear all to 0 if null
     * @param line2  Trade resources to use in Line 2; will clear all to 0 if null
     * @see #getTradeResources()
     */
    public void setTradeResources(SOCResourceSet line1, SOCResourceSet line2)
    {
        squares.setValues(line1, line2);
    }

    /**
     * Is the currently visible {@link #setTradeOffer(SOCTradeOffer)} offered to
     * the client player?
     * @return  True only if {@link #isVisible()} and current offer's "made to" players list
     *     includes the client player (designated earlier by calling {@link #setPlayer(SOCPlayer)}).
     */
    public boolean isOfferToPlayer()
    {
        return isVisible() && isOfferToPlayer;
    }

    /**
     * Set trade panel's "Offered To" text and resource contents, or clear to 0.
     * Also shows button row, if previously hidden by calling {@link #setButtonRowVisible(boolean)}.
     *<P>
     * Should be called when already showing an offer or about to do so.
     * Not for use in a counter-offer panel.
     *
     * @param currentOffer  Trade offer details from hand panel's non-client player,
     *     or null to only clear resource squares to 0 and clear {@link #isOfferToPlayer()} flag.
     * @see #setTradeResources(SOCResourceSet, SOCResourceSet)
     * @see #isOfferToPlayer()
     */
    public void setTradeOffer(SOCTradeOffer offer)
    {
        if (offer == null)
        {
            setTradeResources(null, null);
            isOfferToPlayer = false;
            return;
        }

        // Reminder: Offer panel's line 1 is "Gives You", line 2 is "They Get"
        // from client player's point of view

        final SOCResourceSet give = offer.getGiveSet(), get = offer.getGetSet();
        final boolean[] offerList = offer.getTo();

        isOfferToPlayer = (player != null) && offerList[player.getPlayerNumber()];

        final SOCGame ga = hpan.getGame();

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

        line1.setText(names1);
        line2.setText(names2 != null ? names2 : "");

        setTradeResources(give, get);

        if (! buttonRowVis)
            setButtonRowVisible(true);

        // TODO SOCHandPanel: implement rejCountdownLab soon (based on TradeOfferPanel)
        /*
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
                pi.getEventTimer().scheduleAtFixedRate(rejTimerTask, 300 /* ms * /, 1000 /* ms * / );
                    // initial 300ms delay, so OfferPanel should be visible at first AutoRejectTask.run()
            } else {
                rejCountdownLab.setVisible(false);
                rejCountdownLab.setText("");
            }
        }
        */

        EventQueue.invokeLater(new Runnable()
        {
            public void run()
            {
                invalidate();
                validate();
            }
        });
    }

    /**
     * Does the player have enough resources to give what's shown in the panel?
     * The player is null until set by calling {@link #setPlayer(SOCPlayer)}.
     * Whether the player's resources are in row 1 or row 2 was designated by a constructor parameter.
     * @return  True if, given the resources currently in the panel for the player's half of the trade
     *    (player's row of the panel), they have those resources to give.
     *    False if {@link #setPlayer(SOCPlayer)} hasn't been called to set a non-null player.
     */
    public boolean canPlayerGiveTradeResources()
    {
        if (player == null)
            return false;

        final int[] res1 = new int[5], res2 = new int[5];
        squares.getValues(res1, res2);
        return player.getResources().contains((isPlayerRow1) ? res1 : res2);
    }

    /**
     * Set or clear the optional player used by {@link #canPlayerGiveTradeResources()}.
     * Current convention sets to client player if any, or null.
     * @param pl  Player to use, or {@code null} to clear
     */
    public void setPlayer(final SOCPlayer pl)
    {
        player = pl;
    }

    /**
     * Show or hide the entire row of buttons. Also calls {@link #invalidate()}.
     * When hidden and not in Compact Mode, the panel's {@link #getPreferredSize()} is less tall.
     * Overrides setting of {@link #setButton1Visible(boolean)}.
     * @param shown  True to show, false to hide
     */
    public void setButtonRowVisible(final boolean shown)
    {
        if (shown == buttonRowVis)
            return;

        buttonRowVis = shown;
        btn1.setVisible(shown && button1Vis);
        btn2.setVisible(shown);
        btn3.setVisible(shown);
        invalidate();
    }

    /**
     * Show or hide Button 1.
     * Is initially visible when TradePanel is created.
     * @param shown  True to show, false to hide
     */
    public void setButton1Visible(final boolean shown)
    {
        if (shown == button1Vis)
            return;

        button1Vis = shown;
        btn1.setVisible(shown && buttonRowVis);
    }

    /**
     * Set text contents of line 1 that appears above the trading squares.
     * @param txt  Text to set, or {@code null} to use ""
     */
    public void setLine1Text(String txt)
    {
        if (txt == null)
            txt = "";

        line1.setText(txt);
    }

    /*
    public void setLineBelow(final String txt);  // TODO
    {
        
    } */

    /**
     * Get our preferred height to use when not in Compact Mode.
     * Same calc as {@link #getPreferredSize()}, but without creating a Dimension or any other object.
     * @param ignoreButtonRow  If true, assume button row is hidden; useful for Compact Mode.
     *     If false, uses {@link #setButtonRowVisible(boolean)} flag value.
     */
    public int getPreferredHeight(final boolean ignoreButtonRow)
    {
        int h;

        // Height based on doLayout y-position calcs

        final int lineHeight = LABEL_LINE_HEIGHT * displayScale,
                  lineSpace = LAYOUT_LINE_SPACE * displayScale;
        int nLines = 1;  // text lines
        if (line2 != null)
            ++nLines;
        if (lineBelow != null)
            ++nLines;
        h = (lineHeight + lineSpace) * nLines + lineSpace
            + (SquaresPanel.HEIGHT + ShadowedBox.SHADOW_SIZE) * displayScale + lineSpace;
        if ((! ignoreButtonRow) && buttonRowVis)
            h += (BUTTON_HEIGHT * displayScale) + lineSpace;

        return h;
    }

    /**
     * Get our preferred size to use when not in Compact Mode.
     * For size in Compact Mode see {@link #getCompactPreferredSize()}.
     * @see #getPreferredHeight(boolean)
     */
    @Override
    public Dimension getPreferredSize()
    {
        int w, h;

        w = ((2 * PANEL_MARGIN_HORIZ) + SquaresPanel.WIDTH + SQUARES_LAB_MARGIN_RIGHT)
            * displayScale + calcLabelWidth();

        h = getPreferredHeight(false);

        return new Dimension(w, h);
    }

    /**
     * Get our {@link #getPreferredSize() preferred size} to use when in
     * Compact Mode: Shorter but wider than normal mode.
     * @return  int array with [width, height] in compact mode
     * @see #getPreferredHeight(boolean)
     */
    public int[] getCompactPreferredSize()
    {
        int w, h;

        // TODO is compactMode spacing smaller than SQUARES_LAB_MARGIN_RIGHT?
        w = ((2 * PANEL_MARGIN_HORIZ) + SQUARES_LAB_MARGIN_RIGHT + BUTTON_MARGIN_COMPACT + BUTTON_WIDTH)
            * displayScale + calcLabelWidth();

        h = getPreferredHeight(true);

        return new int[]{ w, h };
    }

    /**
     * Custom layout for this OfferPanel, including the components within
     * its offer {@link #balloon} and counter-offer {@link #counterOfferBox}.
     */
    public void doLayout()
    {
        final Dimension dim = getSize();
        if ((dim.width == 0) || (dim.height == 0))
            return;

        final int lineHeight = LABEL_LINE_HEIGHT * displayScale,
                  lineSpace = LAYOUT_LINE_SPACE * displayScale;

        final boolean compactMode = (dim.height < getPreferredHeight(false));
            // TODO some field for compactMode, if needed outside doLayout or from a getter;
            // but when to calc it?  Maybe method called from here & setSize/setBounds/similar?

        final int inset = PANEL_MARGIN_HORIZ * displayScale;

        // Label text's width may increase required panel width
        //int w = OFFER_MIN_WIDTH * displayScale;
        final int squaresLabelW = calcLabelWidth() + (SQUARES_LAB_MARGIN_RIGHT * displayScale);
            // from theyGetLab, givesYouLab FontMetrics; +SQUARES_LAB_MARGIN_RIGHT for margin before ColorSquares
        /*
        {
            int d = giveW - ((GIVES_MIN_WIDTH + SQUARES_LAB_MARGIN_RIGHT) * displayScale);
            if (d > 0)
                w = Math.max
                    (OFFER_MIN_WIDTH_FROM_BUTTONS * displayScale,
                     OFFER_MIN_WIDTH_FROM_LABELS * displayScale + d);
        }
        */

        // - now position & size all contents:

        int y = lineSpace;
        int w = dim.width - (2 * inset);

        // - txt lines above; 2nd line width is truncated if compact mode, to leave room for right-side buttons

        line1.setBounds(inset, y, w, lineHeight);
        y += (lineSpace + lineHeight);

        if (line2 != null)
        {
            if (compactMode)
                w -= ((BUTTON_WIDTH + BUTTON_MARGIN_COMPACT) * displayScale);

            line2.setBounds(inset, y, w, lineHeight);
            y += (lineSpace + lineHeight);
        }

        // - labels & trading squares
        //     TODO does compact mode have smaller space than SQUARES_LAB_MARGIN_RIGHT between label & sqpanel?
        sqLabRow1.setBounds(inset, y, squaresLabelW, lineHeight);
        squares.setLocation(inset + squaresLabelW + (SQUARES_LAB_MARGIN_RIGHT * displayScale), y);
        y += (ColorSquareLarger.HEIGHT_L * displayScale);
        sqLabRow2.setBounds(inset, y, squaresLabelW, lineHeight);
        y += (ColorSquareLarger.HEIGHT_L * displayScale) + lineSpace;

        // - 3 buttons at right position, etc, based on compact mode.
        //   Might be hidden (offer panel while showing counteroffer).
        if (compactMode)
        {
            // Buttons to right of offer squares, y-centered vs. height of panel
            int buttonY =
                (((dim.height / displayScale) - BUTTON_HEIGHT - ShadowedBox.SHADOW_SIZE - BUTTON_MARGIN_COMPACT)
                 - (3 * BUTTON_HEIGHT + 4))
                * displayScale / 2;
            final int buttonX = inset + squaresLabelW + ((SquaresPanel.WIDTH + BUTTON_MARGIN_COMPACT) * displayScale),
                      buttonW = BUTTON_WIDTH * displayScale,
                      buttonH = BUTTON_HEIGHT * displayScale,
                      buttonMar = BUTTON_MARGIN_COMPACT * displayScale;

            btn1.setBounds(buttonX, buttonY, buttonW, buttonH);
            buttonY += buttonH + buttonMar;
            btn2.setBounds(buttonX, buttonY, buttonW, buttonH);
            buttonY += buttonH + buttonMar;
            btn3.setBounds(buttonX, buttonY, buttonW, buttonH);
        }
        else if (buttonRowVis)
        {
            // Buttons below offer squares and their labels, centered across width
            int buttonX =
                (dim.width - (ShadowedBox.SHADOW_SIZE * displayScale) - ((3 * BUTTON_WIDTH + 10) * displayScale)) / 2;
            final int buttonW = BUTTON_WIDTH * displayScale,
                      buttonH = BUTTON_HEIGHT * displayScale,
                      pix5 = 5 * displayScale;

            btn1.setBounds(buttonX, y, buttonW, buttonH);
            buttonX += pix5 + buttonW;
            btn2.setBounds(buttonX, y, buttonW, buttonH);
            buttonX += pix5 + buttonW;
            btn3.setBounds(buttonX, y, buttonW, buttonH);

            y += lineSpace + (BUTTON_HEIGHT * displayScale);
        }

        // - txt below, if any
        if (lineBelow != null)
            lineBelow.setBounds(inset, y, dim.width - (2 * inset), lineHeight);
    }

    /**
     * If not yet done, try to calculate the width in scaled pixels of the text in the trading square labels,
     * whichever label is wider. Calculated once from FontMetrics, then cached.
     * If not available, falls back to a hardcoded minimum width.
     *<P>
     * If {@link #sqLabRow1} or {@link #sqLabRow2} text or font must be changed after calling, clear cache variable
     * {@link #sqLabWidth}.
     *
     * @return  Calculated label width if FontMetrics available,
     *     otherwise {@link #SQUARES_LAB_MIN_WIDTH} * {@code displayScale}
     */
    private int calcLabelWidth()
    {
        if (sqLabWidth == 0)
        {
            final FontMetrics fm = getFontMetrics(sqLabRow1.getFont());
            if (fm == null)
                return SQUARES_LAB_MIN_WIDTH * displayScale;

            sqLabWidth = Math.max
                (fm.stringWidth(sqLabRow1.getText()), fm.stringWidth(sqLabRow2.getText()));
        }

        return sqLabWidth;
    }

    /** Handle button clicks/activations by calling our {@link TPListener}. */
    public void actionPerformed(ActionEvent e)
    {
        final Object src = e.getSource();
        if (src == btn1)
            listener.button1Clicked();
        else if (src == btn2)
            listener.button2Clicked();
        else if (src == btn3)
            listener.button3Clicked();
    }

    /**
     * Listener for callback methods when {@link TradePanel} buttons are clicked.
     * Since each TradePanel has its own listener, the TradePanel instance
     * isn't passed as a parameter to callback methods.
     */
    public interface TPListener
    {
        /** Callback for when button 1 is clicked or otherwise activated. */
        public void button1Clicked();

        /** Callback for when button 2 is clicked or otherwise activated. */
        public void button2Clicked();

        /** Callback for when button 3 is clicked or otherwise activated. */
        public void button3Clicked();
    }

}
