/*
 * nand.net i18n utilities for Java: Property file editor for translators (side-by-side source and destination languages).
 * This file Copyright (C) 2013-2014,2016 Jeremy D Monin <jeremy@nand.net>
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
 **/
package net.nand.util.i18n.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.TextComponent;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultCellEditor;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.text.JTextComponent;

import net.nand.util.i18n.ParsedPropsFilePair;
import net.nand.util.i18n.PropsFileParser;
import net.nand.util.i18n.PropsFileWriter;
import net.nand.util.i18n.mgr.StringManager;

/**
 * Property file editor for translators (side-by-side source and destination languages).
 * Presents the source and destination language keys and values.
 * Highlights values that still need to be translated into the destination.
 * Saves in the ISO-8859-1 encoding required for .properties files, escaping unicode characters where needed.
 *<P>
 * The main startup class for this package is {@link PTEMain}, which has buttons for New, Open, About, etc.
 *<P>
 * Work in progress. Current limitations:
 *<UL>
 * <LI> Can only change string values, not key names
 * <LI> Can't delete or move lines in the files
 * <LI> Can only edit existing files, not create new ones
 * <LI> Search the source for {@code TODO} for other minor items
 *</UL>
 * There are other properties editors out there, I wanted to see what writing one would be like.
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class PropertiesTranslatorEditor
    implements ActionListener
{

    /** i18n text strings; if null, call {@link #initStringManager()} to initialize. */
    static StringManager strings;

    /**
     * Pair of properties files being edited, and their contents.
     * The files are {@link ParsedPropsFilePair#srcFile pair.srcFile}
     * and {@link ParsedPropsFilePair#destFile pair.destFile}.
     *<P>
     * {@code null} if we start with no parameters, and wait for a file-open dialog from {@link PTEMain}.
     */
    private ParsedPropsFilePair pair;

    /** main window, set up in {@link #init()} */
    private JFrame jfra;

    /** main window's pane with {@link #jtab}, created in {@link #init()}, populated in {@link #showPairInPane()} */
    private JScrollPane jpane;

    /** Help button, brings up a brief text message dialog */
    private JButton bHelp;

    /** Save button for properties file from current editor contents; disabled until changes are made */
    private JButton bSaveSrc, bSaveDest;

    /** Menu items to add a line above or below this line */
    private JMenuItem menuAddAbove, menuAddBelow;

    /**
     * Menu item to copy cell contents to clipboard, or null if not allowed.
     * {@link #init()} calls {@link SecurityManager#checkSystemClipboardAccess()} to check if allowed.
     */
    private JMenuItem menuCopyToClip;

    /** mainwindow's data table, shown within {@link #jpane}, created and populated in {@link #showPairInPane()} */
    private JTable jtab;

    /** Text search panel, not always visible, at bottom of window below {@link #jtab} */
    private SearchPanel sPan;

    /** data model for JTable */
    private PTSwingTableModel mod;

    /** Last-clicked row number in {@link #jtab}, or -1 */
    private int jtabClickedRow = -1;

    /**
     * Last-clicked model column number in {@link #jtab}, or -1.
     * Columns stretch to fit the table width, so a click will always be in a column.
     */
    private int jtabClickedCol = -1;

    /**
     * Listener to double-click while editing cell text, to bring up a larger edit dialog.
     * Added/removed to edit component in {@link JTable#prepareEditor(TableCellEditor, int, int)}
     * / {@link JTable#removeEditor()}.
     */
    private CellEditorMouseListener cellListener;

    /**
     * Editor with source and destination files specified.
     * Call {@link #init()} to bring up the GUI and parse the properties files.
     * @param src  Source language/locale properties file
     * @param dest  Destination language/locale properties file
     */
    public PropertiesTranslatorEditor(final File src, final File dest)
    {
        pair = new ParsedPropsFilePair(src, dest);
    }

    /**
     * Editor where the source filename will be derived from the destination filename.
     * Call {@link #init()} to bring up the GUI and parse the properties files.
     * @param dest  Destination language properties file, full or relative path.
     *           Its filename must end with "_xx.properties" and the
     *           source will be the same filename without the "_xx" part.
     *           (The "_xx" part can be any length, not limited to 2 letters.)
     *           This constructor will call
     *           {@link #makeParentFilename(String) makeParentFilename}({@link File#getPath() dest.getPath()).
     * @throws IllegalArgumentException  Unless destFilename ends with _xx.properties
     *     (xx = any code 2 or more chars long)
     * @throws FileNotFoundException  if no existing parent of {@code dest} can be found on disk
     *     by {@link #makeParentFilename(String)}
     */
    public PropertiesTranslatorEditor(final File dest)
        throws IllegalArgumentException, FileNotFoundException
    {
        final String destFilename = dest.getPath();
        File src = makeParentFilename(destFilename);
            // might throw new IllegalArgumentException("destFilename must end with _xx.properties");
        if (src == null)
            throw new FileNotFoundException("No parent on disk for " + destFilename);
        pair = new ParsedPropsFilePair(src, dest);
    }

    /**
     * Continue GUI startup, once constructor has set {@link #pair} or left it null.
     * Will start the GUI and then parse {@code pair}'s files from its srcFile and destFile fields.
     */
    @SuppressWarnings("serial")
    public void init()
    {
        if (strings == null)
            initStringManager();

        jfra = new JFrame(strings.get("editor.window_title"));  // "Properties Translator's Editor"
        jfra.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        jfra.addWindowListener  // confirm unsaved changes when window closing
            (new WindowAdapter()
            {
                public void windowClosing(WindowEvent arg0)
                {
                    if (checkUnsavedBeforeDispose())
                        jfra.dispose();
                }
            });

        // TODO add menu or buttons: open, save, check consistencies, help
        if (pair != null)
        {
            try
            {
                pair.parseSrc();
                pair.parseDest();

                /*
                // tmp debug prints:

                System.out.println("Parsed result:");
                System.out.println();

                Iterator<ParsedPropsFilePair.FileEntry> ife = pair.getContents();
                while (ife.hasNext())
                    System.err.println(ife.next());
                 */

                // more temporary debug print:
                if (0 != pair.getDestOnlySize())
                {
                    System.out.println();
                    System.out.println("In destination only:");
                    System.out.println();
                    Iterator<PropsFileParser.KeyPairLine> ikpe = pair.getDestOnly();
                    while (ikpe.hasNext())
                        System.err.println(ikpe.next());
                }

            } catch (IOException ioe) {
                // TODO popup somewhere in GUI
                System.err.println(ioe);
            }
        }

        JPanel opan = new JPanel();
        opan.setOpaque(true);  //content panes must be opaque
        opan.setLayout(new BorderLayout());  // stretch JTable on resize

        mod = new PTSwingTableModel(this);  // sets up model to pair
        jtab = new JTable(mod)
        {
            /** Table header tooltips show full src, dest paths */
            protected JTableHeader createDefaultTableHeader()
            {
                return new JTableHeader(columnModel)
                {
                    public String getToolTipText(final MouseEvent e)
                    {
                        final int viewIdx = columnModel.getColumnIndexAtX(e.getPoint().x);
                        return mod.getPTEColumnToolTip(columnModel.getColumn(viewIdx).getModelIndex());
                    }
                };
            }

            /** Enable save button when cell editing begins */
            public Component prepareEditor(final TableCellEditor editor, final int r, final int c)
            {
                final Component ed = super.prepareEditor(editor, r, c);
                ed.addMouseListener(cellListener);

                switch (c)
                {
                case 1:  updateSaveButtonsForEditing(true, true);   break;  // src
                case 2:  updateSaveButtonsForEditing(true, false);  break;  // dest
                // default: do nothing extra
                }

                return ed;
            }

            /** Possibly disable save buttons when cell edit is complete or cancelled; remove cellListener */
            public void removeEditor()
            {
                final Component ed = getEditorComponent();
                if (ed != null)
                    ed.removeMouseListener(cellListener);

                super.removeEditor();
                updateSaveButtonsForEditing(false, false);
            }
        };

        jtab.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);  // don't lose current edit when focus lost
        jtab.setDefaultRenderer(Object.class, new PTCellRenderer(mod));  // background colors, etc
        // don't require double-click to edit jtab cell entries; all editable cols are String, so Object is enough
        ((DefaultCellEditor) jtab.getDefaultEditor(Object.class)).setClickCountToStart(1);

        jpane = new JScrollPane(jtab);
        opan.add(jpane, BorderLayout.CENTER);
        jfra.setContentPane(opan);

        // Is clipboard available to copy cell contents?
        boolean canSetClipboard = true;
        try
        {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null)
                sm.checkSystemClipboardAccess();
        } catch (SecurityException e) {
            canSetClipboard = false;
        }

        // Listen for click locations, so right-click menu knows where it was clicked
        jtab.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                final Point pt = e.getPoint();

                final int r = jtab.rowAtPoint(pt);
                if ((r >= 0) && (r < mod.getRowCount()))
                    jtabClickedRow = r;
                else
                    jtabClickedRow = -1;

                jtabClickedCol = jtab.convertColumnIndexToModel(jtab.columnAtPoint(pt));
                    // columnAtPoint returns -1 if not in a column; convertColumnIndexToModel returns -1 for -1
            }
        });

        // Table right-click menu
        {
            final JPopupMenu tPopup = new JPopupMenu();
            if (canSetClipboard)
            {
                menuCopyToClip = new JMenuItem(strings.get("menu.popup.copy_to_clipboard"));
                menuCopyToClip.addActionListener(this);
                tPopup.add(menuCopyToClip);
            }
            menuAddAbove = new JMenuItem(strings.get("menu.popup.add_above"));
            menuAddBelow = new JMenuItem(strings.get("menu.popup.add_below"));
            menuAddAbove.addActionListener(this);
            menuAddBelow.addActionListener(this);
            tPopup.add(menuAddAbove);
            tPopup.add(menuAddBelow);
            jtab.setComponentPopupMenu(tPopup);
        }

        cellListener = new CellEditorMouseListener();

        // Buttons above JTable
        {
            GridLayout bgl = new GridLayout(1, 0);
            JPanel pba = new JPanel(bgl);

            bHelp = new JButton(strings.get("editor.button.help"));
            bHelp.setToolTipText(strings.get("editor.button.help.tip"));  // "Brief explanation of how to use the editor"
            bHelp.addActionListener(this);
            bSaveSrc = new JButton(strings.get("editor.button.save_src"));
            bSaveSrc.setToolTipText(strings.get("editor.button.save_src.tip"));  // "Save changes to the source language file"
            bSaveSrc.setEnabled(false);
            bSaveSrc.addActionListener(this);
            bSaveDest = new JButton(strings.get("editor.button.save_dest"));
            bSaveDest.setToolTipText(strings.get("editor.button.save_dest"));  // "Save changes to the destination language file"
            bSaveDest.setEnabled(false);
            bSaveDest.addActionListener(this);

            pba.add(bHelp);
            pba.add(new JPanel());  // left-center spacer
            pba.add(bSaveSrc);
            pba.add(new JPanel());  // spacer between buttons
            pba.add(bSaveDest);
            opan.add(pba, BorderLayout.NORTH);
        }

        // Search panel, below JTable
        sPan = new SearchPanel();
        sPan.setVisible(false);
        opan.add(sPan, BorderLayout.SOUTH);

        // Keyboard shortcut setup
        {
            final InputMap im = opan.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            final ActionMap am = opan.getActionMap();

            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "find");  // TODO i18n VK_F ?
            am.put("find", new AbstractAction()
            {
                public void actionPerformed(ActionEvent arg0) { doSearchHotkey(); }
            });
        }

        // show it
        jfra.pack();
        jfra.setSize(700, 500);
        jfra.setVisible(true);
    }

    /** Handle button clicks. */
    public void actionPerformed(final ActionEvent ae)
    {
        final Object item = ae.getSource();

        if (item == menuAddAbove)
            insertRow(ae, true);
        else if (item == menuAddBelow)
            insertRow(ae, false);
        else if ((menuCopyToClip != null) && (item == menuCopyToClip))
        {
            final String cellText = mod.getValueAt(jtabClickedRow, jtabClickedCol).toString();
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            try
            {
                cb.setContents(new StringSelection(cellText), null);
            } catch (IllegalStateException e) {
                JOptionPane.showMessageDialog
                    (jfra, strings.get("editor.cannot_copy_clipboard.text"),
                         // "Could not copy text. Another program might currently own the clipboard."
                     strings.get("editor.cannot_copy_clipboard.title"),  // "Could not copy to clipboard"
                     JOptionPane.WARNING_MESSAGE);
            }
        }
        else if (item == bHelp)
        {
            JOptionPane.showMessageDialog
                (jfra,
                 strings.get("editor.help.text"),
                 /*
                 "This editor shows the comments, keys, and texts for the source and destination files.\n" +
                   "Click on a cell to change source or destination text. Keys cannot be edited in this version.\n" +
                   "New items can be added at the end, or inserted by right-clicking a line.\n" +
                   "To save changes and continue editing, click the button above the Source or Destination column.\n" +
                   "Green cells are empty and expecting text. Gray cells are unused, such as a comment's key column.",
                  */
                 strings.get("editor.help.title"),  // "PTE Help"
                 JOptionPane.PLAIN_MESSAGE);
        }
        else if (item == bSaveDest)
            saveChangesToDest();
        else if (item == bSaveSrc)
            saveChangesToSrc();
    }

    /**
     * Show and focus the Search bar when its hotkey (Ctrl-F 'Find') is pressed.
     * If the cell being edited would be covered, scroll up.
     */
    public void doSearchHotkey()
    {
        sPan.showAndFocus();
        // TODO check location in window of cell being edited, scroll up if now hidden behind sPan
    }

    /**
     * Enable/disable the Save Src or Save Dest button while editing a cell.
     * Save is always enabled while editing; after editing, it's disabled if its file's unsaved flag is false.
     * If the user changes a cell, that will separately fire {@link PTSwingTableModel#setValueAt(Object, int, int)}
     * which will set the file's unsaved flag and enable the button again.
     *
     * @param startEditing  True if the user just started editing the cell, false if they just finished editing
     * @param isSrc  True for source column, false for destination.  Ignored unless {@code startEditing}
     *     because the JTable method called after editing doesn't give the column, so we check both buttons.
     */
    private void updateSaveButtonsForEditing(final boolean startEditing, final boolean isSrc)
    {
        if (startEditing)
        {
            final JButton sbtn = ((isSrc) ? bSaveSrc : bSaveDest);
            if (! sbtn.isEnabled())
                sbtn.setEnabled(true);
        } else {
            if (bSaveSrc.isEnabled() != pair.unsavedSrc)
                bSaveSrc.setEnabled(pair.unsavedSrc);
            if (bSaveDest.isEnabled() != pair.unsavedDest)
                bSaveDest.setEnabled(pair.unsavedDest);
        }
    }

    /**
     * Add/insert a row before or after the row that was right-clicked on,
     * which was stored at click time in {@link #jtabClickedRow}.
     * @param ae  Menu popup action, with right-click location
     * @param beforeRow  If true, insert before (above), otherwise add after (below) this line
     */
    private void insertRow(final ActionEvent ae, final boolean beforeRow)
    {
        if (jtabClickedRow == -1)
            return;

        final int currR = jtab.getEditingRow();
        if ((currR != -1) && (Math.abs(currR - jtabClickedRow) == 1))
        {
            // If currently editing a row near jtabClickedRow, insert above or below
            // that current row instead of the clicked row.
            jtabClickedRow = currR;
        }

        final int r = (beforeRow) ? jtabClickedRow : (jtabClickedRow + 1);  // row number affected by insert

        if ((currR != -1) && (currR <= r))
        {
            // Currently editing, row number is changing: Commit edit changes before the insert
            TableCellEditor ce = jtab.getCellEditor();
            if (ce != null)
                ce.stopCellEditing();
        }

        pair.insertRow(jtabClickedRow, beforeRow);
        mod.fireTableRowsInserted(r, r);
    }

    /**
     * Are there any unsaved changes in the destination and/or source properties files?
     * @see #checkUnsavedBeforeDispose()
     * @see #saveChangesToAny()
     */
    public boolean hasUnsavedChanges()
    {
        return pair.unsavedDest || pair.unsavedSrc;
    }

    /** Save any unsaved changes to the destination and/or source properties files. */
    public void saveChangesToAny()
    {
        if (pair.unsavedDest)
            saveChangesToDest();
        if (pair.unsavedSrc)
            saveChangesToSrc();
    }

    /**
     * Save changes to the destination file, and clear the {@link ParsedPropsFilePair#unsavedDest pair.unsavedDest} flag.
     * If {@link ParsedPropsFilePair#unsavedInsRows pair.unsavedInsRows} is set, calls
     * {@link ParsedPropsFilePair#convertInsertedRows() pair.convertInsertedRows()} before saving.
     * Will save even if the {@code pair.unsavedDest} flag is false.
     */
    public void saveChangesToDest()
    {
        if (pair.unsavedInsRows && pair.convertInsertedRows())
            mod.fireTableDataChanged();

        try
        {
            PropsFileWriter pfw = new PropsFileWriter(pair.destFile);
            pfw.write(pair.extractContentsHalf(true), null);
            pfw.close();
            pair.unsavedDest = false;
            bSaveDest.setEnabled(false);
        }
        catch (Exception e)
        {
            // TODO dialog to user
            e.printStackTrace();
        }
    }

    /**
     * Save changes to the source file, and clear the {@link ParsedPropsFilePair#unsavedSrc pair.unsavedSrc} flag.
     * If {@link ParsedPropsFilePair#unsavedInsRows pair.unsavedInsRows} is set, calls
     * {@link ParsedPropsFilePair#convertInsertedRows() pair.convertInsertedRows()} before saving.
     * Will save even if the {@code pair.unsavedSrc} flag is false.
     */
    public void saveChangesToSrc()
    {
        if (pair.unsavedInsRows && pair.convertInsertedRows())
            mod.fireTableDataChanged();

        try
        {
            PropsFileWriter pfw = new PropsFileWriter(pair.srcFile);
            pfw.write(pair.extractContentsHalf(false), null);
            pfw.close();
            pair.unsavedSrc = false;
            bSaveSrc.setEnabled(false);
        }
        catch (Exception e)
        {
            // TODO dialog to user
            e.printStackTrace();
        }
    }

    /**
     * Checks for unsaved changes ({@link #pair}.{@link ParsedPropsFilePair#unsavedSrc unsavedSrc}
     * || {@link #pair}.{@link ParsedPropsFilePair#unsavedDest unsavedDest}) and if any, ask the
     * user if they want to save before closing.  Calls {@link #saveChangesToAny()} if user clicks yes.
     *
     * @return  True if okay to dispose of this editor frame: Not any unsaved changes, or user clicked Yes to save them,
     *    or user clicked No (don't save changes).  False if unsaved changes and user clicked Cancel (don't close).
     * @see #hasUnsavedChanges()
     */
    public boolean checkUnsavedBeforeDispose()
    {
        if (pair.unsavedSrc || pair.unsavedDest)
        {
            final int choice = JOptionPane.showConfirmDialog
                (jfra, strings.get("dialog.save_before_exit.text"),  // "Do you want to save changes before exiting?"
                 strings.get("dialog.save_before_exit.title"),       // "Unsaved Changes"
                 JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION)
                saveChangesToAny();  // save changes, then can dispose
            else
            {
                if (choice != JOptionPane.NO_OPTION)
                    return false;  // don't dispose the window if they clicked Cancel

                // Clear flags at No answer, so they aren't asked again if pair is checked later
                pair.unsavedSrc = false;
                pair.unsavedDest = false;
            }
        }

        return true;
    }

    /**
     * Given a more-specific destination locale filename, calculate the less-specific
     * source filename by removing _xx suffix(es) and check whether that source exists.
     *
     * @param destFilename   Destination language properties file, full or relative path.
     *           This filename must end with "_xx.properties" and the
     *           source will be the same filename without the "_xx" part.
     *           (The "_xx" part can be any length, not limited to 2 letters.)
     * @return  Parent file for this destination, or null if none exists on disk
     * @throws IllegalArgumentException  Unless destFilename ends with _xx.properties
     *     (xx = any code 2 or more chars long)
     * @throws NullPointerException if {@code destFilename} is null
     */
    public static File makeParentFilename(final String destFilename)
        throws IllegalArgumentException, NullPointerException
    {
        final int dfL = destFilename.length();
        final int iUndersc = destFilename.lastIndexOf('_', dfL - 12);

        if ( (! destFilename.endsWith(".properties"))
             || (dfL <= 14) || (-1 == iUndersc) )
            throw new IllegalArgumentException("destFilename must end with _xx.properties");

        // Remove 1 underscore level (_lang.properties or _COUNTRY.properties) for source file

        String srcFilename = destFilename.substring(0, iUndersc) + destFilename.substring(dfL - 11);
        File srcFile = new File(srcFilename);

        int iUndersc2 = srcFilename.lastIndexOf('_', dfL - 12);
        if (iUndersc2 != -1)
        {
            // It's unlikely that we'd have xyz_lang_COUNTRY.properties derived from
            // xyz.properties not xyz_lang.properties; just in case, if srcFilename
            // doesn't exist, try that
            if (! srcFile.exists())
            {
                srcFilename = srcFilename.substring(0, iUndersc2) + srcFilename.substring(srcFilename.length() - 11);
                srcFile = new File(srcFilename);
            }
        }

        if (srcFile.exists())
            return srcFile;
        else
            return null;
    }

    /**
     * Given a properties file name, try to determine its base name and language if any.
     * The base name is the part preceding the optional _language and _COUNTRY/_REGION suffixes.
     *<P>
     * Assumes the base name won't contain 2 or 3 lowercase letters between underscores,
     * which looks like a language suffix.  This is more strict than the spec for java identifiers
     * (thus classes/props file base names) which allows underscores.
     *<P>
     * Process:
     *<UL>
     * <LI> If filename doesn't end in {@code .properties}, return {@code null}
     * <LI> If filename has no underscores, return the entire portion before {@code .properties} as the base name
     * <LI> Look for the first occurrence of 2 or 3 lowercase letters between underscores, for the language
     * <LI> If no language found, but any underscores are found, is ambiguous: return {@code null}
     *</UL>
     * @param propsFilename  Properties filename to examine; see above for assumptions and process
     * @return A 2-element array containing the base name and 2- or 3-letter language;
     *     if no language is found, that array element will be {@code null}.
     *     If the base name can't be determined, returns {@code null} instead of an array.
     * @throws NullPointerException if {@code propsFilename} is null
     */
    public static String[] findBaseAndLangInFilename(String propsFilename)
        throws NullPointerException
    {
        int L = propsFilename.length();
        if ((L <= 11) || ! propsFilename.substring(L-11).toLowerCase().equals(".properties"))
            return null;

        propsFilename = propsFilename.substring(0, L-11);  // remove .properties
        int i = propsFilename.indexOf('_');
        if (i == -1)
        {
            final String[] ret = { propsFilename, null };
            return ret;
        }

        // _ and 2 or 3 lowercase letters per https://docs.oracle.com/javase/tutorial/i18n/locale/create.html,
        //  followed by end of string or another _
        if (_regex_findBaseAndLang == null)
            _regex_findBaseAndLang = Pattern.compile("_[a-z]{2,3}_|_[a-z]{2,3}$");

        Matcher m = _regex_findBaseAndLang.matcher(propsFilename);
        if (m.find())
        {
            // has language
            i = m.start();
            String lang = propsFilename.substring(i+1, m.end());
            if (lang.endsWith("_"))
                lang = lang.substring(0, lang.length() - 1);

            final String[] ret = { propsFilename.substring(0, i), lang };
            return ret;
        } else {
            // has underscores but no language code
            return null;
        }
    }

    /** Initialized and used in {@link #findBaseAndLangInFilename(String)} */
    private static Pattern _regex_findBaseAndLang;

    /**
     * Initialize {@link #strings} with the properties bundle at {@code net/nand/util/i18n/gui/strings/pte.properties}
     * in the default locale.
     */
    static void initStringManager()
    {
        if (strings != null)
            return;

        strings = new StringManager("net/nand/util/i18n/gui/strings/pte");
    }

    /**
     * @param args  Path/Filename of destination .properties, or source and destination.
     *           If destination only, filename must end with "_xx.properties" and the
     *           source will be the same filename without the "_xx" part.
     * @throws IOException if a properties file does not exist or cannot be read
     * @throws IllegalStateException if any call-sequence errors occur
     */
    public static void main(String[] args) throws IllegalStateException, IOException
    {
        // TODO cmdline parsing, help, etc
        //  although most of that is available in PTEMain

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}

        if (args.length >= 2)
        {
            new PropertiesTranslatorEditor(new File(args[0]), new File(args[1])).init();
        } else if (args.length == 1) {
            new PropertiesTranslatorEditor(new File(args[0])).init();
        } else {
            new PTEMain().initAndShow();
        }

    }

    //
    // Nested Classes
    //

    /**
     * Listener to double-click while editing cell text, to bring up a larger edit dialog.
     */
    private class CellEditorMouseListener extends MouseAdapter
    {
        public void mouseReleased(MouseEvent e)
        {
            if (e.getClickCount() != 2)
                return;

            // Currently editing, so get any edit changes before bringing up the editor
            String etext = null;
            final Component ec = jtab.getEditorComponent();
            if (ec != null)
            {
                if (ec instanceof JTextComponent)
                    etext = ((JTextComponent) ec).getText();
                else if (ec instanceof TextComponent)
                    etext = ((TextComponent) ec).getText();
            }

            final TableCellEditor ce = jtab.getCellEditor();
            if (ce != null)
                ce.cancelCellEditing();

            showCellEditDialog(jtabClickedRow, jtabClickedCol, etext);
        }

        /**
         * Edit this row and column of the table in a dialog.
         * Editor is multi-line except for column 1 (key column).
         * Assumes is called from the event dispatch thread (from mouseReleased or similar).
         * If not, use SwingUtilities.invokeLater.
         * @param row  Row to edit
         * @param col  Column to edit
         * @param etext  Starting text for edit, or null to get text from the model cell
         */
        private void showCellEditDialog(final int row, final int col, String etext)
        {
            if (etext == null)
                etext = (String) mod.getValueAt(row, col);

            if (col == 0)
            {
                etext = JOptionPane.showInputDialog
                    (jfra, strings.get("dialog.edit.key", (String) mod.getValueAt(row, 0)), etext);  // "Edit key: {0}"
            } else {
                final boolean isComment = mod.isRowComment(row);

                if ((! isComment) && (etext.indexOf("\\n") != -1))
                    etext = etext.replace("\\n", "\n");  // for easier editing, replace all "\n" with actual newlines

                JTextComponent jtc;
                if (isComment)
                    jtc = new JTextField(etext, 50);
                else
                    jtc = new JTextArea(etext, 7, 50);

                int ret = JOptionPane.showConfirmDialog
                    (jfra, new JScrollPane(jtc), strings.get("dialog.edit.new_text"),  // "Enter the new text"
                     JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null);
                if (ret == JOptionPane.OK_OPTION)
                {
                    etext = jtc.getText();

                    if ((! isComment) && (etext.indexOf('\n') != -1))
                    {
                        // TODO what if a multi-line comment is created
                        etext = etext.replace("\n", "\\n");
                    }

                    etext = etext.trim();
                } else {
                    etext = null;  // canceled
                }
            }

            if (etext == null)
                return;  // was canceled

            mod.setValueAt(etext, row, col);
        }
    }

    /**
     * JPanel for the text search bar. Text field and buttons.
     * Search keys and values when Enter pressed in textfield. Hide when ESC pressed.
     */
    @SuppressWarnings("serial")
    public class SearchPanel
        extends JPanel implements ActionListener
    {
        // Button characters are from Unicode 1.1 (June 1993) per http://www.fileformat.info/info/unicode/

        /** 'X' button to close (hide) the panel */
        private final JButton bXClose = new JButton("\u2716");  // HEAVY MULTIPLICATION X

        /** Previous ('Up' triangle) button */
        private final JButton bPrev = new JButton("\u25B2");  // BLACK UP-POINTING TRIANGLE

        /** Next ('Down' triangle) button */
        private final JButton bNext = new JButton("\u25BC");  // BLACK DOWN-POINTING TRIANGLE

        /** Text to search for */
        private final JTextField tfSearch = new JTextField(40);

        /** Brief search message, or hidden; message may be "No matches" or "Wrapped around" */
        private final JLabel labMsg = new JLabel();

        public SearchPanel()
        {
            super(new BorderLayout());

            // Everything left-aligned, except Close button
            JPanel pan = new JPanel();

            pan.add(new JLabel(strings.get("editor.find") + " "));  // "Find: "

            tfSearch.addActionListener(this);
            pan.add(tfSearch);

            bPrev.addActionListener(this);
            pan.add(bPrev);
            bNext.addActionListener(this);
            pan.add(bNext);

            pan.add(labMsg);

            add(pan, BorderLayout.LINE_START);

            // Close button; gets its own panel for same size as other buttons
            JPanel xpan = new JPanel();
            bXClose.addActionListener(this);
            xpan.add(bXClose);
            add(xpan, BorderLayout.LINE_END);

            // Keyboard shortcuts: hide on ESC

            final InputMap im = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            final ActionMap am = getActionMap();

            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "hide");
            am.put("hide", new AbstractAction()
            {
                public void actionPerformed(ActionEvent arg0)
                {
                    hideAndEndSearch();
                }
            });
        }

        /** Stop searching and hilighting, and hide the search panel */
        public void hideAndEndSearch()
        {
            labMsg.setText("");
            setVisible(false);
            invalidate();
            mod.endSearch();
        }

        /** Focus in the input box; if panel not visible, makes visible first */
        public void showAndFocus()
        {
            if (! isVisible())
            {
                setVisible(true);
                invalidate();
            }

            tfSearch.requestFocusInWindow();
        }

        /**
         * Search forward or backward for the current contents of the search text field.
         * The cursor will move to the next matching cell, which may wrap around.
         * Also highlights any matching cells, their entire background will be highlighted (not just the match).
         * When the search text changes, the next search will go from the top (forward) or bottom (backward)
         * of the table.
         * @param forward  True to search top to bottom left to right, false to search bottom to top
         * @see PropertiesTranslatorEditor.PTSwingTableModel#search(String, boolean)
         */
        private void search(final boolean forward)
        {
            final String txt = tfSearch.getText();
            if (txt.length() == 0)
                return;

            final boolean gotMatch = mod.search(txt, forward);
            if (gotMatch)
            {
                labMsg.setText(mod.searchWrapped ? strings.get("editor.find.wrapped_around") : "");
                final int r = mod.sr, c = mod.sc;
                jtab.changeSelection(r, c, false, false);
                jtab.scrollRectToVisible(jtab.getCellRect(r, c, true));
            } else {
                labMsg.setText(strings.get("editor.find.no_matches"));
            }
        }

        /** Handle button presses, or Enter in search field */
        public void actionPerformed(final ActionEvent ae)
        {
            final Object item = ae.getSource();

            if ((item == tfSearch) || (item == bNext))
            {
                search(true);
            }
            else if (item == bPrev)
            {
                search(false);
            }
            else if (item == bXClose)
            {
                hideAndEndSearch();
            }
        }
    }

    /**
     * Rendering model (background colors, etc) for {@link #jtab}.
     */
    private static class PTCellRenderer extends DefaultTableCellRenderer
    {
        /** created for JSettlers 2.0.00, no changes since then */
        private static final long serialVersionUID = 2000L;

        final PTSwingTableModel model;

        public PTCellRenderer(PTSwingTableModel modelData)
        {
            model = modelData;
        }

        public Component getTableCellRendererComponent
            (final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int col)
        {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

            switch (model.getCellStatus(row, col))
            {
            case SEARCH_MATCH:
                c.setForeground(Color.BLACK);
                c.setBackground(Color.YELLOW);
                break;

            case SRC_EMPTY_ERROR:
                // fall through
            case DEST_ONLY_ERROR:
                c.setBackground(Color.RED);

            case DEST_EMPTY:
                c.setBackground(Color.GREEN.brighter());
                break;

            case COMMENT_KEY_COL:
                // fall through
            case READONLY_NOT_LOCALIZED:
                c.setBackground(Color.LIGHT_GRAY);
                break;

            case DEFAULT:
                c.setForeground(table.getForeground());
                c.setBackground(table.getBackground());
                break;
            }

            return c;
        }
    }

    /**
     * Data model for viewing/editing data in {@link PropertiesTranslatorEditor#jtab jtab}.
     * Adds {@link #getCellStatus(int, int)}.
     * Model row and column numbers are 0-based.
     * Model contains 1 line for each line in {@link #pair}; its row count is
     * {@link ParsedPropsFilePair#size() pair.size()} + 1 for new data at the end.
     */
    private class PTSwingTableModel
        extends AbstractTableModel
    {
        private static final long serialVersionUID = 1L;

        /** Number of columns: key, value src, value dest */
        private static final int NUM_COLS = 3;

        public ParsedPropsFilePair pair;

        /** Search: lowercased current search text, or null if no search or no matches found */
        private String searchText;

        /**
         * Search: {@link #sr} and {@link #sc} are the row and column of the previous matching cell, if any.
         * Next call to {@link #search(String, boolean)} will start after this cell
         * if the search text is the same as the previous call.  To search from another
         * cell, you can change {@code sr} and {@code sc} before calling {@code search}.
         *<P>
         * If no match was found, {@code sr} may be 0 or may be outside the valid range of rows.
         * {@code sc} is always valid.
         * @see #searchWrapped
         */
        public int sr, sc;

        /**
         * If true, and the previous call to {@link #search(String, boolean)} was successful,
         * the search wrapped around the end of the table before finding a match.
         * @see #sr
         */
        public boolean searchWrapped;

        /**
         * Create and populate with existing data.
         */
        public PTSwingTableModel(PropertiesTranslatorEditor pted)
        {
            pair = pted.pair;
        }

        /**
         * Done searching for now.  Stop highlighting matching cells.
         */
        public void endSearch()
        {
            searchText = null;
            mod.fireTableDataChanged();
        }

        /**
         * Case-insensitive text search.  If matches are found, {@link #getCellStatus(int, int)}
         * will also highlight their cells. When done searching (when closing the search pane), call
         * {@link #endSearch()} to stop highlighting matches.
         *<P>
         * If continuing a previous search, keep moving from the previous match; otherwise start from
         * top or bottom of table, depending on search direction.
         *<P>
         * If a match is found, then {@link #sr}, {@link #sc}, and {@link #searchWrapped} are valid afterwards.
         *
         * @param txt  Text to search for, or {@code null} to repeat previous search
         * @param forward  True to search top to bottom left to right, false to search bottom to top
         * @return  True if a match found, false otherwise
         */
        public boolean search(String txt, final boolean forward)
        {
            // TODO consider, if continuing, keep moving from currently selected r,c, in case
            //       user clicks somewhere else in table, then wants to continue search from there

            final boolean previousHadMatches = (searchText != null);
            final boolean continueSearch =
                (txt == null) || ((searchText != null) && searchText.equalsIgnoreCase(txt));
            if (continueSearch && (searchText == null))
                return false;  // No text and no previous search

            if (continueSearch)
            {
                txt = searchText;
            } else {
                txt = txt.toLowerCase();
                searchText = txt;
            }

            final int rmax = pair.size() - 1;

            if (! continueSearch)
            {
                // set up starting position; will increment from here before first string compare

                if (forward)
                {
                    sr = rmax;  sc = NUM_COLS - 1;
                } else {
                    sr = 0;  sc = 0;
                }
            }

            // sr may be above or below pair's rows, indicating start or end of table

            // remember starting point, in case we wrap around
            final int startRow = sr, startCol = sc;
            int r = startRow, c = startCol;
            searchWrapped = false;

            do
            {
                if (forward)
                {
                    ++c;
                    if (c >= NUM_COLS)
                    {
                        c = 0;
                        ++r;
                        if (r > rmax)
                        {
                            r = 0;
                            if (continueSearch)
                                searchWrapped = true;
                        }
                    }
                } else {
                    --c;
                    if (c < 0)
                    {
                        c = NUM_COLS - 1;
                        --r;
                        if (r < 0)
                        {
                            r = rmax;
                            if (continueSearch)
                                searchWrapped = true;
                        }
                    }
                }

                String cell = ((String) getValueAt(r, c)).toLowerCase();
                if (cell.contains(txt))
                {
                    sr = r;  sc = c;
                    if (! (continueSearch && previousHadMatches))
                        mod.fireTableDataChanged();  // highlight all matches

                    return true;
                }

            } while ((r != startRow) || (c != startCol));

            // If we're here, we've wrapped around to starting position

            String cell = ((String) getValueAt(r, c)).toLowerCase();
            if (cell.contains(txt))
            {
                sr = r;  sc = c;

                return true;
            } else {
                searchText = null;
                if (previousHadMatches)
                    mod.fireTableDataChanged();  // un-highlight previous matches

                return false;
            }
        }

        /** 1 row for each line in matched pair of data files, plus a blank row at the end */
        public final int getRowCount()
        {
            return pair.size() + 1;
        }

        /** 3 columns: key, src value, dest value */
        public final int getColumnCount() { return NUM_COLS; }

        /**
         * In our model this object will always be a String, or "" if the cell is empty.
         *<P>
         * {@inheritDoc}
         */
        public Object getValueAt(final int rowIndex, final int columnIndex)
        {
            if (rowIndex >= pair.size())
                return "";

            final ParsedPropsFilePair.FileEntry fe = pair.getRow(rowIndex);
            if (fe instanceof ParsedPropsFilePair.FileKeyEntry)
            {
                final ParsedPropsFilePair.FileKeyEntry fke = (ParsedPropsFilePair.FileKeyEntry) fe;
                switch (columnIndex)
                {
                case 0:
                    return (fke.key != null) ? fke.key : "";
                case 1:
                    return (fke.srcValue != null) ? fke.srcValue : "";
                case 2:
                    return (fke.destValue != null) ? fke.destValue : "";
                }
            } else {
                // FileCommentEntry
                final ParsedPropsFilePair.FileCommentEntry fce = (ParsedPropsFilePair.FileCommentEntry) fe;
                switch (columnIndex)
                {
                case 1:
                    return (fce.srcComment != null) ? fce.srcComment : "";
                case 2:
                    return (fce.destComment != null) ? fce.destComment : "";
                }
            }

            // default:
            return "";
        }

        public void setValueAt(final Object newVal, final int r, final int c)
        {
            final int sz = pair.size();
            if (r > sz)
                return;  // <--- Early return: should not occur ---

            if ((c < 0) || (c > 2))
                return;  // <--- Early return: ignore unknown column ---

            String newStr = newVal.toString();
            if (newStr.trim().length() == 0)
                newStr = "";  // no whitespace-only entries

            if (newStr.equals(getValueAt(r, c)))
                return;  // <--- Early return: not changed ---

            if (newStr.length() == 0)
                newStr = null;  // empty strings stored as null

            boolean changed = false;
            boolean keyChgHasDestValue = false;  // If key is being changed, does it already have a value in dest?

            // check if we're in the "extra" blank row past bottom of list:
            if (r == sz)
            {
                pair.insertRow(r, false);
                mod.fireTableRowsInserted(r, r);  // may be covered by fireTableCellUpdated below, since r is "new"
            }

            ParsedPropsFilePair.FileEntry fe = pair.getRow(r);
            if (fe instanceof ParsedPropsFilePair.FileKeyEntry)
            {
                ParsedPropsFilePair.FileKeyEntry fke = (ParsedPropsFilePair.FileKeyEntry) fe;

                switch (c)
                {
                case 0:
                    // TODO when to allow key chgs to existing entries?  Are there other langs in the same dir using the key?
                    if (fke.newAdd)
                    {
                        // TODO check format and allowed key characters
                        fke.key = (newStr != null) ? newStr.trim() : null;
                        changed = true;
                        keyChgHasDestValue = (fke.destValue != null) && (fke.destValue.length() > 0);
                    }
                    break;

                case 1:
                    if ((newStr == null) && (fke.key != null) && (fke.key.length() > 0))
                    {
                        return;  // <--- Early return: Can't entirely clear a key's value in src file ---
                    }
                    fke.srcValue = newStr;
                    changed = true;
                    break;

                case 2:
                    if ((fke.destValue == null) && (newStr != null))
                        fke.destSpacedEquals = fke.srcSpacedEquals;
                    fke.destValue = newStr;
                    changed = true;
                    break;
                }
            } else {
                // FileCommentEntry

                if (newStr != null)
                {
                    newStr = newStr.trim();
                    if (! newStr.startsWith("#"))
                        newStr = "# " + newStr;  // Non-blank comment rows need our standard comment character
                }

                ParsedPropsFilePair.FileCommentEntry fce = (ParsedPropsFilePair.FileCommentEntry) fe;
                switch (c)
                {
                case 1:
                    fce.srcComment = newStr;
                    changed = true;
                    break;
                case 2:
                    fce.destComment = newStr;
                    changed = true;
                    break;
                }
            }

            if (changed)
            {
                switch (c)
                {
                case 2:
                    if (! pair.unsavedDest)
                    {
                        pair.unsavedDest = true;
                        bSaveDest.setEnabled(true);
                    }
                    break;

                case 0:
                    if (keyChgHasDestValue && ! pair.unsavedDest)
                    {
                        pair.unsavedDest = true;
                        bSaveDest.setEnabled(true);
                    }
                    // fall through to set unsavedSrc

                case 1:
                    if (! pair.unsavedSrc)
                    {
                        pair.unsavedSrc = true;
                        bSaveSrc.setEnabled(true);
                    }
                    break;

                }

                fireTableCellUpdated(r, c);
            }
        }

        public String getColumnName(final int col)
        {
            switch (col)
            {
            case 0:
                return strings.get("editor.heading.key");  // "Key"
            case 1:
                return pair.srcFile.getName();
            case 2:
                return pair.destFile.getName();
            default:
                return Integer.toString(col);
            }
        }

        /** Show src/dest file full path; see where-used for details */
        public String getPTEColumnToolTip(final int col)
        {
            try
            {
                switch (col)
                {
                case 0:
                    return strings.get("editor.heading.key.tip");  // "Unique key to retrieve this text from java code"
                case 1:
                    return pair.srcFile.getAbsolutePath();
                case 2:
                    return pair.destFile.getAbsolutePath();
                default:
                    return getColumnName(col);
                }
            } catch (SecurityException e) {
                // thrown from getAbsolutePath, unlikely to happen
                return getColumnName(col);
            }
        }

        public boolean isCellEditable(final int r, final int c)
        {
            if (r >= pair.size())
                return true;

            final ParsedPropsFilePair.FileEntry fe = pair.getRow(r);
            if (fe instanceof ParsedPropsFilePair.FileCommentEntry)
            {
                // in comment rows, no key col
                return (c != 0);
            } else {
                // in key-pair rows, no key col unless row was added
                final ParsedPropsFilePair.FileKeyEntry fke = (ParsedPropsFilePair.FileKeyEntry) fe;

                if ((c == 2) && (fke.key != null) && fke.key.startsWith(PropsFileParser.KEY_PREFIX_NO_LOCALIZE))
                    return false;  // can't edit dest if key starts with "_nolocaliz"

                return (c != 0) || fke.newAdd;
            }
        }

        /** Is this row a comment row, not a key+value row? */
        public boolean isRowComment(final int r)
        {
            if (r >= pair.size())
                return false;

            return (pair.getRow(r) instanceof ParsedPropsFilePair.FileCommentEntry);
        }

        /**
         * Get the cell status, to visually show the user.
         */
        public CellStatus getCellStatus(final int r, final int c)
        {
            if (r >= pair.size())
                return CellStatus.DEFAULT;

            final ParsedPropsFilePair.FileEntry fe = pair.getRow(r);
            if (fe instanceof ParsedPropsFilePair.FileKeyEntry)
            {
                final ParsedPropsFilePair.FileKeyEntry fke = (ParsedPropsFilePair.FileKeyEntry) fe;
                if (fke.key == null)
                    return CellStatus.DEFAULT;  // shouldn't happen; just in case

                if (c == 0)  // key column
                {
                    if ((searchText != null) && fke.key.toLowerCase().contains(searchText))
                        return CellStatus.SEARCH_MATCH;
                }

                else if (c == 1)  // source-language column
                {
                    if (fke.srcValue == null)
                        return CellStatus.SRC_EMPTY_ERROR;

                    if ((searchText != null) && fke.srcValue.toString().toLowerCase().contains(searchText))
                        return CellStatus.SEARCH_MATCH;
                }

                else if (c == 2)  // destination-language column
                {
                    if (fke.key.startsWith(PropsFileParser.KEY_PREFIX_NO_LOCALIZE))
                        return CellStatus.READONLY_NOT_LOCALIZED;

                    if ((fke.destValue == null) && (fke.srcValue != null))
                        return CellStatus.DEST_EMPTY;

                    if ((fke.destValue != null) && (searchText != null)
                        && fke.destValue.toString().toLowerCase().contains(searchText))
                        return CellStatus.SEARCH_MATCH;

                    if (pair.isKeyDestOnly(fke.key))
                        return CellStatus.DEST_ONLY_ERROR;
                }
            }
            else if ((fe instanceof ParsedPropsFilePair.FileCommentEntry)
                     && ((c == 0) || (searchText != null)))
            {
                final ParsedPropsFilePair.FileCommentEntry fce = (ParsedPropsFilePair.FileCommentEntry) fe;

                if (c == 0)
                {
                    if ((fce.destComment != null) && (fce.destComment.length() > 0)
                        || (fce.srcComment != null) && (fce.srcComment.length() > 0))
                        return CellStatus.COMMENT_KEY_COL;  // for visual effect, comment rows, not blank rows
                }

                else if (c == 1)
                {
                    if ((fce.srcComment != null) && fce.srcComment.toLowerCase().contains(searchText))
                        return CellStatus.SEARCH_MATCH;
                }

                else if (c == 2)
                {
                    if ((fce.destComment != null) && fce.destComment.toLowerCase().contains(searchText))
                        return CellStatus.SEARCH_MATCH;
                }
            }

            return CellStatus.DEFAULT;
        }

    }

    /** Return types for {@link PTSwingTableModel#getCellStatus(int, int)} */
    public static enum CellStatus
    {
        /** Default status (editable, no errors) */
        DEFAULT,
        /** Key column in a comment row (not a blank row) */
        COMMENT_KEY_COL,
        /** Matches current search */
        SEARCH_MATCH,
        /** Key exists in source, but value is empty in source: Needs a value */
        SRC_EMPTY_ERROR,
        /** Key's value exists in source, destination value is empty: Ready to localize */
        DEST_EMPTY,
        /** Key exists in destination only, not in source */
        DEST_ONLY_ERROR,
        /** Read-only in destination: Key is not localized */
        READONLY_NOT_LOCALIZED
    }

}
