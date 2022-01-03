/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2019-2020 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003 Robert S. Thomas <thomas@infolab.northwestern.edu>
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
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Panel to show a player's status text or response.
 *<P>
 * This panel is used for tasks such as:
 *<UL>
 *  <LI> Saying "no thanks" to a trade offer
 *  <LI> Showing vote on a board reset request
 *  <LI> Showing the player is deciding what to discard
 *</UL>
 *<P>
 * Before v2.0.00 this was an inner class of {@code TradeOfferPanel}.
 */
/*package*/ class MessagePanel extends SpeechBalloon
{
    /** Last structural change in v2.0.00 */
    private static final long serialVersionUID = 2000L;

    /**
     * Margin at left and right side of panel, in unscaled pixels.
     * Not scaled by {@link #displayScale}.
     * @since 2.0.00
     */
    private static final int PANEL_MARGIN_HORIZ = 8;

    /**
     * Minimum width for contents, excluding {@link #PANEL_MARGIN_HORIZ} and {@link SpeechBalloon#SHADOW_SIZE}.
     * Not scaled by {@link #displayScale}.
     * @since 2.0.00
     */
    private static final int PANEL_INNER_WIDTH = 169;

    /**
     * For 1 line of text, {@link #msg} contains the entire text.
     * For 2 lines separated by <tt>\n</tt>, {@link #msg} and {@link #msg2} are used.
     * @see #msgLines
     */
    private final JLabel msg, msg2;

    /**
     * Height of the text in one label, from <tt>getFontMetrics({@link #msg}.getFont()).getHeight()</tt>.
     * Should be less than {@link #msgHeight}. 0 if unknown.
     */
    private int oneLineHeight;

    /**
     * Height of each label ({@link #msg}, {@link #msg2}). Should be {@link #oneLineHeight} + insets.
     * 0 if unknown.
     * @since 1.1.00
     */
    private int msgHeight;

    /**
     * Number of lines of text; 1, or 2 if text contains <tt>\n</tt>.
     * After changing this, set {@link #msgHeight} = 0 and call {@link #validate()}.
     * @since 2.0.00
     */
    private int msgLines;

    /**
     * For high-DPI displays, what scaling factor to use? Unscaled is 1.
     */
    private final int displayScale;

    /**
     * Creates a new MessagePanel.
     * Has room for 1 or 2 centered lines of text.
     * @param bgColor  Background color outside of speech balloon, from {@link SOCPlayerInterface#getPlayerColor(int)},
     *     or {@code null} to not draw that outside portion
     * @param displayScale  For high-DPI displays, what scaling factor to use? Unscaled is 1.
     */
    public MessagePanel(final Color bgColor, final int displayScale)
    {
        super(bgColor, displayScale, null);  // custom doLayout
        this.displayScale = displayScale;

        final Color[] colors = SwingMainDisplay.getForegroundBackgroundColors(true, false);
        if (colors != null)
        {
            setForeground(colors[0]);  // Color.BLACK
            setBackground(colors[2]);  // SwingMainDisplay.DIALOG_BG_GOLDENROD
        }

        final Font msgFont = new Font("SansSerif", Font.PLAIN, 18 * displayScale);

        msg = new JLabel(" ", SwingConstants.CENTER);
        msg.setFont(msgFont);
        msg.setForeground(null);

        msg2 = new JLabel(" ", SwingConstants.CENTER);
        msg2.setVisible(false);
        msg2.setFont(msgFont);
        msg2.setForeground(null);

        msgLines = 1;
        add(msg);
        add(msg2);

        final Dimension initSize = new Dimension(50, 50);  // TODO use constants & displayScale
        setSize(initSize);
        setMinimumSize(initSize);
    }

    /**
     * Update the text shown in this messagepanel.
     * Does not show or hide the panel, only changes the label text.
     * @param message message to display, or null for no text
     */
    public void setText(final String message)
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
     * If not yet done (value 0), calculate the values for {@link #oneLineHeight} and {@link #msgHeight}
     * based on {@link #msgLines} and getFontMetrics({@link #msg}.getFont()).
     *
     * @see OfferPanel#calcLabelWidth(boolean)
     * @since 2.0.00
     */
    private void calcLabelMinHeight()
    {
        if (oneLineHeight == 0)
            oneLineHeight = getFontMetrics(msg.getFont()).getHeight();
        if (msgHeight == 0)
            msgHeight = oneLineHeight + 4 * displayScale;
    }

    /**
     * Get our preferred size for 2 rows of text.
     * Used only when our parent {@link SOCHandPanel} doesn't have a {@link TradePanel};
     * otherwise will be set to same size.
     * @since 2.0.00
     */
    @Override
    public Dimension getPreferredSize()
    {
        calcLabelMinHeight();

        return new Dimension
            ((PANEL_INNER_WIDTH + (2 * PANEL_MARGIN_HORIZ) + SHADOW_SIZE) * displayScale,
             3 * msgHeight + (4 + BALLOON_POINT_SIZE + SHADOW_SIZE) * displayScale);
                 // actual minimum height needs 2 * msgHeight; add another msgHeight here for margins
    }

    /**
     * Custom layout for just the message panel.
     * To center {@link #msg} and {@link #msg2} vertically after changing {@link #msgLines},
     * set {@link #msgHeight} to 0 before calling.
     */
    public void doLayout()
    {
        final Dimension dim = getSize();  // includes BALLOON_POINT_SIZE at top, SHADOW_SIZE at bottom
        final int inset = 2 * SHADOW_SIZE * displayScale;

        calcLabelMinHeight();

        int h = dim.height - ((BALLOON_POINT_SIZE + SHADOW_SIZE) * displayScale);
        if ((msgHeight * msgLines) > h)
            msgHeight = h / msgLines;
        int msgY = (h - msgHeight) / 2 + (BALLOON_POINT_SIZE * displayScale);
        if (msgLines != 1)
            msgY -= (oneLineHeight / 2);  // move up to make room for msg2
        if (msgY < 0)
            msgY = 0;

        int msgW = dim.width - (2 * inset) - ((SHADOW_SIZE * displayScale) / 2);
        msg.setBounds
            (inset, msgY, msgW, msgHeight);
        if (msgLines != 1)
        {
            msgY += oneLineHeight;
            msg2.setBounds
                (inset, msgY, msgW, msgHeight);
        }
    }

}
