/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2012,2016-2017,2019-2020 Jeremy D Monin <jeremy@nand.net>
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
import java.awt.Graphics;
import java.awt.LayoutManager;

import javax.swing.JPanel;


/**
 * This is a rectangular speech balloon shape for use in the hand panel.
 * {@code SpeechBalloon} is used by {@link TradePanel}.
 * related {@link ShadowedBox} is used in {@link MessagePanel}.
 *<P>
 * By default, shows a pointed tip near the left side of its top edge:<PRE>
 * __|\________________
 * |                  | </PRE>
 * Because of this point, the main rectangle of the balloon doesn't take up
 * the entire height (as set by {@link #setSize(int, int)} or
 * {@link #setBounds(int, int, int, int)}), but begins {@link #BALLOON_POINT_SIZE} pixels down.
 * Even when the point is hidden by {@link #setBalloonPoint(boolean) setBalloonPoint(false)},
 * this is still the case.
 *<P>
 * When centering items within the balloon, remember the top inset of
 * {@link #BALLOON_POINT_SIZE} mentioned above, and the bottom and right insets
 * of {@link #SHADOW_SIZE}.  Left inset is 0.
 *
 * @author Robert S. Thomas
 */
@SuppressWarnings("serial")
/*package*/ class SpeechBalloon extends JPanel
{
    /**
     * Size of the shadow appearing on the right and bottom sides: 5 pixels.
     * Not scaled by {@code displayScale}.
     * @since 1.1.08
     */
    public static final int SHADOW_SIZE = 5;

    /**
     * Size of the pointed tip at the top of the balloon, when visible: 12 pixels.
     * Not scaled by {@code displayScale}.
     * @since 2.0.00
     */
    public static final int BALLOON_POINT_SIZE = 12;

    /**
     * Background color for our parent panel beyond the edges of SpeechBalloon,
     * or {@code null} to not draw that outside portion.
     * @see #balloonColor
     * @since 2.0.00
     */
    private final Color behindColor;

    /**
     * Background color of the SpeechBalloon interior; typically {@link SwingMainDisplay#DIALOG_BG_GOLDENROD}.
     * @see #behindColor
     */
    private final Color balloonColor;

    int height;
    int width;

    /**
     * Is the balloon's pointed tip showing? (If not, it's drawn as a rectangle)
     * @since 1.1.08
     */
    private boolean balloonPoint;

    /**
     * For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @since 2.0.00
     */
    private final int displayScale;

    /**
     * Constructor. Foreground color will be {@link Color#BLACK},
     * background color in balloon interior will be {@link SwingMainDisplay#DIALOG_BG_GOLDENROD}.
     * Sets a small default size and assumes a layout manager will soon change that size.
     *
     * @param behindColor  the background color beyond edges of the panel,
     *     or {@code null} to not draw that outside portion
     * @param displayScale  For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @param lm  LayoutManager to use, or {@code null}
     */
    public SpeechBalloon(Color behindColor, final int displayScale, LayoutManager lm)
    {
        super(lm);

        height = 50;
        width = 50;
        this.behindColor = behindColor;
        this.displayScale = displayScale;

        Color[] colors = SwingMainDisplay.getForegroundBackgroundColors(true, false);
        if (colors == null)
            colors = SwingMainDisplay.getForegroundBackgroundColors(false, true);  // system colors (high-contrast mode)

        balloonColor = colors[2];  // SwingMainDisplay.DIALOG_BG_GOLDENROD
        setBackground(balloonColor);
        setForeground(colors[0]);  // Color.BLACK
        balloonPoint = true;

        // nonzero size helps when adding to a JPanel
        Dimension initSize = new Dimension(width, height);
        setSize(initSize);
        setMinimumSize(initSize);
        setPreferredSize(initSize);
    }

    /**
     * Preferred (current) size for this SpeechBalloon.
     */
    public Dimension getPreferredSize()
    {
        return new Dimension(width, height);
    }

    /**
     * Minimum acceptable size for this SpeechBalloon.
     */
    public Dimension getMinimumSize()
    {
        return new Dimension(width, height);
    }

    /**
     * Should this balloon display its point, along the top edge?
     * Height of the balloon point is {@link #BALLOON_POINT_SIZE} pixels.
     * @since 1.1.08
     */
    public boolean getBalloonPoint()
    {
        return balloonPoint;
    }

    /**
     * Should this balloon display its point, along the top edge?
     * Even when hidden, the main rectangle is drawn beginning {@link #BALLOON_POINT_SIZE} pixels down.
     * Triggers a repaint.
     * @param point  true to display, false to hide
     * @since 1.1.08
     */
    public void setBalloonPoint(final boolean point)
    {
        if (balloonPoint == point)
            return;

        balloonPoint = point;
        repaint();
    }

    // TODO To help MessagePanel doLayout, actually set insets and paint here as a custom Border

    /**
     * Draw this balloon.
     *
     * @param g Graphics
     */
    public void paintComponent(Graphics g)
    {
        final Dimension dim = getSize();
        final int h = dim.height;
        final int w = dim.width;
        final int xm = SHADOW_SIZE * displayScale;
        final int ym = SHADOW_SIZE * displayScale;
        final int bpSize = BALLOON_POINT_SIZE * displayScale;

        g.setPaintMode();
        if (behindColor != null)
        {
            g.setColor(behindColor);
            g.fillRect(0, 0, w, h);
        }

        g.setColor(balloonColor);
        if (balloonPoint)
        {
            int[] xPoints =
                { 0, bpSize, bpSize, (7 * BALLOON_POINT_SIZE * displayScale) / 4,
                  w - xm, w - xm, 0, 0 };
            int[] yPoints =
                { bpSize, bpSize, 0, bpSize,
                    bpSize, h - ym, h - ym, bpSize };

            g.fillPolygon(xPoints, yPoints, 8);
            g.setColor(Color.BLACK);
            g.drawPolygon(xPoints, yPoints, 8);
        } else {
            g.fillRect(0, bpSize, w - xm, h - ym - bpSize);
            g.setColor(Color.BLACK);
            g.drawRect(0, bpSize, w - xm, h - ym - bpSize);
        }

        // Draw the shadow
        g.fillRect(ym, h - xm, w, h - 1);  // bottom
        g.fillRect(w - ym, (h / 6) + xm, w - 1, h); // right
    }

}
