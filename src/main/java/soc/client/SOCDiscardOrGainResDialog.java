/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2009,2011-2014,2017-2019 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;


/**
 * This is the dialog to ask players what resources they want
 * to discard from rolling a 7, or to gain from the gold hex.
 * Also used for the Discovery/Year of Plenty dev card.
 *<P>
 * Before v2.0.00 this class was {@code SOCDiscardDialog},
 * and Year of Plenty used {@code SOCDiscoveryDialog}.
 *
 * @author  Robert S. Thomas
 */
@SuppressWarnings("serial")
/*package*/ class SOCDiscardOrGainResDialog
    extends SOCDialog implements ActionListener, MouseListener, Runnable
{

    /**
     * i18n text strings; will use same locale as SOCPlayerClient's string manager.
     * @since 2.0.00
     */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /**
     * Are we discarding, not gaining?
     * @since 2.0.00
     */
    private final boolean isDiscard;

    /**
     * Clear button.  Reset the {@link #pick} resource colorsquare counts to 0.
     * @since 1.1.14
     */
    private final JButton clearBut;

    /** Discard or Pick button */
    private final JButton okBut;

    /** The 'keep' square resource types/counts only change if {@link #isDiscard}. */
    private final ColorSquare[] keep;

    /** Resource types/counts to discard or gain */
    private final ColorSquare[] pick;

    /** Must discard this many resources from {@link #keep}, or must gain this many resources. */
    private final int numPickNeeded;

    /**
     * Has chosen to discard or gain this many resources so far in {@link #pick}.
     * {@link #okBut} is disabled unless proper number of resources ({@link #numPickNeeded}) are chosen.
     */
    private int numChosen;

    /**
     * Creates a new SOCDiscardOrGainResDialog popup.
     * Sets initial values for current resources based on client player's {@link SOCPlayer#getResources()}.
     * To show it on screen and make it active,
     * call {@link #setVisible(boolean) setVisible(true)}.
     *
     * @param pi   Client's player interface
     * @param numPickNeeded Player must discard or gain this many resources
     * @param isDiscard  True for discard (after 7), false for gain (after gold hex)
     */
    public SOCDiscardOrGainResDialog(SOCPlayerInterface pi, final int numPickNeeded, final boolean isDiscard)
    {
        super(pi,
            strings.get
                (isDiscard ? "dialog.discard.title" : "dialog.discard.title.gain", pi.getClient().getNickname()),
                 // "Discard [{0}]" or "Gain Resources [{0}]"
            strings.get((isDiscard) ? "dialog.discard.please.discard.n" : "dialog.discard.please.pick.n", numPickNeeded),
                 // "Please discard {0} resources." or "Please pick {0} resources.",
            false);

        this.isDiscard = isDiscard;
        this.numPickNeeded = numPickNeeded;
        numChosen = 0;

        getRootPane().setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        clearBut = new JButton(strings.get("base.clear"));
        okBut = new JButton(strings.get(isDiscard ? "dialog.discard.discard" : "dialog.discard.pick"));
            // "Discard" or "Pick"

        final boolean isOSHighContrast = SwingMainDisplay.isOSColorHighContrast();
        final boolean shouldClearButtonBGs = (! isOSHighContrast) && SOCPlayerClient.IS_PLATFORM_WINDOWS;
        if (shouldClearButtonBGs)
        {
            clearBut.setBackground(null);  // avoid gray corners on win32 JButtons
            okBut.setBackground(null);
        }

        // Resource panel: labels and colorsquares.
        // X-align must be same for all in BoxLayout
        JPanel resPanel = getMiddlePanel();
        resPanel.setLayout(new BoxLayout(resPanel, BoxLayout.Y_AXIS));

        final JLabel youHave = new JLabel(strings.get("dialog.discard.you.have"), SwingConstants.LEFT);  // "You have:"
        youHave.setAlignmentX(LEFT_ALIGNMENT);
        youHave.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        resPanel.add(youHave);

        keep = new ColorSquare[5];
        pick = new ColorSquare[5];

        JPanel keepPanel = new JPanel(new GridLayout(1, 0, ColorSquareLarger.WIDTH_L, 0));
        JPanel pickPanel = new JPanel(new GridLayout(1, 0, ColorSquareLarger.WIDTH_L, 0));
        if (! isOSHighContrast)
        {
            keepPanel.setBackground(null);
            pickPanel.setBackground(null);
        }
        keepPanel.setAlignmentX(LEFT_ALIGNMENT);
        pickPanel.setAlignmentX(LEFT_ALIGNMENT);

        for (int i = 0; i < 5; i++)
        {
            final Color sqColor = ColorSquare.RESOURCE_COLORS[i];

            keep[i] = new ColorSquareLarger(ColorSquare.BOUNDED_DEC, false, sqColor);
            pick[i] = new ColorSquareLarger(ColorSquare.BOUNDED_INC, false, sqColor);
            keepPanel.add(keep[i]);
            pickPanel.add(pick[i]);
            keep[i].addMouseListener(this);
            pick[i].addMouseListener(this);
        }

        resPanel.add(keepPanel);

        final JLabel pickThese = new JLabel
            (strings.get(isDiscard ? "dialog.discard.these" : "dialog.discard.gain.these"), SwingConstants.LEFT);
             // "Discard these:" or "Gain these:"
        pickThese.setAlignmentX(LEFT_ALIGNMENT);
        pickThese.setBorder(BorderFactory.createEmptyBorder(20, 0, 4, 0));  // also gives 20-pixel margin above.
        resPanel.add(pickThese);

        resPanel.add(pickPanel);

        // set initial values
        final SOCResourceSet resources
            = playerInterface.getGame().getPlayer(playerInterface.getClientPlayerNumber()).getResources();
        keep[0].setIntValue(resources.getAmount(SOCResourceConstants.CLAY));
        keep[1].setIntValue(resources.getAmount(SOCResourceConstants.ORE));
        keep[2].setIntValue(resources.getAmount(SOCResourceConstants.SHEEP));
        keep[3].setIntValue(resources.getAmount(SOCResourceConstants.WHEAT));
        keep[4].setIntValue(resources.getAmount(SOCResourceConstants.WOOD));

        styleButtonsAndLabels(resPanel);

        // bottom button panel:

        final JPanel btnsPanel = getSouthPanel();

        btnsPanel.add(clearBut);
        clearBut.addActionListener(this);
        clearBut.setEnabled(false);  // since nothing picked yet

        btnsPanel.add(okBut);
        okBut.addActionListener(this);
        if (numPickNeeded > 0)
            okBut.setEnabled(false);  // Must choose that many first

        styleButtonsAndLabels(btnsPanel);
        getRootPane().setDefaultButton(okBut);
    }

    /**
     * Show or hide this dialog.
     * If showing (<tt>vis == true</tt>), also requests focus on the Discard/Pick button
     * and plays the appropriate sound: {@link SOCPlayerInterface#SOUND_RSRC_LOST}
     * or {@link SOCPlayerInterface#SOUND_RSRC_GAINED_FREE}.
     *
     * @param vis  True to make visible, false to hide
     */
    @Override
    public void setVisible(final boolean vis)
    {
        if (vis)
        {
            okBut.requestFocus();

            playerInterface.playSound
                ((isDiscard) ? SOCPlayerInterface.SOUND_RSRC_LOST : SOCPlayerInterface.SOUND_RSRC_GAINED_FREE);
        }

        super.setVisible(vis);
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
            SOCResourceSet rsrcs = new SOCResourceSet
                (pick[0].getIntValue(), pick[1].getIntValue(), pick[2].getIntValue(),
                 pick[3].getIntValue(), pick[4].getIntValue(), 0);

            if (rsrcs.getTotal() == numPickNeeded)
            {
                SOCPlayerClient pcli = playerInterface.getClient();
                if (isDiscard)
                    pcli.getGameMessageMaker().discard(playerInterface.getGame(), rsrcs);
                else
                    pcli.getGameMessageMaker().pickResources(playerInterface.getGame(), rsrcs);
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

    /**
     * When a resource's colorsquare is clicked, add/remove 1
     * from the resource totals as requested; update {@link #okBut}
     * and {@link #clearBut}.
     *<P>
     * If not isDiscard, will not subtract change the "keep" colorsquare resource counts.
     *<P>
     * If we only need 1 total, and we've picked one and
     * now pick a different one, zero the previous pick
     * and change our choice to the new one.
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
                    okBut.setEnabled(false);  // Count un-reached (too few)
                    wantsRepaint = true;
                }
                else if (numChosen == numPickNeeded)
                {
                    okBut.setEnabled(true);   // Exact count reached
                    wantsRepaint = true;
                }
                break;
            }
            else if ((target == pick[i]) && ((keep[i].getIntValue() > 0) || ! isDiscard))
            {
                if ((numPickNeeded == 1) && (numChosen == 1))
                {
                    // We only need 1 total, change our previous choice to the new one

                    if (pick[i].getIntValue() == 1)
                        return;  // <--- early return: already set to 1 ---
                    else
                        // clear all to 0
                        for (int j = 0; j < 5; ++j)
                        {
                            final int n = pick[j].getIntValue();
                            if (n == 0)
                                continue;
                            if (isDiscard)
                                keep[j].addValue(n);
                            pick[j].subtractValue(n);
                        }

                    numChosen = 0;
                }

                if (isDiscard)
                    keep[i].subtractValue(1);
                pick[i].addValue(1);
                ++numChosen;
                if (numChosen == numPickNeeded)
                {
                    okBut.setEnabled(true);  // Exact count reached
                    wantsRepaint = true;
                }
                else if (numChosen == (numPickNeeded+1))
                {
                    okBut.setEnabled(false);  // Count un-reached (too many)
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

    /** Stub required for {@link MouseListener}. */
    public void mouseEntered(MouseEvent e) {}

    /** Stub required for {@link MouseListener}. */
    public void mouseExited(MouseEvent e) {}

    /** Stub required for {@link MouseListener}. */
    public void mouseClicked(MouseEvent e) {}

    /** Stub required for {@link MouseListener}. */
    public void mouseReleased(MouseEvent e) {}

}
