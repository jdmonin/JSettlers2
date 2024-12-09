/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 *
 * This file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2012-2014,2017,2020,2022-2024 Jeremy D Monin <jeremy@nand.net>
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
package soc.client.stats;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

import soc.client.ColorSquare;
import soc.client.ColorSquareLarger;
import soc.client.SOCPlayerClient;
import soc.client.SOCPlayerInterface;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.message.SOCBankTrade;  // for VERSION_FOR_OMIT_PLAYERELEMENTS
import soc.message.SOCDiceResultResources;  // for VERSION_FOR_DICERESULTRESOURCES
import soc.message.SOCPlayerStats;  // for STYPE_TRADES

/**
 * Game Statistics frame.  Shows misc stats (dice roll histogram, number of rounds).
 * If this stays visible as the game is played, the stats will update.
 * If was visible before the client player sits, call
 * {@link #statsUpdated(SOCGameStatistics, soc.client.stats.SOCGameStatistics.GameStatisticsEvent) statsUpdated(null, null)}
 * when they do so.
 */
@SuppressWarnings("serial")
public class GameStatisticsFrame extends JFrame implements SOCGameStatistics.Listener
{
    /** i18n text strings */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    private final SOCPlayerInterface pi;

    /**
     * Display scale, from PI; its displayScale field isn't visible to us.
     * @since 2.7.00
     */
    private final int displayScale;

    private SOCGameStatistics.ListenerRegistration reg;
    private RollPanel rollPanel;
    private MiscStatsPanel miscPanel;

    /**
     * Client player stats, from {@link SOCPlayer#getResourceRollStats()} and {@link SOCPlayer#getResourceTradeStats()}.
     * Null if server is too old for client to support accurately updating those stats.
     * (Workarounds to support older servers are technically possible, but users are unlikely to encounter such servers.)
     * @since 2.7.00
     */
    private YourPlayerPanel yourPlayerPanel;

    private SOCGameStatistics lastStats;

    public GameStatisticsFrame(final SOCPlayerInterface pi, final int displayScale)
    {
        setTitle(strings.get("dialog.stats.title"));  // "Game Statistics"
        this.pi = pi;
        this.displayScale = displayScale;

        Container cpane = getContentPane();
        cpane.setLayout(new BoxLayout(cpane, BoxLayout.Y_AXIS));
        createControls();
        pack();
    }

    public void register(SOCGameStatistics stats)
    {
        reg = stats.addListener(this);
        statsUpdated(stats, null);
    }

    @Override
    public void dispose()
    {
        if (reg != null)
            reg.unregister();
        super.dispose();
    }

    /**
     * Update the displayed statistics.
     * Call when an event occurs or when client player has just sat down.
     * @param stats Stats data to update with, or {@code null} to update from game data where possible
     * @param event Event details and type, or {@code null} to update all stats
     */
    public void statsUpdated(final SOCGameStatistics stats, final SOCGameStatistics.GameStatisticsEvent event)
    {
        if (stats != null)
            lastStats = stats;

        if ((event == null) || (event instanceof SOCGameStatistics.DiceRollEvent))
        {
            rollPanel.refresh(stats);
            miscPanel.refreshFromGame();
        }
        if (yourPlayerPanel != null)
            yourPlayerPanel.refreshFromGame(event);
    }

    public void statsDisposing()
    {
        dispose();
    }

    /** Add rows of stats, register ESC keyboard shortcut */
    private void createControls()
    {
        JTabbedPane tabs = new JTabbedPane();
        rollPanel = new RollPanel();
        tabs.addTab(strings.get("dialog.stats.dice_rolls.title"), rollPanel);  // "Dice Rolls"
        getContentPane().add(tabs);

        final int serverVersion = pi.getClient().getServerVersion(pi.getGame());
        if (serverVersion >= SOCDiceResultResources.VERSION_FOR_DICERESULTRESOURCES)
        {
            tabs = new JTabbedPane();
            yourPlayerPanel = new YourPlayerPanel(serverVersion);
            tabs.addTab(strings.get("dialog.stats.your_player.title"), yourPlayerPanel);  // "Your Player"
            getContentPane().add(tabs);
        }

        tabs = new JTabbedPane();
        miscPanel = new MiscStatsPanel();
        tabs.addTab(strings.get("dialog.stats.other_stats.title"), miscPanel);  // "Other Stats"
        getContentPane().add(tabs);

        getRootPane().registerKeyboardAction
            (new ActionListener()
            {
                public void actionPerformed(ActionEvent arg0)
                {
                    dispose();
                }
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Create a row in our panel to display one stat, and return the JLabel that will hold its value.
     * @param label  The stat's name
     * @param gbc    Our layout manager's GBC
     * @return  The JLabel, currently blank, that will hold the stat's value
     */
    public static JLabel createStatControlRow
        (final int rownum, final String label, GridBagConstraints gbc, Container addTo)
    {
        gbc.gridy = rownum;

        JLabel jl = new JLabel(label);
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.LINE_START;
        addTo.add(jl, gbc);

        jl = new JLabel();  // will contain value
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        addTo.add(jl, gbc);

        return jl;
    }

    /**
     * Miscellaneous other statistics, such as number of game rounds so far.
     * Refresh with {@link #refreshFromGame()}.
     * @author jdmonin
     */
    private class MiscStatsPanel extends JPanel
    {
        /** Current round number */
        private JLabel roundNum;

        public MiscStatsPanel()
        {
            super(true);
            GridBagLayout gbl = new GridBagLayout();
            GridBagConstraints gbc = new GridBagConstraints();
            setLayout(gbl);
            gbc.ipadx = 8 * displayScale;
            gbc.ipady = 8 * displayScale;
            roundNum = createStatControlRow(0, strings.get("dialog.stats.current_round"), gbc, this);  // "Current Round:"
        }

        /** Refresh our statistics from the current game. */
        public void refreshFromGame()
        {
            final SOCGame ga = pi.getGame();
            roundNum.setText(Integer.toString(ga.getRoundCount()));
        }
    }

    /**
     * Client player's roll and resource stats, from {@link SOCPlayer#getResourceRollStats()}
     * and {@link SOCPlayer#getResourceTradeStats()}.
     * Refresh with {@link #refreshFromGame(SOCGameStatistics.GameStatisticsEvent)}
     * when stats change or after sitting down to play.
     * @author jdmonin
     * @since 2.7.00
     */
    private class YourPlayerPanel extends JPanel
    {
        private SOCPlayer pl;
        private final ColorSquare[] resRolls = new ColorSquare[5];
        /** Gold gains from dice rolls; initially hidden until refreshFromGame sees gold; not null */
        private final ColorSquare resRollsGold;
        private final JLabel resRollsGoldLab;
        /** All trade resource statistics, or {@code null} if server is too old to send them */
        private final JLabel resTrades;

        /**
         * Create the stats panel. Server must be new enough for client to support accurately updating stats.
         * @param serverVersion  Server version, from {@link SOCPlayerClient#getServerVersion(SOCGame)}
         * @throws IllegalArgumentException  if {@code serverVersion} &lt; 2.0.00
         *     ({@link SOCDiceResultResources#VERSION_FOR_DICERESULTRESOURCES})
         */
        public YourPlayerPanel(final int serverVersion)
            throws IllegalArgumentException
        {
            super(true);

            if (serverVersion < SOCDiceResultResources.VERSION_FOR_DICERESULTRESOURCES)
                throw new IllegalArgumentException("serverVersion");

            final Border pad4 = BorderFactory.createEmptyBorder
                (4 * displayScale, 4 * displayScale, 4 * displayScale, 4 * displayScale);
            setBorder(pad4);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            pl = null;

            final Box rollSquaresRow = Box.createHorizontalBox();
            JLabel jl = new JLabel(strings.get("stats.rolls.resource_rolls"));  // "Resource Rolls:"
            jl.setBorder(pad4);
            rollSquaresRow.add(jl);
            rollSquaresRow.add(Box.createHorizontalGlue());

            int sqWidth = ColorSquareLarger.WIDTH_L * displayScale;
            for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; rtype++)
            {
                ColorSquare sq = new ColorSquare(ColorSquare.RESOURCE_COLORS[rtype - 1], 0, sqWidth, sqWidth);
                sq.setMaximumSizeToCurrent();
                resRolls[rtype - 1] = sq;
                rollSquaresRow.add(sq);
            }
            rollSquaresRow.setAlignmentX(0);
            add(rollSquaresRow);

            final Box rollGoldRow = Box.createHorizontalBox();
            resRollsGoldLab = new JLabel(strings.get("stats.gold_gains.title"));  // "From gold hexes:"
            resRollsGoldLab.setBorder(pad4);
            resRollsGoldLab.setVisible(false);
            resRollsGold = new ColorSquare(ColorSquare.GOLD, 0, sqWidth, sqWidth);
            resRollsGold.setVisible(false);
            resRollsGold.setMaximumSizeToCurrent();
            rollGoldRow.add(resRollsGoldLab);
            rollGoldRow.add(Box.createHorizontalGlue());
            rollGoldRow.add(resRollsGold);
            rollGoldRow.setAlignmentX(0);
            add(rollGoldRow);

            // trade stats, from PI stats handler:

            if (serverVersion >= SOCBankTrade.VERSION_FOR_OMIT_PLAYERELEMENTS)
            {
                jl = new JLabel
                    ("<html><B>" + strings.get("game.trade.stats.heading_short") + "</B> "
                     + strings.get("game.trade.stats.heading_give_get") + "</html>");
                    // "Trade stats: Give (clay, ore, sheep, wheat, wood) -> Get (clay, ore, sheep, wheat, wood):"
                jl.setAlignmentX(0);
                jl.setBorder(pad4);
                add(jl);

                resTrades = new JLabel();
                resTrades.setAlignmentX(0);
                resTrades.setBorder(pad4);
                add(resTrades);
            } else {
                resTrades = null;
            }

            refreshFromGame(null);
        }

        /**
         * Refresh our statistics from the current game.
         * @param event Event that's just occurred, or {@code null} to update all stats
         */
        public void refreshFromGame(final SOCGameStatistics.GameStatisticsEvent event)
        {
            boolean hasSetPlayer = false;
            SOCPlayer piPlayer = pi.getClientPlayer();
            if (pl != piPlayer)
            {
                if (pl == null)
                {
                    // TODO one-time un-hide fields/rows as needed
                }

                pl = piPlayer;
                hasSetPlayer = true;
            }

            if (pl == null)
                return;

            if ((event == null) || (event instanceof SOCGameStatistics.ResourceRollReceivedEvent))
            {
                int[] rollStats = pl.getResourceRollStats();
                for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; rtype++)
                    resRolls[rtype - 1].setIntValue(rollStats[rtype]);
                if (rollStats.length > SOCResourceConstants.GOLD_LOCAL)
                {
                    int gold = rollStats[SOCResourceConstants.GOLD_LOCAL];
                    if (gold > 0)
                    {
                        int prevGold = resRollsGold.getIntValue();
                        if (gold != prevGold)
                        {
                            resRollsGold.setIntValue(gold);
                            if (prevGold == 0)
                            {
                                resRollsGoldLab.setVisible(true);
                                resRollsGold.setVisible(true);
                                revalidate();
                            }
                        }
                    }
                }
            }

            if (((event == null) || (event instanceof SOCGameStatistics.ResourceTradeEvent))
                && (resTrades != null))
            {
                StringBuilder sb = new StringBuilder();
                List<String> stats = pi.getClientListener().playerStats(SOCPlayerStats.STYPE_TRADES, null, false, false);
                if (sb != null)
                {
                    sb.append("<html>");
                    boolean any = false;
                    for (String s : stats)
                    {
                        if (any)
                            sb.append("<br>");
                        else
                            any = true;
                        sb.append(s);
                    }
                    sb.append("</html>");
                }

                resTrades.setText(sb.toString());
                if (hasSetPlayer)
                {
                    // text is several lines longer than previous; make the panel and window longer to fit
                    revalidate();
                    EventQueue.invokeLater(new Runnable()
                    {
                        public void run()
                        {
                            GameStatisticsFrame.this.pack();
                        }
                    });
                }
            }
        }
    }

    /**
     * Per-player roll result stats.
     * One element per player number currently/formerly active in game;
     * checkboxes hidden for never-active vacant seats with 0 VP.
     */
    private class RollPanel extends JPanel
    {
        /** Value counters backing {@link #displays}; indexed by dice roll value, 0 and 1 are unused */
        int[] values;
        /** One element per player number in game; checkboxes hidden for never-active seats */
        List<JCheckBox> playerEnabled;
        /** Displays the amounts in {@link #values} */
        private RollBar[] displays;

        public RollPanel()
        {
            super(true);
            //setBorder(BorderFactory.createTitledBorder("Dice Rolls"));
            values = new int[13];
            displays = new RollBar[13];
            createControls();
        }

        public void refresh(SOCGameStatistics stats)
        {
            if (stats == null)
                return;

            StringBuilder sb = new StringBuilder("<html>");
            for (int i = 2; i < rollPanel.values.length; ++i)
            {
                int r = 0;

                if (i > 2)
                    sb.delete(6, sb.length());
                sb.append(strings.get("dialog.stats.dice_rolls.ttip_roll", i));  // "Roll: {0}<br/>"
                final SOCGame ga = pi.getGame();
                for (SOCPlayer p : ga.getPlayers())
                {
                    final int pn = p.getPlayerNumber();
                    if (ga.isSeatVacant(pn) && (p.getPublicVP() == 0))
                        continue;  // player position was always vacant
                    if (! playerEnabled.get(pn).isSelected())
                        continue;  // not showing player's stats

                    final int v = stats.getRollCount(i, pn);
                    if (v == -1)
                        continue;

                    sb.append(getPlayerName(p)).append(": ").append(v).append("<br/>");
                    r += v;
                }
                sb.append("</html>");

                rollPanel.values[i] = r;
                rollPanel.displays[i].setToolTipText(sb.toString());
            }

            rollPanel.repaint();
        }

        private void createControls()
        {
            // any number of rows, one column
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            JPanel selectPanel = new JPanel();
            this.add(selectPanel);
            SOCPlayer[] players = pi.getGame().getPlayers();
            selectPanel.setLayout(new BoxLayout(selectPanel, BoxLayout.X_AXIS));
            JButton all = new JButton(strings.get("dialog.stats.dice_rolls.all"));  // "All" [players]
            selectPanel.add(all);

            playerEnabled = new ArrayList<JCheckBox>();
            final SOCGame ga = pi.getGame();
            for (int pn = 0; pn < pi.getGame().maxPlayers; ++pn)
            {
                JCheckBox cb = new JCheckBox(getPlayerName(players[pn]), true);
                if (ga.isSeatVacant(pn) && (ga.getPlayer(pn).getPublicVP() == 0))
                    cb.setVisible(false);  // hidden but still present in playerEnabled at playerNumber
                else
                    cb.addActionListener(new CheckActionListener());
                selectPanel.add(cb);
                playerEnabled.add(cb);
            }

            all.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e)
                {
                    for (JCheckBox cb : playerEnabled)
                        cb.setSelected(true);
                    refresh(lastStats);
                }
            });

            JPanel displayPanel = new JPanel(true);
            this.add(displayPanel);

            // one spot for each dice roll
            displayPanel.setLayout(new GridLayout(2, 12-2));

            for (int i=2; i<=12; ++i)
            {
                RollBar lbl = new RollBar();
                displays[i] = lbl;
                displayPanel.add(lbl);
            }

            for (int i=2; i<=12; ++i)
            {
                JLabel lbl = new JLabel(String.valueOf(i));
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                displayPanel.add(lbl);
            }
        }

        /**
         * Get player name, or fall back to "Player 2" format if name is null or ""
         * @param pl  Player to get name; not null
         * @return Player name, or localized "Player #" with {@link SOCPlayer#getPlayerNumber()}
         * @since 2.4.00
         */
        private String getPlayerName(final SOCPlayer pl)
        {
            String plName = pl.getName();
            if ((plName == null) || plName.isEmpty())
                plName = strings.get("base.player_n", pl.getPlayerNumber());  // "Player {0}"

            return plName;
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            int max = 0;
            for (int i = 2; i < values.length; ++i)
                max = Math.max(max, values[i]);

            for (int i = 2; i < values.length; ++i)
                if (max <= 0)
                    displays[i].setValue(0,0);
                else
                    displays[i].setValue(values[i], max);
            super.paintComponent(g);
        }
    }

    /** One dice number's bar chart bar within the roll statistics row. */
    private class RollBar extends JComponent
    {
        private double percent;
        private int value;

        /**
         * Create a RollBar with default value 0% of the shared maximum.
         */
        public RollBar()
        {
            final Dimension size = new Dimension(20, 30);

            setDoubleBuffered(true);
            setMinimumSize(size);
            setPreferredSize(size);
            //setBorder(new EtchedBorder());
            value = 0;
        }

        /**
         * Update the bar's value and the shared maximum. Calls repaint.
         * @param value  New value for bar
         * @param max  Current max value across all bars, to redraw with proper vertical scale
         */
        public void setValue(int value, int max)
        {
            if (max < 1)
                max = 1;
            if (value < 0)
                value = 0;

            this.value = value;
            percent = (double)value / max;

            if (percent < 0)
                percent = 0;
            if (percent > 1)
                percent = 1;

            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Dimension sz = getSize();
            g.setColor(Color.BLACK);
            g.drawRect(0,0,sz.width-1, sz.height-1);

            g.setColor(Color.BLUE);
            int h = (int)((sz.height - 1) * percent);
            g.fillRect(1, sz.height - h, sz.width-2, sz.height-2);

            if (value > 0)
            {
                Font BAR_FONT = new Font("SansSerif", Font.PLAIN, 12);
                final int fHeight = 2 + g.getFontMetrics(BAR_FONT).getAscent();
                final int y;
                if (h < fHeight)
                {
                    g.setColor(Color.BLACK);  // visible against light background
                    y = sz.height - h - 2;    // positioned above value bar
                } else {
                    g.setColor(Color.CYAN);   // visible against dark-blue value bar
                    y = sz.height - 2;        // positioned at bottom of value bar
                }
                g.setFont(BAR_FONT);
                g.drawString(String.valueOf(value), 2, y);
            }
        }
    }

    private class CheckActionListener implements ActionListener
    {
        public void actionPerformed(ActionEvent e)
        {
            rollPanel.refresh(lastStats);
        }
    }

}
