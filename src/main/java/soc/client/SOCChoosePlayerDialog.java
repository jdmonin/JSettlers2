/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2010,2012-2015 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCGameOption;  // only for javadocs
import soc.game.SOCPlayer;
import soc.message.SOCChoosePlayer;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


/**
 * This is the dialog to ask a player from whom she wants to steal.
 * One button for each victim player.  When a player is chosen,
 * send the server a choose-player command with that player number or
 * (if possible to choose none) {@link SOCChoosePlayer#CHOICE_NO_PLAYER}.
 *<P>
 * For convenience with {@link java.awt.EventQueue#invokeLater(Runnable)},
 * contains a {@link #run()} method which calls {@link #setVisible(boolean) setVisible(true)}.
 *
 * @author  Robert S. Thomas
 */
@SuppressWarnings("serial")
class SOCChoosePlayerDialog extends Dialog implements ActionListener, Runnable
{
    /** i18n text strings; will use same locale as SOCPlayerClient's string manager.
     *  @since 2.0.00 */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /**
     * Maximum number of {@link #players[]} buttons to show on a single horizontal line.
     * If asking about more than this many players (including {@code allowChooseNone}),
     * the layout will have each on its own line.
     * @since 2.0.00
     */
    private static final int MAX_ON_SAME_LINE = 3;

    /**
     * Player names on each button. This array's elements align with {@link #players}. Length is {@link #number}.
     * If constructor is called with {@code allowChooseNone}, there's a "decline" (choose none) button.
     */
    Button[] buttons;

    /** Player index of each to choose. This array's elements align with {@link #buttons}.
     *  Only the first {@link #number} elements are used.
     *  If constructor is called with {@code allowChooseNone}, the "decline" button's
     *  player number is -1 ({@link SOCChoosePlayer#CHOICE_NO_PLAYER}).
     */
    final int[] players;

    /** Show Count of resources of each player. Length is {@link #number}. */
    Label[] player_res_lbl;

    /** Number of players to choose from for {@link #buttons} and {@link #players}. */
    final int number;

    final Label msg;
    final SOCPlayerInterface pi;

    /** Desired size (visible size inside of insets) **/
    protected final int wantW, wantH;

    /**
     * If true, {@link #doLayout()} should manually lay out component locations.
     * See that method's javadoc for criteria.
     * @since 2.0.00
     */
    private final boolean hasCustomLayout;

    /**
     * Flag to place window in center once when displayed (in {@link #doLayout()}),
     * and not change position again afterwards. Set in {@link #setLocationCentered(int)}.
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
     *            Only the first <tt>num</tt> elements will be used.
     *            If <tt>allowChooseNone</tt>, p.length must be at least <tt>num + 1</tt>
     *            to leave room for "no player".
     * @param allowChooseNone  If true, player can choose to rob no one
     *            (used with game scenario {@code SC_PIRI})
     */
    public SOCChoosePlayerDialog
        (SOCPlayerInterface plInt, final int num, final int[] p, final boolean allowChooseNone)
    {
        super(plInt, strings.get("dialog.robchoose.choose.player"), true);  // "Choose Player"

        pi = plInt;
        number = (allowChooseNone) ? (num + 1) : num;
        players = p;
        setBackground(new Color(255, 230, 162));
        setForeground(Color.black);
        setFont(new Font("SansSerif", Font.PLAIN, 12));

        hasCustomLayout = (number <= MAX_ON_SAME_LINE);
        didSetLocation = false;

        // custom doLayout() when 3 or fewer players[] to choose from,
        // otherwise GridBagLayout; see doLayout() javadoc for diagram.
        GridBagLayout gbl;
        GridBagConstraints gbc;
        if (hasCustomLayout)
        {
            gbl = null;
            gbc = null;
        } else {
            gbl = new GridBagLayout();
            gbc = new GridBagConstraints();
            // gbc.ipadx = 3;
            gbc.ipady = 3;
            gbc.insets = new Insets(0, 3, 0, 3);  // horiz. padding
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridwidth = GridBagConstraints.REMAINDER;
        }

        setLayout(gbl);  // null if 3 or fewer players[]

        // wantH formula based on doLayout
        //    label: 20  button: 20  label: 16  spacing: 10
        wantW = 320;
        wantH = 20 + 10 + 20 + 10 + 16 + 5;
        setSize(wantW + 10, wantH + 20);  // Can calc & add room for insets at doLayout

        msg = new Label(strings.get("dialog.robchoose.please.choose"), Label.CENTER);  // "Please choose a player to steal from:"
        if (gbl != null)
            gbl.setConstraints(msg, gbc);
        add(msg);

        buttons = new Button[number];
        player_res_lbl = new Label[number];

        SOCGame ga = pi.getGame();

        for (int i = 0; i < num; i++)
        {
            SOCPlayer pl = ga.getPlayer(players[i]);

            buttons[i] = new Button(pl.getName());
            if (gbl != null)
            {
                gbc.gridwidth = 1;
                gbl.setConstraints(buttons[i], gbc);
            }
            add(buttons[i]);
            buttons[i].addActionListener(this);

            final int rescount = pl.getResources().getTotal();
            final int vpcount = pl.getPublicVP();
            player_res_lbl[i] = new Label(strings.get("dialog.robchoose.n.res.n.vp", rescount, vpcount), Label.CENTER);
                // "{0} res, {1} VP"
            SOCHandPanel ph = pi.getPlayerHandPanel(players[i]);
            player_res_lbl[i].setBackground(ph.getBackground());
            player_res_lbl[i].setForeground(ph.getForeground());
            if (gbl != null)
            {
                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbl.setConstraints(player_res_lbl[i], gbc);
            }
            add(player_res_lbl[i]);
            String restooltip = strings.get("dialog.robchoose.player.has.n.rsrcs", rescount);
                // "This player has 1 resource.", "This player has {0} resources."
                // 0 resources is possible if they have cloth. (SC_CLVI)
            new AWTToolTip(restooltip, player_res_lbl[i]);
        }

        if (allowChooseNone)
        {
            Button bNone = new Button(strings.get("base.none"));  // "None"
            buttons[num] = bNone;
            if (gbl != null)
            {
                gbc.gridwidth = 1;
                gbl.setConstraints(bNone, gbc);
            }
            add(bNone);
            bNone.addActionListener(this);

            new AWTToolTip(strings.get("dialog.robchoose.choose.steal.no.player"), bNone);
                // "Choose this to steal from no player"
            players[num] = SOCChoosePlayer.CHOICE_NO_PLAYER;
            player_res_lbl[num] = new Label(strings.get("dialog.robchoose.decline"), Label.CENTER);  // "(decline)"
            if (gbl != null)
            {
                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbl.setConstraints(player_res_lbl[num], gbc);
            }
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
     *<P>
     * With 3 or fewer {@code players[]} buttons, all buttons are on the same row:
     * <table border=1>
     * <tr><td colspan=3 align=center>Please choose a player to steal from:</td></tr>
     * <tr><td>players[0]</td><td>players[1]</td><td>players[2]</td></tr>
     * <tr><td>player_res_lbl[0]</td><td>player_res_lbl[1]</td><td>player_res_lbl[2]</td></tr>
     * </table>
     *<P>
     * With 4 or more buttons, sharing a row would be too wide, so there is 1 player per row:
     * <table border=1>
     * <tr><td colspan=2 align=center>Please choose a player to steal from:</td></tr>
     * <tr><td>players[0]</td><td>player_res_lbl[0]</td></tr>
     * <tr><td>players[1]</td><td>player_res_lbl[1]</td></tr>
     * <tr><td>...</td><td>...</td></tr>
     * </table>
     * This occurs with the {@link SOCGameOption#K_SC_PIRI SC_PIRI} scenario, which allows
     * robbing any player with resources or declining to rob.
     */
    public void doLayout()
    {
        // Reminder: While visible, AWTToolTip will temporarily setLayout(null)
        // and then call doLayout().

        if (! hasCustomLayout)
        {
            super.doLayout();
            if (! didSetLocation)
                setLocationCentered(0);

            return;  // <--- Early return: Don't do custom layout ---
        }

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
            setLocationCentered(width);

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
     * Put the dialog in the top-center of the game window ({@link #pi})
     * and set {@link #didSetLocation}.
     * @param width  Dialog width if known, or 0
     * @since 2.0.00
     */
    private void setLocationCentered(int width)
    {
        if (width == 0)
            width = getWidth();

        int piX = pi.getInsets().left;
        int piY = pi.getInsets().top;
        final int piWidth = pi.getSize().width - piX - pi.getInsets().right;
        piX += pi.getLocation().x;
        piY += pi.getLocation().y;

        setLocation(piX + ((piWidth - width) / 2), piY + 50);
        didSetLocation = true;
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

    /**
     * Run method, for convenience with {@link java.awt.EventQueue#invokeLater(Runnable)}.
     * This method just calls {@link #setVisible(boolean) setVisible(true)},
     * after calling {@link #pack()} if applicable.
     * @since 2.0.00
     */
    public void run()
    {
        if (! hasCustomLayout)
            pack();

        setVisible(true);
    }

}
