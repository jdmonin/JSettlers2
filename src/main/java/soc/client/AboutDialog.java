/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2026 Jeremy D Monin <jeremy@nand.net>
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
 **/
package soc.client;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;  // javadocs only

import javax.swing.JComponent;

import soc.util.Version;


/**
 * Modeless dialog to show information about Java Settlers.
 *<P>
 * To add a listener to a Label which shows this dialog when clicked, use {@link ClickMouseListener}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.7.00
 */
@SuppressWarnings("serial")
/*package*/ class AboutDialog extends NotifyDialog
{
    /**
     * Creates and shows a new AboutDialog.
     *<P>
     * Assumes currently running on AWT event thread.
     *
     * @param cli  Player client; not null
     * @param md  Player client's main display; not null
     * @throws IllegalArgumentException If md is null
     */
    public static void createAndShow(SOCPlayerClient cli, MainDisplay md)
        throws IllegalArgumentException
    {
        if ((cli == null) || (md == null))
            throw new IllegalArgumentException("no nulls");

        new AboutDialog(cli, md).setVisible(true);
    }

    /**
     * Creates a new AboutDialog.
     *
     * @param cli  Player client, to retrieve info; not null
     * @param md  Player client's main display; not null
     */
    private AboutDialog(SOCPlayerClient cli, MainDisplay md)
    {
        super(md, null, buildText(cli), null, true);
        setModal(false);
        setTitle(strings.get("dialog.about.title"));  // "About JSettlers"
    }

    /**
     * Build the body text; called by constructor.
     * @param cli  Player client, to retrieve info; not null
     * @return text to show
     */
    private static String buildText(final SOCPlayerClient cli)
    {
        StringBuilder sb = new StringBuilder(strings.get("dialog.about.text"));
        sb.append("\n");

        final String websiteMain = "http://nand.net/jsettlers/",
            websiteSrc = "https://github.com/jdmonin/JSettlers2/";  // TODO add to Versions props

        if (websiteMain != null)
        {
            sb.append("\n");
            sb.append(strings.get("dialog.about.website.main", websiteMain));  // "Website: {0}"
            sb.append("\n");
        }
        if (websiteSrc != null)
        {
            sb.append(strings.get("dialog.about.website.src", websiteSrc));  // "Source Code: {0}"
            sb.append("\n");
        }

        sb.append("\n");
        if (cli.sVersionBuildnum == null)
        {
            sb.append(strings.get
                ("pcli.cpp.jsettlers.versionbuild", Version.version(), Version.buildnum()));  // "JSettlers 2.7.00 build JM20260515"
        } else {
            sb.append(strings.get
                ("dialog.about.version.client", Version.version(), Version.buildnum()));  // "This client: Version {0} build {1}"
            sb.append("\n");
            sb.append(strings.get
                ("dialog.about.version.server", Version.version(cli.sVersion), cli.sVersionBuildnum));  // "Server: Version {0} build {1}"
        }

        return sb.toString();
    }

    /**
     * MouseListener which shows this dialog when a Swing component is clicked,
     * and optionally sets mouse pointer to {@link Cursor#HAND_CURSOR} while hovering over that component.
     */
    public static class ClickMouseListener extends MouseAdapter
    {
        final SOCPlayerClient cli;
        final SwingMainDisplay mainDisplay;
        final JComponent cursorAt;

        /**
         * Constructor; does not call {@link JComponent#addMouseListener(MouseListener)}.
         *
         * @param cli  Player client, to retrieve info; not null
         * @param mainDisplay  Main display required by dialog ancestor; not {@code null}
         * @param cursorAt  Optional Swing component at which to set mouse pointer to {@link Cursor#HAND_CURSOR}
         *     while hovering and listener receives {@link MouseListener#mouseEntered(MouseEvent)}, or {@code null}
         * @throws IllegalArgumentException if {@code mainDisplay null}
         */
        public ClickMouseListener
            (final SOCPlayerClient cli, final SwingMainDisplay mainDisplay, final JComponent cursorAt)
            throws IllegalArgumentException
        {
            if (cli == null)
                throw new IllegalArgumentException("cli");
            if (mainDisplay == null)
                throw new IllegalArgumentException("mainDisplay");

            this.cli = cli;
            this.mainDisplay = mainDisplay;
            this.cursorAt = cursorAt;
        }

        /**
         * When this listener's component is clicked,
         * show a popup with more info.
         */
        @Override
        public void mouseClicked(MouseEvent e)
        {
            AboutDialog.createAndShow(cli, mainDisplay);
        }

        /**
         * Set the hand cursor when hovering over the component having our listener.
         */
        @Override
        public void mouseEntered(MouseEvent e)
        {
            if (cursorAt != null)
                cursorAt.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        /**
         * Clear the cursor when no longer hovering over the component having our listener.
         */
        @Override
        public void mouseExited(MouseEvent e)
        {
            if (cursorAt != null)
                cursorAt.setCursor(Cursor.getDefaultCursor());
        }
    }

}
