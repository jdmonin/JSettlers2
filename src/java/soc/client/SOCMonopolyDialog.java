/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.client;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


class SOCMonopolyDialog extends Dialog implements ActionListener
{
    Button[] rsrcBut;
    Label msg;
    SOCPlayerInterface pi;

    /**
     * Creates a new SOCMonopolyDialog object.
     *
     * @param pi DOCUMENT ME!
     */
    public SOCMonopolyDialog(SOCPlayerInterface pi)
    {
        super(pi, "Monopoly", true);

        this.pi = pi;
        setBackground(new Color(255, 230, 162));
        setForeground(Color.black);
        setFont(new Font("Geneva", Font.PLAIN, 12));
        setLayout(null);
        addNotify();
        setSize(280, 160);

        msg = new Label("Please pick a resource to monopolize.", Label.CENTER);
        add(msg);

        rsrcBut = new Button[5];

        rsrcBut[0] = new Button("Clay");
        rsrcBut[1] = new Button("Ore");
        rsrcBut[2] = new Button("Sheep");
        rsrcBut[3] = new Button("Wheat");
        rsrcBut[4] = new Button("Wood");

        for (int i = 0; i < 5; i++)
        {
            add(rsrcBut[i]);
            rsrcBut[i].addActionListener(this);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param b DOCUMENT ME!
     */
    public void setVisible(boolean b)
    {
        super.setVisible(b);

        if (b)
        {
            rsrcBut[0].requestFocus();
        }
    }

    /**
     * DOCUMENT ME!
     */
    public void doLayout()
    {
        int width = getSize().width - getInsets().left - getInsets().right;
        int height = getSize().height - getInsets().top - getInsets().bottom;
        int space = 5;

        int pix = pi.getInsets().left;
        int piy = pi.getInsets().top;
        int piwidth = pi.getSize().width - pi.getInsets().left - pi.getInsets().right;
        int piheight = pi.getSize().height - pi.getInsets().top - pi.getInsets().bottom;

        int buttonW = 60;
        int button2X = (width - ((2 * buttonW) + space)) / 2;
        int button3X = (width - ((3 * buttonW) + (2 * space))) / 2;

        /* put the dialog in the center of the game window */
        setLocation(pix + ((piwidth - width) / 2), piy + ((piheight - height) / 2));

        try
        {
            msg.setBounds((width - 188) / 2, getInsets().top, 210, 20);
            rsrcBut[0].setBounds(button2X, (getInsets().bottom + height) - (50 + (2 * space)), buttonW, 25);
            rsrcBut[1].setBounds(button2X + buttonW + space, (getInsets().bottom + height) - (50 + (2 * space)), buttonW, 25);
            rsrcBut[2].setBounds(button3X, (getInsets().bottom + height) - (25 + space), buttonW, 25);
            rsrcBut[3].setBounds(button3X + space + buttonW, (getInsets().bottom + height) - (25 + space), buttonW, 25);
            rsrcBut[4].setBounds(button3X + (2 * (space + buttonW)), (getInsets().bottom + height) - (25 + space), buttonW, 25);
        }
        catch (NullPointerException e) {}
    }

    /**
     * DOCUMENT ME!
     *
     * @param e DOCUMENT ME!
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
                 * Note: This only works if SOCResourceConstants.CLAY == 1
                 */
                pi.getClient().monopolyPick(pi.getGame(), i + 1);
                dispose();

                break;
            }
        }
        } catch (Throwable th) {
            pi.chatPrintStackTrace(th);
        }
    }
}
