/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2010,2012-2015,2019 Jeremy D Monin <jeremy@nand.net>
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

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;


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
/*package*/ class SOCChoosePlayerDialog
    extends SOCDialog implements ActionListener, Runnable
{
    /**
     * i18n text strings; will use same locale as SOCPlayerClient's string manager.
     * @since 2.0.00
     */
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
    final JButton[] buttons;

    /** Player index of each to choose. This array's elements align with {@link #buttons}.
     *  Only the first {@link #number} elements are used.
     *  If constructor is called with {@code allowChooseNone}, the "decline" button's
     *  player number is -1 ({@link SOCChoosePlayer#CHOICE_NO_PLAYER}).
     */
    final int[] players;

    /** Show Count of resources of each player. Length is {@link #number}. */
    JLabel[] player_res_lbl;

    /** Number of players to choose from for {@link #buttons} and {@link #players}. */
    final int number;

    /**
     * Creates a new SOCChoosePlayerDialog object.
     * After creation, call {@link #pack()} and {@link #setVisible(boolean)}.
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
     *
     * @param pi  PlayerInterface that owns this dialog
     * @param num   The number of players to choose from
     * @param p   The player IDs of those players; length of this
     *            array may be larger than count (may be {@link SOCGame#maxPlayers}).
     *            Only the first <tt>num</tt> elements will be used.
     *            If <tt>allowChooseNone</tt>, p.length must be at least <tt>num + 1</tt>
     *            to leave room for "no player".
     * @param allowChooseNone  If true, player can choose to rob no one
     *            (used with game scenario {@code SC_PIRI})
     */
    public SOCChoosePlayerDialog
        (final SOCPlayerInterface pi, final int num, final int[] p, final boolean allowChooseNone)
    {
        super
            (pi, strings.get("dialog.robchoose.choose.player"),  // "Choose Player"
             strings.get("dialog.robchoose.please.choose"),  // "Please choose a player to steal from:"
             false);

        number = (allowChooseNone) ? (num + 1) : num;
        players = p;

        final JPanel btnsPane = getMiddlePanel();
        final Font panelFont = getFont();

        // Different layout when 3 or fewer players[] to choose from;
        // see constructor javadoc for diagram
        final boolean sameLineLayout = (number <= MAX_ON_SAME_LINE);

        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.ipady = 3;
        gbc.insets = new Insets(2, 4, 2, 4);  // horiz. and bit of vertical padding
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = GridBagConstraints.REMAINDER;

        btnsPane.setLayout(gbl);

        buttons = new JButton[number];
        player_res_lbl = new JLabel[number];

        final SOCGame ga = pi.getGame();

        final boolean isPlatformWindows = SOCPlayerClient.IS_PLATFORM_WINDOWS;
        for (int i = 0; i < num; i++)
        {
            SOCPlayer pl = ga.getPlayer(players[i]);

            buttons[i] = new JButton(pl.getName());
            buttons[i].addActionListener(this);
            if (isPlatformWindows)
                buttons[i].setBackground(null);  // inherit from panel: avoid gray corners on win32

            final int rescount = pl.getResources().getTotal();
            final int vpcount = pl.getPublicVP();
            JLabel pLabel = new JLabel
                (strings.get("dialog.robchoose.n.res.n.vp", rescount, vpcount), SwingConstants.CENTER);
                // "{0} res, {1} VP"
            SOCHandPanel ph = pi.getPlayerHandPanel(players[i]);
            pLabel.setBackground(ph.getBackground());
            pLabel.setForeground(ph.getForeground());
            pLabel.setOpaque(true);
            pLabel.setFont(panelFont);
            pLabel.setToolTipText(strings.get("dialog.robchoose.player.has.n.rsrcs", rescount));
                // "This player has 1 resource.", "This player has {0} resources."
                // 0 resources is possible if they have cloth. (SC_CLVI)

            player_res_lbl[i] = pLabel;
        }

        if (allowChooseNone)
        {
            JButton bNone = new JButton(strings.get("base.none"));  // "None"
            if (isPlatformWindows)
                bNone.setBackground(null);
            buttons[num] = bNone;

            bNone.setToolTipText(strings.get("dialog.robchoose.choose.steal.no.player"));
                // "Choose this to steal from no player"
            bNone.addActionListener(this);

            players[num] = SOCChoosePlayer.CHOICE_NO_PLAYER;

            JLabel lNone = new JLabel(strings.get("dialog.robchoose.decline"), SwingConstants.CENTER);  // "(decline)"
            lNone.setForeground(null);
            lNone.setFont(panelFont);
            player_res_lbl[num] = lNone;
        }

        int n = buttons.length;
        if (sameLineLayout)
        {
            // row with all buttons, then row with all player labels

            gbc.weightx = 1.0 / n;

            gbc.gridwidth = 1;
            for (int i = 0; i < n; ++i)
            {
                if (i == (n-1))
                    gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbl.setConstraints(buttons[i], gbc);
                btnsPane.add(buttons[i]);
            }

            gbc.gridwidth = 1;
            gbc.ipadx = 32;
            for (int i = 0; i < n; ++i)
            {
                if (i == (n-1))
                    gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbl.setConstraints(player_res_lbl[i], gbc);
                btnsPane.add(player_res_lbl[i]);
            }
        } else {
            // each player on their own row (button is left of label)

            for (int i = 0; i < n; ++i)
            {
                gbc.gridwidth = 1;
                gbc.ipadx = 0;
                gbl.setConstraints(buttons[i], gbc);
                btnsPane.add(buttons[i]);

                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbc.ipadx = 32;
                gbl.setConstraints(player_res_lbl[i], gbc);
                btnsPane.add(player_res_lbl[i]);
            }
        }
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
                playerInterface.getClient().getGameMessageMaker().choosePlayer(playerInterface.getGame(), players[i]);
                dispose();

                break;
            }
        }
        } catch (Throwable th) {
            playerInterface.chatPrintStackTrace(th);
        }
    }

}
