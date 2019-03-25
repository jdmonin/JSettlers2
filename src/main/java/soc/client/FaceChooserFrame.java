/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file copyright (C) 2007,2013,2016-2017,2019 Jeremy D Monin <jeremy@nand.net>
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import soc.game.SOCGame;


/**
 * A popup dialog with all available {@link SOCFaceButton} icons
 * for the user to browse and change to.
 *<P>
 * To adjust the grid's size in rows and columns, set {@link FaceChooserList#rowFacesWidth}
 * and {@link FaceChooserList#faceRowsHeight}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
@SuppressWarnings("serial")
/*package*/ class FaceChooserFrame
    extends JFrame implements ActionListener, WindowListener, KeyListener
{
    /** Face button that launched us. Passed to constructor, not null. */
    protected final SOCFaceButton fb;

    /** Player client. Passed to constructor, not null */
    protected final SOCPlayerClient pcli;

    /** Player interface. Passed to constructor, not null */
    protected final SOCPlayerInterface pi;

    /** Player number. Needed for bg color. */
    protected int pNumber;

    /** Width,height of one face, in pixels. Assumes icon is square. */
    protected int faceWidthPx;

    /** Scrolling choice of faces; takes up most of the Frame */
    protected FaceChooserList fcl;

    /** Button for confirm change */
    protected JButton changeFaceBut;

    /** Button for cancel */
    protected JButton cancelBut;

    /** Label to prompt to choose a new face */
    protected JLabel promptLbl;

    /**
     * Is this still visible and interactive? (vs already dismissed)
     * @see #isStillAvailable()
     */
    private boolean stillAvailable;

    /** i18n text strings */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /**
     * Creates a new FaceChooserFrame.
     *
     * @param fbutton  Face button in player's handpanel
     * @param cli      Player client interface
     * @param gamePI   Current game's player interface
     * @param pnum     Player number in game
     * @param faceWidth Width and height of one face button, in pixels. Assumes icon is square.
     *
     * @throws IllegalArgumentException If fbutton is null, or faceWidth is 0 or negative,
     *    or pnum is negative or more than SOCGame.MAXPLAYERS.
     * @throws NullPointerException if cli or gamePI is null
     */
    public FaceChooserFrame(SOCFaceButton fbutton, SOCPlayerClient cli,
            SOCPlayerInterface gamePI, int pnum, int faceID, int faceWidth)
        throws IllegalArgumentException
    {
        super(strings.get("facechooser.title", gamePI.getGame().getName(), cli.getNickname()));
            // "Choose Face Icon: {0} [{1}]"

        if (fbutton == null)
            throw new IllegalArgumentException("fbutton cannot be null");
        if ((pnum < 0) || (pnum >= SOCGame.MAXPLAYERS))
            throw new IllegalArgumentException("pnum out of range: " + pnum);
        if (faceWidth <= 0)
            throw new IllegalArgumentException("faceWidth must be positive, not " + faceWidth);

        fb = fbutton;
        pcli = cli;
        pi = gamePI;
        pNumber = pnum;
        faceWidthPx = faceWidth;
        stillAvailable = true;
        final int displayScale = pi.displayScale;

        final boolean isOSHighContrast = SwingMainDisplay.isOSColorHighContrast();
        if (! isOSHighContrast)
        {
            final Color[] colors = SwingMainDisplay.getForegroundBackgroundColors(true, false);

            setBackground(colors[2]);  // SwingMainDisplay.DIALOG_BG_GOLDENROD; face-icon backgrounds will match player
            setForeground(colors[0]);  // Color.BLACK

            getRootPane().setBackground(null);  // inherit
            getContentPane().setBackground(null);
        }
        setFont(new Font("Dialog", Font.PLAIN, 12 * displayScale));

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        changeFaceBut = new JButton(strings.get("base.change"));
        cancelBut = new JButton(strings.get("base.cancel"));
        if (SOCPlayerClient.IS_PLATFORM_WINDOWS && ! isOSHighContrast)
        {
            // avoid gray corners on win32 JButtons
            changeFaceBut.setBackground(null);
            cancelBut.setBackground(null);
        }
        setLayout (new BorderLayout());

        final int bsize = 4 * displayScale;
        promptLbl = new JLabel(strings.get("facechooser.prompt"), SwingConstants.LEFT);  // "Choose your face icon."
        promptLbl.setBorder(new EmptyBorder(bsize, bsize, bsize, bsize));
        add(promptLbl, BorderLayout.NORTH);

        fcl = new FaceChooserList(this, faceID);
        add(fcl, BorderLayout.CENTER);

        try
        {
            Point mloc = MouseInfo.getPointerInfo().getLocation();
            setLocation(mloc.x + 20 * displayScale, mloc.y + 10 * displayScale);
        } catch (RuntimeException e) {
            // in case of SecurityException, etc
            setLocationRelativeTo(gamePI);
        }

        JPanel pBtns = new JPanel();
        pBtns.setLayout(new FlowLayout(FlowLayout.CENTER));
        pBtns.setBackground(null);

        pBtns.add(changeFaceBut);
        changeFaceBut.addActionListener(this);

        pBtns.add(cancelBut);
        cancelBut.addActionListener(this);

        add(pBtns, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(changeFaceBut);

        addWindowListener(this);  // To handle close-button
        addKeyListener(this);     // To handle Enter, Esc keys.
        changeFaceBut.addKeyListener(this);
        cancelBut.addKeyListener(this);
    }

    /**
     * Show or hide this Frame.
     * If {@code vis} is true, requests keyboard focus on {@link FaceChooserList}
     * for arrow key controls / Enter / Escape; this is especially helpful on OSX.
     * @param vis True to make visible, false to hide
     * @since 1.2.00
     */
    @Override
    public void setVisible(boolean vis)
    {
        super.setVisible(vis);
        if (vis)
            fcl.requestFocusInWindow();
    }

    /**
     * Face selected (clicked) by user.  If already-selected, and player has chosen
     * a new face in this window, treat as double-click: change face and close window.
     *
     * @param id  face ID
     * @param alreadySelected  Was the face currently selected, when clicked?
     */
    public void selectFace(int id, boolean alreadySelected)
    {
        if (! alreadySelected)
            fcl.selectFace(id);
        else if (id != fcl.initialFaceId)
        {
            dispose();
            changeButtonChosen();
        }
    }

    /**
     * @return Player color in game (background color for face icons)
     */
    public Color getPlayerColor()
    {
        return pi.getPlayerColor(pNumber);
    }

    /**
     * Is this chooser still visible and interactive?
     *
     * @return True if still interactive (vs already dismissed).
     * @see #isVisible()
     */
    public boolean isStillAvailable()
    {
        return stillAvailable;
    }

    /**
     * Dispose of this window. Overrides to clear stillAvailable flag
     * and call faceButton.clearFacePopupPreviousChooser.
     */
    public void dispose()
    {
        stillAvailable = false;
        fb.clearFacePopupPreviousChooser();
        super.dispose();
    }

    /**
     * check size and set focus to the default button (if any).
     *
     * @param listSizeKnown if true, the list knows what size it wants to be;
     *    adjust our size if needed.
     */
    protected void checkSizeAndFocus(boolean listSizeKnown)
    {
        if (listSizeKnown)
            doLayout();
        changeFaceBut.requestFocus();
    }

    /**
     * Change or Cancel button has been chosen by the user.
     * Call changeButtonChosen or cancelButtonChosen, and dispose of this dialog.
     */
    public void actionPerformed(ActionEvent e)
    {
        try
        {
            Object target = e.getSource();

            if (target == changeFaceBut)
            {
                dispose();
                changeButtonChosen();  // <--- Callback for button 1 ---
            }
            else if (target == cancelBut)
            {
                dispose();
                cancelButtonChosen();  // <--- Callback for button 2 ---
            }
        } catch (Throwable th) {
            pi.chatPrintStackTrace(th);
        }
    }

    /**
     * Change-face button has been chosen by the user. React accordingly.
     * actionPerformed has already called dialog.dispose().
     */
    public void changeButtonChosen()
    {
        pcli.getGameMessageMaker().changeFace(pi.getGame(), fcl.currentFaceId);
    }

    /**
     * Cancel button has been chosen by the user. React accordingly.
     * Also called if user closes window or hits Escape key.
     * actionPerformed has already called dialog.dispose().
     */
    public void cancelButtonChosen() { }

    /**
     * Move the cursor choosing the current face.
     * For behavioral details, see the inner class method description.
     *
     * @see soc.client.FaceChooserFrame.FaceChooserList#moveCursor(int, int, KeyEvent)
     *
     * @param dr Delta row: -3 jumps to very top; -2 is PageUp; -1 is one row; same for +.
     * @param dc Delta column: -2 jumps to far-left, -1 is one to left, +1 is one to right, +2 jumps to far-right.
     * @param e  KeyEvent to be consumed, or null.
     */
    public void moveCursor(int dr, int dc, KeyEvent e)
    {
        fcl.moveCursor(dr, dc, e);
    }

    /**
     * Dialog close requested by user. Dispose and call windowCloseChosen.
     */
    public void windowClosing(WindowEvent e)
    {
        dispose();
        cancelButtonChosen();  // <--- Callback for close/ESC ---
    }

    /** Window is appearing - check the size and the default button keyboard focus */
    public void windowOpened(WindowEvent e)
    {
        checkSizeAndFocus(false);
    }

    /** Stub required by WindowListener */
    public void windowActivated(WindowEvent e) { }

    /** Stub required by WindowListener */
    public void windowClosed(WindowEvent e) { }

    /** Stub required by WindowListener */
    public void windowDeactivated(WindowEvent e) { }

    /** Stub required by WindowListener */
    public void windowDeiconified(WindowEvent e) { }

    /** Stub required by WindowListener */
    public void windowIconified(WindowEvent e) { }

    /** Handle Enter or Esc key, arrow keys, home/end, ctrl-home/ctrl-end */
    public void keyPressed(KeyEvent e)
    {
        if (e.isConsumed())
            return;

        switch (e.getKeyCode())
        {
        case KeyEvent.VK_ENTER:
            dispose();
            e.consume();
            changeButtonChosen();
            break;

        case KeyEvent.VK_CANCEL:
        case KeyEvent.VK_ESCAPE:
            dispose();
            e.consume();
            cancelButtonChosen();
            break;

        case KeyEvent.VK_UP:
            fcl.moveCursor (-1, 0, e);
            break;

        case KeyEvent.VK_DOWN:
            fcl.moveCursor (+1, 0, e);
            break;

        case KeyEvent.VK_LEFT:
            fcl.moveCursor (0, -1, e);
            break;

        case KeyEvent.VK_RIGHT:
            fcl.moveCursor (0, +1, e);
            break;

        case KeyEvent.VK_PAGE_UP:
            fcl.moveCursor (-2, 0, e);
            break;

        case KeyEvent.VK_PAGE_DOWN:
            fcl.moveCursor (+2, 0, e);
            break;

        case KeyEvent.VK_HOME:
            if (0 != (e.getModifiers() & KeyEvent.CTRL_MASK))
                fcl.moveCursor (-3, -2, e);
            else
                fcl.moveCursor (0, -2, e);
            break;

        case KeyEvent.VK_END:
            if (0 != (e.getModifiers() & KeyEvent.CTRL_MASK))
                fcl.moveCursor (+3, +2, e);
            else
                fcl.moveCursor (0, +2, e);
            break;
        }
    }

    /** Stub required by KeyListener */
    public void keyReleased(KeyEvent arg0) { }

    /** Stub required by KeyListener */
    public void keyTyped(KeyEvent arg0) { }

    /**
     * FaceChooserList holds face icons (in rows and columns) and an optional scrollbar.
     * Custom layout.
     */
    protected static class FaceChooserList extends Container
        implements AdjustmentListener
    {
        /**
         *  How many faces per row?  Default 7.
         *  Do not change after creating an instance.
         */
        protected static int rowFacesWidth = 7;

        /**
         *  How many rows to show?  Default 6.
         *  Do not change after creating an instance.
         *  If all faces (SOCFaceButton.NUM_FACES) fit in fewer than
         *  faceRowsHeight rows, the first instance's constructor will
         *  reduce faceRowsHeight to the proper value.
         */
        protected static int faceRowsHeight = 6;

        protected final FaceChooserFrame fcf;
        private int currentRow;     // upper-left row #, first row is 0
        private int currentOffset;  // upper-left, from faceid==0
        private int rowCount;       // how many rows total
        private int currentFaceId;  // currently selected faceId in this window
        private int initialFaceId;  // faceId of player in game

        /**
         * Will contain all faces.
         * Length is rowCount.  Each row contains rowFacesWidth faces.
         * Some elements initially null, if the scrollbar is needed
         * (if rowCount > faceRowsHeight).
         * Contents are references to same objects as in visibleFaceGrid.
         */
        private FaceChooserRow[] faceGrid;

        /**
         * Contains only visible faces (based on scrollbar).
         * Length is faceRowsHeight.  Each row contains rowFacesWidth faces.
         * Contents are references to same objects as in faceGrid.
         */
        private FaceChooserRow[] visibleFaceGrid;

        private boolean needsScroll;  // Scrollbar required?
        private JScrollBar faceSB;

        /** Desired size (visible size inside of insets; not incl scrollW) **/
        protected int wantW, wantH;

        /** Desired size */
        protected Dimension wantSize;

        /** Padding beyond desired size; not known until doLayout() **/
        protected int padW, padH;

        /** faceSB pixel width; not known until doLayout */
        protected int scrollW;

        /**
         * Create facechooserlist for this frame, with selected face ID.
         */
        protected FaceChooserList(FaceChooserFrame fcf, int selectedFaceId)
        {
            this.fcf = fcf;
            initialFaceId = selectedFaceId;
            currentFaceId = selectedFaceId;

            // Padding between faces is done by SOCFaceButton.FACE_WIDTH_BORDERED_PX:
            // the padding is internal to SOCFaceButton's component size.

            rowCount = (int) Math.ceil
                ((SOCFaceButton.NUM_FACES - 1) / (float) rowFacesWidth);
            if (rowCount < faceRowsHeight)
                faceRowsHeight = rowCount;  // Reduce if number of "visible rows" would be too many.

            // If possible, place the selectedFaceId in the
            // middle row of the frame.
            currentRow = ((selectedFaceId - 1) / rowFacesWidth) - (faceRowsHeight / 2);
            if (currentRow < 0)
            {
                // Near the top
                currentRow = 0;
            }
            else if (currentRow + faceRowsHeight >= rowCount)
            {
                // Near the end
                currentRow = rowCount - faceRowsHeight;
            }
            currentOffset = 1 + (currentRow * rowFacesWidth);  // id's 0 and below are for robot

            needsScroll = (rowCount > faceRowsHeight);

            faceGrid = new FaceChooserRow[rowCount];
            visibleFaceGrid = new FaceChooserRow[faceRowsHeight];

            setLayout(null);  // Custom due to visibleFaceGrid; see doLayout()

            int nextId = currentOffset;
            for (int r = currentRow, visR = 0; visR < faceRowsHeight; ++r, ++visR)
            {
                FaceChooserRow fcr = new FaceChooserRow(nextId);
                    // FaceChooserRow constructor will also set the
                    // hilight if current face is within its row.
                faceGrid[r] = fcr;
                visibleFaceGrid[visR] = fcr;
                add(fcr);
                nextId += rowFacesWidth;
            }
            if (needsScroll)
            {
                faceSB = new JScrollBar
                       (JScrollBar.VERTICAL, currentRow,
                        /* number-rows-visible */ faceRowsHeight,
                        0, rowCount );
                    // Range 0 to rowCount per API note: "actual maximum value is
                    // max minus visible".  Scrollbar value is thus row number for
                    // top of visible window.
                add(faceSB);
                faceSB.addAdjustmentListener(this);
                faceSB.addKeyListener(fcf);  // Handle Enter, Esc keys on window's behalf
            }

            final int displayScale = fcf.pi.displayScale;
            wantW = rowFacesWidth * SOCFaceButton.FACE_WIDTH_BORDERED_PX * displayScale;
            wantH = faceRowsHeight * SOCFaceButton.FACE_WIDTH_BORDERED_PX * displayScale;
            scrollW = 0;  // unknown before is visible
            padW = 10 * displayScale;  padH = 30 * displayScale;  // assumed; will get actual at doLayout.
            wantSize = new Dimension (wantW + padW, wantH + padH);
        }

        /**
         * Face chosen (clicked), by user or otherwise.
         * Select it and show the hilight border.
         * If the new face isn't currently visible, scroll to show it.
         *
         * @param id  Face ID to select
         *
         * @throws IllegalArgumentException if id <= 0 or id >= SOCFaceButton.NUM_FACES
         *
         * @see #moveCursor(int, int, KeyEvent)
         */
        public void selectFace(int id)
        {
            if ((id <= 0) || (id >= SOCFaceButton.NUM_FACES))
                throw new IllegalArgumentException("id not within range: " + id);

            int prevFaceId = currentFaceId;
            int r;

            // Clear hilight of prev face-id
            r = (prevFaceId - 1) / rowFacesWidth;
            faceGrid[r].setFaceHilightBorder(prevFaceId, false);

            // Set hilight of new face-id
            r = (id - 1) / rowFacesWidth;
            scrollToRow(r);
            faceGrid[r].setFaceHilightBorder(id, true);

            currentFaceId = id;
        }

        /**
         * Ensure this row of faces is visible.  Calls repaint if needed.
         * Number of rows visible at a time is faceRowsHeight.
         *
         * @param newRow  Row number, counting from 0.
         *   The row number can be determined from the faceID
         *   by newRow = (faceId - 1) / rowFacesWidth.
         *
         * @throws IllegalArgumentException if newRow < 0 or newRow >= rowCount
         */
        public void scrollToRow(int newRow)
        {
            if ((newRow < 0) || (newRow >= rowCount))
                throw new IllegalArgumentException
                ("newRow not in range (0 to " + (rowCount-1) + "): " + newRow);
            if ((newRow >= currentRow) && (newRow < (currentRow + faceRowsHeight)))
            {
                return;  // <--- Early return: Already showing ---
            }

            boolean createdRows = false;  // Any objects instantiated? (Need to pack their layout)
            int numNewRows;    // How many not currently showing?
            int newCurRow;     // New first-showing row number
            int newCurOffset;  // new upper-left corner face ID

            if (newRow < currentRow)
            {
                numNewRows = currentRow - newRow;
                newCurRow = newRow;
            }
            else
            {
                numNewRows = 1 + (newRow - (currentRow + faceRowsHeight));
                newCurRow = newRow - faceRowsHeight + 1;
            }
            newCurOffset = newCurRow * rowFacesWidth + 1;
            if (numNewRows > faceRowsHeight)
                numNewRows = faceRowsHeight;

            int r;
            if ((numNewRows == faceRowsHeight) || (newRow < currentRow))
            {
                // Scroll up, or completely replace visible.
                if (numNewRows == faceRowsHeight)
                {
                    // Completely replace current visible face grid.
                    for (r = faceRowsHeight - 1; r >= 0; --r)
                    {
                        visibleFaceGrid[r].setVisible(false);
                        visibleFaceGrid[r] = null;
                    }
                } else {
                    // newRow < currentRow:
                    // Remove current bottom, scroll up
                    for (r = faceRowsHeight - numNewRows; r < faceRowsHeight; ++r)
                    {
                        visibleFaceGrid[r].setVisible(false);
                        visibleFaceGrid[r] = null;
                    }
                    for (r = faceRowsHeight - numNewRows - 1; r >= 0; --r)
                        visibleFaceGrid[r + numNewRows] = visibleFaceGrid[r];
                }

                // Add newly-visible
                int nextId = newCurOffset;  // face ID to add
                int visR = 0;  // Visual row number to add
                // in this loop, r = row number in faceGrid to add
                for (r = newRow; r < (newRow + numNewRows); ++r, ++visR)
                {
                    if (faceGrid[r] == null)
                    {
                        faceGrid[r] = new FaceChooserRow(nextId);
                        add(faceGrid[r]);
                        createdRows = true;
                    }
                    visibleFaceGrid[visR] = faceGrid[r];
                    visibleFaceGrid[visR].setVisible(true);
                    nextId += rowFacesWidth;
                }
            }
            else  // (newRow >= currentRow + faceRowsHeight)
            {
                // Remove current top, scroll down
                for (r = 0; r < numNewRows; ++r)
                {
                    visibleFaceGrid[r].setVisible(false);
                    visibleFaceGrid[r] = null;
                }
                for (r = 0; r < faceRowsHeight - numNewRows; ++r)
                    visibleFaceGrid[r] = visibleFaceGrid[r + numNewRows];

                // Add newly-visible
                int visR = faceRowsHeight - numNewRows;   // Visual row number to add
                int nextId = newCurOffset + (visR * rowFacesWidth);  // face ID to add
                r = newCurRow + visR;   // Row number in faceGrid to add
                for ( ; visR < faceRowsHeight ; ++r, ++visR )
                {
                    if (faceGrid[r] == null)
                    {
                        faceGrid[r] = new FaceChooserRow(nextId);
                        add(faceGrid[r]);
                        createdRows = true;
                    }
                    visibleFaceGrid[visR] = faceGrid[r];
                    visibleFaceGrid[visR].setVisible(true);
                    nextId += rowFacesWidth;
                }
            }
            currentRow = newCurRow;
            currentOffset = newCurOffset;
            if (createdRows)
                fcf.pack();  // needed for proper repaint due to visibleFaceGrid scheme
            doLayout();  // for setLocation, setSize of visibleFaceGrid members
            repaint();
            if (faceSB != null)
                faceSB.setValue(newCurRow);  // Update scrollbar if needed (we don't know our caller)
        }

        /**
         * Move the cursor choosing the current face, and change selected face in this browser.
         * If needed, calls scrollToRow to make the selected face visible.
         * Both dr and dc can be nonzero in the same call.
         *
         * @param dr Delta row: -3 jumps to very top; -2 is PageUp; -1 is one row; same for +.
         * @param dc Delta column: -2 jumps to far-left, -1 is one to left, +1 is one to right, +2 jumps to far-right.
         *           If already at far-left or far-right, -1/+1 move into the previous/next row.
         * @param e  KeyEvent to be consumed, or null. The event contents are ignored, only dr and dc
         *           are used to choose the direction of movement.
         *           If dr==0 and dc==0, e is still consumed.
         *
         * @throws IllegalArgumentException If dr or dc is out of the range described here
         *
         * @see #selectFace(int)
         * @see #scrollToRow(int)
         */
        public void moveCursor(int dr, int dc, KeyEvent e) throws IllegalArgumentException
        {
            if ((dr < -3) || (dr > +3))
                throw new IllegalArgumentException("dr outside range +-3: " + dr);
            if ((dc < -2) || (dc > +2))
                throw new IllegalArgumentException("dc outside range +-2: " + dc);
            if (e != null)
                e.consume();
            if ((dr == 0) && (dc == 0))
                return;  // <--- Early return: both zero ---

            // currentRow is top of window
            // currentOffset gives upper-left face ID
            // currentFaceId gives absolute "cursor position"
            int abs_r = (currentFaceId - 1) / rowFacesWidth;
            int abs_c = (currentFaceId - 1) % rowFacesWidth;

            // if moved is true, recalc currentFaceId from abs_r, abs_c.
            // (selectFace is called; if needed, selectFace calls scrollToRow.)
            boolean moved = false;

            // Check dc first, it's easier than dr
            switch (dc)
            {
            case -2:   // Home
                if (abs_c > 0)
                {
                    moved = true;
                    abs_c = 0;
                }
                break;

            case -1:   // Left
                if (currentFaceId > 0)
                {
                    moved = true;
                    if (abs_c > 0)
                        --abs_c;
                    else
                    {
                        --abs_r;
                        abs_c = rowFacesWidth - 1;
                    }
                }
                break;

            case +1:   // Right
                if (currentFaceId < (SOCFaceButton.NUM_FACES - 1))
                {
                    moved = true;
                    if (abs_c < (rowFacesWidth - 1))
                        ++abs_c;
                    else
                    {
                        ++abs_r;
                        abs_c = 0;
                    }
                }
                break;

            case +2:   // End
                if (abs_c < (rowFacesWidth - 1))
                {
                    moved = true;
                    abs_c = rowFacesWidth - 1;
                }
                break;

            }  // switch (dc)

            // Time for dr checks
            if ((dr < 0) && (abs_r > 0))
            {
                moved = true;
                if (dr == -1)
                    --abs_r;    // Up
                else if (dr == -3)
                    abs_r = 0;  // Ctrl-Home
                else
                {
                    abs_r -= faceRowsHeight;  // PageUp
                    if (abs_r < 0)
                        abs_r = 0;  // PgUp while at top of scroll range
                }
            }
            else if ((dr > 0) && (abs_r < (rowCount - 1)))
            {
                moved = true;
                if (dr == +1)
                    ++abs_r;    // Down
                else if (dr == +3)
                    abs_r = rowCount - 1;  // Ctrl-End
                else
                {
                    abs_r += faceRowsHeight;  // PageDown
                    if (abs_r >= rowCount)
                        abs_r = rowCount - 1;  // PgDn while at bottom of scroll range
                }
            }

            // Now, adjust vars if needed:
            if (moved)
            {
                // re-calc currentFaceId, select it, and ensure visible.
                int newId = abs_r * rowFacesWidth + abs_c + 1;
                if (newId >= SOCFaceButton.NUM_FACES)
                    newId = SOCFaceButton.NUM_FACES - 1;
                else if (newId < 1)
                    newId = 1;
                selectFace(newId);
            }
        }

        /**
         * Update displayed rows when scrollbar changes
         */
        public void adjustmentValueChanged(AdjustmentEvent e)
        {
            if (e.getSource() != faceSB)
                return;
            int r = e.getValue();
            scrollToRow(r);  // Top of window: make visible
            scrollToRow(r + faceRowsHeight - 1);  // Bottom of window visible
        }

        /**
         * Now that insets and scrollbar size are known, check our size and padding.
         * If too small, resize the frame.
         *
         * @param  i  Insets
         * @return True if dimensions were updated and setSize was called.
         */
        protected boolean checkInsetsPadding(Insets i)
        {
            int iw = (i.left + i.right);
            int ih = (i.top + i.bottom);
            int sw;
            if (needsScroll)
            {
                sw = faceSB.getWidth();
                if (sw == 0)
                {
                    sw = faceSB.getPreferredSize().width;
                    if (sw == 0)
                        sw = 12;  // Guessing, so it's not zero
                }
            }
            else
                sw = 0;

            boolean changedWantSize = false;
            if ((padW < iw) || (padH < ih) || (scrollW < sw))
            {
                padW = iw;
                padH = ih;
                scrollW = sw;
                wantSize = new Dimension (wantW + scrollW + padW, wantH + padH);
                setSize (wantSize);
                changedWantSize = true;
            }
            // Now that we know insets, check if our window is too narrow or short
            boolean tooSmall = false;
            {
                Insets fi = fcf.getInsets();  // frame insets
                if (fi != null)
                {
                    int fw = fcf.getSize().width;   // frame width
                    int fh = fcf.getSize().height;  // frame height
                    int fiw = fw - fi.left - fi.right;  // inner width
                    int fih = fh - fi.top - fi.bottom;  // inner height
                    int fioh = 0;   // frame inner "other" height (of labels & buttons)
                    if (fcf.changeFaceBut != null)
                        fioh += fcf.changeFaceBut.getPreferredSize().height;
                    if (fcf.promptLbl != null)
                        fioh += fcf.promptLbl.getPreferredSize().height;
                    tooSmall = (fiw < wantSize.width) || ((fih - fioh) < wantSize.height);
                }
            }
            if (changedWantSize || tooSmall)
            {
                fcf.pack();
                fcf.checkSizeAndFocus(true);  // noting our new size
                return true;
            }
            return false;
        }

        /**
         *  Custom layout for this list, which makes things easier
         *  because visibleFaceGrid changes frequently.
         */
        public void doLayout()
        {
            Insets i = getInsets();
            int x = i.left;
            int y = i.top;
            int width = getSize().width - i.left - i.right;
            int height = getSize().height - i.top - i.bottom;

            if (checkInsetsPadding(i))
            {
                width = getSize().width - padW;
                height = getSize().height - padH;
            }

            if (needsScroll)
            {
                if (scrollW == 0)
                {
                    IllegalStateException e = new IllegalStateException("scrollW==0");
                    fcf.pi.chatPrintStackTrace(e);
                    scrollW = faceSB.getPreferredSize().width;
                    if (scrollW == 0)
                        scrollW = 12;
                    wantSize = new Dimension (wantW + scrollW + padW, wantH + padH);
                    setSize (wantSize);
                    fcf.pack();
                    width = getSize().width - padW;
                    height = getSize().height - padH;
                }
                faceSB.setLocation(x + width - scrollW, y);
                faceSB.setSize(scrollW, height);
            }

            final int rowHeightPx = SOCFaceButton.FACE_WIDTH_BORDERED_PX * fcf.pi.displayScale;
            for (int r = 0; r < faceRowsHeight; ++r)
            {
                visibleFaceGrid[r].setLocation(x, y);
                visibleFaceGrid[r].setSize(wantW, rowHeightPx);
                y += rowHeightPx;
            }
        }

        public Dimension getMinimumSize() { return wantSize; }

        public Dimension getPreferredSize() { return wantSize; }

        /**
         * Within FaceChooserList, one row of faces.
         * Takes its width (number of faces) from FaceChooserList.rowFacesWidth.
         */
        private class FaceChooserRow extends Container
        {
            private final int startFaceId;

            /** Will not go past SOCFaceButton.NUM_FACES */
            private SOCFaceButton[] faces;

            /**
             * If our FaceChooserList.currentFaceId is within our row, calls that
             * face id's setHilightBorder when adding it.
             * If we're at the end of the range, some gridlayout members may be left blank.
             *
             * @param startId  Starting face ID (ID of first face in row)
             * @throws IllegalArgumentException if startId<=0 or startId >= SOCFaceButton.NUM_FACES
             */
            public FaceChooserRow (final int startId)
                throws IllegalArgumentException
            {
                if ((startId <= 0) || (startId >= SOCFaceButton.NUM_FACES))
                    throw new IllegalArgumentException("startId not within range: " + startId);

                startFaceId = startId;

                int numFaces = FaceChooserList.rowFacesWidth;
                if ((startId + numFaces) >= SOCFaceButton.NUM_FACES)
                    numFaces = SOCFaceButton.NUM_FACES - startId;

                faces = new SOCFaceButton[numFaces];  // At least 1 due to startId check above

                GridLayout glay = new GridLayout(1, FaceChooserList.rowFacesWidth, 0, 0);
                setLayout(glay);

                for (int i = 0; i < numFaces; ++i)
                {
                    SOCFaceButton fb = new SOCFaceButton(fcf.pi, fcf, startId + i);
                    faces[i] = fb;
                    if (startId + i == currentFaceId)
                        fb.setHilightBorder(true);
                    fb.addKeyListener(fcf);
                    add (fb);
                }

                if (numFaces < FaceChooserList.rowFacesWidth)
                {
                    // The grid will be left-justified by FaceChooserList's layout.
                    // Fill blanks with the proper background color.
                    final Color bg = faces[0].getBackground();
                    for (int i = numFaces; i < FaceChooserList.rowFacesWidth; ++i)
                    {
                        JLabel la = new JLabel();
                        la.setBackground(bg);
                        la.setOpaque(true);
                        add (la);
                    }
                }
            }

            /**
             * If this faceId is within our row, call its setHilightBorder.
             *
             * @param faceId     Face ID - If outside our range, do nothing.
             * @param borderSet  Set or clear?
             *
             * @see soc.client.SOCFaceButton#setHilightBorder(boolean)
             */
            public void setFaceHilightBorder (int faceId, boolean borderSet)
            {
                faceId = faceId - startFaceId;
                if ((faceId < 0) || (faceId >= faces.length))
                    return;
                faces[faceId].setHilightBorder(borderSet);
            }

            /**
             * setVisible - overrides to call each face's setVisible
             *
             * @param vis  Make visible?
             */
            public void setVisible (boolean vis)
            {
                for (int i = faces.length - 1; i >= 0; --i)
                    faces[i].setVisible(vis);
                super.setVisible(vis);
            }

        }  /* inner class FaceChooserRow */

    }  /* static nested class FaceChooserList */

}  /* class FaceChooserFrame */
