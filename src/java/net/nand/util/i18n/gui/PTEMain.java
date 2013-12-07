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
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * Main startup for {@link PropertiesTranslatorEditor}.
 * Gives buttons with choice of new, open, open backup, exit.
 * Prompts whether to open 1 or 2 files, etc.
 * Work in progress.
 */
@SuppressWarnings("serial")
public class PTEMain extends JFrame
    implements ActionListener, WindowListener
{
    private final JPanel btns;
    private JButton bNew, bOpen, bOpenDestSrc, bAbout, bExit;

    /** {@link Preferences} key for directory of the prefs file most recently edited */
    private final static String LAST_EDITED_DIR = "lastEditedDir";

    private Preferences userPrefs;

    /**
     * 'Current' directory for open/save dialogs, from {@link #LAST_EDITED_DIR}, or null.
     * Tracked here because Java has no standard way to change the JVM's current directory.
     * Used and set in {@link StartupChoiceFrame#chooseFile(boolean)}.
     */
    private File lastEditedDir;

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

    /**
     * Show the PropertiesTranslatorEditor "About" dialog.
     * Static to allow calls from any class.
     * @param parent  Parent for the dialog, as in {@link JOptionPane#showMessageDialog(java.awt.Component, Object)}
     */
    static void showAbout(final JFrame parent)
    {
        JOptionPane.showMessageDialog
            (parent, "PropertiesTranslatorEditor is a side-by-side editor for translators, showing each key's value in the source and destination languages next to each other.",
             "About PropertiesTranslatorEditor", JOptionPane.PLAIN_MESSAGE);
    }

    public PTEMain()
    {
        super("PropertiesTranslatorEditor");

        addWindowListener(this);  // windowClosing: save prefs and exit

        userPrefs = Preferences.userNodeForPackage(PTEMain.class);
        tryGetLastEditedDir();

        btns = new JPanel();
        btns.setLayout(new BoxLayout(btns, BoxLayout.PAGE_AXIS));
        btns.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));

        btns.add(new JLabel("Welcome to PropertiesTranslatorEditor. Please choose:"));
        bNew = addBtn("New...", KeyEvent.VK_N);
        bOpen = addBtn("Open...", KeyEvent.VK_O);
        bOpenDestSrc = addBtn("Open Dest + Src...", KeyEvent.VK_D);
        bAbout = addBtn("About", KeyEvent.VK_A);
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
     * If possible, changes 'current' directory field ({@link #lastEditedDir}) to that of
     * the most recently edited destination file.
     */
    private void tryGetLastEditedDir()
    {
        lastEditedDir = null;

        try
        {
            if (userPrefs == null)
                return;  // unlikely, just in case

            final String dir = userPrefs.get(LAST_EDITED_DIR, null);
            if (dir == null)
                return;

            final File fdir = new File(dir);
            if (fdir.exists() && fdir.isDirectory())
                lastEditedDir = fdir;

        } catch (RuntimeException e) {
            // ignore SecurityException, IllegalStateException: don't change dir
        }
    }

    /**
     * Store 'current' directory {@link #lastEditedDir} to preferences.
     */
    private void trySetDirMostRecent()
    {
        if ((lastEditedDir == null) || (userPrefs == null))
            return;

        try
        {
            userPrefs.put(LAST_EDITED_DIR, lastEditedDir.getAbsolutePath());
        } catch (SecurityException se) {}
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
        else if (src == bOpenDestSrc)
        {
            // TODO implement; need 2 file choosers, or 1 dest chooser & pick a parent or other src
            System.err.println("Not implmented yet");
        }
        else if (src == bAbout)
        {
            showAbout(this);
        }
        else if (src == bExit)
        {
            windowClosing(null);
        }
    }

    /**
     * Choose a file to open or save.  Uses and updates {@link #lastEditedDir}.
     * @param forNew  If true, use Save dialog, otherwise Open dialog
     * @return   the chosen file, or null if nothing was chosen
     */
    private File chooseFile(final boolean forNew)
    {
        // TODO filtering: setFileFilter, addChoosableFileFilter, etc
        final JFileChooser fc = new JFileChooser();
        if ((lastEditedDir != null) && lastEditedDir.exists())
            fc.setCurrentDirectory(lastEditedDir);

        int returnVal;
        if (forNew)
            returnVal = fc.showSaveDialog(this);
        else
            returnVal = fc.showOpenDialog(this);

        if (returnVal != JFileChooser.APPROVE_OPTION)
            return null;

        lastEditedDir = fc.getCurrentDirectory();
        trySetDirMostRecent();

        File file = fc.getSelectedFile();
        return file;
    }

    /**
     * Save {@link #userPrefs} if possible, dispose of the main button window,
     * and call {@link System#exit(int) System.exit(0)}.
     * @param e  Event, ignored (null is okay)
     */
    public void windowClosing(WindowEvent e)
    {
        try
        {
            if (userPrefs != null)
                userPrefs.flush();
        } catch (BackingStoreException ex) {}  // OK if we can't save last-opened-location pref

        dispose();
        System.exit(0);
    }

    public void windowActivated(WindowEvent e) {}
    public void windowClosed(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowOpened(WindowEvent e) {}

}
