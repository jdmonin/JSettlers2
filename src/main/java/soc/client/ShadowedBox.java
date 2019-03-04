/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2017,2019 Jeremy D Monin <jeremy@nand.net>
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
 * This is a shadowed box for use in the hand panel.
 * Both {@code ShadowedBox} and {@link SpeechBalloon} are used in {@link TradeOfferPanel}.
 *
 * @author Robert S. Thomas
 */
@SuppressWarnings("serial")
/*package*/ class ShadowedBox extends JPanel
{
    /**
     * Size of the shadow, in pixels. Not scaled by {@code displayScale}.
     * @since 1.1.08
     */
    public static final int SHADOW_SIZE = 5;

    int height;
    int width;
    Color interior;

    /**
     * For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @since 2.0.00
     */
    private final int displayScale;

    /**
     * Constructor. Sets a small default size and assumes a layout manager will soon change that size.
     *
     * @param bg  the background color beyond edges of the panel
     * @param interior  the color of the box interior
     * @param displayScale  For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @param lm  LayoutManager to use, or {@code null}
     */
    public ShadowedBox(Color bg, Color interior, final int displayScale, LayoutManager lm)
    {
        super(lm);
        height = 50;
        width = 50;
        this.displayScale = displayScale;

        setBackground(bg);
        setForeground(Color.black);
        this.interior = interior;

        // nonzero size helps when adding to a JPanel
        Dimension initSize = new Dimension(width, height);
        setSize(initSize);
        setMinimumSize(initSize);
        setPreferredSize(initSize);
    }

    public void setInterior(Color interior)
    {
        this.interior = interior;
    }

    public Color getInterior()
    {
        return interior;
    }

    /**
     * Preferred (current) size for this ShadowedBox.
     */
    public Dimension getPreferredSize()
    {
        return new Dimension(width, height);
    }

    /**
     * Minimum acceptable size for this ShadowedBox.
     */
    public Dimension getMinimumSize()
    {
        return new Dimension(width, height);
    }

    // TODO To help TradeOfferPanel doLayout, actually set insets and paint here as a custom Border

    /**
     * Draw this ShadowedBox.
     *
     * @param g Graphics
     */
    public void paintComponent(Graphics g)
    {
        Dimension dim = getSize();
        final int h = dim.height;
        final int w = dim.width;
        final int xm = SHADOW_SIZE * displayScale;
        final int ym = SHADOW_SIZE * displayScale;

        g.setPaintMode();
        g.setColor(getBackground());
        g.fillRect(0, 0, w, h);

        g.setColor(interior);
        g.fillRect(0, 0, w - xm, h - ym);
        g.setColor(Color.black);
        g.drawRect(0, 0, w - xm, h - ym);

        // Draw the shadow
        g.fillRect(ym, h - xm, w, h - 1);  // bottom
        g.fillRect(w - ym, xm, w - 1, h);  // right
    }

}
