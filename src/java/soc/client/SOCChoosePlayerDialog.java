/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2010,2012 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.message.SOCChoosePlayer;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


/**
 * This is the dialog to ask a player from whom she wants to steal.
 * One button for each victim player.  When a player is chosen,
 * send the server a choose-player command with that player number.
 *
 * @author  Robert S. Thomas
 */
class SOCChoosePlayerDialog extends Dialog implements ActionListener
{
    /** Player names on each button. This array's elements align with {@link #players}. Length is {@link #number}. */
    Button[] buttons;

    /** Player index of each to choose. This array's elements align with {@link #buttons}.
     *  Only the first {@link #number} elements are used.
     */    
    final int[] players;

    /** Show Count of resources of each player. Length is {@link #number}. */
    Label[] player_res_lbl;

    /** Number of players to choose from for {@link #buttons} and {@link #players}. */
    final int number;

    /** If true, player is allowed to choose to steal from no one.
     *  Used with game scenario <tt>SC_PIRI</tt>.
     *  @since 2.0.00
     */
    final private boolean allowChooseNone;

    final Label msg;
    final SOCPlayerInterface pi;

    /** Desired size (visible size inside of insets) **/
    protected final int wantW, wantH;

    /**
     * Place window in center when displayed (in doLayout),
     * don't change position afterwards
     */
    boolean didSetLocation;

    /**
     * Creates a new SOCChoosePlayerDialog object.
     * After creation, call {@link #setVisible(boolean)}.
     *
     * @param plInt  PlayerInterface that owns this dialog
     * @param num    The number of players to choose from
     * @param p   The player ids of those players; length of this
     *            array may be larger than count (may be {@link SOCGame#maxPlayers}).
     *            Only the first <tt>count</tt> elements will be used.
     * @param allowChooseNone  If true, player can choose to rob no one
     *            (game scenario <tt>SC_PIRI</tt>)
     */
    public SOCChoosePlayerDialog
        (SOCPlayerInterface plInt, final int num, final int[] p, final boolean allowChooseNone)
    {
        super(plInt, /*I*/"Choose Player"/*18N*/, true);

        pi = plInt;
        number = (allowChooseNone) ? (num + 1) : num;
        players = p;
        this.allowChooseNone = allowChooseNone;
        setBackground(new Color(255, 230, 162));
        setForeground(Color.black);
        setFont(new Font("SansSerif", Font.PLAIN, 12));
        didSetLocation = false;
        setLayout(null);
        // wantH formula based on doLayout
        //    label: 20  button: 20  label: 16  spacing: 10
        wantW = 320;
        wantH = 20 + 10 + 20 + 10 + 16 + 5;
        setSize(wantW + 10, wantH + 20);  // Can calc & add room for insets at doLayout

        msg = new Label(/*I*/"Please choose a player to steal from:"/*18N*/, Label.CENTER);
        add(msg);

        buttons = new Button[number];
        player_res_lbl = new Label[number];

        SOCGame ga = pi.getGame();

        for (int i = 0; i < num; i++)
        {
            SOCPlayer pl = ga.getPlayer(players[i]);            

            buttons[i] = new Button(pl.getName());
            add(buttons[i]);
            buttons[i].addActionListener(this);

            final int rescount = pl.getResources().getTotal();
            final int vpcount = pl.getPublicVP();
            player_res_lbl[i] = new Label(/*I*/rescount + " res, " + vpcount + " VP"/*18N*/, Label.CENTER);
            SOCHandPanel ph = pi.getPlayerHandPanel(players[i]);
            player_res_lbl[i].setBackground(ph.getBackground());
            player_res_lbl[i].setForeground(ph.getForeground());
            add(player_res_lbl[i]);
            String restooltip;
            switch (rescount)
            {
            case 0:
                restooltip = /*I*/"This player has no resources."/*18N*/;
                break;

            case 1:
                restooltip = /*I*/"This player has 1 resource."/*18N*/;
                break;

            default:
                restooltip = /*I*/"This player has " + rescount + " resources."/*18N*/;
            }
            new AWTToolTip(restooltip, player_res_lbl[i]);
        }

        if (allowChooseNone)
        {
            Button bNone = new Button(/*I*/"None"/*18N*/);
            buttons[num] = bNone;
            add(bNone);
            bNone.addActionListener(this);
            new AWTToolTip(/*I*/"Choose this to steal from no player"/*18N*/, bNone);
            players[num] = SOCChoosePlayer.CHOICE_NO_PLAYER;
            player_res_lbl[num] = new Label(/*I*/"(decline)"/*18N*/, Label.CENTER);
            add(player_res_lbl[num]);
        }
    }

    /**
     * Show or hide this dialog.
     * If showing it, request focus on the first button.
     *
     * @param b True to show, false to hide
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
     * Do our dialog's custom layout.
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
     * A button was clicked to choose a victim player.
     * Find the right {@link #buttons}[i]
     * and send the server a choose-player command
     * with the corresponding player number {@link #players}[i].
     *
     * @param e AWT event, from a button source
     */
    public void actionPerformed(ActionEvent e)
    {
        try {
        Object target = e.getSource();

        for (int i = 0; i < number; i++)
        {
            if (target == buttons[i])
            {
                pi.getClient().getGameManager().choosePlayer(pi.getGame(), players[i]);
                dispose();

                break;
            }
        }
        } catch (Throwable th) {
            pi.chatPrintStackTrace(th);
        }
    }
}
