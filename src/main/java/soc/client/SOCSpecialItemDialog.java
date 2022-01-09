/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2014-2020 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCGameOptionSet;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.game.SOCScenario;  // for javadocs only
import soc.game.SOCSpecialItem;

import java.awt.Container;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.MissingResourceException;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;


/**
 * This is a modal dialog for info / actions related to known {@link SOCSpecialItem}s.
 * Its content will be specific to the scenario using Special Items, such as {@link SOCScenario#K_SC_WOND SC_WOND}.
 * This dialog class keeps special item types' actions in one place, instead of adding new classes
 * when new scenarios use them.
 *<P>
 * Currently used by:
 *<UL>
 * <LI> {@link SOCScenario#K_SC_WOND SC_WOND} - Show Wonder info and pick one to build
 *</UL>
 * If the Special Item's {@code typeKey} is unknown, this dialog shouldn't be called.
 *<P>
 * This dialog is work in progress: Currently it's functional but ugly.
 *<P>
 * <b>I18N:</b><br>
 * In the {@link SOCScenario#K_SC_WOND SC_WOND} scenario, the Wonder names are keyed strings
 * {@code game.specitem.sc_wond.w1} - {@code game.specitem.sc_wond.w5}.  Some Wonders require
 * a settlement or city at certain node locations, which are named as keyed strings
 * {@code board.nodelist._SC_WOND.N1} - {@code board.nodelist._SC_WOND.N3}.
 * See also {@link SOCSpecialItem#getStringValue()}.
 *
 * @since 2.0.00
 */
@SuppressWarnings("serial")
/*package*/ class SOCSpecialItemDialog
    extends JDialog implements ActionListener
{
    /** i18n text strings; will use same locale as SOCPlayerClient's string manager. */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /**
     * Special items' {@code typeKey}, such as {@link SOCGameOptionSet#K_SC_WOND _SC_WOND}.
     * See {@link #SOCSpecialItemDialog(SOCPlayerInterface, String) constructor} javadoc.
     */
    private final String typeKey;

    /** Item pick buttons. */
    private JButton[] buttons;

    /** Button to close dialog without taking any action. */
    private JButton bClose;

    private final SOCPlayerInterface pi;
    private final SOCGame ga;

    /**
     * Optional listener.
     * @see #nbddListenerCalled
     * @since 2.0.00
     */
    private PlayerClientListener.NonBlockingDialogDismissListener nbddListener;

    /** Set true before {@code dispose()} to prevent 2 calls to {@link #nbddListener}. */
    private volatile boolean nbddListenerCalled = false;

    /** Place dialog in center once when displayed (in doLayout), don't change position afterwards */
    private boolean didSetLocation;

    /**
     * Creates a new SOCSpecialItemDialog object for known types of special items.
     * After creation, call {@code pack()} and {@link #setVisible(boolean) setVisible(true)} to show the modal dialog;
     * this dialog's code will request any action chosen by the player.
     *<P>
     * Currently {@link SOCGameOptionSet#K_SC_WOND _SC_WOND} is the only known {@code typeKey}.
     *
     * @param pi  PlayerInterface that owns this dialog
     * @param typeKey  Special item type key; for details see the {@link SOCSpecialItem} class javadoc
     *     and {@link SOCSpecialItem#makeKnownItem(String, int)} javadoc's {@code typeKey} param.
     * @throws IllegalArgumentException if the {@code typeKey} is unknown here
     */
    public SOCSpecialItemDialog
        (SOCPlayerInterface pi, final String typeKey)
        throws IllegalArgumentException
    {
        super(pi, "Special Items", true);  // default title text here, in case typeKey has no string

        try {
            setTitle(strings.get("dialog.specitem." + typeKey + ".title"));
                // "dialog.specitem._SC_WOND.title" -> "Wonders"
        } catch(MissingResourceException e) {}

        if (! SOCGameOptionSet.K_SC_WOND.equals(typeKey))
            throw new IllegalArgumentException(typeKey);

        this.pi = pi;
        this.typeKey = typeKey;

        final int px3 = 3 * pi.displayScale;  // for spacing

        Container cpane = getContentPane();
        if (! (cpane instanceof JPanel))
        {
            cpane = new JPanel();
            setContentPane(cpane);
        }
        ((JPanel) cpane).setBorder(new EmptyBorder(2 * px3, 2 * px3, px3, 2 * px3));
        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        setLayout(gbl);

        // most components pad to avoid text against adjacent component
        final Insets insPadLR = new Insets(0, px3, 0, px3),
            insPadL = new Insets(0, px3, 0, 0),
            insPadBottom = new Insets(0, 0, 5 * px3, 0),    // wide bottom insets, as gap between wonders
            insNone = gbc.insets;

        ga = pi.getGame();
        final int numWonders = 1 + ga.maxPlayers;
        final SOCPlayer cliPlayer = pi.getClientPlayer();  // will be null if observer

        didSetLocation = false;

        final JLabel subtitle_prompt = new JLabel
            (strings.get("dialog.specitem._SC_WOND.subtitle"));  // "The Wonders and their Builders:"
            // If client player doesn't have a Wonder yet, text will change below to prompt them:
            // "dialog.specitem._SC_WOND.prompt" -- "Choose the Wonder you will build."
        subtitle_prompt.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(subtitle_prompt, gbc);
        cpane.add(subtitle_prompt);

        JLabel L;

        // blank row below prompt

        L = new JLabel(" ");
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(L, gbc);
        cpane.add(L);

        // Header row for wonders table:

        L = new JLabel(strings.get("dialog.specitem._SC_WOND.wonder"));  // "Wonder:"
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbl.setConstraints(L, gbc);
        cpane.add(L);

        L = new JLabel(strings.get("dialog.specitem.cost"));  // "Cost:"
        gbc.gridx = GridBagConstraints.RELATIVE;
        gbc.gridwidth = 5;  // span 5 ColorSquares for the 5 resource types
        gbl.setConstraints(L, gbc);
        cpane.add(L);

        L = new JLabel(strings.get("dialog.specitem._SC_WOND.requires"));  // "Requires:"
        // match border and insets of buildRequirementsText labels
        L.setBorder(new EmptyBorder(0, 3, 0, 3));
        gbc.insets = insPadLR;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(L, gbc);
        cpane.add(L);

        // Wonders laid out vertically, two rows for each Wonder:
        // Shows button, name, cost, requirements, current builder and level.

        buttons = new JButton[numWonders];

        final boolean playerOwnsWonder =
            (cliPlayer != null) && (cliPlayer.getSpecialItem(SOCGameOptionSet.K_SC_WOND, 0) != null);
        boolean hasStartingCostShip = false;  // true only if player has available ship && ! playerOwnsWonder
        try
        {
            if ((cliPlayer != null) && ! playerOwnsWonder)
                hasStartingCostShip = ga.getSpecialItem(typeKey, 1).checkStartingCostPiecetype(cliPlayer, false);
                // assumes all special items have same startingCostPiecetype, which is true for SC_WOND
        }
        catch (Throwable th) {}  // null item, etc

        final String buildStr = strings.get("base.build");
        if (ga.getGameState() < SOCGame.START1A)
            subtitle_prompt.setText(strings.get("dialog.specitem.start_game"));  // "Must start the game to see this info."
        else if ((cliPlayer != null) && ! playerOwnsWonder)
            subtitle_prompt.setText(strings.get
                ((hasStartingCostShip)
                 ? "dialog.specitem._SC_WOND.prompt"             // "Choose the Wonder you will build."
                 : "dialog.specitem._SC_WOND.starting_cost" ));  // "Starting cost: Resources shown, and 1 ship used for ownership marker"

        for (int i = 0; i < numWonders; ++i)
        {
            SOCSpecialItem itm = ga.getSpecialItem(typeKey, i+1);
            if (itm == null)
                continue;  // shouldn't happen once game starts, unless SOCSpecialItem.makeKnownItem has failed

            // GBL Layout rows for a Wonder:
            // [Build] wonder name [sq][sq][sq][sq][sq] (cost) - requirements
            //                     Builder: ___  - Current level: #  (if being built)

            gbc.gridwidth = 1;

            // First row:

            // Build button
            final SOCPlayer owner = itm.getPlayer();
            final boolean playerOwnsThis = playerOwnsWonder && (owner == cliPlayer);
            final boolean playerCanBuildThis =
                (ga.getGameState() >= SOCGame.PLAY1)
                && (ga.getGameState() < SOCGame.OVER)
                && (playerOwnsWonder)
                    ? (playerOwnsThis && itm.checkCost(cliPlayer))
                    : ((owner == null) && hasStartingCostShip && itm.checkRequirements(cliPlayer, true));
            if (playerOwnsThis || ! playerOwnsWonder)
            {
                final JButton b = new JButton(buildStr);
                if (playerCanBuildThis)
                    b.addActionListener(this);
                else
                    b.setEnabled(false);

                gbc.insets = insPadL;
                gbl.setConstraints(b, gbc);
                cpane.add(b);
                buttons[i] = b;
            } else {
                // already building a different wonder: leave blank
            }

            gbc.insets = insPadLR;

            // Wonder Name
            gbc.gridx = 1;  // skip possibly-empty button column
            final Font labelFont;  // will use same font for requirements text
            {
                String wname;
                try
                {
                    wname = strings.get("game.specitem.sc_wond." + itm.getStringValue());
                        // game.specitem.sc_wond.w1 -> "Theater", etc
                } catch (MissingResourceException e) {
                    wname = "WONDERNAME_" + (i+1);  // fallback, should not occur
                }

                L = new JLabel(wname);
                gbl.setConstraints(L, gbc);
                cpane.add(L);
                labelFont = L.getFont();
            }
            gbc.gridx = GridBagConstraints.RELATIVE;

            // Cost
            gbc.insets = insNone;
            final SOCResourceSet cost = itm.getCost();  // or null
            for (int j = 0; j < 5; ++j)
            {
                ColorSquareLarger sq = new ColorSquareLarger(ColorSquare.NUMBER, false, ColorSquare.RESOURCE_COLORS[j]);
                sq.setIntValue((cost != null) ? cost.getAmount(j + 1) : 0);
                gbl.setConstraints(sq, gbc);
                cpane.add(sq);
            }
            gbc.insets = insPadLR;

            // Requirements
            final JComponent itmDesc = buildRequirementsText(itm.req);  // returns JLabel or JTextArea
            if (itmDesc instanceof JTextArea)
                itmDesc.setFont(labelFont);  // override default font (monospaced on win32)
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(itmDesc, gbc);
            cpane.add(itmDesc);

            // Second row:

            gbc.insets = insPadBottom;  // wide bottom border, as gap between wonders

            // Placeholder label, to fill the column 1 table cell below wonder name;
            // otherwise, GBL will push this and the next wonder's rows together and
            // its wonder name (col 1) will be left of this one's builder name (col 2)
            L = new JLabel();
            gbc.gridx = 1;
            gbc.gridwidth = 1;
            gbl.setConstraints(L, gbc);
            cpane.add(L);

            // Builder name; current level (if build in progress)
            final StringBuffer sb = new StringBuffer();  // builder if any, current level

            if (owner != null)
            {
                sb.append(strings.get("dialog.specitem._SC_WOND.builder", owner.getName()));  // "Builder: {0}"
            }

            if (itm.getLevel() > 0)
            {
                if (owner != null)
                    sb.append(" - ");

                sb.append(strings.get
                    ("dialog.specitem._SC_WOND.current_level", itm.getLevel(), SOCSpecialItem.SC_WOND_WIN_LEVEL));  // "Current Level: {0} of {1}"
            }

            L = new JLabel(sb.toString());
            gbc.gridx = GridBagConstraints.RELATIVE;
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(L, gbc);
            cpane.add(L);
        }

        if (hasStartingCostShip)
        {
            // footer row with reminder

            L = new JLabel(strings.get("dialog.specitem._SC_WOND.starting_cost"));
                // "Starting cost: Resources shown, and 1 ship used for ownership marker"
            gbc.insets = insPadLR;
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(L, gbc);
            cpane.add(L);
        }

        // Close button at bottom
        bClose = new JButton(strings.get("base.close"));
        bClose.addActionListener(this);
        JPanel bPan = new JPanel(new FlowLayout(FlowLayout.CENTER));  // easy way to center and not stretch width
        bPan.add(bClose);

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(bPan, gbc);
        cpane.add(bPan);

        // Finish dialog setup

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter()
        {
            /** React to Closed, not Closing, to ensure dialog was already dispose()d. */
            public void windowClosed(WindowEvent e)
            {
                if ((nbddListener != null) && ! nbddListenerCalled)
                {
                    nbddListenerCalled = true;
                    EventQueue.invokeLater(new Runnable()
                    {
                        public void run() { nbddListener.dialogDismissed(SOCSpecialItemDialog.this, true); }
                    });
               }
            }
        });
        getRootPane().setDefaultButton(bClose);
        getRootPane().registerKeyboardAction
            (new ActionListener()
            {
                public void actionPerformed(ActionEvent arg0)
                {
                    nbddListenerCalled = true;
                    dispose();
                    if (nbddListener != null)
                        EventQueue.invokeLater(new Runnable()
                        {
                            public void run() { nbddListener.dialogDismissed(SOCSpecialItemDialog.this, true); }
                        });
                }
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Ensure dialog is validated; otherwise the top and bottom are sometimes clipped
        super.pack();
        setSize(400, 400);  // set a nonzero minimum before validate
        validate();
    }

    /**
     * Build and return a text component to display the requirements for this special item.
     *<P>
     * This method is mentioned in the SOCSpecialItem.Requirement javadoc;
     * if it's refactored or renamed, please update that javadoc.
     *
     * @param reqs  Special item requirements to build text for using i18n string keys
     *     (see {@link SOCSpecialItemDialog class javadoc}). If null or empty, returns new {@link JLabel}("").
     * @return  The requirements text in a new {@link JLabel} (single requirement)
     *     or {@link JTextArea} (multiple requirements, shown one per line).
     *     The component will have a plain font and an empty border 3px wide on the left and right,
     *     0px top and bottom.
     */
    private JComponent buildRequirementsText(final List<SOCSpecialItem.Requirement> reqs)
    {
        if ((reqs == null) || reqs.isEmpty())
            return new JLabel("");

        final int n = reqs.size();
        final String[] descStrings = new String[n];
        for (int i = 0; i < n; ++i)
        {
            final SOCSpecialItem.Requirement req = reqs.get(i);
            final String reqStr;
            switch (req.reqType)
            {
            case 'C':
                // fall through to 'S'
            case 'S':
                String sLoc;
                final String sKey;
                if (req.atPort)
                {
                    sLoc = strings.get("game.aport");
                } else if (req.atCoordList != null) {
                    try
                    {
                        sLoc = strings.get("board.nodelist._SC_WOND." + req.atCoordList);
                            // board.nodelist._SC_WOND.N1 -> "The Wasteland", etc
                    } catch (MissingResourceException e) {
                        sLoc = req.atCoordList;  // lookup failed
                    }
                } else {
                    sLoc = null;
                }
                if (req.reqType == 'C')
                    sKey = (sLoc != null) ? "specitem.req.c.at" : "specitem.req.c";
                else
                    sKey = (sLoc != null) ? "specitem.req.s.at" : "specitem.req.s";
                reqStr = strings.get(sKey, req.count, sLoc);
                break;

            case 'L':
                reqStr = strings.get("specitem.req.rl", req.count);
                break;

            default:  // case 'V'; default is required for compiler: final reqStr
                reqStr = strings.get("specitem.req.vp", req.count);
            }
            descStrings[i] = reqStr;
        }

        final JComponent ret;

        if (n == 1)
        {
            ret = new JLabel(descStrings[0]);
        } else {
            // wrap one per line
            StringBuilder sb = new StringBuilder(descStrings[0]);
            for (int i = 1; i < n; ++i)
            {
                sb.append("\n");
                sb.append(descStrings[i]);
            }
            JTextArea pmsg = new JTextArea(sb.toString());
            pmsg.setEditable(false);
            pmsg.setLineWrap(true);
            pmsg.setWrapStyleWord(true);
            ret = pmsg;
        }

        ret.setBorder(new EmptyBorder(0, 3, 0, 3));
        ret.setBackground(null);  // will inherit from panel
        ret.setForeground(null);

        return ret;
    }

    /**
     * Call {@link java.awt.Container#doLayout()}, then if this is the first time placing it,
     * place the dialog in the top-center of the game window.
     */
    public void doLayout()
    {
        super.doLayout();

        try
        {
            if (! didSetLocation)
            {
                int piX = pi.getInsets().left;
                int piY = pi.getInsets().top;
                final int piWidth = pi.getSize().width - piX - pi.getInsets().right;
                piX += pi.getLocation().x;
                piY += pi.getLocation().y;
                setLocation(piX + ((piWidth - getWidth()) / 2), piY + 50);

                didSetLocation = true;
            }
        }
        catch (NullPointerException e) {}
    }

    /**
     * A button was clicked to choose a special item such as a Wonder.
     * Find the right {@link #buttons}[i] and send the server a pick-item command.
     *
     * @param e AWT event, from a button source
     */
    public void actionPerformed(ActionEvent e)
    {
        try {
            final Object src = e.getSource();
            if (src == null)
                return;

            if (src == bClose)
            {
                nbddListenerCalled = true;
                dispose();
                if (nbddListener != null)
                    EventQueue.invokeLater(new Runnable()
                    {
                        public void run() { nbddListener.dialogDismissed(SOCSpecialItemDialog.this, true); }
                    });

                return;
            }

            int i;
            for (i = 0; i < buttons.length; ++i)
            {
                if (src == buttons[i])
                    break;
            }

            if (i < buttons.length)
            {
                // assumes typeKey == _SC_WOND -> always sends PICK
                // or (6-player) asks for Special Building Phase on
                // other players' turns. Eventually other typeKeys
                // may allow other actions besides PICK, or actions
                // during other players' turns.

                final GameMessageSender messageSender = pi.getClient().getGameMessageSender();
                boolean askedSBP = false;
                if (! pi.clientIsCurrentPlayer())
                {
                    final int cpn = pi.getClientPlayerNumber();
                    if ((cpn != -1) && ga.canAskSpecialBuild(cpn, false))
                    {
                        // Can't build on other players' turns, but can request SBP.
                        // Consistent with what happens when clicking Buy for a road,
                        // city, etc on another player's turn in 6-player game.
                        messageSender.buildRequest(ga, -1);
                        askedSBP = true;
                    }
                    // else: Fall through, send PICK request, server will
                    // send feedback it can't be built right now: That way
                    // this dialog's feedback is consistently delivered.
                }

                if (! askedSBP)
                    messageSender.pickSpecialItem(ga, typeKey, 1 + i, 0);

                nbddListenerCalled = true;
                dispose();
                if (nbddListener != null)
                    EventQueue.invokeLater(new Runnable()
                    {
                        public void run() { nbddListener.dialogDismissed(SOCSpecialItemDialog.this, false); }
                    });
            }

        } catch (Exception ex) {
            pi.chatPrintStackTrace(ex);
        }
    }

    /**
     * Set or clear the optional {@link PlayerClientListener.NonBlockingDialogDismissListener listener}
     * for when this dialog is no longer visible.
     * @param li  Listener, or {@code null} to clear
     */
    public void setNonBlockingDialogDismissListener
        (PlayerClientListener.NonBlockingDialogDismissListener li)
    {
        nbddListener = li;
    }

}
