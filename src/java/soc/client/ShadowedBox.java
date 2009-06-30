/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;


/**
 * This is a shadowed box for use in the hand panel
 *
 * @author Robert S. Thomas
 */
public class ShadowedBox extends Canvas
{
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
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public Dimension getPreferedSize()
    {
        return new Dimension(width, height);
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public Dimension getMinimumSize()
    {
        return new Dimension(width, height);
    }

    /**
     * DOCUMENT ME!
     *
     * @param g DOCUMENT ME!
     */
    public void paint(Graphics g)
    {
        Dimension dim = getSize();
        int h = dim.height;
        int w = dim.width;
        int xm = 5;
        int ym = 5;
        int i;

        g.setPaintMode();
        g.setColor(interior);
        g.fillRect(0, 0, w - xm, h - ym);
        g.setColor(Color.black);
        g.drawRect(0, 0, w - xm, h - ym);

        for (i = xm; i > 0; i--)
        {
            g.drawLine(ym, h - i, w, h - i);
        }

        for (i = ym; i > 0; i--)
        {
            g.drawLine(w - i, xm, w - i, h);
        }
    }
}
