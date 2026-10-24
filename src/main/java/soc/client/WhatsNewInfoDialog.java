/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2026 Jeremy D Monin <jeremy@nand.net>
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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;  // for jdocs
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

/**
 * Show what's new in JSettlers: brief release notes for current and recent releases,
 * based on github releases and doc/Versions.md.
 * To instantiate and use this class, call {@link #createAndShow(SOCPlayerClient)} on the AWT event thread.
 *
 * @since 2.7.00
 */
@SuppressWarnings("serial")
public class WhatsNewInfoDialog extends JFrame
    implements ActionListener
{
    // TODO allow text selecton
    // TODO can we make URLs clickable?

    /**
     * Release notes directory path within our jar: {@code "/resources/releaseNotes"}.
     */
    public final static String RELNOTES_RSRC_DIR_PATH = "/resources/releaseNotes";

    /**
     * i18n text strings; will use same locale as SOCPlayerClient's string manager.
     */
    protected static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    protected final JPanel infoText;

    protected final JButton okBut;

    /**
     * Creates and shows a new {@link WhatsNewInfoDialog}.
     * Calls {@link Window#pack()}, then checks size and position versus screen and reduces if needed to fit,
     * before it calls {@link Window#setVisible(boolean) setVisible(true)}.
     *<P>
     * Assumes currently running on AWT event thread.
     *
     * @param cli  Player client; not null, {@link SOCPlayerClient#getMainDisplay() cli.getMainDisplay()} not null
     * @throws NullPointerException  if cli is null
     * @throws IllegalArgumentException  if {@link SOCPlayerClient#getMainDisplay()} is null
     */
    public static void createAndShow(SOCPlayerClient cli)
        throws NullPointerException, IllegalArgumentException
    {
        WhatsNewInfoDialog dia = new WhatsNewInfoDialog(cli);  // constructor checks for null cli, mainDisplay
        dia.pack();

        Dimension preferred = dia.getSize();
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle bounds = env.getMaximumWindowBounds();
        bounds.width = (int) (bounds.width * 0.8f);
        bounds.height = (int) (bounds.height * 0.9f);
        preferred.width = preferred.width > bounds.width ? bounds.width : preferred.width;
        preferred.height = preferred.height > bounds.height ? bounds.height : preferred.height;
        dia.setSize(preferred);
        dia.setLocationRelativeTo(null);  // now that we have the size we want, recalc to center bounds on screen before setVisible
        dia.setVisible(true);
    }

    /**
     * Creates a new WhatsNewInfoDialog.
     *
     * @param cli  Player client; not null, {@link SOCPlayerClient#getMainDisplay() cli.getMainDisplay()} not null
     * @throws NullPointerException  if cli is null
     * @throws IllegalArgumentException  if {@link SOCPlayerClient#getMainDisplay()} is null
     */
    private WhatsNewInfoDialog(SOCPlayerClient cli)
        throws NullPointerException, IllegalArgumentException
    {
        super(strings.get("dialog.whatsnew.title"));  // "What's New: Recent Versions of JSettlers"

        final int displayScale = cli.getMainDisplay().getDisplayScaleFactor();
        setFont(new Font("Dialog", Font.PLAIN, 12 * displayScale));
        setResizable(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        final boolean isOSHighContrast = SwingMainDisplay.isOSColorHighContrast();
        // TODO colors: see FCF & askdia after isOSHighContrast
        // and maybe from pcli:
        /*
        SwingMainDisplay.scaleUIManagerFonts(displayScale);

        final Color[] colors = SwingMainDisplay.getForegroundBackgroundColors(false, false);
        if (colors != null)
        {
            frame.setBackground(colors[2]);  // SwingMainDisplay.JSETTLERS_BG_GREEN
            frame.setForeground(colors[0]);  // Color.BLACK
         */

        infoText = new AskDialog.ScrollableHTMLJLabelPanel(buildHTML(cli));
        Font ourfont = getFont();
        // override fixed-width font in JFrame on win32
        if (ourfont != null)
            infoText.setFont(ourfont);
        else
            infoText.setFont(new Font("Dialog", Font.PLAIN, 12));
        if (! isOSHighContrast)
        {
            infoText.setBackground(getBackground());  // avoid white background
            infoText.setForeground(null);
        }
        JScrollPane pScroll = new JScrollPane(infoText);
        pScroll.setOpaque(false);
        add(pScroll, BorderLayout.CENTER);

        // TODO FCF has addKeyListener(this) for window and buttons
        okBut = new JButton(strings.get("base.ok"));
        if (SOCPlayerClient.IS_PLATFORM_WINDOWS && ! isOSHighContrast)
            // avoid gray corners on win32 JButtons
            okBut.setBackground(null);
        JPanel pBtns = new JPanel();
        pBtns.setLayout(new FlowLayout(FlowLayout.CENTER));
        pBtns.setBackground(null);
        pBtns.add(okBut);
        okBut.addActionListener(this);
        add(pBtns, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okBut);
    }

    /**
     * OK button has been chosen by the user. Dispose of this dialog.
     */
    public void actionPerformed(ActionEvent e)
    {
        dispose();
    }

    /**
     * Build the body text HTML; called by constructor.
     * Embeds some newlines so our superclass sets up to show a multi-line string.
     * 
     * @param cli  Player client, to retrieve info; not null
     * @return text to show
     */
    private static String buildHTML(final SOCPlayerClient cli)
    {
        StringBuilder sb = new StringBuilder("<html><body>\n<H3>");
        sb.append(strings.get("dialog.whatsnew.title"));  // "What's New: Recent Versions of JSettlers"
        sb.append("</H3>\n");

        try
        {
            ReleaseNotesFromDirectory rv = new ReleaseNotesFromDirectory(RELNOTES_RSRC_DIR_PATH);

            for (String fname : rv.notesHTML.descendingKeySet())
            {
                if (fname.charAt(0) != 'v')
                    continue;
                sb.append(rv.notesHTML.get(fname));
            }
            if (rv.notesHTML.containsKey("footer.html"))
                sb.append(rv.notesHTML.get("footer.html"));

        } catch (IOException e) {
            sb.append("Unexpected error:<BR>Cannot read release notes within JAR:<BR>");
            sb.append(e.toString());
        }

        return sb.toString();
    }

    /**
     * Reads all notes files in a given resource directory.
     * Constructor collects them into {@link #notesHTML}.
     */
    public static class ReleaseNotesFromDirectory
    {
        /**
         * Size (chars) of the buffer used while reading into notesHTML.
         * This is public for use in a unit test.
         */
        public static final int BUFFER_SIZE = 2048;

        /**
         * HTML text resource contents read during constructor.
         * Keys are {@code *.html} filenames in the constructor's dirPath: {@code "v2610.html"}, {@code "footer.html"}, etc.
         * To iterate from newest to oldest versions, use {@link TreeMap#descendingKeySet()}.
         * Values are the file contents, read as UTF-8.
         */
        public final TreeMap<String, String> notesHTML = new TreeMap<>();

        /**
         * Read resource contents into a new ReleaseNotesFromDirectory.
         * Reads into {@link #notesHTML}; see that field for structure of loaded data.
         * @param resDirPath  Resource directory path to read, within our jar or classpath
         * @throws IOException if {@code resDirPath} not found, not a directory, or not readable
         */
        public ReleaseNotesFromDirectory(final String resDirPath)
            throws IOException
        {
            // Scan that dir path for *.html filenames:
            {
                final URI dirUri;
                try
                {
                    URL dirUrl = getClass().getResource(resDirPath);
                    if (dirUrl == null)
                        throw new FileNotFoundException("Path not found: " + resDirPath);
                    dirUri = dirUrl.toURI();
                } catch (URISyntaxException e) {
                    throw new IOException("URISyntaxException for path", e);  // unlikely to occur
                }

                if ("jar".equals(dirUri.getScheme()))
                {
                    try (FileSystem fileSystem = FileSystems.newFileSystem(dirUri, Collections.emptyMap()))
                    {
                        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(fileSystem.getPath(resDirPath)))
                        {
                            for (Path dirEntry : dirStream)
                            {
                                String fname = dirEntry.getFileName().toString();
                                if (fname.toLowerCase(Locale.US).endsWith(".html"))
                                    notesHTML.put(fname, "");
                            }
                        }
                        // if not a dir, throws NotDirectoryException with detail text containing resDirPath;
                        // we don't need to catch and re-throw
                    }
                }
                else if ("file".equals(dirUri.getScheme()))  // probably running in an IDE
                {
                    File dirFile = new File(dirUri);
                    if (! dirFile.isDirectory())
                        throw new IOException("Not a directory: " + resDirPath);

                    for (String fname : dirFile.list())
                        if (fname.toLowerCase(Locale.US).endsWith(".html"))
                            notesHTML.put(fname, "");
                }
            }

            // Now read their contents:
            final char[] buffer = new char[BUFFER_SIZE];
            final StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : notesHTML.entrySet())
            {
                final String fname = entry.getKey();

                try (InputStream ins = getClass().getResourceAsStream(resDirPath + "/" + fname))
                {
                    if (ins == null)
                        entry.setValue("(cannot read " + fname + ")");
                    else
                        try (InputStreamReader insr = new InputStreamReader(ins, StandardCharsets.UTF_8))
                        {
                            sb.delete(0, sb.length());
                            for (int numRead; (numRead = insr.read(buffer, 0, buffer.length)) > 0; )
                                sb.append(buffer, 0, numRead);

                            entry.setValue(sb.toString());
                        }
                }
            }
        }
    }

}
