/**
 * Testing for cross-platform context-click (right-click)
 */
package soc.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import org.apache.log4j.Logger;

/**
 * Testing for cross-platform context-click (right-click); standalone class.
 * @author Jeremy D Monin <jeremy@nand.net>
 *
 */
public class AWTClickTester extends java.awt.Canvas implements MouseListener
{
	/** debug logging */
    private transient Logger log = Logger.getLogger(this.getClass().getName());

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
    
    public void update(Graphics g) { paint(g); }
    
    public Dimension getPreferredSize() { return sz; }
    
    public Dimension getMinimumSize() { return sz; }
    
    public void setLastClick (int x, int y)
    {
        lastX = x;
        lastY = y;
        repaint();
    }
    
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
        log.info
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
        log.info("BUTTON:");
        log.info("  1:  0x" + Integer.toHexString(MouseEvent.BUTTON1) + " mask 0x" + Integer.toHexString(MouseEvent.BUTTON1_MASK));
        log.info("  2:  0x" + Integer.toHexString(MouseEvent.BUTTON2) + " mask 0x" + Integer.toHexString(MouseEvent.BUTTON2_MASK));
        log.info("  3:  0x" + Integer.toHexString(MouseEvent.BUTTON3) + " mask 0x" + Integer.toHexString(MouseEvent.BUTTON3_MASK));
        log.info("MODS:");
        log.info("  Shift: 0x" + Integer.toHexString(MouseEvent.SHIFT_MASK));
        log.info("  Ctrl:  0x" + Integer.toHexString(MouseEvent.CTRL_MASK));
        log.info("  Alt:   0x" + Integer.toHexString(MouseEvent.ALT_MASK));
        log.info("  Meta:  0x" + Integer.toHexString(MouseEvent.META_MASK));
        log.info("");
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
        f.show();
        ct.printButtonsMods();
    }

}
