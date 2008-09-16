/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2008 Jeremy D. Monin <jeremy@nand.net>
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

import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;


/**
 * This is the dialog to ask players what resources they want
 * to discard.
 *
 * @author  Robert S. Thomas
 */
class SOCDiscardDialog extends Dialog implements ActionListener, MouseListener
{
    Button discardBut;
    ColorSquare[] keep;
    ColorSquare[] disc;
    Label msg;
    Label youHave;
    Label discThese;
    SOCPlayerInterface playerInterface;
    int numDiscards;
    int numChosen;  // Button disabled unless proper number of resources are chosen

    /** Desired size (visible size inside of insets) **/
    protected int wantW, wantH;

    /**
     * Place window in center when displayed (in doLayout),
     * don't change position afterwards
     */
    boolean didSetLocation;

    /**
     * Creates a new SOCDiscardDialog object.
     *
     * @param pi   Client's player interface
     * @param rnum Player must dicard this many resources
     */
    public SOCDiscardDialog(SOCPlayerInterface pi, int rnum)
    {
        super(pi, "Discard [" + pi.getClient().getNickname() + "]", true);

        playerInterface = pi;
        numDiscards = rnum;
        numChosen = 0;
        setBackground(new Color(255, 230, 162));
        setForeground(Color.black);
        setFont(new Font("Geneva", Font.PLAIN, 12));

        discardBut = new Button("Discard");

        didSetLocation = false;
        setLayout(null);

        msg = new Label("Please discard " + Integer.toString(numDiscards) + " resources.", Label.CENTER);
        add(msg);
        youHave = new Label("You have:", Label.LEFT);
        add(youHave);
        discThese = new Label("Discard these:", Label.LEFT);
        add(discThese);

        // wantH formula based on doLayout
        //    labels: 20  colorsq: 20  button: 25  spacing: 5
        wantW = 270;
        wantH = 20 + 5 + (2 * (20 + 5 + 20 + 5)) + 25 + 5;
        setSize(wantW + 10, wantH + 20);  // Can calc & add room for insets at doLayout

        add(discardBut);
        discardBut.addActionListener(this);
        if (numDiscards > 0)
            discardBut.disable();  // Must choose that many first

        keep = new ColorSquare[5];
        keep[0] = new ColorSquareLarger(ColorSquare.BOUNDED_DEC, false, ColorSquare.CLAY);
        keep[1] = new ColorSquareLarger(ColorSquare.BOUNDED_DEC, false, ColorSquare.ORE);
        keep[2] = new ColorSquareLarger(ColorSquare.BOUNDED_DEC, false, ColorSquare.SHEEP);
        keep[3] = new ColorSquareLarger(ColorSquare.BOUNDED_DEC, false, ColorSquare.WHEAT);
        keep[4] = new ColorSquareLarger(ColorSquare.BOUNDED_DEC, false, ColorSquare.WOOD);

        disc = new ColorSquare[5];
        disc[0] = new ColorSquareLarger(ColorSquare.BOUNDED_INC, false, ColorSquare.CLAY);
        disc[1] = new ColorSquareLarger(ColorSquare.BOUNDED_INC, false, ColorSquare.ORE);
        disc[2] = new ColorSquareLarger(ColorSquare.BOUNDED_INC, false, ColorSquare.SHEEP);
        disc[3] = new ColorSquareLarger(ColorSquare.BOUNDED_INC, false, ColorSquare.WHEAT);
        disc[4] = new ColorSquareLarger(ColorSquare.BOUNDED_INC, false, ColorSquare.WOOD);

        for (int i = 0; i < 5; i++)
        {
            add(keep[i]);
            add(disc[i]);
            keep[i].addMouseListener(this);
            disc[i].addMouseListener(this);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param b DOCUMENT ME!
     */
    public void setVisible(boolean b)
    {
        if (b)
        {
            /**
             * set initial values
             */
            SOCPlayer player = playerInterface.getGame().getPlayer(playerInterface.getClient().getNickname());
            SOCResourceSet resources = player.getResources();
            keep[0].setIntValue(resources.getAmount(SOCResourceConstants.CLAY));
            keep[1].setIntValue(resources.getAmount(SOCResourceConstants.ORE));
            keep[2].setIntValue(resources.getAmount(SOCResourceConstants.SHEEP));
            keep[3].setIntValue(resources.getAmount(SOCResourceConstants.WHEAT));
            keep[4].setIntValue(resources.getAmount(SOCResourceConstants.WOOD));

            discardBut.requestFocus();
        }

        super.setVisible(b);
    }

    /**
     * DOCUMENT ME!
     */
    public void doLayout()
    {
        int x = getInsets().left;
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

        int space = 5;
        int msgW = this.getFontMetrics(this.getFont()).stringWidth(msg.getText());
        int sqwidth = ColorSquareLarger.WIDTH_L;
        int sqspace = (width - (5 * sqwidth)) / 6;

        int keepY;
        int discY;

        /* put the dialog in the center of the game window */
        if (! didSetLocation)
        {
            int cfx = playerInterface.getInsets().left;
            int cfy = playerInterface.getInsets().top;
            int cfwidth = playerInterface.getSize().width - playerInterface.getInsets().left - playerInterface.getInsets().right;
            int cfheight = playerInterface.getSize().height - playerInterface.getInsets().top - playerInterface.getInsets().bottom;

            setLocation(cfx + ((cfwidth - width) / 2), cfy + ((cfheight - height) / 2));
            didSetLocation = true;
        }

        try
        {
            msg.setBounds((width - msgW) / 2, getInsets().top, msgW + 4, 20);
            discardBut.setBounds((getSize().width - 80) / 2, (getInsets().top + height) - 30, 80, 25);
            youHave.setBounds(getInsets().left, getInsets().top + 20 + space, 70, 20);
            discThese.setBounds(getInsets().left, getInsets().top + 20 + space + 20 + space + sqwidth + space, 100, 20);
        }
        catch (NullPointerException e) {}

        keepY = getInsets().top + 20 + space + 20 + space;
        discY = keepY + sqwidth + space + 20 + space;

        try
        {
            for (int i = 0; i < 5; i++)
            {
                keep[i].setSize(sqwidth, sqwidth);
                keep[i].setLocation(x + sqspace + (i * (sqspace + sqwidth)), keepY);
                disc[i].setSize(sqwidth, sqwidth);
                disc[i].setLocation(x + sqspace + (i * (sqspace + sqwidth)), discY);
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

        if (target == discardBut)
        {
            SOCResourceSet rsrcs = new SOCResourceSet(disc[0].getIntValue(), disc[1].getIntValue(), disc[2].getIntValue(), disc[3].getIntValue(), disc[4].getIntValue(), 0);

            if (rsrcs.getTotal() == numDiscards)
            {
                playerInterface.getClient().discard(playerInterface.getGame(), rsrcs);
                dispose();
            }
        }
        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
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
     *
     * @param e DOCUMENT ME!
     */
    public void mousePressed(MouseEvent e)
    {
        try {
        Object target = e.getSource();

        for (int i = 0; i < 5; i++)
        {
            if ((target == keep[i]) && (disc[i].getIntValue() > 0))
            {
                keep[i].addValue(1);
                disc[i].subtractValue(1);
                --numChosen;
                if (numChosen == (numDiscards-1))
                {
                    discardBut.disable();  // Count un-reached (too few)
                    discardBut.repaint();
                }
                else if (numChosen == numDiscards)
                {
                    discardBut.enable();   // Exact count reached
                    discardBut.repaint();
                }
                break;
            }
            else if ((target == disc[i]) && (keep[i].getIntValue() > 0))
            {
                keep[i].subtractValue(1);
                disc[i].addValue(1);
                ++numChosen;
                if (numChosen == numDiscards)
                {
                    discardBut.enable();  // Exact count reached
                    discardBut.repaint();
                }
                else if (numChosen == (numDiscards+1))
                {
                    discardBut.disable();  // Count un-reached (too many)
                    discardBut.repaint();
                }
                break;
            }
        }
        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }
}
