/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2009 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCResourceSet;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


/**
 * Dialog asking player to pick two resources
 * when playing a discovery card.
 */
class SOCDiscoveryDialog extends Dialog implements ActionListener, ColorSquareListener
{
    /** Pick (Done) button */
    private Button doneBut;
    /** Clear button */
    private Button clearBut;
    private ColorSquare[] rsrc;
    private Label msg;
    private SOCPlayerInterface pi;
    /** Total number of resources clicked */
    private int rsrcTotal;

    /**
     * Creates a new SOCDiscoveryDialog object.
     *
     * @param pi  the interface that this panel is a part of
     */
    public SOCDiscoveryDialog(SOCPlayerInterface pi)
    {
        super(pi, "Year of Plenty", true);

        rsrcTotal = 0;

        this.pi = pi;
        setBackground(new Color(255, 230, 162));
        setForeground(Color.black);
        setFont(new Font("Geneva", Font.PLAIN, 12));

        doneBut = new Button("Pick");
        clearBut = new Button("Clear");

        setLayout(null);
        addNotify();

        msg = new Label("Please pick two resources.", Label.CENTER);
        add(msg);

        setSize(280, 60 + 3 * ColorSquareLarger.HEIGHT_L);

        add(doneBut);
        doneBut.addActionListener(this);
        doneBut.setEnabled(false);  // Since nothing picked yet

        add(clearBut);
        clearBut.addActionListener(this);
        // clearBut.disable();

        rsrc = new ColorSquare[5];
        for (int i = 0; i < 5; i++)
        {
            // On OSX: We must use the wrong color, then change it, in order to
            // not use AWTToolTips (redraw problem for button enable/disable).
            Color sqColor;
            if (SOCPlayerClient.isJavaOnOSX)
                sqColor = Color.WHITE;
            else
                sqColor = ColorSquare.RESOURCE_COLORS[i];

            rsrc[i] = new ColorSquareLarger(ColorSquare.BOUNDED_INC, true, sqColor, 2, 0);
            if (SOCPlayerClient.isJavaOnOSX)
            {
                rsrc[i].setBackground(ColorSquare.RESOURCE_COLORS[i]);
            }
            add(rsrc[i]);
            rsrc[i].setSquareListener(this);
        }
    }

    /**
     * When dialog becomes visible, set focus to the "Clear" button.
     *
     * @param b Visible?
     */
    @Override
    public void setVisible(boolean b)
    {
        super.setVisible(b);

        if (b)
        {
            clearBut.requestFocus();
        }
    }

    /**
     * Custom layout for this dialog
     */
    @Override
    public void doLayout()
    {
        int x = getInsets().left;
        int y = getInsets().top;
        int width = getSize().width - getInsets().left - getInsets().right;
        int height = getSize().height - getInsets().top - getInsets().bottom;
        int space = 5;

        // Account for bottom inset (browser applet warning message)
        if (height < (35 + 3 * ColorSquareLarger.HEIGHT_L))
        {
            int insetPad = getInsets().top + getInsets().bottom;
            // force taller
            height = 3 + 35 + 3 * ColorSquareLarger.HEIGHT_L + insetPad;
            setSize (getSize().width, height);
            // adj for further calcs
            height = getSize().height - insetPad;
        }

        int pix = pi.getInsets().left;
        int piy = pi.getInsets().top;
        int piwidth = pi.getSize().width - pi.getInsets().left - pi.getInsets().right;
        int piheight = pi.getSize().height - pi.getInsets().top - pi.getInsets().bottom;

        int sqwidth = ColorSquareLarger.WIDTH_L;
        int sqspace = (width - (5 * sqwidth)) / 6;

        int buttonW = 80;
        int buttonX = (width - ((2 * buttonW) + space)) / 2;
        int rsrcY;

        /* put the dialog in the center of the game window */
        setLocation(pix + ((piwidth - width) / 2), piy + ((piheight - height) / 2));

        if (msg != null)
        {
            int msgW = this.getFontMetrics(this.getFont()).stringWidth(msg.getText());
            msg.setBounds((width - msgW) / 2, getInsets().top, msgW + 4, 20);
        }

        if (clearBut != null)
        {
            clearBut.setBounds(x + buttonX, getSize().height - getInsets().bottom - 30, buttonW, 25);
        }

        if (doneBut != null)
        {
            doneBut.setBounds(x + buttonX + buttonW + space, getSize().height - getInsets().bottom - 30, buttonW, 25);
        }

        try
        {
            rsrcY = y + ColorSquareLarger.HEIGHT_L + 2 * space;

            for (int i = 0; i < 5; i++)
            {
                rsrc[i].setSize(sqwidth, sqwidth);
                rsrc[i].setLocation(x + sqspace + (i * (sqspace + sqwidth)), rsrcY);
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

        if (target == doneBut)
        {
            int[] rsrcCnt = new int[5];
            int i;
            int sum = 0;

            for (i = 0; i < 5; i++)
            {
                rsrcCnt[i] = rsrc[i].getIntValue();
                sum += rsrcCnt[i];
            }

            if (sum == 2)
            {
                SOCResourceSet resources = new SOCResourceSet(rsrcCnt);
                pi.getClient().getGameManager().discoveryPick(pi.getGame(), resources);
                dispose();
            }
        }
        else if (target == clearBut)
        {
            for (int i = 0; i < 5; i++)
            {
                rsrc[i].setIntValue(0);
            }
            rsrcTotal = 0;
            doneBut.setEnabled(false);
        }
        } catch (Throwable th) {
            pi.chatPrintStackTrace(th);
        }
    }

    /**
     * Called by colorsquare when clicked; potentially
     * enable/disable Done button, based on new value.
     */
    public void squareChanged(ColorSquare sq, int oldValue, int newValue)
    {
        boolean wasDone = (rsrcTotal == 2);
        rsrcTotal += (newValue - oldValue);
        boolean isDone = (rsrcTotal == 2);
        if (wasDone != isDone)
            doneBut.setEnabled(isDone);
    }
}
