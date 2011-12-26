/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2007-2009,2011 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Label;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;


/**
 * This is the dialog to ask players what resources they want
 * to discard.
 *
 * @author  Robert S. Thomas
 */
class SOCDiscardDialog extends Dialog implements ActionListener, MouseListener
{
    /**
     * Clear button.  Reset discard count to 0.
     * @since 1.2.00
     */
    private Button clearBut;

    /** Discard button */
    Button discardBut;

    ColorSquare[] keep;
    ColorSquare[] disc;
    Label msg;
    Label youHave;
    Label discThese;
    SOCPlayerInterface playerInterface;

    /** Must discard this many resources from {@link #keep} */
    int numDiscards;

    /**
     * Has chosen to discard this many resources so far in {@link #disc}.
     * {@link #discardBut} is disabled unless proper number of resources ({@link #numDiscards}) are chosen.
     */
    private int numChosen;

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

        clearBut = new Button("Clear");
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

        add(clearBut);
        clearBut.addActionListener(this);
        clearBut.setEnabled(false);  // since nothing picked yet

        add(discardBut);
        discardBut.addActionListener(this);
        if (numDiscards > 0)
            discardBut.disable();  // Must choose that many first

        keep = new ColorSquare[5];
        disc = new ColorSquare[5];

        for (int i = 0; i < 5; i++)
        {
            // On OSX: We must use the wrong color, then change it, in order to
            // not use AWTToolTips (redraw problem for button enable/disable).
            Color sqColor;
            if (SOCPlayerClient.isJavaOnOSX)
                sqColor = Color.WHITE;
            else
                sqColor = ColorSquare.RESOURCE_COLORS[i];

            keep[i] = new ColorSquareLarger(ColorSquare.BOUNDED_DEC, false, sqColor);
            disc[i] = new ColorSquareLarger(ColorSquare.BOUNDED_INC, false, sqColor);
            if (SOCPlayerClient.isJavaOnOSX)
            {
                sqColor = ColorSquare.RESOURCE_COLORS[i];                
                keep[i].setBackground(sqColor);
                disc[i].setBackground(sqColor);
            }
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
     * Custom layout, and setLocation call, for this dialog.
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

            final Point piLoc = playerInterface.getLocation();
            setLocation(piLoc.x + cfx + ((cfwidth - width) / 2), piLoc.y + cfy + ((cfheight - height) / 3));
            didSetLocation = true;
        }

        try
        {
            msg.setBounds((width - msgW) / 2, getInsets().top, msgW + 4, 20);
            final int btnsX = (getSize().width - (2 * 80 + 5)) / 2;
            int y = (getInsets().top + height) - 30;
            clearBut.setBounds(btnsX, y, 80, 25);
            discardBut.setBounds(btnsX + 85, y, 80, 25);
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
     * React to clicking Discard button or Clear button.
     *<P>
     * ColorSquare clicks are handled in {@link #mousePressed(MouseEvent)}.
     *
     * @param e  ActionEvent for the click, with {@link ActionEvent#getSource()} == our button
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
        else if (target == clearBut)
        {
            for (int i = disc.length - 1; i >= 0; --i)
            {
                keep[i].addValue(disc[i].getIntValue());
                disc[i].setIntValue(0);
            }
            numChosen = 0;
            clearBut.setEnabled(false);
            discardBut.setEnabled(numDiscards > 0);
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
     * When a resource's colorsquare is clicked, add/remove 1
     * from the resource totals as requested; update {@link #discardBut}
     * and {@link #clearBut}.
     *<P>
     * Clear/Discard button clicks are handled in {@link #actionPerformed(ActionEvent)}.
     */
    public void mousePressed(MouseEvent e)
    {
        try {
        Object target = e.getSource();
        boolean wantsRepaint = false;

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
                    wantsRepaint = true;
                }
                else if (numChosen == numDiscards)
                {
                    discardBut.enable();   // Exact count reached
                    wantsRepaint = true;
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
                    wantsRepaint = true;
                }
                else if (numChosen == (numDiscards+1))
                {
                    discardBut.disable();  // Count un-reached (too many)
                    wantsRepaint = true;
                }
                break;
            }
        }

        clearBut.setEnabled(numChosen > 0);

        if (wantsRepaint)
        {
            discardBut.repaint();
        }

        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }
}
