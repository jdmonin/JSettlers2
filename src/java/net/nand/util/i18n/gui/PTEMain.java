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
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * Main startup for {@link PropertiesTranslatorEditor}.
 * Gives buttons with choice of new, open, open backup, exit.
 * Prompts whether to open 1 or 2 files, etc.
 *<P>
 * Work in progress.  See {@link PropertiesTranslatorEditor} for current limitations.
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
     * Used and set in {@link #chooseFile(boolean, String)}.
     */
    private File lastEditedDir;

    /**
     * Try to edit a destination file by finding its matching source file's name via the
     * {@link PropertiesTranslatorEditor#PropertiesTranslatorEditor(String) PropertiesTranslatorEditor(String)}
     * constructor.  If not found, or if it's a source file instead of a destination, displays an error message
     * and returns false.   Otherwise calls {@link PropertiesTranslatorEditor#init()} to show the pair for editing.
     *
     * @param dest  Destination .properties file (full path or just filename)
     * @param parent  Parent for any MessageDialog shown, or {@code null}
     * @return  True if found and shown for editing, false if error message shown instead
     */
    public static boolean tryEditFromDestOnly(final String dest, final JFrame parent)
    {
        try {
            new PropertiesTranslatorEditor(dest).init();
            return true;
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog
                (parent, "Please select the destination (more specific locale) .properties file, not the source file.",
                 "Select destination, not source", JOptionPane.INFORMATION_MESSAGE);
        } catch (FileNotFoundException e) {
            // wrap error text in case dest is a long path
            JOptionPane.showMessageDialog
                (parent, "Could not find less-specific source locale .properties file on disk\nto match " + dest,
                 "Source .properties file not found", JOptionPane.ERROR_MESSAGE);
        }

        return false;
    }

    /**
     * If there's 1 or 2 properties files on the command line, try to open it.
     * Otherwise, bring up the startup buttons.
     */
    public static void main(String[] args)
    {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}

        if (args.length >= 2)
        {
            new PropertiesTranslatorEditor(args[0], args[1]).init();
        } else {
            if ((args.length == 1) && tryEditFromDestOnly(args[0], null))
                return;
                // if can't open args[0], shows error and falls through to PTEMain

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
            (parent, "PropertiesTranslatorEditor is a side-by-side editor for translators,\nshowing each key's value in the source and destination languages next to each other.",
             "About PropertiesTranslatorEditor", JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Initialize layout and fields.  Does not pack or make visible; call {@link #initAndShow()} for that.
     */
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
        bNew = addBtn(btns, this, "New...", KeyEvent.VK_N);
        bNew.setEnabled(false);  // TODO add this functionality
        bOpen = addBtn(btns, this, "Open Dest...", KeyEvent.VK_O);
        bOpenDestSrc = addBtn(btns, this, "Open Dest + Src...", KeyEvent.VK_D);
        bAbout = addBtn(btns, this, "About", KeyEvent.VK_A);
        bExit = addBtn(btns, this, "Exit", KeyEvent.VK_X);

        getContentPane().add(btns);
        getRootPane().setDefaultButton(bOpen);
    }

    /**
     * Pack and make visible.
     */
    public void initAndShow()
    {
        pack();
        setVisible(true);
    }

    /**
     * Open these file(s) in a {@link PropertiesTranslatorEditor}.
     * 
     * @param chooseFileSrc   Source properties file, or {@code null} to determine via the
     *                        {@link PropertiesTranslatorEditor#PropertiesTranslatorEditor(String) PropertiesTranslatorEditor(String)}
     *                        constructor.  If {@code null} and not found, displays an error message.
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
            tryEditFromDestOnly(dpath, this);  // calls new PropertiesTranslatorEditor(dpath).init() or shows error message
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
     * @param btns  Add to this button panel
     * @param lsnr  Add this action listener to the button
     * @param label Button's label
     * @param vkN  Shortcut mnemonic from {@link KeyEvent}
     * @return the new button
     */
    private static JButton addBtn(final JPanel btns, final ActionListener lsnr, final String label, final int vkN)
    {
        JButton b = new JButton(label);
        b.setMnemonic(vkN);
        btns.add(b);
        b.addActionListener(lsnr);
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
            // openPropsEditor(null, chooseFile(true, null), true);
        }
        else if (src == bOpen)
        {
            openPropsEditor(null, chooseFile(false, null), false);
        }
        else if (src == bOpenDestSrc)
        {
            clickedOpenDestSrc();
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
     * Handle a click on the "Open Dest + Src" button.  Create and show the dialog to choose 2 property files.
     */
    private final void clickedOpenDestSrc()
    {
        // TODO implement; need 2 file choosers, or 1 dest chooser & pick a parent or other src
        System.err.println("Not implmented yet");
    }

    /**
     * Choose a file to open or save.  Uses and updates {@link #lastEditedDir}.
     * @param forNew  If true, use Save dialog, otherwise Open dialog
     * @param title  Optional dialog title, or {@code null} for default
     * @return   the chosen file, or null if nothing was chosen
     */
    private File chooseFile(final boolean forNew, final String title)
    {
        // TODO filtering: setFileFilter, addChoosableFileFilter, etc
        final JFileChooser fc = new JFileChooser();
        if ((lastEditedDir != null) && lastEditedDir.exists())
            fc.setCurrentDirectory(lastEditedDir);
        if (title != null)
            fc.setDialogTitle(title);

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
