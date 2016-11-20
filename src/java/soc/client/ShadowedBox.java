/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009 Jeremy D. Monin <jeremy@nand.net>
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.client;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;


/**
 * This is a shadowed box for use in the hand panel
 *
 * @author Robert S. Thomas
 */
@SuppressWarnings("serial")
public class ShadowedBox extends Canvas
{
    /**
     * Size of the shadow, in pixels.
     * @since 1.1.08
     */
    public static final int SHADOW_SIZE = 5;

    int height;
    int width;
    Color interior;

    /**
     * constructor
     *
     * @param bg  the background color of the panel
     * @param interior  the color of the box interior
     */
    public ShadowedBox(Color bg, Color interior)
    {
        super();
        height = 50;
        width = 50;
        setBackground(bg);
        setForeground(Color.black);
        this.interior = interior;
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

    /**
     * Draw this ShadowedBox.
     *
     * @param g Graphics
     */
    public void paint(Graphics g)
    {
        Dimension dim = getSize();
        int h = dim.height;
        int w = dim.width;
        final int xm = SHADOW_SIZE;
        final int ym = SHADOW_SIZE;

        g.setPaintMode();
        g.setColor(interior);
        g.fillRect(0, 0, w - xm, h - ym);
        g.setColor(Color.black);
        g.drawRect(0, 0, w - xm, h - ym);

        // Draw the shadow
        g.fillRect(ym, h - xm, w, h - 1);
        g.fillRect(w - ym, xm, w - 1, h);
    }
}
