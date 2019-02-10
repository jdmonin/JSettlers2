/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012-2014,2018-2019 Jeremy D Monin <jeremy@nand.net>
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.WindowConstants;


@SuppressWarnings("serial")
class SOCMonopolyDialog extends JDialog implements ActionListener, Runnable
{
    final JButton[] rsrcBut;
    /** Prompt message. Text alignment is centered. */
    final JLabel msg;
    final SOCPlayerInterface pi;

    /** i18n text strings; will use same locale as SOCPlayerClient's string manager.
     *  @since 2.0.00 */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /**
     * Creates a new SOCMonopolyDialog object.
     *
     * @param pi Parent window
     */
    public SOCMonopolyDialog(SOCPlayerInterface pi)
    {
        super(pi, strings.get("spec.dcards.monopoly"), true);  // "Monopoly"

        this.pi = pi;

        final JRootPane rpane = getRootPane();
        final Container cpane = getContentPane();

        rpane.setBorder(BorderFactory.createEmptyBorder(5, 20, 20, 20));
        rpane.setBackground(SOCPlayerInterface.DIALOG_BG_GOLDENROD);
        rpane.setForeground(Color.BLACK);
        cpane.setBackground(null);  // inherit from parent
        cpane.setForeground(null);

        msg = new JLabel(strings.get("dialog.mono.please.pick.resource"), JLabel.CENTER);
            // "Please pick a resource to monopolize."
        add(msg, BorderLayout.PAGE_START);  // NORTH

        // The actual content of this dialog is btnsPane, a narrow stack of 5 rows, 1 per resource type.
        // Each row has 1 resource type's colorsquare and a button with its name.
        // This stack of rows is centered horizontally in the larger container,
        // and doesn't fill the entire width. Since the content pane's BorderLayout wants to
        // stretch things to fill its center, to leave space on the left and right
        // we wrap btnsPane in a larger btnsContainer ordered horizontally.

        final JPanel btnsContainer = new JPanel();
        btnsContainer.setLayout(new BoxLayout(btnsContainer, BoxLayout.X_AXIS));
        btnsContainer.setBackground(null);
        btnsContainer.setForeground(null);

        // In center of btnContainer, the stack of resource buttons:
        final GridBagLayout gbl = new GridBagLayout();
        final GridBagConstraints gbc = new GridBagConstraints();
        final JPanel btnsPane = new JPanel(gbl)
        {
            /**
             * Override to prevent some unwanted extra width, because default max is 32767 x 32767
             * and parent's BoxLayout adds some proportion of that, based on its overall container width
             * beyond btnsPane's minimum/preferred width.
             */
            public Dimension getMaximumSize() { return getPreferredSize(); }
        };
        btnsPane.setBackground(null);
        btnsPane.setForeground(null);
        btnsPane.setAlignmentX(CENTER_ALIGNMENT);  // center btnsPane within entire content pane
        btnsPane.setBorder
            (BorderFactory.createEmptyBorder(9, 0, 0, 0));  // space between prompt label and resource rows

        rsrcBut = new JButton[5];
        final String[] rsrcStr
            = { "resources.clay", "resources.ore", "resources.sheep", "resources.wheat", "resources.wood" };
        for (int i = 0; i < 5; ++i)
        {
            // Need to use the wrong color, then change it, in order to
            // not use AWTToolTips (redraw problem when in a JDialog).
            // See also SOCDiscardOrGainResDialog which uses the same tooltip-avoidance code.
            final Color sqColor = ColorSquare.RESOURCE_COLORS[i];

            ColorSquareLarger sq = new ColorSquareLarger(Color.WHITE);
            sq.setBackground(sqColor);

            JButton b = new JButton(strings.get(rsrcStr[i]));
            b.setBackground(null);  // needed to avoid gray corners on win32
            b.addActionListener(this);
            rsrcBut[i] = b;

            // add to layout; stretch buttons so they all have the same width, but don't stretch colorsquare

            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.NONE;
            gbc.gridwidth = 1;
            gbl.setConstraints(sq, gbc);
            btnsPane.add(sq);

            gbc.anchor = GridBagConstraints.LINE_START;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(b, gbc);
            btnsPane.add(b);
        }

        btnsContainer.add(Box.createHorizontalGlue());
        btnsContainer.add(btnsPane);
        btnsContainer.add(Box.createHorizontalGlue());

        add(btnsContainer, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(pi);  // will center dialog in game window

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) { }  // don't close
        });
    }

    /**
     * Set this dialog visible or hide it. If visible, request focus on the first resource button.
     */
    public void setVisible(boolean b)
    {
        super.setVisible(b);

        if (b)
            rsrcBut[0].requestFocus();
    }

    /**
     * Handle resource button clicks.
     */
    public void actionPerformed(ActionEvent e)
    {
        try {
        Object target = e.getSource();

        for (int i = 0; i < 5; i++)
        {
            if (target == rsrcBut[i])
            {
                /**
                 * Note: This only works if SOCResourceConstants.CLAY == 1 and so on, in same order as rsrcBut buttons
                 */
                pi.getClient().getGameManager().pickResourceType(pi.getGame(), i + 1);
                dispose();

                break;
            }
        }
        } catch (Throwable th) {
            pi.chatPrintStackTrace(th);
        }
    }

    /**
     * Run method, for convenience with {@link java.awt.EventQueue#invokeLater(Runnable)}.
     * This method just calls {@link #setVisible(boolean) setVisible(true)}.
     * @since 2.0.00
     */
    public void run()
    {
        setVisible(true);
    }

}
