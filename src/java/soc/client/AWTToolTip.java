/*
 * $Id$
 *
 * (c)2000 IoS Gesellschaft fr innovative Softwareentwicklung mbH
 * http://www.IoS-Online.de    mailto:info@IoS-Online.de
 * Portions (c)2007,2008 Jeremy D Monin <jeremy@nand.net>
 * originally from (GPL'd) de.ios.framework.gui.ExpandTooltip;
 * JM - using for jsettlers AWT tooltip
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 */


package soc.client;

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;

/**
 * A short tooltip for a component.
 * Does not handle component resize or moving, but will respond to hide/un-hide.
 */
public class AWTToolTip
  extends Canvas
  implements MouseListener, MouseMotionListener, ComponentListener
{

  /** The tip is hidden after the mouse has been moved
   *  closeAfterMoveX poinntes horizontally or closeAfterMoveY
   *  points vertically away from the point where it has been clicked
   *  (or it has left its parent component's area)
   */
  public int closeAfterMoveX = 100;
  public int closeAfterMoveY = 20;

  private String tip;

  /** tfont is parentComp.getFont, set at mouseEntered */
  protected Font tfont;

  /** Component to which tooltip is notionally added, set in constructor.
   *  Actually tip is added directly to mainParentComp when needed.
   *  Tooltip is a mouseListener, mouseMotionListener of parentComp.
   */
  protected Component parentComp;

  /** parentComp's top-level parent, set at mouseEntered; null if not currently visible */
  protected Container mainParentComp;

  /** thread-lock on changes/accesses of mainParentComp */
  protected Object l_mainParentComp;

  /** true layout manager of mainParentComp; temporarily changed to add the tooltip */
  protected LayoutManager mainParentLayout;

  /** Position of parentComp within painParentComp */
  protected int parentX, parentY;

  /** Mouse location within parentComp */
  private int mousePosAtWinShowX, mousePosAtWinShowY;

  /** Does the tooltip automatically appear when mouse enters {@link #parentComp}? */
  private boolean autoPopup = false;

  /**
   * X-offset (from mouse location) of tooltip upper-left corner
   * @see #showAtMouse(int, int)
   */
  public static int OFFSET_X = 10;

  /**
   * Y-offset (from mouse location) of tooltip upper-left corner
   * @see #showAtMouse(int, int)
   */
  public static int OFFSET_Y = 10;

  /** Want shown? If true, must dynamically add us to {@link #parentComp} when become visible. */
  private boolean wantsShown;

  /** Currently showing?  Also indicated by mainParentComp != null. */
  private boolean isShown;  // TODO pick one (isShown or mainParentComp != null)

  /** Our location within parentComp */
  private int boxX, boxY;

  /** Our size */
  private int boxW, boxH;

  /** The background color of the window */
  static Color bgcol = new Color(240, 240, 180); // light yellow
  /** The foreground color of the window */
  static Color fgcol = Color.BLACK;

  /**
   * Constructs a Tooltip which is displayed when the mouse enters the given component.
   * The tooltip's font is the same as the component's font.
   *
   * @param _comp the Component which this Tooltip describes.
   * @param _tip Text to show; single line.
   */
  public AWTToolTip(String _tip, Component _comp)
  {
    if (_tip == null)
      throw new IllegalArgumentException("tip null");
    if (_comp == null)
      throw new IllegalArgumentException("comp null");
    parentComp = _comp;
    autoPopup = true;
    tip = _tip;
    parentComp.addMouseListener( this );
    parentComp.addMouseMotionListener( this );
    parentComp.addComponentListener( this );
    setBackground(bgcol);
    wantsShown = true;
    isShown = false;
    l_mainParentComp = new Object();

    // These are set at mouseEntered
    mainParentComp = null;
    mainParentLayout = null;
    tfont = null;
  }

  /**
   * @return the tooltip text.
   */
  public String getTip()
  {
    return tip;
  }

  /**
   * Change the tooltip text. Handles repaint and (if needed) reposition
   *
   * @param newTip New tip text
   *
   * @throws IllegalArgumentException if newTip is null
   */
  public void setTip(String newTip) throws IllegalArgumentException
  {
    if (newTip == null)
      throw new IllegalArgumentException("newTip null");
    if (tip.equals(newTip))
        return;    // <--- Early return: same text ---
    tip = newTip;

    if ( (! (wantsShown || isShown)) || (mainParentComp == null))
      return;      // <--- Early return: done, is not showing ---

    if (! isShown)
    {
      wantsShown = true;
    }
    else
    {
      int x = mousePosAtWinShowX;
      int y = mousePosAtWinShowY;
      removeFromParent();
      wantsShown = true;
      addToParent(x, y);
    }
  }

  /**
   * Show tip at appropriate location when mouse
   * is at (x,y) within mainparent (NOT within parentComp).
   * If not currently visible, nothing happens.
   */
  protected void showAtMouse(int x, int y)
  {
      if (mainParentComp == null)
          return;  // Not showing

      boxX = OFFSET_X + x;
      boxY = OFFSET_Y + y;

      // Remember for next time
      mousePosAtWinShowX = x - parentX;
      mousePosAtWinShowY = y - parentY;

      // Goals:
      // - Don't have it extend off the visible area
      // - Mouse pointer tip should not be within our bounding box (flickers)

      if ( ((x >= boxX) && (x < (boxX + boxW)))
          || (mainParentComp.getSize().width <= ( boxX + boxW )) )
      {
          // Try to float it to left of mouse pointer
          boxX = x - boxW - OFFSET_X;
          if (boxX < 0)
          {
              // Not enough room, just place flush against right-hand side
              boxX = mainParentComp.getSize().width - boxW;
          }
      }
      if ( ((y >= boxY) && (y < (boxY + boxH)))
          || ((mainParentComp.getSize().height - mainParentComp.getInsets().bottom)
               <= ( boxY + boxH )) )
      {
          // Try to float it above mouse pointer
          boxY = y - boxH - OFFSET_Y;
          if (boxY < 0)
          {
              // Not enough room, just place flush against top
              boxY = 0;
          }
      }

      setLocation(boxX, boxY);
  }

  public void update(Graphics g)
  {
      paint(g);
  }

  public void paint(Graphics g)
  {
    if (! (wantsShown && isShown))
        return;
    g.setColor(getBackground());
    g.fillRect(0, 0, boxW-1, boxH-1);
    g.setColor(fgcol);
    g.drawRect(0, 0, boxW-1, boxH-1);
    g.setFont(tfont);
    g.drawString(tip, 2, boxH -3);
  }

  /** Hide and remove from a main parent, until addToParent is called (typically from mouseEntered).
   *  Does not remove from the immediate parent passed to the constructor.
   *
   * @see #addToParent(int, int)
   */
  protected void removeFromParent()
  {
    if (isShown)
    {
      Container ourMP;
      synchronized (l_mainParentComp)
      {
        if (mainParentComp == null)
        {
          isShown = false;
          return;
        }
        ourMP = mainParentComp;
        ourMP.remove(0);
        if (mainParentLayout != null)
          ourMP.setLayout(mainParentLayout);
        mainParentComp = null;
        isShown = false;
      }
      ourMP.validate();
    }
  }

  /** Add and show tooltip, with mouse at this location.
   *  mainParentComp will be set to the "top-level" container of the
   *  parent passed to the constructor.
   *
   *  If already added to a (main) parent, nothing happens.
   *  If the parent is currently not visible, nothing happens.
   *
   * @param x Mouse position within parentComp when adding
   *      (NOT within mainparent)
   * @param y Mouse position within parentComp when adding
   *      (NOT within mainparent)
   *
   * @see #removeFromParent()
   */
  protected void addToParent(int x, int y)
  {
    if (! wantsShown)
        return;
    if (! parentComp.isVisible())
        return;
    Container ourMP;
    synchronized (l_mainParentComp)
    {
        if (mainParentComp != null)  // Already showing
            return;

        mainParentComp = getParentContainer(parentComp);
        ourMP = mainParentComp;
        mainParentLayout = ourMP.getLayout();
        ourMP.setLayout(null);  // Allow free placement
    }

    tfont = parentComp.getFont();
    FontMetrics fm = getFontMetrics(tfont);
    boxW = fm.stringWidth(tip) + 6;
    boxH = fm.getHeight();
    setSize(boxW, boxH);

    parentX = parentComp.getLocationOnScreen().x - mainParentComp.getLocationOnScreen().x;
    parentY = parentComp.getLocationOnScreen().y - mainParentComp.getLocationOnScreen().y;
    showAtMouse(x + parentX, y + parentY);

    synchronized (l_mainParentComp)
    {
        if (ourMP == mainParentComp)
        {
            ourMP.add(this, 0);
            ourMP.validate();
            isShown = true;
            repaint();
        }
    }
  }

  /**
   * Gets the top-level container of c.
   * @param c The Component.
   * @return The parent-frame, dialog, or applet, or null.
   */
  public static Container getParentContainer( Component c )
  {
    Component last;
    while (! ((c instanceof Frame) || (c instanceof Applet) || (c instanceof Dialog)))
    {
      last = c;
      c = c.getParent();
      if (c == null)
        throw new IllegalStateException("Assert failed, parent should not be null; last: "
                + last.getClass().getName() + " " + last );
    }
    return (Container) c;
  }

  /**
   * hides the tooltip. (Removes from parent container)
   */
  private void hideTip()
  {
    wantsShown = false;
    removeFromParent();
  }

  /**
   * destroys the tooltip.
   */
  public void destroy()
  {
    hideTip();
    if (parentComp != null)
    {
      parentComp.removeMouseListener(this);
      parentComp.removeMouseMotionListener(this);
      parentComp.removeComponentListener(this);
      parentComp = null;
    }
  }

  /**
   * MouseListener-Methods
   */
  public void mouseClicked( MouseEvent e ) {
    removeFromParent();
  }

  public void mouseExited( MouseEvent e) {
    removeFromParent();
  }

  public void mouseEntered( MouseEvent e)
  {
    if (autoPopup)
    {
      addToParent(e.getX(), e.getY());
    }
  }

  public void mousePressed( MouseEvent e)
  {
    removeFromParent();
  }

  public void mouseReleased( MouseEvent e) {}

  /**
   * MouseMotionListener-Methods
   */

  /**
   * Must keep out of the way of the mouse pointer.
   * On some Win32, flickers if (x,y) of mouse is in our bounding box.
   * showAtMouse is called here to move the box if needed.
   *
   * @see #showAtMouse(int, int)
   */
  public void mouseMoved( MouseEvent e)
  {
    if (! isShown)
      return;

    int x = e.getX();
    int y = e.getY();
    if ( java.lang.Math.abs( x - mousePosAtWinShowX )> closeAfterMoveX ||
     java.lang.Math.abs( y - mousePosAtWinShowY )> closeAfterMoveY)
    {
      removeFromParent();
    } else {
      showAtMouse(x + parentX, y + parentY);
    }
  }

  public void mouseDragged( MouseEvent e) {}

  /**
   * ComponentListener-Methods
   */

  /** when parentComp becomes hidden, hide this tooltip if shown. */
  public void componentHidden(ComponentEvent e)
  {
    hideTip();
  }

  /** stub, required for ComponentListener */
  public void componentMoved(ComponentEvent e) { }

  /** stub, required for ComponentListener */
  public void componentResized(ComponentEvent e) { }

  /**
   * When parentComp becomes un-hidden, flag this tooltip to be shown when mouse moves in.
   * If the mouse was already in the parent's bounding box, tip will not know that
   * until it receives a mouseEntered event.
   */
  public void componentShown(ComponentEvent e)
  {
    wantsShown = true; 
  }

}  /* public class AWTToolTip */

/*
 * $Log$
 * Revision 1.1.1.1  2001/02/07 15:23:49  rtfm
 * initial
 *
 * Revision 1.12  2008/08/26 20:02:00  jm
 * - minor clarify javadocs
 *
 * Revision 1.11  2008/06/22 02:06:00  jm
 * - remove unused method createValuePanel
 * - javadocs
 *
 * Revision 1.10  2008/01/12 17:48:00  jm
 * - removeFromParent don't re-set layout if mainParentLayout is null
 * - addToParent, removeFromParent thread-lock protects mainParentLayout
 * - addToParent do nothing if parent component not visible
 * - destroy removes self as parent listeners
 * - implement ComponentListener
 *
 * Revision 1.9  2008/01/05 10:02:00  jm
 * - Javadoc clarifications; rename hideWindow to hideTip
 *
 * Revision 1.8  2007/12/17 22:14:00  jm
 * - Ensure parent font at paint, not default font
 *
 * Revision 1.7  2007/11/27 20:16:00  jm
 * - Note bottom insets when placing near bottom of window
 *
 * Revision 1.6  2007/11/10 23:05:00  jm
 * - JSettlers package
 * - Canvas, not Window
 * - Simple constructor, simple layout
 * - Find applet or dialog as parent, not just frame
 * - Add to layout at mouseentered/mouseexited, not constructor (add wantsShown, etc)
 * - Override update(Graphics) for less flicker
 * - Add setTip
 *
 * Revision 1.5  2000/01/27 13:35:29  ch
 * Added to methods
 *
 * Revision 1.4  2000/01/20 15:19:40  ch
 * de/ios/framework/gui/ILNField.java added; Bugfix in ExpandTooltip.java
 *
 * Revision 1.3  1999/12/29 10:49:00  mm
 * Bugfixing: berprfung der anzuzeigendenen Werte; anstatt Werte zu Strings zu
 * casten wird jetzt toString() aufgerufen.
 *
 * Revision 1.2  1999/12/27 14:10:01  ch
 * Bugfis in ExpandTooltip.java
 *
 * Revision 1.1  1999/12/27 13:55:11  ch
 * de.ios.framework.gui.ExpandTooltip added
 *
 */

