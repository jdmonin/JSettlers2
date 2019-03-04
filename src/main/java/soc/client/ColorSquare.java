/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2012,2018-2019 Jeremy D Monin <jeremy@nand.net>
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
 **/
package soc.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BorderFactory;
import javax.swing.JComponent;


/**
 * This is a square box with a background color and
 * possibly a number, checkmark, or text in it.  This box can be
 * interactive, or non-interactive.  The possible
 * colors of the box correspond to resources in SoC.
 *<P>
 * Default size and minimum size are {@link #WIDTH} by {@link #HEIGHT} pixels: Call {@link #setSize(int, int)} to change
 * size, {@link #setMinimumSize(Dimension)} to change minimum. Minimum isn't set by setSize, to avoid a "disappearing"
 * 0-height or 0-width square when layout manager calls setSize or setBounds.
 *<P>
 * Most colorsquares in JSettlers are actually {@link ColorSquareLarger} instances:
 * Creating that subclass was easier than changing the values of {@link #WIDTH} and {@link #HEIGHT} here,
 * which are used for setting the size of many GUI elements.
 *
 * @author Robert S Thomas
 */
@SuppressWarnings("serial")
public class ColorSquare extends JComponent implements MouseListener
{
    /**
     * The color constants are used by ColorSquare,
     * and also used for the robber's "ghost" when
     * moving the robber, and fallback for missing hex graphics.
     *
     * @see soc.client.SOCBoardPanel#drawRobber(Graphics, int, boolean, boolean)
     * @see soc.client.SOCBoardPanel#drawHex(Graphics, int)
     */
    public final static Color CLAY = new Color(204, 102, 102);
    public final static Color ORE = new Color(153, 153, 153);
    public final static Color SHEEP = new Color(51, 204, 51);
    public final static Color WHEAT = new Color(204, 204, 51);
    public final static Color WOOD = new Color(204, 153, 102);
    public final static Color GREY = new Color(204, 204, 204);  // Must not equal ORE, for ore's auto-tooltip to show
    public final static Color DESERT = new Color(255, 255, 153);

    /**
     * {@link soc.game.SOCBoardLarge#GOLD_HEX Gold hex} color.
     * @since 2.0.00
     */
    public final static Color GOLD = new Color(255, 250, 0);

    /**
     * {@link soc.game.SOCBoardLarge#FOG_HEX Fog hex} color.
     * @since 2.0.00
     */
    public final static Color FOG = new Color(220, 220, 220);  // Should not equal GREY, for comparisons

    /** Water hex color, for fallback if graphic is missing. @since 1.1.07 */
    public static final Color WATER = new Color(72, 97, 162);  // grey-blue; waterHex.gif average is actually (76, 102, 152)

    /**
     * Array of resource colors.
     * 0 is {@link #CLAY}, 1 is {@link #ORE}, {@link #SHEEP}, {@link #WHEAT}, 4 is {@link #WOOD}.
     *<P>
     * Because this array has the resource types a player can hold or trade,
     * it does not contain {@link #GOLD}.
     * @since 1.1.08
     */
    public static final Color[] RESOURCE_COLORS =
        { CLAY, ORE, SHEEP, WHEAT, WOOD };

    public final static int NUMBER = 0;
    public final static int YES_NO = 1;
    public final static int CHECKBOX = 2;
    public final static int BOUNDED_INC = 3;
    public final static int BOUNDED_DEC = 4;

    /**
     * Colorsquare type TEXT displays a short message.
     * You will have to change the colorsquare's size yourself.
     * @since 1.1.06
     */
    public static final int TEXT = 5;

    public final static int WIDTH = 16;
    public final static int HEIGHT = 16;

    /** The warning-level text color (high, low, or zero)
     *
     *  @see #setHighWarningLevel(int)
     *  @see #setLowWarningLevel(int)
     *  @see #setToolTipZeroText(String)
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
    private String textValue;  // since 1.1.06
    boolean valueVis;
    int kind;
    int upperBound;
    int lowerBound;
    final boolean interactive;

    /** Border color, BLACK by default
     * @since 1.1.13
     */
    private Color borderColor = Color.BLACK;

    protected ColorSquareListener sqListener;

    /**
     * Normal background color is GREY (when not high or low "warning" color).
     * Background does not change for warning, unless this is true.
     * @see #WARN_LEVEL_COLOR_BG_FROMGREY
     */
    protected boolean warn_bg_grey;

    /**
     * Text to use when numeric value is in normal range (not low-warning-level).
     * Field contents are unused unless a low-bound or high-bound or zero-level-text is set.
     */
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

    /**
     * Size from most recent call to {@link #setSize(int, int)}.
     * @see #minSize
     */
    protected Dimension squareSize;

    /**
     * Size from most recent call to {@link #setMinimumSize(Dimension)}.
     * @see #squareSize
     * @since 2.0.00
     */
    protected Dimension minSize;

    /** i18n text strings */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /**
     * Creates a new grey ColorSquare object without a visible value.
     * Uses type {@link #NUMBER}, non-interactive, with lower and upper limits both 0.
     *
     * @see #ColorSquare(int, boolean, Color, int, int)
     * @see #ColorSquare(Color)
     */
    public ColorSquare()
    {
        this(NUMBER, false, GREY, 0, 0);
        valueVis = false;
    }

    /**
     * Creates a new ColorSquare object with specified background color and without a visible value.
     * Non-interactive. Uses type {@link #CHECKBOX}.
     *<P>
     * A tooltip with the resource name is created if {@code c} is one of the
     * resource colors defined in ColorSquare ({@link #CLAY}, {@link #WHEAT}, etc,
     * or an element of {@link #RESOURCE_COLORS}).
     *
     * @param c background color; creates resource-name tooltip if is a resource color
     * @see #ColorSquare(Color, int, int)
     * @see #ColorSquare(int, boolean, Color, int, int)
     */
    public ColorSquare(Color c)
    {
        this(CHECKBOX, false, c, 99, 0);
    }

    /**
     * Creates a new ColorSquare with specified background color and size, without a visible value;
     * calls {@link #ColorSquare(Color)}.
     *
     * @param c background color; creates resource-name tooltip if is a resource color
     * @param w width in pixels
     * @param h height in pixels
     * @since 2.0.00
     */
    public ColorSquare(Color c, int w, int h)
    {
        this(c);
        if ((w != ColorSquare.WIDTH) || (h != ColorSquare.HEIGHT))
            setSizesAndFont(w, h);
    }

    /**
     * Creates a new ColorSquare object with specified background color and
     * initial value. Type {@link #NUMBER}, non-interactive, upper=99,
     * lower=0.
     *<P>
     * A tooltip with the resource name is created if {@code c} is one of the
     * resource colors defined in ColorSquare ({@link #CLAY}, {@link #WHEAT}, etc,
     * or an element of {@link #RESOURCE_COLORS}).
     *
     * @param c background color; creates resource-name tooltip if is a resource color
     * @param v initial int value
     * @see #ColorSquare(Color, int, int, int)
     * @see #ColorSquare(int, boolean, Color, int, int)
     */
    public ColorSquare(Color c, int v)
    {
        this(NUMBER, false, c, 99, 0);
        intValue = v;
    }

    /**
     * Creates a new ColorSquare with specified background color, initial value, and size;
     * calls {@link #ColorSquare(Color, int)}.
     *
     * @param c background color; creates resource-name tooltip if is a resource color
     * @param v initial int value
     * @param w width in pixels
     * @param h height in pixels
     * @since 2.0.00
     */
    public ColorSquare(Color c, int v, int w, int h)
    {
        this(c, v);
        if ((w != ColorSquare.WIDTH) || (h != ColorSquare.HEIGHT))
            setSizesAndFont(w, h);
    }

    /**
     * Creates a new ColorSquare object with specified background color and
     * initial value. Type {@link #TEXT}, non-interactive.
     *<P>
     * The colorsquare's size is small by default and not changed here, so
     * be sure to call {@link #setSize(int, int) setSize} or
     * {@link #setBounds(int, int, int, int) setBounds} to make the square
     * large enough to display your text.
     *<P>
     * A tooltip with the resource name is created if {@code c} is one of the
     * resource colors defined in ColorSquare ({@link #CLAY}, {@link #WHEAT}, etc,
     * or an element of {@link #RESOURCE_COLORS}).
     *
     * @param c background color; creates resource-name tooltip if is a resource color
     * @param v initial string value
     *
     * @since 1.1.06
     */
    public ColorSquare(Color c, String v)
    {
        this(TEXT, false, c);
        textValue = v;
    }

    /**
     * Creates a new ColorSquare of the specified kind and background
     * color. Possibly interactive. For kind = NUMBER, upper=99, lower=0.
     *<P>
     * A tooltip with the resource name is created if {@code c} is one of the
     * resource colors defined in ColorSquare ({@link #CLAY}, {@link #WHEAT}, etc,
     * or an element of {@link #RESOURCE_COLORS}).
     *
     * @param k Kind: {@link #NUMBER}, YES_NO, CHECKBOX, BOUNDED_INC, BOUNDED_DEC
     * @param in interactive flag allowing user interaction
     * @param c background color; creates resource-name tooltip if is a resource color
     * @see #ColorSquare(int, boolean, int, int, Color)
     * @see #ColorSquare(int, boolean, Color, int, int)
     */
    public ColorSquare(int k, boolean in, Color c)
    {
        this(k, in, c, 99, 0);
    }

    /**
     * Creates a new ColorSquare with specified kind, background color, and size;
     * calls {@link #ColorSquare(int, boolean, Color)}.
     *<P>
     * A tooltip with the resource name is created if {@code c} is one of the
     * resource colors defined in ColorSquare ({@link #CLAY}, {@link #WHEAT}, etc,
     * or an element of {@link #RESOURCE_COLORS}).
     *
     * @param k Kind: {@link #NUMBER}, YES_NO, CHECKBOX, BOUNDED_INC, BOUNDED_DEC
     * @param in interactive flag allowing user interaction
     * @param w width in pixels
     * @param h height in pixels
     * @param c background color; creates resource-name tooltip if is a resource color
     * @since 2.0.00
     */
    public ColorSquare(int k, boolean in, int w, int h, Color c)
    {
        this(k, in, c);
        setSizesAndFont(w, h);
    }

    /**
     * Creates a new ColorSquare of the specified kind and background
     * color. Possibly interactive, with upper and lower bounds specified for
     * NUMBER kinds.
     *<P>
     * A tooltip with the resource name is created if {@code c} is one of the
     * resource colors defined in ColorSquare ({@link #CLAY}, {@link #WHEAT}, etc,
     * or an element of {@link #RESOURCE_COLORS}).
     *
     * @param k Kind: NUMBER, YES_NO, CHECKBOX, BOUNDED_INC, BOUNDED_DEC
     * @param in interactive flag allowing user interaction
     * @param c background color; creates resource-name tooltip if is a resource color
     * @param upper upper bound if k == NUMBER
     * @param lower lower bound if k == NUMBER
     */
    public ColorSquare(int k, boolean in, Color c, int upper, int lower)
    {
        super();

        setSize(WIDTH, HEIGHT);
        setMinimumSize(squareSize);
        setPreferredSize(squareSize);
        setFont(new Font("Dialog", Font.PLAIN, 10));

        setOpaque(true);
        setBackground(c);
        setBorder(BorderFactory.createLineBorder(borderColor));

        kind = k;
        interactive = in;
        sqListener = null;

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
            // fallthrough
        case TEXT:
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
            // If needed, can call setToolTipText explicitly.
        }
        else if (c == CLAY)
            setToolTipText(strings.get("resources.clay"));
        else if (c == ORE)
            setToolTipText(strings.get("resources.ore"));
        else if (c == SHEEP)
            setToolTipText(strings.get("resources.sheep"));
        else if (c == WHEAT)
            setToolTipText(strings.get("resources.wheat"));
        else if (c == WOOD)
            setToolTipText(strings.get("resources.wood"));

        if (in)
            addMouseListener(this);
    }

    /**
     * Set minimum and current size of this ColorSquare.
     * If {@code w} or {@code h} != {@link ColorSquare#HEIGHT},
     * also update the font size to fill the square.
     * @param w  New width
     * @param h  New height
     * @since 2.0.00
     */
    protected final void setSizesAndFont(final int w, final int h)
    {
        setSize(w, h);
        setMinimumSize(squareSize);
        final int size = (w < h) ? w : h;
        if (size != ColorSquare.HEIGHT)
            setFont(getFont().deriveFont(10f * (size / (float) ColorSquare.HEIGHT)));
    }

    /**
     * Set the minimum size to be reported by {@link #getMinimumSize()}.
     * Overrides the width and height set by {@link #setSize(int, int)},
     * {@link #setSize(Dimension)}, or {@link #setBounds(int, int, int, int)}.
     * @since 2.0.00
     */
    @Override
    public void setMinimumSize(Dimension d)
    {
        super.setMinimumSize(d);
        minSize = (d != null) ? new Dimension(d) : null;
            // copy w, h values instead of copying a reference that might be squareSize
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

    /**
     * Set this square's border color.
     * @param c  New color; the default is {@link Color#BLACK}
     * @since 1.1.13
     * @throws IllegalArgumentException if c is null
     */
    public void setBorderColor(Color c)
        throws IllegalArgumentException
    {
        if (c == null)
            throw new IllegalArgumentException();
        if (borderColor.equals(c))
            return;

        setBorder(BorderFactory.createLineBorder(c));
        borderColor = c;
    }

    /**
     * Set the width and height of this ColorSquare.
     * Does not need to be a square (w != h is OK).
     * This size will also be returned by {@link #getPreferredSize()}.
     * If {@link #setMinimumSize(Dimension)} has been called,
     * will honor that minimum width and height here.
     * @param w width in pixels
     * @param h height in pixels
     * @see #setMinimumSize(Dimension)
     */
    @Override
    public void setSize(int w, int h)
    {
        if (minSize != null)
        {
            if (w < minSize.width)
                w = minSize.width;
            if (h < minSize.height)
                h = minSize.height;
        }

        squareW = w;
        squareH = h;
        squareSize = new Dimension(w, h);

        super.setSize(w, h);
    }

    /**
     * Set the size of this ColorSquare; overriden to call {@link #setSize(int, int)}.
     * @since 2.0.00
     */
    @Override
    public void setSize(Dimension d)
    {
        if (d != null)
            setSize(d.width, d.height);
    }

    /**
     * Change tooltip text or show or (if null) hide tooltip.
     * Any previously set warning-level or zero-level tooltip text is cleared to null.
     *<P>
     * Before v2.0.00 this method was {@code setTooltipText} with lowercase "tip".
     *
     * @param tip New tip text; will create tooltip if needed.
     *     If tip is null, tooltip is removed.
     *
     * @see #setToolTipHighWarningLevel(String, int)
     * @see #setToolTipLowWarningLevel(String, int)
     * @see #setToolTipZeroText(String)
     */
    @Override
    public void setToolTipText(String tip)
    {
        ttip_text = tip;
        ttip_text_warnLow = null;
        ttip_text_zero = null;

        super.setToolTipText(tip);
    }

    /**
     * Set low-level warning (TODO docu text)
     *
     * @param warnLevel If the colorsquare value is at warnLevel or lower,
     *     indicate with the warning color.
     *
     * @see #clearLowWarningLevel()
     * @see #setToolTipZeroText(String)
     *
     * @throws IllegalArgumentException if warnLevel is above high level, or is zero.
     *     To set text for value 0, use {@link #setToolTipZeroText(String)} instead.
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
                throw new IllegalArgumentException("To set zero text, call setToolTipZeroText instead");
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
                super.setToolTipText(ttip_text_zero);
            else if (ttip_text_warnLow != null)
                super.setToolTipText((isWarnLow) ? ttip_text_warnLow : ttip_text);
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
                    super.setToolTipText(ttip_text_zero);
                else
                    super.setToolTipText(ttip_text);
            }
        }
    }

    /**
     * Set low-level warning, and set or clear its tooltip text.
     * If warnTip not null, we must already have a standard tooltip text.
     * Does not affect zero-level or high-level tooltip text.
     *<P>
     * Before v2.0.00 this method was {@code setTooltipLowWarningLevel} with lowercase "tip".
     *
     * @param warnTip   TODO docu - or null to clear tip text
     * @param warnLevel TODO docu - at or below
     *
     * @see #setHighWarningLevel(int)
     * @see #setLowWarningLevel(int)
     * @see #setToolTipText(String)
     * @see #setToolTipZeroText(String)
     *
     * @throws IllegalStateException if setToolTipText has not yet been called
     *     and warnTip is not null
     *
     * @throws IllegalArgumentException if warnLevel is above high level, or is zero.
     *     To set text for value 0, use {@link #setToolTipZeroText(String)} instead.
     *     To clear the warning level, use {@link #clearLowWarningLevel()} instead.
     */
    public void setToolTipLowWarningLevel(String warnTip, int warnLevel)
        throws IllegalStateException, IllegalArgumentException
    {
        if ((ttip_text == null) && (warnTip != null))
            throw new IllegalStateException("Must call setToolTipText first");
        if (warnLevel == 0)
        {
            if (ttip_text == null)
                throw new IllegalArgumentException("To clear, call clearLowWarningLevel instead");
            else
                throw new IllegalArgumentException("To set zero text, call setToolTipZeroText instead");
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
                super.setToolTipText(ttip_text_zero);  // Revert to zero-level tooltip text
            else if (wasWarnLow)
                super.setToolTipText(ttip_text);  // Revert to non-warning tooltip text
        }
        else if ((warnTip != null) && wasWarnLow && willWarnLow)
        {
            // If the status won't change (we're still at warning level),
            // change the text, because setLowWarningLevel won't.
            // Change text unless we're at zero and there's a zero text.
            if ((intValue != 0) || (ttip_text_zero == null))
                super.setToolTipText(warnTip);
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
                    super.setToolTipText(ttip_text_warnHigh);
                else
                    super.setToolTipText(ttip_text);
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
                super.setToolTipText(ttip_text);
            }
        }
    }

    /**
     * Set high-level warning, and set or clear its tooltip text.
     * If warnTip not null, we must already have a standard tooltip text.
     * Does not affect zero-level or low-level tooltip text.
     *<P>
     * Before v2.0.00 this method was {@code setTooltipHighWarningLevel} with lowercase "tip".
     *
     * @param warnTip   TODO docu - or null to clear tip text
     * @param warnLevel TODO docu - at or above
     *
     * @see #setHighWarningLevel(int)
     * @see #setLowWarningLevel(int)
     * @see #setToolTipText(String)
     *
     * @throws IllegalStateException if setToolTipText has not yet been called
     *     and warnTip is not null
     *
     * @throws IllegalArgumentException if warnLevel is below low-warning level.
     */
    public void setToolTipHighWarningLevel(String warnTip, int warnLevel)
        throws IllegalStateException, IllegalArgumentException
    {
        if ((ttip_text == null) && (warnTip != null))
            throw new IllegalStateException("Must call setToolTipText first");
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
                super.setToolTipText(ttip_text);  // Revert to non-warning tooltip text
        }
        else if ((warnTip != null) && wasWarnHigh && willWarnHigh)
        {
            // If the status won't change (we're still at warning level),
            // change the text, because setHighWarningLevel won't.
            super.setToolTipText(warnTip);
        }

        setHighWarningLevel(warnLevel);  // Remember new warning level
    }

    /**
     * Set or clear zero-level tooltip text.
     * Setting this text will also change the tooltip background color the warning color
     * when at value 0.
     *<P>
     * Before v2.0.00 this method was {@code setTooltipZeroText} with lowercase "tip".
     *
     * @param zeroTip  Text to display only when value is 0,
     *     or {@code null} to not have a separate zero-level-only tip text
     *
     * @see #setToolTipText(String)
     * @see #setToolTipHighWarningLevel(String, int)
     * @see #setToolTipLowWarningLevel(String, int)
     *
     * @throws IllegalStateException if setToolTipText has not yet been called
     *     and zeroTip is not null
     */
    public void setToolTipZeroText(String zeroTip)
        throws IllegalStateException
    {
        if ((ttip_text == null) && (zeroTip != null))
            throw new IllegalStateException("Must call setToolTipText first");

        boolean isZero = (intValue == 0);

        ttip_text_zero = zeroTip;  // Remember new zeroTip text

        // TODO simplify, docu
        if ((zeroTip == null) && (ttip_text != null))
        {
            // No more zeroTip text.
            if (isZero)
            {
                if (hasWarnLow && isWarnLow)
                    super.setToolTipText(ttip_text_warnLow);  // Revert to low-level tooltip text
                else
                    super.setToolTipText(ttip_text);  // Revert to non-warning tooltip text
            }
        }
        else if ((zeroTip != null) && isZero)
        {
            // New zeroTip text. We may have been at low-level or standard tooltip text.
            super.setToolTipText(zeroTip);
        }
    }

    /**
     * Get our preferred size:
     * Default from constructor, or any value passed to {@link #setSize(int, int)}.
     */
    @Override
    public Dimension getPreferredSize()
    {
        return squareSize;
    }

    /**
     * Get our minimum size:
     * Default from constructor, or any value passed to {@link #setMinimumSize(Dimension)}.
     */
    @Override
    public Dimension getMinimumSize()
    {
        return minSize;
    }

    /**
     * Set bounds (position and size).
     * Does not need to be a square (w != h is OK).
     * If {@link #setMinimumSize(Dimension)} has been called,
     * will honor that minimum width and height here.
     * @param x x-position
     * @param y y-position
     * @param w width in pixels
     * @param h height in pixels
     *
     * @since 1.1.06
     * @see java.awt.Component#setBounds(int, int, int, int)
     */
    public void setBounds(int x, int y, int w, int h)
    {
        if (minSize != null)
        {
            if (w < minSize.width)
                w = minSize.width;
            if (h < minSize.height)
                h = minSize.height;
        }

        squareW = w;
        squareH = h;
        if (squareSize != null)
        {
            squareSize.width = w;
            squareSize.height = h;
        }

        super.setBounds(x, y, w, h);
    }

    /**
     * Set bounds (position and size). Overrides to call {@link #setBounds(int, int, int, int)}.
     * @since 2.0.00
     */
    @Override
    public void setBounds(Rectangle r)
    {
        if (r != null)
            setBounds(r.x, r.y, r.width, r.height);
    }

    /**
     * Paint our contents.
     *<P>
     * Before v2.0.00 and its Swing conversion, this method was {@code paint}.
     */
    public void paintComponent(Graphics g)
    {
            super.paintComponent(g);

            g.setPaintMode();
            if (warn_bg_grey && (isWarnLow || isWarnHigh))
                g.setColor(WARN_LEVEL_COLOR_BG_FROMGREY);
            else
                g.setColor(getBackground());
            g.fillRect(0, 0, squareW, squareH);

            int x;
            int y;

            if (valueVis)
            {
                if (isWarnLow || isWarnHigh)
                    g.setColor(WARN_LEVEL_COLOR);
                else
                    g.setColor(Color.BLACK);

                FontMetrics fm = this.getFontMetrics(this.getFont());
                int numW;
                int numH = fm.getHeight();
                //int numA = fm.getAscent();

                switch (kind)
                {
                case NUMBER:
                case BOUNDED_INC:
                case BOUNDED_DEC:
                case TEXT:
                    {
                        String valstring;
                        if (kind != TEXT)
                            valstring = Integer.toString(intValue);
                        else
                            valstring = textValue;

                        numW = fm.stringWidth(valstring);

                        x = (squareW - numW) / 2;

                        // y = numA + (HEIGHT - numH) / 2; // proper way
                        // y = 12; // way that works
                        y = (squareH + ((int)(.6 * numH))) / 2;  // Semi-proper

                        g.drawString(valstring, x, y);
                    }
                    break;

                case YES_NO:
                    String value = (boolValue ? strings.get("abbr.yes") : strings.get("abbr.no"));

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
            super.setToolTipText(ttip_text_zero);
        }
        else if ((ttip_text != null) &&
            ((isZero != wasZero) || (isWarnLow != wasWarnLow) || (isWarnHigh != wasWarnHigh)))
        {
            if (isWarnHigh && (ttip_text_warnHigh != null))
                super.setToolTipText(ttip_text_warnHigh);
            else if (isWarnLow && (ttip_text_warnLow != null))
                super.setToolTipText(ttip_text_warnLow);
            else
                super.setToolTipText(ttip_text);
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
