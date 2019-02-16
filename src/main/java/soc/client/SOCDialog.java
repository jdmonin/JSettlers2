/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
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
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.LayoutManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Base modal dialog class to consistently handle basic Swing setup.
 * Sets up its {@link #getContentPane()} with a {@link BorderLayout} having
 * a prompt at the top, a center "middle panel" that can be centered instead of
 * stretched to fill the entire {@link BorderLayout#CENTER}, and an optional
 * button panel centered horizontally at the bottom.
 *<P>
 * Uses JSettlers dialog colors of black on {@link SOCPlayerClient#DIALOG_BG_GOLDENROD}
 * with default dialog font, increased to 12 points if default is smaller.
 * The root pane is given an empty border of 8 pixels.
 *<P>
 * Useful methods and fields:
 *<UL>
 * <LI> {@link #getMiddlePanel()}
 * <LI> {@link #getSouthPanel()}
 * <LI> {@link #styleButtonsAndLabels(Container)}
 * <LI> {@link #makeJPanel(Font)}
 * <LI> {@link #playerInterface}
 *</UL>
 *
 * Like most GUI classes, SOCDialog is not thread-safe.
 *<P>
 * Public for possible use by anyone extending JSettlers in a different package.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
@SuppressWarnings("serial")
public abstract class SOCDialog
    extends JDialog implements Runnable
{
    /** Player interface that's the parent of this dialog, or {@code null}, as passed to constructor */
    protected final SOCPlayerInterface playerInterface;

    /** Optional component filling {@link BorderLayout#NORTH} with prompt text, or {@code null} if none. */
    private JComponent northComponent;

    /**
     * Centered panel in {@link BorderLayout#CENTER} with all other contents of dialog.
     * Optionally may be stretched to fill entire {@code CENTER}.
     */
    private final JPanel middlePanel;

    /**
     * Optional centered panel filling {@link BorderLayout#SOUTH}, typically contains response buttons.
     * Is {@code null} until {@link #getSouthPanel()} is called.
     */
    private JPanel southPanel;

    /**
     * Create a {@link SOCDialog} for caller to customize further.
     * After creation, caller should call {@link #getMiddlePanel()} and maybe {@link #getSouthPanel()}.
     * To pack and show the dialog, either call {@link #run()} when in the AWT event dispatch thread,
     * or call {@link java.awt.EventQueue#invokeLater(Runnable) EventQueue.invokeLater(socDialog)}
     * from any other thread.
     * @param piParent  Parent of this dialog, or {@code null}. Dialog will be centered in {@code piParent}.
     * @param titleText  Text to place into dialog window's title bar
     * @param promptText  Prompt text to place at top of dialog, or {@code null}
     * @param middleFillsCenter  True if constructor should stretch {@link #getMiddlePanel()}'s JPanel
     *     to fill the entire {@link BorderLayout#CENTER} portion of the content pane;
     *     false to center it horizontally and vertically without stretching
     */
    protected SOCDialog
        (final SOCPlayerInterface piParent, final String titleText, final String promptText, boolean middleFillsCenter)
    {
        super(piParent, titleText, true);  // gets a BorderLayout from JDialog's default content pane

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);  // user must take action
        playerInterface = piParent;
        if (piParent != null)
            setLocationRelativeTo(piParent);  // center

        Font panelFont = getFont();
        if (panelFont.getSize() < 12)
        {
            panelFont = panelFont.deriveFont(12f);
            setFont(panelFont);
        }

        final JRootPane rpane = getRootPane();
        rpane.setBackground(SOCPlayerInterface.DIALOG_BG_GOLDENROD);
        rpane.setForeground(Color.BLACK);
        rpane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        Container cpane = getContentPane();
        if (! (cpane instanceof JPanel))
        {
            cpane = new JPanel();
            setContentPane(cpane);
        }
        cpane.setBackground(null);  // inherit from parent/rootpane
        cpane.setForeground(null);
        cpane.setFont(panelFont);

        if (promptText != null)
        {
            northComponent = new JLabel(promptText, SwingConstants.CENTER);
            northComponent.setForeground(null);  // inherit from panel
            northComponent.setFont(panelFont);
            northComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));  // margin between north & middle panel
            add(northComponent, BorderLayout.NORTH);
        }

        if (middleFillsCenter)
        {
            middlePanel = makeJPanel(panelFont);
            add(middlePanel, BorderLayout.CENTER);
        } else {
            // middlePanel, the actual content of this dialog, gets centered horizontally in the larger container.
            // Since the content pane's BorderLayout wants to stretch things to fill its center,
            // to leave space on the left and right we wrap it in a wider wrapperContainer.

            final JPanel wrapperContainer = new JPanel();
            wrapperContainer.setLayout(new BoxLayout(wrapperContainer, BoxLayout.X_AXIS));
            wrapperContainer.setBackground(null);
            wrapperContainer.setForeground(null);

            middlePanel = new JPanel()
            {
                /**
                 * Override to prevent some unwanted extra width, because default max is 32767 x 32767
                 * and parent's BoxLayout adds some proportion of that, based on its overall container width
                 * beyond btnsPane's minimum/preferred width.
                 */
                public Dimension getMaximumSize() { return getPreferredSize(); }
            };
            middlePanel.setBackground(null);
            middlePanel.setForeground(null);
            middlePanel.setFont(panelFont);
            middlePanel.setAlignmentX(CENTER_ALIGNMENT);  // within entire content pane

            wrapperContainer.add(Box.createHorizontalGlue());
            wrapperContainer.add(middlePanel);
            wrapperContainer.add(Box.createHorizontalGlue());

            add(wrapperContainer, BorderLayout.CENTER);

        }

        // southPanel init, if any, happens later when getSouthPanel() is called
    }

    /**
     * Get the centered panel in {@link BorderLayout#CENTER} with all other contents of dialog.
     * Is stretched to fill entire {@code CENTER} if that flag was passed to constructor.
     *<P>
     * You are free to add any type of component in here and change the layout manager.
     * After adding all buttons and labels, you can call {@link #styleButtonsAndLabels(Container)}.
     */
    protected JPanel getMiddlePanel()
    {
        return middlePanel;
    }

    /**
     * Get the optional southern button panel, centered horizontally at the bottom.
     * If none exists, will create it and add to content pane.
     *<P>
     * This panel's default {@link FlowLayout} gives a 4-pixel gap between components,
     * and its default top border creates a 16-pixel margin between the middle and south panels.
     * You are free to add any type of component in here, not only buttons, and change the layout manager.
     * After adding all buttons and labels, you can call {@link #styleButtonsAndLabels(Container)}.
     * You might also want to call {@link JRootPane#setDefaultButton(JButton) getRootPane().setDefaultButton(...)}.
     *
     * @return the optional south panel
     */
    protected JPanel getSouthPanel()
    {
        if (southPanel == null)
        {
            southPanel = makeJPanel(new FlowLayout(FlowLayout.CENTER, 4, 0), getFont());  // horiz border & gap 4 pixels
            southPanel.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));  // margin between south & middle panels
            southPanel.setAlignmentX(CENTER_ALIGNMENT);
            add(southPanel, BorderLayout.SOUTH);
        }

        return southPanel;
    }

    /**
     * Make a new JPanel with our colors and font, using its default {@link FlowLayout}.
     * Foreground and background color are set to {@code null} to inherit from parent.
     * @param panelFont Font to optionally set
     * @return new JPanel, with default {@link FlowLayout} from calling <tt>new {@link JPanel#JPanel()}</tt>
     * @see #makeJPanel(LayoutManager, Font)
     */
    protected final JPanel makeJPanel(final Font panelFont)
    {
        return makeJPanel(null, panelFont);
    }

    /**
     * Make a new JPanel with our colors and font, and the specified layout manager.
     * Foreground and background color are set to {@code null} to inherit from parent.
     * @param lm  Layout manager to use, or {@code null} for default
     * @param panelFont Font to optionally set
     * @return new JPanel, with specified layout manager from calling <tt>new {@link JPanel#JPanel(LayoutManager)}</tt>
     * @see #makeJPanel(Font)
     */
    protected final JPanel makeJPanel(final LayoutManager lm, final Font panelFont)
    {
        final JPanel p = ((lm == null) ? new JPanel() : new JPanel(lm));
        p.setBackground(null);  // inherit from parent
        p.setForeground(null);
        if (panelFont != null)
            p.setFont(panelFont);

        return p;
    }

    /**
     * Convenience method to make all {@link JLabel}s and {@link JButton}s
     * in a container use its font and background color.
     * @param c  Container such as a {@link JPanel}
     */
    public final static void styleButtonsAndLabels(final Container c)
    {
        final Font panelFont = c.getFont();
        for (Component co : c.getComponents())
        {
            if (! ((co instanceof JLabel) || (co instanceof JButton)))
                continue;

            if (co instanceof JLabel)
            {
                co.setFont(panelFont);
                co.setForeground(null);  // inherit panel's color
            }

            co.setBackground(null);  // inherit panel's bg color; required for win32 to avoid gray corners on JButton
        }

    }

    /**
     * Run method, for convenience with {@link java.awt.EventQueue#invokeLater(Runnable)}.
     * Calls {@link #pack()} and {@link #setVisible(boolean) setVisible(true)}.
     */
    public void run()
    {
        pack();
        setVisible(true);
    }

}