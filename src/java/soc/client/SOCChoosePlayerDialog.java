/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2010 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;
import soc.game.SOCPlayer;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


/**
 * This is the dialog to ask a player from whom she wants to steal.
 *
 * @author  Robert S. Thomas
 */
class SOCChoosePlayerDialog extends Dialog implements ActionListener
{
    /** Player names on each button */
    Button[] buttons;

    /** Player index of each to choose */    
    int[] players;

    /** Show Count of resources of each player */
    Label[] player_res_lbl;

    int number;
    Label msg;
    SOCPlayerInterface pi;

    /** Desired size (visible size inside of insets) **/
    protected int wantW, wantH;

    /**
     * Place window in center when displayed (in doLayout),
     * don't change position afterwards
     */
    boolean didSetLocation;

    /**
     * Creates a new SOCChoosePlayerDialog object.
     *
     * @param plInt DOCUMENT ME!
     * @param num DOCUMENT ME!
     * @param p DOCUMENT ME!
     */
    public SOCChoosePlayerDialog(SOCPlayerInterface plInt, int num, int[] p)
    {
        super(plInt, "Choose Player", true);

        pi = plInt;
        number = num;
        players = p;
        setBackground(new Color(255, 230, 162));
        setForeground(Color.black);
        setFont(new Font("Geneva", Font.PLAIN, 12));
        didSetLocation = false;
        setLayout(null);
        // wantH formula based on doLayout
        //    label: 20  button: 20  label: 16  spacing: 10
        wantW = 320;
        wantH = 20 + 10 + 20 + 10 + 16 + 5;
        setSize(wantW + 10, wantH + 20);  // Can calc & add room for insets at doLayout

        msg = new Label("Please choose a player to steal from:", Label.CENTER);
        add(msg);

        buttons = new Button[number];
        player_res_lbl = new Label[number];

        SOCGame ga = pi.getGame();

        for (int i = 0; i < number; i++)
        {
            SOCPlayer pl = ga.getPlayer(players[i]);            

            buttons[i] = new Button(pl.getName());
            add(buttons[i]);
            buttons[i].addActionListener(this);

            final int rescount = pl.getResources().getTotal();
            final int vpcount = pl.getPublicVP();
            player_res_lbl[i] = new Label(rescount + " res, " + vpcount + " VP", Label.CENTER);
            SOCHandPanel ph = pi.getPlayerHandPanel(players[i]);
            player_res_lbl[i].setBackground(ph.getBackground());
            player_res_lbl[i].setForeground(ph.getForeground());
            add(player_res_lbl[i]);
            String restooltip;
            switch (rescount)
            {
            case 0:
                restooltip = "This player has no resources.";
                break;

            case 1:
                restooltip = "This player has 1 resource.";
                break;

            default:
                restooltip = "This player has " + rescount + " resources.";
            }
            new AWTToolTip(restooltip, player_res_lbl[i]);
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
            buttons[0].requestFocus();
        }
    }

    /**
     * DOCUMENT ME!
     */
    public void doLayout()
    {
        int x = getInsets().left;
        int y = getInsets().top;
        int padW = getInsets().left + getInsets().right;
        int padH = getInsets().top + getInsets().bottom;
        int width = getSize().width - padW;
        int height = getSize().height - padH;

        /* check visible-size vs insets */
        if ((width < wantW + padW) || (height < wantH + padH))
        {
            if (width < wantW + padW)
                width = wantW + 1;
            if (height < wantH + padH)
                height = wantH + 1;
            setSize (width + padW, height + padH);
            width = getSize().width - padW;
            height = getSize().height - padH;
        }

        int space = 10;
        int bwidth = (width - ((number - 1 + 2) * space)) / number;

        /* put the dialog in the top-center of the game window */
        if (! didSetLocation)
        {
            int piX = pi.getInsets().left;
            int piY = pi.getInsets().top;
            final int piWidth = pi.getSize().width - piX - pi.getInsets().right;
            piX += pi.getLocation().x;
            piY += pi.getLocation().y;
            setLocation(piX + ((piWidth - width) / 2), piY + 50);
            didSetLocation = true;
        }

        try
        {
            msg.setBounds(x, y, width, 20);

            for (int i = 0; i < number; i++)
            {
                buttons[i].setBounds(x + space + (i * (bwidth + space)), (getInsets().top + height) - (20 + space + 16 + 5), bwidth, 20);
                player_res_lbl[i].setBounds(x + space + (i * (bwidth + space)), (getInsets().top + height) - (16 + 5), bwidth, 16);
            }
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

        for (int i = 0; i < number; i++)
        {
            if (target == buttons[i])
            {
                pi.getClient().choosePlayer(pi.getGame(), players[i]);
                dispose();

                break;
            }
        }
        } catch (Throwable th) {
            pi.chatPrintStackTrace(th);
        }
    }
}
