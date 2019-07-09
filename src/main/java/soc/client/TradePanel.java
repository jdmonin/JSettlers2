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

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import soc.game.SOCResourceSet;

/**
 * Panel for a trade offer or counter-offer in a non-client player's {@link SOCHandPanel}.
 *<P>
 * Contains:
 *<UL>
 * <LI> Text above the trading squares (1 or 2 lines)
 * <LI> A 2-row grid of trading squares, with labels to the left of each row such as "They Get"
 * <LI> 3 buttons, appearing below the trading squares, or to their right if no room below ("compact mode")
 * <LI> Optional text below the trading squares
 *</UL>
 *<P>
 * The parent SOCHandPanel controls this panel's size, position, and visibility.
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

    /**
     * Typical button height for doLayout, in unscaled pixels.
     */
    private static final int BUTTON_HEIGHT = 18;

    /**
     * Typical button width, for doLayout, in unscaled pixels.
     */
    private static final int BUTTON_WIDTH = 55;

    /**
     * Height of a single-line text label in unscaled pixels,
     * including the auto-reject timer countdown when visible.
     * For convenience of other classes' layout calculations.
     * Not scaled by {@link #displayScale}.
     */
    private static final int LABEL_LINE_HEIGHT = 14;

    /**
     * Minimum width for trading square labels, for fallback if FontMetrics not available yet.
     * Not scaled by {@link #displayScale}.
     */
    private static final int SQUARES_LAB_MIN_WIDTH = 49;

    /** Listener to use for callbacks when buttons are clicked. */
    private final TPListener listener;

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
     * When clicked by user, callback methods are called.
     */
    private final JButton btn1, btn2, btn3;

    /**
     * Trade offer's resources.
     * Labeled with {@link #sqLabLine1} and {@link #sqLabLine2}.
     */
    private final SquaresPanel squares;

    /**
     * Text to label line 1 and line 2 of the trading {@link #squares}.
     * Width {@link #sqLabWidth} is calculated during {@link #doLayout()}.
     */
    private final JLabel sqLabLine1, sqLabLine2;

    /**
     * Width of {@link #sqLabLine1} and {@link #sqLabLine2}.
     * Calculated by {@link #calcLabelWidth()} for {@link #doLayout()}.
     */
    private int sqLabWidth;

    /**
     * Make a new TradePanel to hold a trade offer or counter-offer.
     *
     * @param buttonTexts  Array with 3 strings: Text for button 1, button 2, button 3
     * @param sqLabel1  Label for row 1 of the trading squares
     * @param sqLabel2  Label for row 2 of the trading squares
     * @param hasLine2  True if text above trading squares has a second line
     * @param hasLineBelow  True if the layout should leave room for text below the trading squares
     * @param listener   Listener for callbacks when buttons pressed; not {@code null}
     * @param displayScale  For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @throws IllegalArgumentException  if {@code buttonTexts} is null or length != 3, or {@code listener} is null,
     *     or {@code sqLabel1} or {@link sqLabel2} is null
     */
    public TradePanel
        (final String[] buttonTexts, final String sqLabel1, final String sqLabel2,
         boolean hasLine2, boolean hasLineBelow, TPListener listener, final int displayScale)
        throws IllegalArgumentException
    {
        if ((buttonTexts == null) || (buttonTexts.length != 3))
            throw new IllegalArgumentException("buttonTexts");
        if (listener == null)
            throw new IllegalArgumentException("listener");
        if ((sqLabel1 == null) || (sqLabel2 == null))
            throw new IllegalArgumentException("sqLabels");

        this.listener = listener;

        line1 = new JLabel();
        line2 = (hasLine2) ? new JLabel() : null;
        lineBelow = (hasLineBelow) ? new JLabel() : null;
        squares = new SquaresPanel(true, displayScale);
        sqLabLine1 = new JLabel(sqLabel1);
        sqLabLine2 = new JLabel(sqLabel2);
        this.displayScale = displayScale;

        setLayout(null);
        add(line1);
        if (hasLine2)
            add(line2);
        if (hasLineBelow)
            add(lineBelow);
        add(squares);

        JButton[] bs = new JButton[3];
        for (int i = 0; i < 3; ++i)
        {
            bs[i] = new JButton(buttonTexts[i]);
            add(bs[i]);
            bs[i].addActionListener(this);
        }
        btn1 = bs[0];
        btn2 = bs[1];
        btn3 = bs[2];
    }

    // TODO constants for min size, etc, from TradeOfferPanel

    /**
     * Get resource sets for each of the 2 resource square lines.
     * Does not validate that total &gt; 0 or that give/get totals are equal.
     * @return Array with a resource set for line 1, and a set for line 2,
     *     from contents of this panel's resource squares
     * @see #setTradeContents(SOCResourceSet, SOCResourceSet)
     */
    public SOCResourceSet[] getTradeContents()
    {
        int[] res1 = new int[5], res2 = new int[5];
        squares.getValues(res1, res2);

        return new SOCResourceSet[] { new SOCResourceSet(res1), new SOCResourceSet(res2) };
    }

    /**
     * Set trade panel's resource contents, or clear to 0.
     * @param line1  Trade resources to use in Line 1; will clear all to 0 if null
     * @param line2  Trade resources to use in Line 2; will clear all to 0 if null
     * @see #getTradeContents()
     */
    public void setTradeContents(SOCResourceSet line1, SOCResourceSet line2)
    {
        squares.setValues(line1, line2);
    }

    /**
     * Show or hide Button 1.
     * Is initially visible when TradePanel is created.
     * @param shown  True to show, false to hide
     */
    public void setButton1Visible(final boolean shown)
    {
        btn1.setVisible(shown);
    }

    /*
    public void setLineBelow(final String txt);  // TODO
    {
        
    } */

    /**
     * Custom layout for this OfferPanel, including the components within
     * its offer {@link #balloon} and counter-offer {@link #counterOfferBox}.
     */
    public void doLayout()
    {
        final Dimension dim = getSize();
        if ((dim.width == 0) || (dim.height == 0))
            return;

        final int lineHeight = LABEL_LINE_HEIGHT * displayScale, lineSpace = 3 * displayScale;

        final boolean compactMode;
            // TODO some field for compactMode, if needed outside doLayout or from a getter;
            // but when to calc it?  Maybe method called from here, setSize/setBounds/similar?
        {
            int nLines = 2;  // text lines
            if (line2 != null)
                ++nLines;
            if (lineBelow != null)
                ++nLines;

            compactMode =
                (dim.height <
                    ((lineHeight + lineSpace) * nLines + lineSpace + SquaresPanel.HEIGHT + lineSpace + ShadowedBox.SHADOW_SIZE)
                    * displayScale);
        }

        final int inset = 8 * displayScale;

        // Label text's width may increase required panel width
        //int w = OFFER_MIN_WIDTH * displayScale;
        final int squaresLabelW = calcLabelWidth() + (6 * displayScale);
            // from theyGetLab, givesYouLab FontMetrics; +6 is for padding before ColorSquares
        /*
        {
            int d = giveW - ((GIVES_MIN_WIDTH + 6) * displayScale);
            if (d > 0)
                w = Math.max
                    (OFFER_MIN_WIDTH_FROM_BUTTONS * displayScale,
                     OFFER_MIN_WIDTH_FROM_LABELS * displayScale + d);
        }
        */

        // - now position & size all contents:

        int y = lineSpace;
        int w = dim.width - (2 * inset);

        // - txt lines above; 2nd line width is truncated if compact mode

        line1.setBounds(inset, y, w, lineHeight);
        y += (lineSpace + lineHeight);

        if (line2 != null)
        {
            if (compactMode)
                w -= ((BUTTON_WIDTH + 2) * displayScale);

            line2.setBounds(inset, y, w, lineHeight);
            y += (lineSpace + lineHeight);
        }

        // - labels & trading squares
        //     TODO does compact mode have smaller space than 6 between label & sqpanel?
        sqLabLine1.setBounds(inset, y, squaresLabelW, lineHeight);
        squares.setLocation(inset + squaresLabelW + (6 * displayScale), y);
        y += (ColorSquareLarger.HEIGHT_L * displayScale);
        sqLabLine2.setBounds(inset, y + (ColorSquareLarger.HEIGHT_L * displayScale), squaresLabelW, lineHeight);

        // - 3 buttons at right position, etc, based on compact mode
        if (compactMode)
        {
            // Buttons to right of offer squares, y-centered vs. height of panel
            int buttonY =
                (((dim.height / displayScale) - BUTTON_HEIGHT - ShadowedBox.SHADOW_SIZE - 2) - (3 * BUTTON_HEIGHT + 4))
                * displayScale / 2;
            final int buttonX = inset + squaresLabelW + ((SquaresPanel.WIDTH + 2) * displayScale),
                      buttonW = BUTTON_WIDTH * displayScale,
                      buttonH = BUTTON_HEIGHT * displayScale,
                      pix2 = 2 * displayScale;

            btn1.setBounds(buttonX, buttonY, buttonW, buttonH);
            buttonY += buttonH + pix2;
            btn2.setBounds(buttonX, buttonY, buttonW, buttonH);
            buttonY += buttonH + pix2;
            btn3.setBounds(buttonX, buttonY, buttonW, buttonH);
        } else {
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
        }

        // - txt below, if any
        if (lineBelow != null)
        {
            y += lineSpace + (SquaresPanel.HEIGHT * displayScale);
            lineBelow.setBounds(inset, y, dim.width - (2 * inset), lineHeight);
        }
    }

    /**
     * If not yet done, try to calculate the width in scaled pixels of the text in the trading square labels,
     * whichever label is wider. Calculated once from FontMetrics, then cached.
     * If not available, falls back to a hardcoded minimum width.
     *
     * @return  Calculated label width if FontMetrics available,
     *     otherwise {@link #SQUARES_LAB_MIN_WIDTH} * {@code displayScale}
     */
    private int calcLabelWidth()
    {
        if (sqLabWidth == 0)
        {
            final FontMetrics fm = getFontMetrics(sqLabLine1.getFont());
            if (fm == null)
                return SQUARES_LAB_MIN_WIDTH * displayScale;

            sqLabWidth = Math.max
                (fm.stringWidth(sqLabLine1.getText()), fm.stringWidth(sqLabLine2.getText()));
        }

        return sqLabWidth;
    }

    /** Handle button clicks/activations by calling our {@link TPListener}. */
    public void actionPerformed(ActionEvent e)
    {
        final Object src = e.getSource();
        if (src == btn1)
            listener.button1Clicked(this);
        else if (src == btn2)
            listener.button2Clicked(this);
        else if (src == btn3)
            listener.button3Clicked(this);
    }

    /**
     * Listener for callback methods when {@link TradePanel} buttons are clicked.
     */
    public interface TPListener
    {
        /** Callback for when button 1 is clicked or otherwise activated. */
        public void button1Clicked(TradePanel src);

        /** Callback for when button 2 is clicked or otherwise activated. */
        public void button2Clicked(TradePanel src);

        /** Callback for when button 3 is clicked or otherwise activated. */
        public void button3Clicked(TradePanel src);
    }

}
