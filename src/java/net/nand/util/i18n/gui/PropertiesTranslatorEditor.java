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
 **/
package net.nand.util.i18n.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;

import net.nand.util.i18n.ParsedPropsFilePair;
import net.nand.util.i18n.PropsFileParser;
import net.nand.util.i18n.PropsFileWriter;

/**
 * Property file editor for translators (side-by-side source and destination languages).
 * Presents the source and destination language keys and values.
 * Highlights values that still need to be translated into the destination.
 * Saves in ISO-8859-1 encoding required for .properties files, escaping unicode characters where needed.
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
    /**
     * Pair of properties files being edited, and their contents.
     * The filenames are {@link ParsedPropsFilePair#srcFilePath pair.srcFilePath}
     * and {@link ParsedPropsFilePair#destFilePath pair.destFilePath}.
     *<P>
     * {@code null} if we start with no parameters, and wait for a file-open dialog.
     */
    private ParsedPropsFilePair pair;

    /** main window, set up in {@link #init()} */
    private JFrame jfra;

    /** main window's pane, created in {@link #init()}, populated in {@link #showPairInPane()} */
    private JScrollPane jpane;

    /** Save button for properties file from current editor contents; disabled until changes are made */
    private JButton bSaveSrc, bSaveDest;

    /** Menu items to add a line above or below this line */
    private JMenuItem menuAddAbove, menuAddBelow;

    /** mainwindow's data table, created and populated in {@link #showPairInPane()} */
    private JTable jtab;

    /** data model for JTable */
    private PTSwingTableModel mod;

    /** Last-clicked row number in {@link #jtab}, or -1 */
    private int jtabClickedRow = -1;

    /**
     * Continue GUI startup, once constructor has set {@link #pair} or left it null.
     * Will start the GUI and then parse {@code #pair}'s files from the filenames given.
     */
    public void init()
    {
        jfra = new JFrame("Properties: Translator's Editor");
        jfra.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        jfra.addWindowListener  // confirm unsaved changes when window closing
            (new WindowAdapter()
            {
                public void windowClosing(WindowEvent arg0)
                {
                    if (pair.unsavedSrc || pair.unsavedDest)
                    {
                        final int choice = JOptionPane.showConfirmDialog
                            (jfra, "Do you want to save changes before exiting?",
                            "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                        if (choice == JOptionPane.YES_OPTION)
                            saveChangesToAny();  // save changes, then exit
                        else if (choice != JOptionPane.NO_OPTION)
                            return;  // don't close the window unless they say no
                    }
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
        jtab = new JTable(mod);
        jtab.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);  // don't lose current edit when focus lost
        jtab.setDefaultRenderer(Object.class, new PTCellRenderer(mod));  // background colors, etc
        // don't require double-click to edit jtab cell entries; all editable cols are String, so Object is enough
        ((DefaultCellEditor) jtab.getDefaultEditor(Object.class)).setClickCountToStart(1);

        jpane = new JScrollPane(jtab);
        opan.add(jpane, BorderLayout.CENTER);
        jfra.setContentPane(opan);

        // Listen for click locations, so right-click menu knows where it was clicked
        jtab.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                final int r = jtab.rowAtPoint(e.getPoint());
                if ((r >= 0) && (r < mod.getRowCount()))
                    jtabClickedRow = r;
                else
                    jtabClickedRow = -1;
            }
        });

        // Table right-click menu
        {
            final JPopupMenu tPopup = new JPopupMenu();
            menuAddAbove = new JMenuItem("Add above");
            menuAddBelow = new JMenuItem("Add below");
            menuAddAbove.addActionListener(this);
            menuAddBelow.addActionListener(this);
            tPopup.add(menuAddAbove);
            tPopup.add(menuAddBelow);
            jtab.setComponentPopupMenu(tPopup);
        }

        // Buttons above JTable
        {
            GridLayout bgl = new GridLayout(1, 4);
            JPanel pba = new JPanel(bgl);
            bSaveSrc = new JButton("Save Src");
            bSaveSrc.setToolTipText("Save changes to the source language file");
            bSaveSrc.setEnabled(false);
            bSaveSrc.addActionListener(this);
            bSaveDest = new JButton("Save Dest");
            bSaveDest.setToolTipText("Save changes to the destination language file");
            bSaveDest.setEnabled(false);
            bSaveDest.addActionListener(this);
            pba.add(new JPanel());  // left spacer
            pba.add(new JPanel());  // left-center spacer
            pba.add(bSaveSrc);
            pba.add(new JPanel());  // spacer between buttons
            pba.add(bSaveDest);
            opan.add(pba, BorderLayout.NORTH);
        }

        // show it
        jfra.pack();
        jfra.setSize(500, 300);
        jfra.setVisible(true);
    }

    /**
     * Editor with source and destination filenames specified.
     * Call {@link #init()} to bring up the GUI and parse the properties files.
     * @param srcFilename  Source language properties file, full or relative path
     * @param destFilename  Destination language properties file, full or relative path
     */
    public PropertiesTranslatorEditor(final String srcFilename, final String destFilename)
    {
        pair = new ParsedPropsFilePair(srcFilename, destFilename);
    }

    /**
     * Editor where the source filename will be derived from the destination filename.
     * Call {@link #init()} to bring up the GUI and parse the properties files.
     * @param destFilename   Destination language properties file, full or relative path.
     *           This filename must end with "_xx.properties" and the
     *           source will be the same filename without the "_xx" part.
     *           (The "_xx" part can be any length, not limited to 2 letters.)
     */
    public PropertiesTranslatorEditor(final String destFilename)
        throws IllegalArgumentException
    {
        final int dfL = destFilename.length();
        final int iUndersc = destFilename.lastIndexOf('_', dfL - 12);

        if ( (! destFilename.endsWith(".properties"))
             || (dfL <= 14) || (-1 == iUndersc) )
            throw new IllegalArgumentException("destFilename must end with _xx.properties");

        // Remove 1 underscore level (_lang.properties or _COUNTRY.properties) for source file

        String srcFilename = destFilename.substring(0, iUndersc) + destFilename.substring(dfL - 11);

        int iUndersc2 = srcFilename.lastIndexOf('_', dfL - 12);
        if (iUndersc2 != -1)
        {
            // It's unlikely that we'd have xyz_lang_COUNTRY.properties derived from
            // xyz.properties not xyz_lang.properties; just in case, if srcFilename
            // doesn't exist, try that
            File srcTest = new File(srcFilename);
            if (! srcTest.exists())
                srcFilename = srcFilename.substring(0, iUndersc2) + srcFilename.substring(srcFilename.length() - 11);
        }
        pair = new ParsedPropsFilePair(srcFilename, destFilename);
    }

    /** Handle button clicks. */
    public void actionPerformed(final ActionEvent ae)
    {
        final Object item = ae.getSource();

        if (item == menuAddAbove)
            insertRow(ae, true);
        else if (item == menuAddBelow)
            insertRow(ae, false);
        else if (item == bSaveDest)
            saveChangesToDest();
        else if (item == bSaveSrc)
            saveChangesToSrc();
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
     * Will save even if that flag is false.
     */
    public void saveChangesToDest()
    {
        if (pair.unsavedInsRows && pair.convertInsertedRows())
            mod.fireTableDataChanged();

        try
        {
            PropsFileWriter pfw = new PropsFileWriter(pair.destFilePath);
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
     * Will save even if that flag is false.
     */
    public void saveChangesToSrc()
    {
        if (pair.unsavedInsRows && pair.convertInsertedRows())
            mod.fireTableDataChanged();

        try
        {

            PropsFileWriter pfw = new PropsFileWriter(pair.srcFilePath);
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
     * @param args  Path/Filename of destination .properties, or source and destination.
     *           If destination only, filename must end with "_xx.properties" and the
     *           source will be the same filename without the "_xx" part.
     * @throws IOException if a properties file does not exist or cannot be read
     * @throws IllegalStateException if any call-sequence errors occur
     */
    public static void main(String[] args) throws IllegalStateException, IOException
    {
        // TODO cmdline parsing, help, etc

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}

        if (args.length >= 2)
        {
            new PropertiesTranslatorEditor(args[0], args[1]).init();
        } else if (args.length == 1) {
            new PropertiesTranslatorEditor(args[0]).init();
        } else {
            new PTEMain().initAndShow();
        }

    }

    //
    // Nested Classes
    //

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
            case SRC_EMPTY_ERROR:
                // fall through
            case DEST_ONLY_ERROR:
                c.setBackground(Color.RED);

            case DEST_EMPTY:
                c.setBackground(Color.GREEN.brighter());
                break;

            case READONLY_NOT_LOCALIZED:
                c.setForeground(Color.DARK_GRAY);
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
     */
    private class PTSwingTableModel
        extends AbstractTableModel
    {
        private static final long serialVersionUID = 1L;

        public ParsedPropsFilePair pair;

        /**
         * Create and populate with existing data.
         */
        public PTSwingTableModel(PropertiesTranslatorEditor pted)
        {
            pair = pted.pair;
        }

        public final int getRowCount()
        {
            return pair.size() + 1;
        }

        public final int getColumnCount() { return 3; }  // key, src value, dest value

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
            if (r >= pair.size())
                return;

            if ((c < 1) && (c > 2))
                return;  // TODO temporary; can change only src or dest for now, not keys yet

            String newStr = newVal.toString();
            if (newStr.trim().length() == 0)
                newStr = "";  // no whitespace-only entries

            if (newStr.equals(getValueAt(r, c)))
                return;  // <--- Early return: not changed ---

            if (newStr.length() == 0)
                newStr = null;  // empty strings stored as null

            boolean changed = false;
            boolean keyChgHasDestValue = false;  // If key is being changed, does it already have a value in dest?
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
                return "Key";
            case 1:
                return pair.srcFilePath;
            case 2:
                return pair.destFilePath;
            default:
                return Integer.toString(col);
            }
        }

        public boolean isCellEditable(final int r, final int c)
        {
            if (r >= pair.size())
                return true;

            final ParsedPropsFilePair.FileEntry fe = pair.getRow(r);
            if (fe instanceof ParsedPropsFilePair.FileCommentEntry)
                // in comment rows, no key col
                return (c != 0);
            else
                // in key-pair rows, no key col unless row was added
                return (c != 0) || ((ParsedPropsFilePair.FileKeyEntry) fe).newAdd;
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

                if (c == 1)  // source-language column
                {
                    if (fke.srcValue == null)
                        return CellStatus.SRC_EMPTY_ERROR;
                }

                else if (c == 2)  // destination-language column
                {
                    if (fke.key.startsWith(PropsFileParser.KEY_PREFIX_NO_LOCALIZE))
                        return CellStatus.READONLY_NOT_LOCALIZED;

                    if ((fke.destValue == null) && (fke.srcValue != null))
                        return CellStatus.DEST_EMPTY;

                    if (pair.isKeyDestOnly(fke.key))
                        return CellStatus.DEST_ONLY_ERROR;
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
