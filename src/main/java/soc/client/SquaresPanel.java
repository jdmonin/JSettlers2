/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2009,2012,2019 Jeremy D Monin <jeremy@nand.net>
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
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JPanel;

/**
 * Display grid of give/get resources
 * for trade and bank/port offers.
 * 2 rows of 5 columns:  1 column per resource
 * type: Clay, ore, sheep, wheat, wood.
 *
 * @author Robert S Thomas
 *
 * @see SOCHandPanel
 * @see TradeOfferPanel
 */
@SuppressWarnings("serial")
/*package*/ class SquaresPanel
    extends JPanel
    implements MouseListener, ColorSquareListener
{
    /**
     * Width of this panel: 5 columns of {@link ColorSquareLarger}s,
     * which share 1 pixel overlap for squares' shared border.
     * @since 2.0.00
     */
    public static final int WIDTH = 5 * (ColorSquareLarger.WIDTH_L - 1) + 1;

    /**
     * Height of this panel: 2 lines of {@link ColorSquareLarger}s,
     * which share 1 pixel overlap for squares' shared border.
     * @since 1.1.08
     */
    public static final int HEIGHT = (2 * (ColorSquareLarger.HEIGHT_L - 1)) + 1;

    /**
     * Size of this panel: {@link #HEIGHT} 2 lines x {@link #WIDTH} 5 columns of {@link ColorSquareLarger}s.
     * Scaled by {@link #displayScale}.
     * @since 2.0.00
     */
    private final Dimension size;

    /**
     *  To change its value, each ColorSquare handles its own mouse events.
     *  We also add ourself as listeners to mouse and ColorSquare value changes.
     */
    private ColorSquare[] give, get;
    boolean interactive;
    boolean notAllZero;
    SOCHandPanel parentHand;

    /**
     * For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @since 2.0.00
     */
    private final int displayScale;

    /**
     * Creates a new SquaresPanel object.
     *
     * @param in Interactive?
     * @param displayScale  For high-DPI displays, what scaling factor to use? Unscaled is 1.
     */
    public SquaresPanel(boolean in, final int displayScale)
    {
        this (in, null, displayScale);
    }

    /**
     * Creates a new SquaresPanel object, as part of a SOCHandPanel.
     *
     * @param in Interactive?
     * @param hand HandPanel containing this SquaresPanel
     * @param displayScale  For high-DPI displays, what scaling factor to use? Unscaled is 1.
     */
    public SquaresPanel(boolean in, SOCHandPanel hand, final int displayScale)
    {
        super(null);
        this.displayScale = displayScale;

        interactive = in;
        notAllZero = false;
        parentHand = hand;

        setFont(new Font("SansSerif", Font.PLAIN, 10 * displayScale));

        give = new ColorSquare[5];
        get = new ColorSquare[5];
        final int sqSize = ColorSquareLarger.WIDTH_L * displayScale;
        for (int i = 0; i < 5; i++)
        {
            final Color sqColor = ColorSquare.RESOURCE_COLORS[i];
            get[i] = new ColorSquareLarger(ColorSquare.NUMBER, in, sqSize, sqSize, sqColor);
            give[i] = new ColorSquareLarger(ColorSquare.NUMBER, in, sqSize, sqSize, sqColor);
            add(get[i]);
            add(give[i]);
            get[i].setSquareListener(this);
            get[i].addMouseListener(this);
            give[i].setSquareListener(this);
            give[i].addMouseListener(this);
        }

        // without these calls, parent panel layout is incomplete even when this panel overrides get*Size
        size = new Dimension(WIDTH * displayScale, HEIGHT * displayScale);
        setSize(size);
        setMinimumSize(size);
        setPreferredSize(size);
    }

    @Override
    public Dimension getMinimumSize()   { return size; };
    @Override
    public Dimension getMaximumSize()   { return size; };
    @Override
    public Dimension getPreferredSize() { return size; };

    /**
     * Custom layout for panel.
     */
    public void doLayout()
    {
        final int lineH = ColorSquareLarger.HEIGHT_L * displayScale - 1;
        final int sqW = ColorSquareLarger.WIDTH_L * displayScale - 1;
        int i;

        for (i = 0; i < 5; i++)
        {
            give[i].setSize(sqW + 1, lineH + 1);
            give[i].setLocation(i * sqW, 0);

            get[i].setSize(sqW + 1, lineH + 1);
            get[i].setLocation(i * sqW, lineH);
        }
    }

    /** Don't "roll" plus/minus if shift or ctrl key is held during click */
    public static final int shiftKeysMask = MouseEvent.SHIFT_MASK
        | MouseEvent.CTRL_MASK | MouseEvent.ALT_MASK | MouseEvent.META_MASK;

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mousePressed(MouseEvent e)
    {
        Object target = e.getSource();

        if ( ! interactive )
            return;

        if (0 != (shiftKeysMask & e.getModifiers()))
        {
            /**
             * Shift, Ctrl, or a similar key is being held.
             * Instead of normally "rolling" the give/get pair up/down,
             * just increment the square being clicked.  This is
             * done by the square's own mouselistener, not here.
             */
            return;
        }

        for (int i = 0; i < 5; i++)
        {
            if ( (target == get[i]) && (give[i].getIntValue() > 0) )
            {
                give[i].subtractValue(1);
                get[i].subtractValue(1);
            }
            else if ( (target == give[i]) && (get[i].getIntValue() > 0) )
            {
                get[i].subtractValue(1);
                give[i].subtractValue(1);
            }
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param give DOCUMENT ME!
     * @param get DOCUMENT ME!
     */
    public void setValues(int[] give, int[] get)
    {
        boolean notAllZ = false;
        for (int i = 0; i < 5; i++)
        {
            this.give[i].setIntValue(give[i]);
            this.get[i].setIntValue(get[i]);
            if ((give[i]!=0) || (get[i]!=0))
                notAllZ = true;
        }
        notAllZero = notAllZ;
    }

    /**
     * DOCUMENT ME!
     *
     * @param give DOCUMENT ME!
     * @param get DOCUMENT ME!
     */
    public void getValues(int[] give, int[] get)
    {
        for (int i = 0; i < 5; i++)
        {
            give[i] = this.give[i].getIntValue();
            get[i] = this.get[i].getIntValue();
        }
    }

    /** Does any grid square contain a non-zero value? */
    public boolean containsNonZero()
    {
        return notAllZero;
    }

    /**
     * Called by colorsquare when clicked; if we're part of a HandPanel,
     * could enable/disable its buttons based on new value.
     * If needed, also call {@link SOCHandPanel#sqPanelZerosChange(boolean)}
     * if {@link #parentHand} is set.
     */
    public void squareChanged(ColorSquare sq, int oldValue, int newValue)
    {
        boolean wasNotZero = notAllZero;

        if (newValue != 0)
            notAllZero = true;
        else
        {
            // A square became zero; how are the others?
            boolean notAllZ = false;
            for (int i = 0; i < 5; i++)
            {
                if (0 != this.give[i].getIntValue())
                {
                    notAllZ = true;
                    break;
                }
                if (0 != this.get[i].getIntValue())
                {
                    notAllZ = true;
                    break;
                }
            }

            notAllZero = notAllZ;
        }

        if ((parentHand != null) && (wasNotZero != notAllZero))
            parentHand.sqPanelZerosChange(notAllZero);
    }


    // Stubs required for MouseListener:

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mouseEntered(MouseEvent e) {}

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mouseExited(MouseEvent e) {}

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mouseClicked(MouseEvent e) {}

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mouseReleased(MouseEvent e) {}

}
