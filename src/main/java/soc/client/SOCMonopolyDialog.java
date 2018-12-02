/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012-2014,2018 Jeremy D Monin <jeremy@nand.net>
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

import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JRootPane;


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
        rpane.setBackground(new Color(255, 230, 162));
        rpane.setForeground(Color.black);
        cpane.setBackground(null);  // inherit from parent
        cpane.setForeground(null);

        cpane.setLayout(new GridLayout(6, 1, 10, 10));  // label + 1 row per button

        msg = new JLabel(strings.get("dialog.mono.please.pick.resource"), JLabel.CENTER);
            // "Please pick a resource to monopolize."
        add(msg);

        rsrcBut = new JButton[5];

        rsrcBut[0] = new JButton(strings.get("resources.clay"));   // "Clay"
        rsrcBut[1] = new JButton(strings.get("resources.ore"));    // "Ore"
        rsrcBut[2] = new JButton(strings.get("resources.sheep"));  // "Sheep"
        rsrcBut[3] = new JButton(strings.get("resources.wheat"));  // "Wheat"
        rsrcBut[4] = new JButton(strings.get("resources.wood"));   // "Wood"

        for (int i = 0; i < 5; i++)
        {
            add(rsrcBut[i]);
            rsrcBut[i].addActionListener(this);
        }

        pack();
        setLocationRelativeTo(pi);  // will center dialog in game window
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
