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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;


@SuppressWarnings("serial")
/*package*/ class SOCMonopolyDialog
    extends SOCDialog implements ActionListener
{
    final JButton[] rsrcBut;

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
        super(pi, strings.get("spec.dcards.monopoly"), strings.get("dialog.mono.please.pick.resource"), false);
            // title: "Monopoly"  prompt: "Please pick a resource to monopolize."

        getRootPane().setBorder(BorderFactory.createEmptyBorder(5, 20, 20, 20));

        // The actual content of this dialog is btnsPane, a narrow stack of 5 rows, 1 per resource type.
        // Each row has 1 resource type's colorsquare and a button with its name.
        // This stack of rows is centered horizontally in a larger container,
        // and doesn't fill the entire width.

        JPanel btnsPane = getMiddlePanel();
        final GridBagLayout gbl = new GridBagLayout();
        final GridBagConstraints gbc = new GridBagConstraints();
        btnsPane.setLayout(gbl);

        rsrcBut = new JButton[5];
        final String[] rsrcStr
            = { "resources.clay", "resources.ore", "resources.sheep", "resources.wheat", "resources.wood" };
        final boolean isPlatformWindows = SOCPlayerClient.IS_PLATFORM_WINDOWS;
        for (int i = 0; i < 5; ++i)
        {
            ColorSquareLarger sq = new ColorSquareLarger(ColorSquare.RESOURCE_COLORS[i]);

            JButton b = new JButton(strings.get(rsrcStr[i]));
            if (isPlatformWindows)
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

        pack();
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
                 * Note: This works because SOCResourceConstants.CLAY == 1 and so on, in same order as rsrcBut buttons
                 */
                playerInterface.getClient().getGameMessageMaker().pickResourceType(playerInterface.getGame(), i + 1);
                dispose();

                break;
            }
        }
        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }

}
