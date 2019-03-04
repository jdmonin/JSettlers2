/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file copyright (C) 2007-2011,2016-2017,2019 Jeremy D Monin <jeremy@nand.net>
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
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/
package soc.client;

import soc.game.SOCGame;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;


/**
 * This is a component that can display a face.
 * When you click on the face, it changes to another face.
 * Double-click or right-click to bring up the {@link FaceChooserFrame} dialog.
 *<P>
 * There are two modes: Standard (with associated player ID) for use in HandPanel;
 * and Bordered (with associated {@link FaceChooserFrame}) for choosing a new face icon.
 * The two constructors correspond to the two modes.
 *
 * @author Robert S. Thomas
 */
@SuppressWarnings("serial")
/*package*/ class SOCFaceButton extends Canvas
{
    public static final int DEFAULT_FACE = 1;  // Human face # 1 (face1.gif)

    private static final String IMAGEDIR = "/resources/images";

    /**
     * number of /numbered/ face images, /plus 1/ for indexing.
     * If NUM_FACES is 74, there will be no face74.gif (but will be face73.gif).
     */
    public static final int NUM_FACES = 74;

    /**
     * number of robot faces, which are separately numbered.
     * Robot face 0 is just robot.gif, otherwise robot1.gif, robot2.gif, etc.
     * Internally, robot faces are negative faceIds.
     */
    public static final int NUM_ROBOT_FACES = 2;

    /** width,height of button showing only the face icon (standard mode) */
    public static final int FACE_WIDTH_PX = 40;

    /** width of border (per side), used in FACE_WIDTH_BORDERED_PX */
    public static final int FACE_BORDER_WIDTH_PX = 2;

    /** width,height of button with border around the face icon (bordered mode) */
    public static final int FACE_WIDTH_BORDERED_PX = FACE_WIDTH_PX + 2 * FACE_BORDER_WIDTH_PX;

    /** Shared images */
    private static Image[] images;
    private static Image[] robotImages;

    /** For status in drawFace */
    private static MediaTracker tracker;

    /**
     * Human face images are positive numbers, 1-based (range 1 to NUM_FACES).
     * Robot face images are negative, 0-based (range -(NUM_ROBOT_FACES-1) to 0).
     */
    private int currentImageNum = DEFAULT_FACE;
    private int panelx;  // Width
    private int panely;  // Height
    /** player number */
    private int pNumber;
    private SOCGame game;
    private final SOCPlayerInterface pi;  // For callbacks (stack-trace print)

    /** Null unless being used in the face chooser */
    private FaceChooserFrame faceChooser;

    /** Hilight selection border? always false if faceChooser == null. */
    private boolean hilightBorderShown;

    /** Recently shown hilight selection border? (Used in paint method to clear it away) Always false if faceChooser == null. */
    private boolean hilightBorderWasShown;

    /**
     * Color for selection border; ignored if faceChooser == null.
     *
     * @see soc.client.SOCPlayerInterface#makeGhostColor(Color)
     */
    private Color hilightBorderColor;

    /**
     * Context menu for face icon chooser
     */
    private FaceButtonPopupMenu popupMenu;

    /**
     * Tracks last popup-menu time.  Avoids misinterpretation of popup-click with placement-click
     * during initial placement: On Windows, popup-click must be caught in mouseReleased,
     * but mousePressed is called immediately afterwards.
     */
    private long popupMenuSystime;

    /** For popup-menu, length of time after popup to ignore further mouse-clicks.
     *  Avoids Windows accidental left-click by popup-click. (150 ms)
     */
    protected static int POPUP_MENU_IGNORE_MS = 150;

    /**
     * offscreen buffer
     */
    private Image buffer;

    /**
     * size
     */
    protected Dimension ourSize;

    private static synchronized void loadImages(Component c)
    {
        if (images == null)
        {
            tracker = new MediaTracker(c);
            Toolkit tk = c.getToolkit();
            Class<?> clazz = c.getClass();

            images = new Image[NUM_FACES];
            robotImages = new Image[NUM_ROBOT_FACES];

            /**
             * load the images
             */
            robotImages[0] = tk.getImage(clazz.getResource(IMAGEDIR + "/robot.gif"));
            tracker.addImage(robotImages[0], 0);

            for (int i = 1; i < NUM_FACES; i++)
            {
                images[i] = tk.getImage(clazz.getResource(IMAGEDIR + "/face" + i + ".gif"));
                tracker.addImage(images[i], 0);
            }

            for (int i = 1; i < NUM_ROBOT_FACES; i++)
            {
                // Client possibly only has robot.gif.
                // Check getResource vs null, and use MediaTracker;
                // drawFace can check tracker.statusID vs MediaTracker.COMPLETE.
                URL imgSrc = clazz.getResource(IMAGEDIR + "/robot" + i + ".gif");
                if (imgSrc != null)
                {
                    robotImages[i] = tk.getImage(imgSrc);
                    tracker.addImage(robotImages[i], i);
                }
            }

            try
            {
                tracker.waitForID(0);
            }
            catch (InterruptedException e) {}

            if (tracker.isErrorID(0))
            {
                System.out.println("Error loading Face images");
            }
        }
    }

    /**
     * create a new SOCFaceButton, for a player's handpanel (standard mode). Face id DEFAULT_FACE.
     *
     * @param pi  the interface that this button is attached to
     * @param pn  the number of the player that owns this button. Must be in range 0 to ({@link SOCGame#maxPlayers} - 1).
     *
     * @throws IllegalArgumentException if pn is < -1 or >= SOCGame.MAXPLAYERS.
     */
    public SOCFaceButton(SOCPlayerInterface pi, int pn)
        throws IllegalArgumentException
    {
        this (pi, pn, pi.getPlayerColor(pn), FACE_WIDTH_PX * pi.displayScale);
    }

    /**
     * create a new SOCFaceButton, for the FaceChooserFrame (bordered mode)
     *
     * @param pi  Player interface (only for stack-print callback and {@link SOCPlayerInterface#displayScale})
     * @param fcf Face chooser frame for callback
     * @param faceId Face ID to show; same range as {@link #setFace(int)}
     * @since 1.1.00
     */
    public SOCFaceButton(SOCPlayerInterface pi, FaceChooserFrame fcf, int faceId)
    {
        this (pi, -1, fcf.getPlayerColor(), FACE_WIDTH_BORDERED_PX * pi.displayScale);
        setFace(faceId);
        faceChooser = fcf;
    }

    /**
     * implement creation of a new SOCFaceButton (common to both modes)
     *
     * @param pi  the interface that this button is attached to
     * @param pn  the number of the player that owns this button, or -1 if none;
     *          if <tt>pn</tt> >= 0, <tt>pi.getGame()</tt> must not be null.
     * @param bgColor  background color to use
     * @param width width,height in pixels; FACE_WIDTH_PX or FACE_WIDTH_BORDERED_PX
     *
     * @throws IllegalArgumentException if pn is < -1 or >= {@link SOCGame#maxPlayers},
     *           or if <tt>pi.getGame()</tt> is null.
     * @since 1.1.00
     */
    private SOCFaceButton(final SOCPlayerInterface pi, final int pn, final Color bgColor, final int width)
        throws IllegalArgumentException
    {
        super();

        this.pi = pi;
        if (pn == -1)
        {
            game = null;
        } else {
            game = pi.getGame();
            if (game == null)
                throw new IllegalArgumentException("null pi.getGgame");
            if ((pn < 0) && (pn <= game.maxPlayers))
                throw new IllegalArgumentException("Player number out of range: " + pn);
        }
        pNumber = pn;
        faceChooser = null;
        hilightBorderShown = false;
        hilightBorderColor = null;

        setBackground(bgColor);

        panelx = width;
        panely = width;
        ourSize = new Dimension(panelx, panely);

        // load the static images
        loadImages(this);

        this.addMouseListener(new MyMouseAdapter());

        // set initial size to help when in a JPanel
        setSize(ourSize);
        setMinimumSize(ourSize);
        setPreferredSize(ourSize);
    }

    /**
     * @return  which image id is shown
     * @since 1.1.00
     */
    public int getFace()
    {
        return currentImageNum;
    }

    /**
     * set which image is shown
     *
     * @param id  the id for the image. Range is within
     *    -NUM_ROBOT_FACES to +NUM_FACES.
     *    Human id's out of range (>= NUM_FACES) get id DEFAULT_FACE.
     *    Robot id's out of range (<= -NUM_ROBOT_FACES) get id 0.
     */
    public void setFace(int id)
    {
        if (id >= NUM_FACES)
            id = DEFAULT_FACE;
        else if (id <= (-NUM_ROBOT_FACES))
            id = 0;
        currentImageNum = id;
        repaint();
    }

    /**
     * Reset to the default face.
     */
    public void setDefaultFace()
    {
        setFace(DEFAULT_FACE);
    }

    /**
     * Designate player as client (can click and right-click to choose face icon).
     * If we don't have a popup menu, and player is client, add it.
     * If we already have one, nothing happens.
     *
     * @throws IllegalStateException if player isn't client (checks getName vs client.getNickname)
     * @since 1.1.00
     */
    public void addFacePopupMenu()
        throws IllegalStateException
    {
        if (popupMenu == null)
        {
            if ((game == null) || ! game.getPlayer(pNumber).getName().equals(pi.getClient().getNickname()))
                throw new IllegalStateException("Player must be client");

            popupMenu = new FaceButtonPopupMenu(this);
            add(popupMenu);
        }
    }

    /**
     * If we have a popup menu, remove it.
     * All clicks will be ignored, and won't change the face id shown.
     * @since 1.1.00
     */
    public void removeFacePopupMenu()
    {
        if (popupMenu != null)
        {
            remove(popupMenu);
            popupMenu = null;
        }
    }

    /**
     * The previous face-chooser window (from the face-popup menu) has been disposed.
     * If menu item is chosen again, don't show the previous one, create a new face-chooser window.
     *
     * @see #addFacePopupMenu()
     * @since 1.1.00
     */
    public void clearFacePopupPreviousChooser()
    {
        if (popupMenu != null)
            popupMenu.clearPreviousChooser();
    }

    /**
     * Set or clear the hilight border flag. Repaints if needed.
     *
     * @param wantBorder Show the hilight border?
     *
     * @throws IllegalStateException If this FaceButton wasn't created for a face chooser.
     * @since 1.1.00
     */
    public void setHilightBorder(boolean wantBorder)
        throws IllegalStateException
    {
        if (faceChooser == null)
            throw new IllegalStateException("Border only usable in FaceChooser bordered mode");
        if (hilightBorderShown == wantBorder)
            return;
        hilightBorderWasShown = hilightBorderShown;
        hilightBorderShown = wantBorder;
        repaint();
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    @Override
    public Dimension getPreferredSize()
    {
        return ourSize;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    @Override
    public Dimension getMinimumSize()
    {
        return ourSize;
    }

    /**
     * @return Size of this square facebutton
     * @since 1.1.00
     */
    @Override
    public Dimension getSize()
    {
        return ourSize;
    }

    /**
     * Redraw the facebutton using double buffering. Don't call this directly, use
     * {@link Component#repaint()} instead.
     */
    @Override
    public void paint(Graphics g)
    {
        if (buffer == null)
        {
            buffer = this.createImage(panelx, panely);
        }
        drawFace(buffer.getGraphics());
        buffer.flush();
        g.drawImage(buffer, 0, 0, this);
        if (hilightBorderShown)
        {
            paintBorder(g, true);
        }
        else if (hilightBorderWasShown)
        {
            paintBorder(g, false);
            hilightBorderWasShown = false;
        }
    }

    /**
     * Overriden so the peer isn't painted, which clears background. Don't call
     * this directly, use {@link Component#repaint()} instead.
     */
    @Override
    public void update(Graphics g)
    {
        paint(g);
    }

    /**
     * Draw the face. Will scale up if {@link SOCPlayerInterface#displayScale} > 1.
     */
    private void drawFace(Graphics g)
    {
        final int displayScale = pi.displayScale;
        Image fimage;

        /**
         * before drawing, ensure this face number is loaded
         */
        int findex;
        if (currentImageNum > 0)
        {
            findex = currentImageNum;
            if ((findex >= NUM_FACES) || (null == images[findex]))
            {
                findex = DEFAULT_FACE;
                currentImageNum = findex;
            }
            fimage = images[findex];
        }
        else
        {
            findex = -currentImageNum;
            if ((findex >= NUM_ROBOT_FACES) || (null == robotImages[findex])
                || (0 != (tracker.statusID(findex, false) & (MediaTracker.ABORTED | MediaTracker.ERRORED))))
            {
                findex = 0;
                currentImageNum = -findex;
            }
            fimage = robotImages[findex];
        }

        final int offs;  // optional offset for border
        if (panelx == FACE_WIDTH_BORDERED_PX * displayScale)
            offs = FACE_BORDER_WIDTH_PX * displayScale;
        else
            offs = 0;

        if (displayScale == 1)
        {
            g.drawImage(fimage, offs, offs, getBackground(), this);
        } else {
            if (g instanceof Graphics2D)
            {
                ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }
            int w = panelx, h = panely;
            if (offs != 0)
            {
                w -= (2 * offs);
                h -= (2 * offs);
            }

            g.drawImage(fimage, offs, offs, w, h, getBackground(), this);
        }
    }

    /**
     * Paint or clear the highlight border
     * @since 1.1.00
     */
    private void paintBorder(Graphics g, boolean showNotClear)
    {
        Color drawColor;

        if (showNotClear)
        {
            if (hilightBorderColor == null)
                hilightBorderColor = SOCPlayerInterface.makeGhostColor(getBackground());
            drawColor = hilightBorderColor;
        }
        else
        {
            drawColor = getBackground();
        }

        final int pix1 = pi.displayScale, pix3 = 3 * pix1;

        g.setColor(drawColor);
        g.drawRect(0, pix1, panelx - pix1, panely - pix3);
        g.drawRect(pix1, 0, panelx - pix3, panely - pix1);
    }

    /*********************************
     * Handle Events
     *********************************/
    private class MyMouseAdapter extends MouseAdapter
    {
        /**
         * Handle popup-click.
         * mousePressed has xwindows/OS-X popup trigger.
         */
        @Override
        public void mousePressed(MouseEvent evt)
        {
            mouseReleased(evt);  // same desired code: react to isPopupTrigger
        }

        /**
         * Handle click to change face.
         */
        @Override
        public void mouseClicked(MouseEvent evt)
        {
            try {
            int x = evt.getX();

            if (evt.isPopupTrigger())
            {
                popupMenuSystime = evt.getWhen();
                evt.consume();
                if (popupMenu != null)
                    popupMenu.show(x, evt.getY());
                return;  // <--- Pop up menu, nothing else to do ---
            }

            if (evt.getWhen() < (popupMenuSystime + POPUP_MENU_IGNORE_MS))
            {
                return;  // <--- Ignore click: too soon after popup click ---
            }

            /**
             * either faceChooser or game will be non-null.
             * faceChooser: Bordered mode (within FaceChooserFrame)
             * game: Standard mode (within SOCHandPanel)
             */
            if (faceChooser != null)
            {
                faceChooser.selectFace(currentImageNum, hilightBorderShown);
                evt.consume();
                return;  // <--- Notify, nothing else to do ---
            }

            if (game == null)
            {
                return;  // <--- Early return: further checks need game ---
            }

            if (game.isDebugFreePlacement())
            {
                pi.setDebugFreePlacementPlayer(pNumber);
                evt.consume();
                return;  // <--- Early return ---
            }

            /**
             * only change the face if it's the client player's button
             */
            if (popupMenu != null)
            {
                if (evt.getClickCount() >= 2)  // Show FCF on double-click. added in 1.1.09
                {
                    evt.consume();
                    popupMenu.showFaceChooserFrame();
                    return;  // <--- Notify, nothing else to do ---
                }

                if (x < (getWidth() / 2))
                {
                    // if the click is on the left side, decrease the number
                    currentImageNum--;

                    if (currentImageNum <= 0)
                    {
                        currentImageNum = NUM_FACES - 1;
                    }
                }
                else
                {
                    // if the click is on the right side, increase the number
                    currentImageNum++;

                    if (currentImageNum == NUM_FACES)
                    {
                        currentImageNum = 1;
                    }
                }

                evt.consume();
                pi.getClient().getGameMessageMaker().changeFace(game, currentImageNum);
                repaint();
            }
            } catch (Throwable th) {
                pi.chatPrintStackTrace(th);
            }
        }

        /**
         * Handle popup-click.
         * mouseReleased has win32 popup trigger.
         */
        @Override
        public void mouseReleased(MouseEvent evt)
        {
            try {
            // Needed in Windows for popup-menu handling
            if (evt.isPopupTrigger())
            {
                popupMenuSystime = evt.getWhen();
                evt.consume();
                if (popupMenu != null)
                    popupMenu.show(evt.getX(), evt.getY());
                return;
            }
            } catch (Throwable th) {
                pi.chatPrintStackTrace(th);
            }
        }

    }  /* inner class MyMouseAdapter */

    /**
     * Menu for right-click on face icon to choose a new face (Player's hand only).
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.1.00
     */
    protected static class FaceButtonPopupMenu extends PopupMenu
        implements java.awt.event.ActionListener
    {
        SOCFaceButton fb;
        MenuItem changeFaceItem;
        FaceChooserFrame fsf;

        public FaceButtonPopupMenu(SOCFaceButton fbutton)
        {
          super ("JSettlers");
          fb = fbutton;

          changeFaceItem = new MenuItem(/*I*/"Change face..."/*18N*/);
          add(changeFaceItem);
          changeFaceItem.addActionListener(this);
        }

        /** Show menu at this position.
         *
         * @param x   Mouse x-position relative to facebutton
         * @param y   Mouse y-position relative to facebutton
         */
        public void show(int x, int y)
        {
            super.show(fb, x, y);
        }

        /** Handling the menu item **/
        public void actionPerformed(ActionEvent e)
        {
            try {
                if (e.getSource() != changeFaceItem)
                    return;
                showFaceChooserFrame();
            } catch (Throwable th) {
                fb.pi.chatPrintStackTrace(th);
            }
        }

        /**
         * Create or show a face-chooser frame, from handpanel right-click or triple-click.
         * @since 1.1.09
         */
        private void showFaceChooserFrame()
        {
            if ((fsf == null) || ! fsf.isStillAvailable())
            {
                fsf = new FaceChooserFrame
                    (fb, fb.pi.getClient(), fb.pi, fb.pNumber, fb.getFace(), fb.getSize().width);
                fsf.pack();
            }
            fsf.setVisible(true);
        }

        /**
         * The previous face-chooser window has been disposed.
         * If menu item is chosen, don't show it, create a new one.
         */
        public void clearPreviousChooser()
        {
            if (fsf != null)
                fsf = null;
        }

    }  /* static nested class FaceButtonPopupMenu */

}
