/*
 * nand.net i18n utilities for Java: Property file editor for translators (side-by-side source and destination languages).
 * This file Copyright (C) 2013 Jeremy D Monin <jeremy@nand.net>
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
 * The maintainer of this program can be reached at jeremy@nand.net
 */

package net.nand.util.i18n.gui;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Main startup for {@link PropertiesTranslatorEditor}.
 * Gives buttons with choice of new, open, open backup, exit.
 * Prompts whether to open 1 or 2 files, etc.
 * Work in progress.
 */
@SuppressWarnings("serial")
public class PTEMain extends JFrame
    implements ActionListener
{
    private final JPanel btns;
    private JButton bNew, bOpen, bOpenSrcDest, bExit;

    /**
     * If there's 1 or 2 properties files on the command line, try to open it.
     * Otherwise, bring up the startup buttons. 
     */
    public static void main(String[] args)
    {
        if (args.length >= 2)
        {
            new PropertiesTranslatorEditor(args[0], args[1]).init();
        } else if (args.length == 1) {
            new PropertiesTranslatorEditor(args[0]).init();
        } else {
            new PTEMain().initAndShow();
        }
    }

    public PTEMain()
    {
        super("PropertiesTranslatorEditor");

        btns = new JPanel();
        btns.setLayout(new BoxLayout(btns, BoxLayout.PAGE_AXIS));
        btns.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));

        btns.add(new JLabel("Welcome to PropertiesTranslatorEditor. Please choose:"));
        bNew = addBtn("New...", KeyEvent.VK_N);
        bOpen = addBtn("Open...", KeyEvent.VK_O);
        bOpenSrcDest = addBtn("Open Src+Dest...", KeyEvent.VK_D);
        bExit = addBtn("Exit", KeyEvent.VK_X);

        getContentPane().add(btns);
        getRootPane().setDefaultButton(bOpen);
    }

    private void initAndShow()
    {
        pack();
        setVisible(true);
    }

    /**
     * Open these file(s) in a {@link PropertiesTranslatorEditor}.
     * 
     * @param chooseFileSrc   Source properties file, or {@code null}
     * @param chooseFileDest  Destination properties file; can't be {@code null} or returns immediately
     * @param isNew           True if {@code chooseFileDest} should be created; not implemented yet.  Assumes
     *                        dest name follows properties naming standards for language and region.
     */
    public void openPropsEditor(File chooseFileSrc, File chooseFileDest, final boolean isNew)
    {
        if (chooseFileDest == null)
            return;

        // TODO handle isNew: create file?

        final String dpath = chooseFileDest.getAbsolutePath();
        if (chooseFileSrc == null)
        {
            new PropertiesTranslatorEditor(dpath).init();
        } else {
            final String spath = chooseFileSrc.getAbsolutePath();
            new PropertiesTranslatorEditor(spath, dpath).init();
        }
    }

    /**
     * Add this button to the layout.
     * @param label Button's label
     * @param vkN  Shortcut mnemonic from {@link KeyEvent}
     * @return the new button
     */
    private JButton addBtn(final String label, final int vkN)
    {
        JButton b = new JButton(label);
        b.setMnemonic(vkN);
        btns.add(b);
        b.addActionListener(this);
        Dimension size = b.getPreferredSize();
        size.width = Short.MAX_VALUE;
        b.setMaximumSize(size);
        return b;
    }

    /** Handle button clicks. */
    public void actionPerformed(ActionEvent e)
    {
        final Object src = e.getSource();
        if (src == bNew)
        {
            // TODO implement; need to enforce naming standards, or have dialog to ask src/dest lang+region
            System.err.println("Not implmented yet");
            // openPropsEditor(null, chooseFile(true), true);
        }
        else if (src == bOpen)
        {
            openPropsEditor(null, chooseFile(false), false);
        }
        else if (src == bOpenSrcDest)
        {
            // TODO implement; need 2 file choosers, or 1 dest chooser & pick a parent or other src
            System.err.println("Not implmented yet");
        }
        else if (src == bExit)
        {
            System.exit(0);
        }
    }

    /**
     * Choose a file to open or save.
     * @param forNew  If true, use Save dialog, otherwise Open dialog
     * @return   the chosen file, or null if nothing was chosen
     */
    private File chooseFile(final boolean forNew)
    {
        // TODO filtering: setFileFilter, addChoosableFileFilter, etc
        final JFileChooser fc = new JFileChooser();
        int returnVal;
        if (forNew)
            returnVal = fc.showSaveDialog(this);
        else
            returnVal = fc.showOpenDialog(this);

        if (returnVal != JFileChooser.APPROVE_OPTION)
            return null;

        File file = fc.getSelectedFile();
        return file;
    }

}
