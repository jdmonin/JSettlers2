/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2009,2012,2019-2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;

/**
 * Display grid of give/get resources
 * for trade and bank/port offers.
 * 2 rows of 5 columns of {@link ColorSquareLarger}:
 * 1 column per resource type: Clay, ore, sheep, wheat, wood.
 *
 * @author Robert S Thomas
 *
 * @see SOCHandPanel
 * @see TradePanel
 */
@SuppressWarnings("serial")
/*package*/ class SquaresPanel
    extends JPanel
    implements MouseListener, ColorSquareListener
{
    /**
     * Width of this panel, in unscaled pixels: 5 columns of {@link ColorSquareLarger}s,
     * which share 1 pixel overlap for squares' shared border.
     *<P>
     * Before v2.4.50 this field was {@code WIDTH}.
     *
     * @since 2.0.00
     */
    public static final int WIDTH_PX = 5 * (ColorSquareLarger.WIDTH_L - 1) + 1;

    /**
     * Height of this panel, in unscaled pixels: 2 lines of {@link ColorSquareLarger}s,
     * which share 1 pixel overlap for squares' shared border.
     *<P>
     * Before v2.4.50 this field was {@code HEIGHT}.
     *
     * @since 1.1.08
     */
    public static final int HEIGHT_PX = (2 * (ColorSquareLarger.HEIGHT_L - 1)) + 1;

    /**
     * Size of this panel: {@link #HEIGHT_PX} 2 lines x {@link #WIDTH_PX} 5 columns of {@link ColorSquareLarger}s.
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

    /**
     * True if any square's value != 0.
     * @since 1.1.00
     */
    boolean notAllZero;

    /**
     * HandPanel containing this SquaresPanel, or null.
     * @since 1.1.00
     */
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
     * @param in Interactive, not read-only?  Can be changed later with {@link #setInteractive(boolean)}.
     * @param hand HandPanel containing this SquaresPanel
     * @param displayScale  For high-DPI displays, what scaling factor to use? Unscaled is 1.
     * @since 1.1.00
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
            get[i] = new ColorSquare(ColorSquare.NUMBER, in, sqSize, sqSize, sqColor);
            give[i] = new ColorSquare(ColorSquare.NUMBER, in, sqSize, sqSize, sqColor);
            add(get[i]);
            add(give[i]);
            get[i].setSquareListener(this);
            get[i].addMouseListener(this);
            give[i].setSquareListener(this);
            give[i].addMouseListener(this);
        }

        // without these calls, parent panel layout is incomplete even when this panel overrides get*Size
        size = new Dimension(WIDTH_PX * displayScale, HEIGHT_PX * displayScale);
        setSize(size);
        setMinimumSize(size);
        setPreferredSize(size);
    }

    /**
     * Set this panel to interactive or read-only mode.
     * If read-only, user can't click the resource amounts to change them.
     * @param inter  True for interactive, false for read-only
     * @see 2.0.00
     */
    public void setInteractive(final boolean inter)
    {
        if (inter == interactive)
            return;

        interactive = inter;
        for (int i = 0; i < 5; ++i)
        {
            get[i].setInteractive(inter);
            give[i].setInteractive(inter);
        }
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
    private static final int shiftKeysMask = MouseEvent.SHIFT_DOWN_MASK
        | MouseEvent.CTRL_DOWN_MASK | MouseEvent.ALT_DOWN_MASK | MouseEvent.META_DOWN_MASK;

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

        if (0 != (shiftKeysMask & e.getModifiersEx()))
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
     * Set trading squares' values from int arrays.
     *
     * @param give DOCUMENT ME!
     * @param get DOCUMENT ME!
     * @see #setValues(SOCResourceSet, SOCResourceSet)
     */
    public void setValues(int[] give, int[] get)
    {
        boolean notAllZ = false;

        for (int i = 0; i < 5; i++)
        {
            this.give[i].setIntValue(give[i]);
            this.get[i].setIntValue(get[i]);
            if ((give[i] != 0) || (get[i] != 0))
                notAllZ = true;
        }

        notAllZero = notAllZ;
    }

    /**
     * Set or clear trading squares' values from resource set contents.
     * @param give  Trade resources to use in Line 1; will clear all to 0 if null
     * @param get   Trade resources to use in Line 2; will clear all to 0 if null
     * @since 2.0.00
     */
    public void setValues(final SOCResourceSet give, final SOCResourceSet get)
    {
        boolean notAllZ = false;

        if (give != null)
        {
            for (int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; ++res)
            {
                int amt = give.getAmount(res);
                notAllZ |= (amt != 0);
                this.give[res - 1].setIntValue(amt);
            }
        } else {
            for (int i = 0; i < 5; ++i)
                this.give[i].setIntValue(0);
        }

        if (get != null)
        {
            for (int res = SOCResourceConstants.CLAY; res <= SOCResourceConstants.WOOD; ++res)
            {
                int amt = get.getAmount(res);
                notAllZ |= (amt != 0);
                this.get[res - 1].setIntValue(amt);
            }
        } else {
            for (int i = 0; i < 5; ++i)
                this.get[i].setIntValue(0);
        }

        notAllZero = notAllZ;
    }

    /**
     * DOCUMENT ME!
     *
     * @param giveRsrcs DOCUMENT ME!
     * @param getRsrcs DOCUMENT ME!
     */
    public void getValues(int[] giveRsrcs, int[] getRsrcs)
    {
        for (int i = 0; i < 5; i++)
        {
            giveRsrcs[i] = give[i].getIntValue();
            getRsrcs[i] = get[i].getIntValue();
        }
    }

    /**
     * Does any grid square contain a non-zero value?
     * @since 1.1.00
     */
    public boolean containsNonZero()
    {
        return notAllZero;
    }

    /**
     * Called by colorsquare when clicked; if we're part of a HandPanel,
     * could enable/disable its buttons based on new value.
     * If needed, also call {@link SOCHandPanel#sqPanelZerosChange(boolean)}
     * if {@link #parentHand} is set.
     * @since 1.1.00
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
