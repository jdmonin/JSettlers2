/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007,2008 Jeremy D. Monin <jeremy@nand.net>
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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;


/**
 * This is a square box with a background color and
 * possibly a number or X in it.  This box can be
 * interactive, or non-interactive.  The possible
 * colors of the box correspond to resources in SoC.
 *
 * @author Robert S Thomas
 */
public class ColorSquare extends Canvas implements MouseListener
{
    /**
     * The color constants are used by ColorSquare,
     * and also used for the robber's "ghost" when
     * moving the robber. Specifying the color here is
     * simpler than trying to read it at runtime from the
     * hex graphics, which may have a texture.
     *
     * @see soc.client.SOCBoardPanel#drawRobber(Graphics, int, boolean)
     */
    public final static Color CLAY = new Color(204, 102, 102);
    public final static Color ORE = new Color(153, 153, 153);
    public final static Color SHEEP = new Color(51, 204, 51);
    public final static Color WHEAT = new Color(204, 204, 51);
    public final static Color WOOD = new Color(204, 153, 102);
    public final static Color GREY = new Color(204, 204, 204);  // Must not equal ORE, for ore's auto-tooltip to show
    public final static Color DESERT = new Color(255, 255, 153);
    public final static int NUMBER = 0;
    public final static int YES_NO = 1;
    public final static int CHECKBOX = 2;
    public final static int BOUNDED_INC = 3;
    public final static int BOUNDED_DEC = 4;
    public final static int WIDTH = 16;
    public final static int HEIGHT = 16;

    /** The warning-level text color (high, low, or zero)
     * 
     *  @see #setHighWarningLevel(int)
     *  @see #setLowWarningLevel(int)
     *  @see #setTooltipZeroText(String)
     *  @see #WARN_LEVEL_COLOR_BG_FROMGREY
     */
    public static Color WARN_LEVEL_COLOR = new Color(200, 0, 0);

    /**
     * Background color for warning-level, if grey normally
     * @see #WARN_LEVEL_COLOR
     */
    public static Color WARN_LEVEL_COLOR_BG_FROMGREY = new Color(255, 255, 0);

    int intValue;
    boolean boolValue;
    boolean valueVis;
    int kind;
    int upperBound;
    int lowerBound;
    boolean interactive;
    protected ColorSquareListener sqListener;
    protected AWTToolTip ttip;

    /**
     * Normal background color is GREY (when not high or low "warning" color).
     * Background does not change for warning, unless this is true.
     * @see #WARN_LEVEL_COLOR_BG_FROMGREY
     */
    protected boolean warn_bg_grey;

    /** Text for normal vs low-warning-level. Unused unless a low-bound or high-bound or zero-level-text is set. */
    protected String ttip_text;
    /** Optional text for low-warning-level and high-warning-level (intValue). */
    protected String ttip_text_warnLow, ttip_text_warnHigh;
    /** Optional text for zero level (intValue). */
    protected String ttip_text_zero;
    /** Low-level or high-level warning level has been set (intValue). */
    protected boolean hasWarnLow, hasWarnHigh;
    /** At low-level warning, or at zero if ttip_text_zero was set. */
    protected boolean isWarnLow;
    /** At high-level warning. */
    protected boolean isWarnHigh;
    protected int warnLowBound;  // TODO rename any warn-thing from "bound" incl comments
    protected int warnHighBound;  // TODO rename any warn-thing from "bound" incl comments

    /** Size per instance, for ColorSquareLarger */
    protected int squareW, squareH;
    protected Dimension squareSize;

    /**
     * Creates a new grey ColorSquare object without a visible value.
     *
     * @see #ColorSquare(int, boolean, Color, int, int)
     */
    public ColorSquare()
    {
        this(NUMBER, false, GREY, 0, 0);
        valueVis = false;
    }

    /**
     * Creates a new ColorSquare object with specified background color. Type
     * <code>NUMBER</code>, non-interactive, upper=99, lower=0.
     *<P>
     * A tooltip with the resource name is created if c is one of the
     * resource colors defined in ColorSquare (CLAY, WHEAT, etc).
     *
     * @param c background color
     * @see #ColorSquare(int, boolean, Color, int, int)
     */
    public ColorSquare(Color c)
    {
        this(NUMBER, false, c, 99, 0);
    }

    /**
     * Creates a new ColorSquare object with specified background color and
     * initial value. Type <code>NUMBER</code>, non-interactive, upper=99,
     * lower=0.
     *<P>
     * A tooltip with the resource name is created if c is one of the
     * resource colors defined in ColorSquare (CLAY, WHEAT, etc).
     *
     * @param c background color
     * @param v initial int value
     * @see #ColorSquare(int, boolean, Color, int, int)
     */
    public ColorSquare(Color c, int v)
    {
        this(NUMBER, false, c, 99, 0);
        intValue = v;
    }

    /**
     * Creates a new ColorSquare of the specified kind and background
     * color. Possibly interactive. For kind = NUMBER, upper=99, lower=0.
     *<P>
     * A tooltip with the resource name is created if c is one of the
     * resource colors defined in ColorSquare (CLAY, WHEAT, etc).
     *
     * @param k Kind: NUMBER, YES_NO, CHECKBOX, BOUNDED_INC, BOUNDED_DEC
     * @param in interactive flag allowing user interaction
     * @param c background color
     * @see #ColorSquare(int, boolean, Color, int, int)
     */
    public ColorSquare(int k, boolean in, Color c)
    {
        this(k, in, c, 99, 0);
    }

    /**
     * Creates a new ColorSquare of the specified kind and background
     * color. Possibly interactive, with upper and lower bounds specified for
     * NUMBER kinds.
     *<P>
     * A tooltip with the resource name is created if c is one of the
     * resource colors defined in ColorSquare (CLAY, WHEAT, etc).
     *
     * @param k Kind: NUMBER, YES_NO, CHECKBOX, BOUNDED_INC, BOUNDED_DEC
     * @param in interactive flag allowing user interaction
     * @param c background color
     * @param upper upper bound if k == NUMBER
     * @param lower lower bound if k == NUMBER
     */
    public ColorSquare(int k, boolean in, Color c, int upper, int lower)
    {
        super();

        setSize(WIDTH, HEIGHT);
        setFont(new Font("Geneva", Font.PLAIN, 10));

        setBackground(c);
        kind = k;
        interactive = in;
        sqListener = null;
        ttip = null;
        ttip_text = null;
        ttip_text_warnLow = null;
        ttip_text_zero = null;
        hasWarnLow = false;
        isWarnLow = false;
        hasWarnHigh = false;
        isWarnHigh = false;
        warn_bg_grey = c.equals(GREY);

        switch (k)
        {
        case NUMBER:
            valueVis = true;
            intValue = 0;

            break;

        case YES_NO:
            valueVis = true;
            boolValue = false;

            break;

        case CHECKBOX:
            valueVis = true;
            boolValue = false;

            break;

        case BOUNDED_INC:
            valueVis = true;
            boolValue = false;
            upperBound = upper;
            lowerBound = lower;

            break;

        case BOUNDED_DEC:
            valueVis = true;
            boolValue = false;
            upperBound = upper;
            lowerBound = lower;

            break;
        }

        // Color-based tooltip
        if (c.equals(GREY))
        {
            // Most common case.
            // Do nothing.
            // If needed, can call setTooltipText explicitly.
        }
        else if (c == CLAY)
            ttip = new AWTToolTip ("Clay", this);
        else if (c == ORE)
            ttip = new AWTToolTip ("Ore", this);
        else if (c == SHEEP)
            ttip = new AWTToolTip ("Sheep", this);
        else if (c == WHEAT)
            ttip = new AWTToolTip ("Wheat", this);
        else if (c == WOOD)
            ttip = new AWTToolTip ("Wood", this);

        this.addMouseListener(this);
    }

    /**
     * Overrides standard to allow special warning behavior for {@link #GREY}.
     * Only grey squares change background color when a warning-level
     * threshold is reached ({@link #setHighWarningLevel(int)}
     * or {@link #setLowWarningLevel(int)}).
     * TODO DOCU - what do other squares do?
     *
     * @param c New background color
     */
    public void setBackground(Color c)
    {
        warn_bg_grey = c.equals(GREY);
        super.setBackground(c);
    }

    /**
     * Set this square's background color.  The text color cannot be changed.
     * See {@link #setBackground(Color)} for special behavior with warning-level.
     * thresholds.
     *
     * @param c New background color
     */
    public void setColor(Color c)
    {
        setBackground(c);
    }

    public void setSize(int w, int h)
    {
        super.setSize(w, h);
        squareW = w;
        squareH = h;
        squareSize = new Dimension(w, h);
    }

    /**
     * If we have a tooltip, return its text.
     *
     * @return tooltip text, or null if none
     */
    public String getTooltipText()
    {
        if (ttip == null)
            return null;
        return ttip.getTip();
    }

    /**
     * Change tooltip text or show or hide tooltip.
     * (Set tip text to null to hide it.)
     *
     * @param tip New tip text; will create tooltip if needed.
     *     If tip is null, tooltip is removed, and any warning-level tip text
     *     or zero-level text is also set to null.
     *
     * @see #setTooltipHighWarningLevel(String, int)
     * @see #setTooltipLowWarningLevel(String, int)
     * @see #setTooltipZeroText(String)
     */
    public void setTooltipText(String tip)
    {
        ttip_text = tip;
        if (tip == null)
        {
            if (ttip != null)
            {
                ttip.destroy();
                ttip = null;
            }
            ttip_text_warnLow = null;
            ttip_text_zero = null;
            return;
        }
        if (ttip == null)
            ttip = new AWTToolTip(tip, this);
        else
            ttip.setTip(tip);  // Handles its own repaint        
    }

    /**
     * Set low-level warning (TODO docu text)
     *
     * @param warnLevel If the colorsquare value is at warnLevel or lower,
     *     indicate with the warning color.
     *
     * @see #clearLowWarningLevel()
     * @see #setTooltipZeroText(String)
     *
     * @throws IllegalArgumentException if warnLevel is above high level, or is zero.
     *     To set text for value 0, use {@link #setTooltipZeroText(String)} instead.
     *     To clear the warning level, use {@link #clearLowWarningLevel()} instead.
     */
    public void setLowWarningLevel(int warnLevel)
        throws IllegalArgumentException
    {
        if (warnLevel == 0)
        {
            if (ttip_text == null)
                throw new IllegalArgumentException("To clear, call clearLowWarningLevel instead");
            else
                throw new IllegalArgumentException("To set zero text, call setTooltipZeroText instead");
        }
        if (hasWarnHigh && (warnLevel >= warnHighBound))
            throw new IllegalArgumentException("Asked for low warning (" + warnLevel
                + ") higher than existing high warning (" + warnHighBound + ")");

        boolean wasWarnLow = isWarnLow;
        hasWarnLow = true;
        warnLowBound = warnLevel;
        isWarnLow = ((intValue <= warnLevel)
                || ((intValue == 0) && (ttip_text_zero != null)));
        if (isWarnLow != wasWarnLow)
        {
            repaint();
            if ((intValue == 0) && (ttip_text_zero != null))
                ttip.setTip(ttip_text_zero);
            else if (ttip_text_warnLow != null)
            {
                if (isWarnLow)
                    ttip.setTip(ttip_text_warnLow);
                else
                    ttip.setTip(ttip_text);
            }
        }
    }

    /**
     * If a tooltip low-warning has been set, it is also cleared
     * TODO docu
     */
    public void clearLowWarningLevel()
    {
        hasWarnLow = false;
        if (isWarnLow)
        {
            isWarnLow = false;            
            repaint();
            if (ttip_text_warnLow != null)
            {
                ttip_text_warnLow = null;
                if ((intValue == 0) && (ttip_text_zero != null))
                    ttip.setTip(ttip_text_zero);
                else
                    ttip.setTip(ttip_text);
            }
        }
    }

    /**
     * Set low-level warning, and set or clear its tooltip text.
     * If warnTip not null, we must already have a standard tooltip text.
     * Does not affect zero-level or high-level tooltip text.
     *
     * @param warnTip   TODO docu - or null to clear tip text
     * @param warnLevel TODO docu - at or below
     *
     * @see #setHighWarningLevel(int)
     * @see #setLowWarningLevel(int)
     * @see #setTooltipText(String)
     * @see #setTooltipZeroText(String)
     *
     * @throws IllegalStateException if setTooltipText has not yet been called,
     *     and warnTip is not null
     *
     * @throws IllegalArgumentException if warnLevel is above high level, or is zero.
     *     To set text for value 0, use {@link #setTooltipZeroText(String)} instead.
     *     To clear the warning level, use {@link #clearLowWarningLevel()} instead.
     */
    public void setTooltipLowWarningLevel(String warnTip, int warnLevel)
        throws IllegalStateException, IllegalArgumentException
    {
        if ((ttip_text == null) && (warnTip != null))
            throw new IllegalStateException("Must call setTooltipText first");
        if (warnLevel == 0)
        {
            if (ttip_text == null)
                throw new IllegalArgumentException("To clear, call clearLowWarningLevel instead");
            else
                throw new IllegalArgumentException("To set zero text, call setTooltipZeroText instead");
        }
        if (hasWarnHigh && (warnLevel >= warnHighBound))
            throw new IllegalArgumentException("Asked for low warning (" + warnLevel
                + ") higher than existing high warning (" + warnHighBound + ")");

        boolean wasWarnLow = isWarnLow;
        boolean willWarnLow = (intValue <= warnLevel);

        ttip_text_warnLow = warnTip;  // Remember new warnTip text

        // TODO simplify, docu
        if ((warnTip == null) && (ttip_text != null))
        {
            // No more warnTip text.
            if ((intValue == 0) && (ttip_text_zero != null))
                ttip.setTip(ttip_text_zero);  // Revert to zero-level tooltip text
            else if (wasWarnLow)
                ttip.setTip(ttip_text);  // Revert to non-warning tooltip text
        }
        else if ((warnTip != null) && wasWarnLow && willWarnLow)
        {
            // If the status won't change (we're still at warning level),
            // change the text, because setLowWarningLevel won't.
            // Change text unless we're at zero and there's a zero text.
            if ((intValue != 0) || (ttip_text_zero == null))
                ttip.setTip(warnTip);
        }

        setLowWarningLevel(warnLevel);  // Remember new warning level
    }

    /**
     * Set high-level warning (TODO docu text)
     *
     * @param warnLevel If the colorsquare value is at warnLevel or higher,
     *     indicate with the warning color.
     *
     * @see #clearHighWarningLevel()
     *
     * @throws IllegalArgumentException if warnLevel is below low-warning level.
     */
    public void setHighWarningLevel(int warnLevel)
        throws IllegalArgumentException
    {
        if (hasWarnLow && (warnLevel <= warnLowBound))
            throw new IllegalArgumentException("Asked for high warning (" + warnLevel
                + ") lower than existing low warning (" + warnLowBound + ")");

        boolean wasWarnHigh = isWarnHigh;
        hasWarnHigh = true;
        warnHighBound = warnLevel;
        isWarnHigh = (intValue >= warnLevel);
        if (isWarnHigh != wasWarnHigh)
        {
            repaint();
            if (ttip_text_warnHigh != null)
            {
                if (isWarnHigh)
                    ttip.setTip(ttip_text_warnHigh);
                else
                    ttip.setTip(ttip_text);
            }
        }
    }

    /**
     * If a tooltip high-warning has been set, it is also cleared
     * TODO docu
     */
    public void clearHighWarningLevel()
    {
        hasWarnHigh = false;
        if (isWarnHigh)
        {
            isWarnHigh = false;            
            repaint();
            if (ttip_text_warnHigh != null)
            {
                ttip_text_warnHigh = null;
                ttip.setTip(ttip_text);
            }
        }
    }

    /**
     * Set high-level warning, and set or clear its tooltip text.
     * If warnTip not null, we must already have a standard tooltip text.
     * Does not affect zero-level or low-level tooltip text.
     *
     * @param warnTip   TODO docu - or null to clear tip text
     * @param warnLevel TODO docu - at or above
     *
     * @see #setHighWarningLevel(int)
     * @see #setLowWarningLevel(int)
     * @see #setTooltipText(String)
     *
     * @throws IllegalStateException if setTooltipText has not yet been called,
     *     and warnTip is not null
     *
     * @throws IllegalArgumentException if warnLevel is below low-warning level.
     */
    public void setTooltipHighWarningLevel(String warnTip, int warnLevel)
        throws IllegalStateException, IllegalArgumentException
    {
        if ((ttip_text == null) && (warnTip != null))
            throw new IllegalStateException("Must call setTooltipText first");
        if (hasWarnLow && (warnLevel <= warnLowBound))
            throw new IllegalArgumentException("Asked for high warning (" + warnLevel
                + ") lower than existing low warning (" + warnLowBound + ")");

        boolean wasWarnHigh = isWarnHigh;
        boolean willWarnHigh = (intValue >= warnLevel);

        ttip_text_warnHigh = warnTip;  // Remember new warnTip text

        // TODO simplify, docu
        if ((warnTip == null) && (ttip_text != null))
        {
            // No more warnTip text.
            if (wasWarnHigh)
                ttip.setTip(ttip_text);  // Revert to non-warning tooltip text
        }
        else if ((warnTip != null) && wasWarnHigh && willWarnHigh)
        {
            // If the status won't change (we're still at warning level),
            // change the text, because setHighWarningLevel won't.
            ttip.setTip(warnTip);
        }

        setHighWarningLevel(warnLevel);  // Remember new warning level
    }

    /**
     * Set or clear zero-level tooltip text.
     * Setting this text will also make the tooltip color the warning color
     * when at value 0.
     *
     * @param zeroTip   TODO docu - or null to clear tip text
     *
     * @see #setTooltipText(String)
     * @see #setTooltipHighWarningLevel(String, int)
     * @see #setTooltipLowWarningLevel(String, int)
     * @see #setTooltipZeroText(String)
     *
     * @throws IllegalStateException if setTooltipText has not yet been called,
     *     and zeroTip is not null
     */
    public void setTooltipZeroText(String zeroTip)
        throws IllegalStateException
    {
        if ((ttip_text == null) && (zeroTip != null))
            throw new IllegalStateException("Must call setTooltipText first");

        boolean isZero = (intValue == 0);

        ttip_text_zero = zeroTip;  // Remember new zeroTip text

        // TODO simplify, docu
        if ((zeroTip == null) && (ttip_text != null))
        {
            // No more zeroTip text.
            if (isZero)
            {
                if (hasWarnLow && isWarnLow)
                    ttip.setTip(ttip_text_warnLow);  // Revert to low-level tooltip text
                else
                    ttip.setTip(ttip_text);  // Revert to non-warning tooltip text
            }
        }
        else if ((zeroTip != null) && isZero)
        {
            // New zeroTip text. We may have been at low-level or standard tooltip text.
            ttip.setTip(zeroTip);
        }
    }

    /** Show or hide the colorsquare.
     *
     *  If we have a tooltip, will also show/hide that tooltip.
     */
    public void setVisible(boolean newVis)
    {
        if (ttip != null)
            ttip.setVisible(newVis);
        super.setVisible(newVis);
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public Dimension getPreferredSize()
    {
        return squareSize;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public Dimension getMinimumSize()
    {
        return squareSize;
    }

    /**
     * DOCUMENT ME!
     *
     * @param g DOCUMENT ME!
     */
    public void paint(Graphics g)
    {
            g.setPaintMode();
            if (warn_bg_grey && (isWarnLow || isWarnHigh))
            {
                g.setColor(WARN_LEVEL_COLOR_BG_FROMGREY);
                g.fillRect(1, 1, squareW - 2, squareH - 2);
            }
            else
            {
                g.clearRect(0, 0, squareW, squareH);
            }
            g.setColor(Color.black);
            g.drawRect(0, 0, squareW - 1, squareH - 1);

            int x;
            int y;

            if (valueVis)
            {
                if (isWarnLow || isWarnHigh)
                    g.setColor(WARN_LEVEL_COLOR);

                FontMetrics fm = this.getFontMetrics(this.getFont());
                int numW;
                int numH = fm.getHeight();
                //int numA = fm.getAscent();
                switch (kind)
                {
                case NUMBER:
                case BOUNDED_INC:
                case BOUNDED_DEC:

                    numW = fm.stringWidth(Integer.toString(intValue));

                    x = (squareW - numW) / 2;

                    // y = numA + (HEIGHT - numH) / 2; // proper way
                    // y = 12; // way that works
                    y = (squareH + ((int)(.6 * numH))) / 2;  // Semi-proper

                    g.drawString(Integer.toString(intValue), x, y);

                    break;

                case YES_NO:
                    String value = (boolValue ? "Y" : "N");

                    numW = fm.stringWidth(value);

                    x = (squareW - numW) / 2;

                    // y = numA + (HEIGHT - numH) / 2; // proper way
                    // y = 12; // way that works
                    y = (squareH + ((int)(.6 * numH))) / 2;  // Semi-proper

                    g.drawString(value, x, y);

                    break;

                case CHECKBOX:

                    if (boolValue)
                    {
                        int checkX = squareW / 5;
                        int checkY = squareH / 4;
                        g.drawLine(checkX, 2 * checkY, 2 * checkX, 3 * checkY);
                        g.drawLine(2 * checkX, 3 * checkY, 4 * checkX, checkY);
                    }

                    break;
                }
            }
    }

    /**
     * DOCUMENT ME!
     *
     * @param v DOCUMENT ME!
     */
    public void addValue(int v)
    {
        setIntValue(intValue + v);
    }

    /**
     * DOCUMENT ME!
     *
     * @param v DOCUMENT ME!
     */
    public void subtractValue(int v)
    {
        setIntValue (intValue - v);
    }

    /**
     * DOCUMENT ME!
     * If a {@link ColorSquareListener} is attached, and value changes,
     * the listener will be called.
     *
     * @param v DOCUMENT ME!
     */
    public void setIntValue(int v)
    {
        if (v == intValue)
            return;  // <-- Early return: No change in intValue

        int oldIntValue = intValue;

        // Must check for zero before change, because
        // isWarnLow is also true for 0, but they
        // have different tooltip texts.
        boolean wasZero = ((intValue == 0) && (ttip_text_zero != null));  

        // Set the new value
        intValue = v;

        // Zero isn't flagged graphically, unless its tooltip text is set
        boolean isZero = ((intValue == 0) && (ttip_text_zero != null));

        // Previous and new low/high warning flags
        boolean wasWarnLow = isWarnLow;
        boolean wasWarnHigh = isWarnHigh;
        isWarnLow = (isZero || (hasWarnLow && ((intValue <= warnLowBound))));
        isWarnHigh = (hasWarnHigh && (intValue >= warnHighBound));

        repaint();

        // Possible tooltip text update
        if (isZero)
        {
            ttip.setTip(ttip_text_zero);            
        }
        else if ((ttip_text != null) &&
            ((isZero != wasZero) || (isWarnLow != wasWarnLow) || (isWarnHigh != wasWarnHigh)))
        {
            if (isWarnHigh && (ttip_text_warnHigh != null))
                ttip.setTip(ttip_text_warnHigh);
            else if (isWarnLow && (ttip_text_warnLow != null))
                ttip.setTip(ttip_text_warnLow);
            else
                ttip.setTip(ttip_text);
        }

        // Listener callback
        if (sqListener != null)
            sqListener.squareChanged(this, oldIntValue, intValue);
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public int getIntValue()
    {
        return intValue;
    }

    /**
     * DOCUMENT ME!
     * If a {@link ColorSquareListener} is attached, and value changes,
     * the listener will be called.
     *
     * @param v DOCUMENT ME!
     */
    public void setBoolValue(boolean v)
    {
        if (v == boolValue)
            return;  // <-- Early return: No change in intValue

        boolean oldBoolValue = boolValue;
        boolValue = v;
        repaint();

        // Listener callback
        if (sqListener != null)
            sqListener.squareChanged
                (this, oldBoolValue ? 1 : 0, boolValue ? 1 : 0);
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public boolean getBoolValue()
    {
        return boolValue;
    }

    /**
     * Optionally, a square listener can be called when the value changes.
     * If this square is part of a {@link SquaresPanel}, that panel is the listener. 
     * @return square listener, or null.
     */
    public ColorSquareListener getSquareListener()
    {
        return sqListener;
    }

    /**
     * Optionally, a square listener can be called when the value changes.
     * @param sp Square listener, or null to clear
     */
    public void setSquareListener(ColorSquareListener sp)
    {
        sqListener = sp;
    }

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mouseEntered(MouseEvent e)
    {
        ;
    }

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mouseExited(MouseEvent e)
    {
        ;
    }

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mouseClicked(MouseEvent e)
    {
        ;
    }

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
     */
    public void mouseReleased(MouseEvent e)
    {
        ;
    }

    /**
     * DOCUMENT ME!
     * If a {@link ColorSquareListener} is attached, and value changes,
     * the listener will be called.
     *
     * @param evt DOCUMENT ME!
     */
    public void mousePressed(MouseEvent evt)
    {
        if (interactive)
        {
            int oldIVal = intValue;
            boolean bvalChanged = false;

            switch (kind)
            {
            case YES_NO:
            case CHECKBOX:
                boolValue = !boolValue;
                bvalChanged = true;

                break;

            case NUMBER:
                intValue++;

                break;

            case BOUNDED_INC:

                if (intValue < upperBound)
                {
                    intValue++;
                }

                break;

            case BOUNDED_DEC:

                if (intValue > lowerBound)
                {
                    intValue--;
                }

                break;
            }

            repaint();
            if (sqListener != null)
            {
                if (bvalChanged)
                    sqListener.squareChanged(this, boolValue ? 0 : 1, boolValue ? 1 : 0);
                else if (oldIVal != intValue)
                    sqListener.squareChanged(this, oldIVal, intValue);
            }
        }
    }
}
