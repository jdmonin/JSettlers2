/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2012,2016-2017,2019 Jeremy D Monin <jeremy@nand.net>
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

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;


/**
 * This is a rectangular speech balloon shape for use in the hand panel.
 * Both {@code SpeechBalloon} and {@link ShadowedBox} are used in {@link TradeOfferPanel}.
 *<P>
 * By default, it shows a point near the left side of its top edge:<PRE>
 * __|\________________
 * |                  | </PRE>
 * Because of this point, the main rectangle of the balloon doesn't take up
 * the entire height (as set by {@link #setSize(int, int)} or
 * {@link #setBounds(int, int, int, int)}), but begins at height / 8.
 * Even when the point is hidden by {@link #setBalloonPoint(boolean) setBalloonPoint(false)},
 * this is still the case.
 *<P>
 * When centering items within the balloon, remember the top inset of
 * <tt>height / 8</tt> mentioned above, and the bottom and right insets
 * of {@link #SHADOW_SIZE}.  Left inset is 0.
 *
 * @author Robert S. Thomas
 */
@SuppressWarnings("serial")
public class SpeechBalloon extends Canvas
{
    /**
     * Size of the shadow appearing on the right and bottom sides, in pixels.
     * @since 1.1.08
     */
    public static final int SHADOW_SIZE = 5;

    private static Color balloonColor = SOCPlayerInterface.DIALOG_BG_GOLDENROD;
    int height;
    int width;

    /**
     * Is the balloon's point showing? (If not, it's drawn as a rectangle)
     * @since 1.1.08
     */
    private boolean balloonPoint;

    /**
     * constructor
     *
     * @param bg  the background color of the panel; foreground will be {@link Color#BLACK}
     */
    public SpeechBalloon(Color bg)
    {
        super();
        height = 50;
        width = 50;
        setBackground(bg);
        setForeground(Color.BLACK);
        balloonPoint = true;
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
     * Height of the balloon point is balloon height / 8.
     * @since 1.1.08
     */
    public boolean getBalloonPoint()
    {
        return balloonPoint;
    }

    /**
     * Should this balloon display its point, along the top edge?
     * Even when hidden, the main rectangle is drawn beginning at height / 8.
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

    /**
     * Draw this balloon.
     *
     * @param g Graphics
     */
    public void paint(Graphics g)
    {
        final Dimension dim = getSize();
        final int h = dim.height;
        final int w = dim.width;
        final int xm = SHADOW_SIZE;
        final int ym = SHADOW_SIZE;

        g.setPaintMode();
        g.setColor(balloonColor);
        if (balloonPoint)
        {
            int[] xPoints = { 0, w / 8, w / 8, ((w / 8) + (w / 16)), w - xm, w - xm, 0, 0 };
            int[] yPoints = { h / 8, h / 8, 0, h / 8, h / 8, h - ym, h - ym, h / 8 };

            g.fillPolygon(xPoints, yPoints, 8);
            g.setColor(Color.BLACK);
            g.drawPolygon(xPoints, yPoints, 8);
        } else {
            final int hdiv8 = h / 8;
            g.fillRect(0, hdiv8, w - xm, h - ym - hdiv8);
            g.setColor(Color.BLACK);
            g.drawRect(0, hdiv8, w - xm, h - ym - hdiv8);
        }

        // Draw the shadow
        g.fillRect(ym, h - xm, w, h - 1);
        g.fillRect(w - ym, (h / 6) + xm, w - 1, h);
    }

}
