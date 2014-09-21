/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 *
 * This file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2012-2014 Jeremy D Monin <jeremy@nand.net>
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

import soc.client.SOCPlayerInterface;
import soc.game.SOCGame;
import soc.game.SOCPlayer;

/**
 * Game Statistics frame.  Shows misc stats (dice roll histogram, number of rounds).
 * If this stays visible as the game is played, the stats will update.
 */
@SuppressWarnings("serial")
public class GameStatisticsFrame extends JFrame implements SOCGameStatistics.Listener
{
    /** i18n text strings */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    private final SOCPlayerInterface pi;
    private SOCGameStatistics.ListenerRegistration reg;
    private RollPanel rollPanel;
    private MiscStatsPanel miscPanel;
    private SOCGameStatistics lastStats;

    public GameStatisticsFrame(SOCPlayerInterface pi)
    {
        setTitle(strings.get("dialog.stats.title"));  // "Game Statistics"
        this.pi = pi;

        Container cpane = getContentPane();
        cpane.setLayout(new BoxLayout(cpane, BoxLayout.Y_AXIS));
        createControls();
        pack();
    }
    
    public void register(SOCGameStatistics stats)
    {
        reg = stats.addListener(this);
        statsUpdated(stats);
    }
    
    @Override
    public void dispose()
    {
        if (reg != null)
            reg.unregister();
        super.dispose();
    }
    
    public void statsUpdated(SOCGameStatistics stats)
    {
        lastStats = stats;
        rollPanel.refresh(stats);
        miscPanel.refreshFromGame();
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
            gbc.ipadx = 8;
            gbc.ipady = 8;
            roundNum = createStatControlRow(0,  strings.get("dialog.stats.current_round"), gbc);  // "Current Round:"
        }

        /**
         * Create a row in our panel to display one stat, and return the JLabel that will hold its value.
         * @param label  The stat's name
         * @param gbc    Our layout manager's GBC
         * @return  The JLabel, currently blank, that will hold the stat's value
         */
        private JLabel createStatControlRow
            (final int rownum, final String label, GridBagConstraints gbc)
        {
            gbc.gridy = rownum;

            JLabel jl = new JLabel(label);
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.LINE_START;
            add(jl, gbc);

            jl = new JLabel();  // will contain value
            gbc.gridx = 1;
            gbc.anchor = GridBagConstraints.LINE_START;
            add(jl, gbc);

            return jl;
        }

        /** Refresh our statistics from the current game. */
        public void refreshFromGame()
        {
            final SOCGame ga = pi.getGame();
            roundNum.setText(Integer.toString(ga.getRoundCount()));
        }
    }

    private class RollPanel extends JPanel
    {
        /** Value counters backing {@link #displays}; indexed by dice roll value, 0 and 1 are unused */
        int[] values;
        /** One element per player number in game; vacant seats' checkboxes are hidden */
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
            
            for (int i=2; i<rollPanel.values.length; ++i)
            {
                int r = 0;
                StringBuilder sb = new StringBuilder();
                sb.append(strings.get("dialog.stats.dice_rolls.ttip_roll", i));  // "Roll: {0}<br/>"
                final SOCGame ga = pi.getGame();
                for (SOCPlayer p : ga.getPlayers())
                {
                    final int pn = p.getPlayerNumber();
                    if (ga.isSeatVacant(pn))
                        continue;  // player name is null
                    if (! playerEnabled.get(pn).isSelected())
                        continue;  // not showing player's stats

                    final Integer v = stats.getRollCount(i, pn);
                    if (v != null)
                    {
                        sb.append(p.getName()).append(": ").append(v.intValue()).append("<br/>");
                        r += v.intValue();
                    }
                }
                
                String str = null;
                if (sb.length() > 0)
                {
                    sb.insert(0, "<html>");
                    sb.append("</html>");
                    str = sb.toString();
                }
                
                rollPanel.values[i] = r;
                rollPanel.displays[i].setToolTipText(str);
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
            for (int pn = 0; pn < pi.getGame().maxPlayers; ++pn)
            {
                JCheckBox cb = new JCheckBox(players[pn].getName(), true);
                if (pi.getGame().isSeatVacant(pn))
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
//            displayPanel.setLayout(new BoxLayout(displayPanel, BoxLayout.X_AXIS));

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
        
        @Override
        protected void paintComponent(Graphics g)
        {
            int max = 0;
            for (int i=2; i<values.length; ++i)
                max = Math.max(max, values[i]);
            
            for (int i=2; i<values.length; ++i)
                if (max <= 0)
                    displays[i].setValue(0,0);
                else
                    displays[i].setValue(values[i], max);
            super.paintComponent(g);
        }
    }
    
    private class RollBar extends JComponent
    {
        private double percent;
        private int value;
        
        public RollBar()
        {
            setDoubleBuffered(true);
            //setBorder(new EtchedBorder());
            value = 0;
        }
        
        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(20, 30);
        }

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
