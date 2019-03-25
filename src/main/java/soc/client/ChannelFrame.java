/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2012-2013,2017,2019 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012-2013 Paul Bilnoski <paul@bilnoski.net>
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;


/** The chat channel window
 *  @version 2.0 (no GridbagLayout) with textwrapping and customized window
 *  @author <A HREF="http://www.nada.kth.se/~cristi">Cristian Bogdan</A>
 */
@SuppressWarnings("serial")
/*package*/ class ChannelFrame extends JFrame
{
    public SnippingTextArea ta;
    public JTextField tf;
    public JList<String> lst;

    final MainDisplay md;
    String cname;
    Vector<String> history = new Vector<String>();
    int historyCounter = 1;
    boolean down = false;

    /** i18n text strings */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /** Build a frame with the given title, belonging to the given frame/applet */
    public ChannelFrame(final String t, final MainDisplay md)
    {
        super(strings.get("channel.channel", t));
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        final Container cp = getContentPane();

        final Font panelFont = new Font("SansSerif", Font.PLAIN, 12);

        cp.setLayout(new BorderLayout(2, 2));
        cp.setFont(panelFont);

        ta = new SnippingTextArea(20, 40, 100);  // minimum width is based on number of character columns
        tf = new JTextField(strings.get("base.please.wait"));  // "Please wait..."
        lst = new JList<String>(new DefaultListModel<String>());
        lst.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lst.setSize(new Dimension(180, 200));
        lst.setMinimumSize(new Dimension(30, 200));

        this.md = md;
        cname = t;
        ta.setFont(panelFont);
        // on Windows, make sure ta keeps its usual black/white colors and not grayed-out un-editable colors:
        {
            final Color bg = ta.getBackground(), fg = ta.getForeground();
            ta.setEditable(false);
            ta.setBackground(bg);
            ta.setForeground(fg);
        }
        tf.setEditable(false);

        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ta, new JScrollPane(lst));
        add(sp, BorderLayout.CENTER);
        add(tf, BorderLayout.SOUTH);

        setSize(640, 480);
        setMinimumSize(getSize());
        setLocationByPlatform(true);
        sp.setDividerLocation(500);
        history.addElement("");

        tf.addActionListener(new InputActionListener());
        tf.addKeyListener(new InputKeyListener());
        addWindowListener(new CFWindowListener());
    }

    /**
     * Add some text.
     * @param s  Text to add; will use {@code "\n"} to split to multiple lines
     */
    public void print(String s)
    {
        StringTokenizer st = new StringTokenizer(s, "\n", false);

        while (st.hasMoreElements())
        {
            ta.append(st.nextToken() + "\n");
        }

    }

    /** an error occured, stop editing */
    public void over(String s)
    {
        tf.setEditable(false);
        tf.setText(s);
    }

    /** start */
    public void began()
    {
        tf.setEditable(true);
        tf.setText("");
    }

    /** add a member to the group */
    public void addMember(String s)
    {
        final DefaultListModel<String> lm = (DefaultListModel<String>) lst.getModel();

        synchronized(lst.getTreeLock())
        {
            int i;

            for (i = lm.getSize() - 1; i >= 0; i--)
            {
                if (lm.get(i).compareTo(s) < 0)
                    break;
            }

            lm.add(i + 1, s);
        }
    }

    /** delete a member from the channel */
    public void deleteMember(String s)
    {
        final DefaultListModel<String> lm = (DefaultListModel<String>) lst.getModel();

        synchronized(lst.getTreeLock())
        {
            for (int i = lm.getSize() - 1; i >= 0; i--)
            {
                if (lm.get(i).equals(s))
                {
                    lm.remove(i);

                    break;
                }
            }
        }
    }

    /** send the message that was just typed in, or start editing a private
     * message */
    private class InputActionListener implements ActionListener
    {
        public void actionPerformed(ActionEvent e)
        {
            String s = tf.getText().trim();

            if (s.length() > 0)
            {
                tf.setText("");
                md.sendToChannel(cname, s + "\n");

                history.setElementAt(s, history.size() - 1);
                history.addElement("");
                historyCounter = 1;
            }
        }
    }

    private class InputKeyListener extends KeyAdapter
    {
        public void keyPressed(KeyEvent e)
        {
            int hs = history.size();
            int key = e.getKeyCode();

            if ((key == KeyEvent.VK_UP) && (hs > historyCounter))
            {
                if (historyCounter == 1)
                {
                    history.setElementAt(tf.getText(), hs - 1);
                }

                historyCounter++;
                tf.setText(history.elementAt(hs - historyCounter));
            }
            else if ((key == KeyEvent.VK_DOWN) && (historyCounter > 1))
            {
                historyCounter--;
                tf.setText(history.elementAt(hs - historyCounter));
            }
        }
    }

    /** when the window is destroyed, tell the applet to leave the group */
    private class CFWindowListener extends WindowAdapter
    {
        public void windowClosing(WindowEvent e)
        {
            md.getClient().leaveChannel(cname);
            dispose();
        }
        public void windowOpened(WindowEvent e)
        {
            tf.requestFocus();
        }
    }

}
