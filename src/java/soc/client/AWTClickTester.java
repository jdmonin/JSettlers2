/**
 * Testing for cross-platform context-click (right-click)
 *
 * This file copyright (C) 2007-2010 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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
 * The author of this program can be reached at jeremy@nand.net
 */
package soc.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * Testing for cross-platform context-click (right-click); standalone class.
 * @author Jeremy D Monin <jeremy@nand.net>
 *
 */
public class AWTClickTester extends java.awt.Canvas implements MouseListener
{
    int lastX, lastY;
    int wid, ht;
    Dimension sz;
    
    public AWTClickTester()
    {
        lastX = -1;  lastY = -1;
        wid = 200;  ht = 150;
        sz = new Dimension (wid, ht);
        setBackground(Color.WHITE);
        addMouseListener(this);
    }
    
    @Override
    public void update(Graphics g) { paint(g); }
    
    @Override
    public Dimension getPreferredSize() { return sz; }
    
    @Override
    public Dimension getMinimumSize() { return sz; }
    
    public void setLastClick (int x, int y)
    {
        lastX = x;
        lastY = y;
        repaint();
    }
    
    @Override
    public void paint(Graphics g)
    {
        g.clearRect(0, 0, wid, ht);
        if (lastX == -1)
            return;
        g.setColor(Color.BLUE);
        g.drawLine(lastX - 15, lastY, lastX + 15, lastY);
        g.drawLine(lastX, lastY - 15, lastX, lastY + 15);
    }

    /* (non-Javadoc)
     * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
     */
    public void mouseClicked(MouseEvent e)
    {
        report("click", e);
    }

    /* (non-Javadoc)
     * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
     */
    public void mouseEntered(MouseEvent e) { }

    /* (non-Javadoc)
     * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
     */
    public void mouseExited(MouseEvent e) { }

    /* (non-Javadoc)
     * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
     */
    public void mousePressed(MouseEvent e)
    {
        report("press", e);
    }

    /* (non-Javadoc)
     * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
     */
    public void mouseReleased(MouseEvent e)
    {
        report("rele ", e);
    }
    
    protected void report (String etype, MouseEvent e)
    {
        int x = e.getX();
        int y = e.getY();
        String popclick;
        if (e.isPopupTrigger())
            popclick = " isPopupTrigger";
        else
            popclick = "";
        System.out.println
            (etype + " (" + x + ", " + y + ") at "
            + e.getWhen() + " button=0x"
            + Integer.toHexString(e.getButton())
            + " mods=0x"
            + Integer.toHexString(e.getModifiers())
            + popclick);
        setLastClick (x, y);
    }
    
    public void printButtonsMods()
    {
        System.out.println("BUTTON:");
        System.out.println("  1:  0x" + Integer.toHexString(MouseEvent.BUTTON1) + " mask 0x" + Integer.toHexString(InputEvent.BUTTON1_MASK));
        System.out.println("  2:  0x" + Integer.toHexString(MouseEvent.BUTTON2) + " mask 0x" + Integer.toHexString(InputEvent.BUTTON2_MASK));
        System.out.println("  3:  0x" + Integer.toHexString(MouseEvent.BUTTON3) + " mask 0x" + Integer.toHexString(InputEvent.BUTTON3_MASK));
        System.out.println("MODS:");
        System.out.println("  Shift: 0x" + Integer.toHexString(InputEvent.SHIFT_MASK));
        System.out.println("  Ctrl:  0x" + Integer.toHexString(InputEvent.CTRL_MASK));
        System.out.println("  Alt:   0x" + Integer.toHexString(InputEvent.ALT_MASK));
        System.out.println("  Meta:  0x" + Integer.toHexString(InputEvent.META_MASK));
        System.out.println();
    }

    /**
     * @param args
     */
    public static void main(String[] args)
    {
        Frame f = new Frame("Click tester");
        AWTClickTester ct = new AWTClickTester();
        f.add(ct);
        f.pack();
        f.setVisible(true);
        ct.printButtonsMods();
    }

}
