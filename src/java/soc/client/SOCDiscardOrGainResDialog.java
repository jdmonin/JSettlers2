/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2009,2011-2012 Jeremy D Monin <jeremy@nand.net>
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
 * to discard from rolling a 7, or to gain from the gold hex.
 *<P>
 * Before v2.0.00, this was called <tt>SOCDiscardDialog</tt>.
 *
 * @author  Robert S. Thomas
 */
class SOCDiscardOrGainResDialog extends Dialog implements ActionListener, MouseListener
{
    /**
     * Are we discarding, not gaining?
     * @since 2.0.00
     */
    private final boolean isDiscard;

    /**
     * Clear button.  Reset the {@link #pick} resource colorsquare counts to 0.
     * @since 1.1.14
     */
    private Button clearBut;

    /** Discard or Pick button */
    private Button okBut;

    /** The 'keep' square resource types/counts only change if {@link #isDiscard}. */
    ColorSquare[] keep;

    /** Resource types/counts to discard or gain */
    private ColorSquare[] pick;

    Label msg;
    Label youHave;
    Label pickThese;
    SOCPlayerInterface playerInterface;

    /** Must discard this many resources from {@link #keep}, or must gain this many resources. */
    private final int numPickNeeded;

    /**
     * Has chosen to discard or gain this many resources so far in {@link #pick}.
     * {@link #okBut} is disabled unless proper number of resources ({@link #numPickNeeded}) are chosen.
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
     * Creates a new SOCDiscardOrGainResDialog popup.
     * To show it on screen and make it active,
     * call {@link #setVisible(boolean) setVisible(true)}.
     *
     * @param pi   Client's player interface
     * @param rnum Player must discard or gain this many resources
     * @param isDiscard  True for discard (after 7), false for gain (after gold hex)
     */
    public SOCDiscardOrGainResDialog(SOCPlayerInterface pi, final int rnum, final boolean isDiscard)
    {
        super(pi, (isDiscard ? "Discard [" : "Gain Resources [" ) + pi.getClient().getNickname() + "]", true);

        this.isDiscard = isDiscard;
        playerInterface = pi;
        numPickNeeded = rnum;
        numChosen = 0;
        setBackground(new Color(255, 230, 162));
        setForeground(Color.black);
        setFont(new Font("SansSerif", Font.PLAIN, 12));

        clearBut = new Button("Clear");
        okBut = new Button(isDiscard ? "Discard" : "Pick");

        didSetLocation = false;
        setLayout(null);

        msg = new Label
            ((isDiscard ? "Please discard " : "Please pick ")
             + Integer.toString(numPickNeeded)
             + ((numPickNeeded != 1) ? " resources." : " resource.")
             , Label.CENTER);
        add(msg);
        youHave = new Label("You have:", Label.LEFT);
        add(youHave);
        pickThese = new Label((isDiscard ? "Discard these:" : "Gain these:"), Label.LEFT);
        add(pickThese);

        // wantH formula based on doLayout
        //    labels: 20  colorsq: 20  button: 25  spacing: 5
        wantW = 270;
        wantH = 20 + 5 + (2 * (20 + 5 + 20 + 5)) + 25 + 5;
        setSize(wantW + 10, wantH + 20);  // Can calc & add room for insets at doLayout

        add(clearBut);
        clearBut.addActionListener(this);
        clearBut.setEnabled(false);  // since nothing picked yet

        add(okBut);
        okBut.addActionListener(this);
        if (numPickNeeded > 0)
            okBut.disable();  // Must choose that many first

        keep = new ColorSquare[5];
        pick = new ColorSquare[5];

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
            pick[i] = new ColorSquareLarger(ColorSquare.BOUNDED_INC, false, sqColor);
            if (SOCPlayerClient.isJavaOnOSX)
            {
                sqColor = ColorSquare.RESOURCE_COLORS[i];                
                keep[i].setBackground(sqColor);
                pick[i].setBackground(sqColor);
            }
            add(keep[i]);
            add(pick[i]);
            keep[i].addMouseListener(this);
            pick[i].addMouseListener(this);
        }
    }

    /**
     * Show or hide this dialog.
     * If showing (<tt>vis == true</tt>), also sets the initial values
     * of our current resources, based on {@link SOCPlayer#getResources()},
     * and requests focus on the Discard/Pick button.
     *
     * @param vis  True to make visible, false to hide
     */
    public void setVisible(final boolean vis)
    {
        if (vis)
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

            okBut.requestFocus();
        }

        super.setVisible(vis);
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
            okBut.setBounds(btnsX + 85, y, 80, 25);
            youHave.setBounds(getInsets().left, getInsets().top + 20 + space, 70, 20);
            pickThese.setBounds(getInsets().left, getInsets().top + 20 + space + 20 + space + sqwidth + space, 100, 20);
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
                pick[i].setSize(sqwidth, sqwidth);
                pick[i].setLocation(x + sqspace + (i * (sqspace + sqwidth)), discY);
            }
        }
        catch (NullPointerException e) {}
    }

    /**
     * React to clicking Discard/Pick button or Clear button.
     *<P>
     * ColorSquare clicks are handled in {@link #mousePressed(MouseEvent)}.
     *
     * @param e  ActionEvent for the click, with {@link ActionEvent#getSource()} == our button
     */
    public void actionPerformed(ActionEvent e)
    {
        try {
        Object target = e.getSource();

        if (target == okBut)
        {
            SOCResourceSet rsrcs = new SOCResourceSet(pick[0].getIntValue(), pick[1].getIntValue(), pick[2].getIntValue(), pick[3].getIntValue(), pick[4].getIntValue(), 0);

            if (rsrcs.getTotal() == numPickNeeded)
            {
                SOCPlayerClient pcli = playerInterface.getClient();
                if (isDiscard)
                    pcli.getGameManager().discard(playerInterface.getGame(), rsrcs);
                else
                    pcli.getGameManager().pickResources(playerInterface.getGame(), rsrcs);
                dispose();
            }
        }
        else if (target == clearBut)
        {
            for (int i = pick.length - 1; i >= 0; --i)
            {
                if (isDiscard)
                    keep[i].addValue(pick[i].getIntValue());
                pick[i].setIntValue(0);
            }
            numChosen = 0;
            clearBut.setEnabled(false);
            okBut.setEnabled(numPickNeeded == numChosen);
        }
        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }

    /** Stub, required for {@link MouseListener}. */
    public void mouseEntered(MouseEvent e)
    {
        ;
    }

    /** Stub, required for {@link MouseListener}. */
    public void mouseExited(MouseEvent e)
    {
        ;
    }

    /** Stub, required for {@link MouseListener}. */
    public void mouseClicked(MouseEvent e)
    {
        ;
    }

    /** Stub, required for {@link MouseListener}. */
    public void mouseReleased(MouseEvent e)
    {
        ;
    }

    /**
     * When a resource's colorsquare is clicked, add/remove 1
     * from the resource totals as requested; update {@link #okBut}
     * and {@link #clearBut}.
     *<P>
     * If not isDiscard, will not subtract change the "keep" colorsquare resource counts.
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
            if ((target == keep[i]) && (pick[i].getIntValue() > 0))
            {
                if (isDiscard)
                    keep[i].addValue(1);
                pick[i].subtractValue(1);
                --numChosen;
                if (numChosen == (numPickNeeded-1))
                {
                    okBut.disable();  // Count un-reached (too few)
                    wantsRepaint = true;
                }
                else if (numChosen == numPickNeeded)
                {
                    okBut.enable();   // Exact count reached
                    wantsRepaint = true;
                }
                break;
            }
            else if ((target == pick[i]) && ((keep[i].getIntValue() > 0) || ! isDiscard))
            {
                if (isDiscard)
                    keep[i].subtractValue(1);
                pick[i].addValue(1);
                ++numChosen;
                if (numChosen == numPickNeeded)
                {
                    okBut.enable();  // Exact count reached
                    wantsRepaint = true;
                }
                else if (numChosen == (numPickNeeded+1))
                {
                    okBut.disable();  // Count un-reached (too many)
                    wantsRepaint = true;
                }
                break;
            }
        }

        clearBut.setEnabled(numChosen > 0);

        if (wantsRepaint)
        {
            okBut.repaint();
        }

        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }
}
