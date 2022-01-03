/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2008,2010,2013-2014,2019-2020 Jeremy D Monin <jeremy@nand.net>
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
 **/
package soc.client;

import java.awt.Frame;


/**
 * This is the dialog to confirm when someone closes the client.
 * The quit action is to call client.putLeaveAll() and System.exit(0).
 * The dialog is modal against {@link SOCPlayerClient}'s main frame.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
@SuppressWarnings("serial")
/*package*/ class SOCQuitAllConfirmDialog extends AskDialog
{
    protected boolean hostedServerActive;

    /**
     * Creates and shows a new SOCQuitAllConfirmDialog.
     * "Continue" is default.
     *<P>
     * Assumes currently running on AWT event thread.
     *
     * @param md  Player client's main display
     * @param gamePIOrSelf   An active game's player interface, or the client's Frame
     *                 if we're hosting a local server but not actively playing
     * @throws IllegalArgumentException If cli or gameOrSelf is null
     */
    public static void createAndShow(MainDisplay md, Frame gamePIOrSelf)
        throws IllegalArgumentException
    {
        if ((md == null) || (gamePIOrSelf == null))
            throw new IllegalArgumentException("no nulls");

        boolean hasAny = md.getClient().getNet().anyHostedActiveGames();
        SOCQuitAllConfirmDialog qcd = new SOCQuitAllConfirmDialog(md, gamePIOrSelf, hasAny);
        qcd.setVisible(true);
    }

    /**
     * Creates a new SOCQuitAllConfirmDialog.
     *
     * @param md  Player client's main display
     * @param gamePIOrSelf   An active game's player interface, or the client's Frame
     *                 if we're hosting a local server but not actively playing.
     *                 Showing the dialog will make this frame topmost if possible, then appear over it.
     * @param hostedServerActive Is client hosting a local server with games active?
     *                 Caller should use {@link ClientNetwork#anyHostedActiveGames()} to determine.
     */
    protected SOCQuitAllConfirmDialog(MainDisplay md, Frame gamePIOrSelf, boolean hostedServerActive)
    {
        super(md, gamePIOrSelf,
            strings.get(hostedServerActive ? "dialog.quitall.shut.srv" : "dialog.quitall.really"),
                // "Shut down game server?" / "Really quit all games?"
            strings.get("dialog.quitall.still.active"),  // "One or more games are still active."
            strings.get(hostedServerActive ? "dialog.quitall.shut.anyway" : "dialog.quitall.games"),
                // "Shut down server anyway" / "Quit all games"
            strings.get(hostedServerActive ? "dialog.quitall.cont.srv" : "dialog.quitall.cont.play"),
                // "Continue serving" / "Continue playing"
            false, true);

        this.hostedServerActive = hostedServerActive;
    }

    /**
     * React to the Quit button. Just as SOCPlayerClient does,
     * call client.putLeaveAll() and System.exit(0).
     */
    @Override
    public void button1Chosen()
    {
        md.getClient().getNet().putLeaveAll();
        System.exit(0);
    }

    /**
     * React to the Continue button. (Nothing to do, continue playing)
     */
    @Override
    public void button2Chosen()
    {
        // Nothing to do (continue playing)
    }

    /**
     * React to the dialog window closed by user. (Nothing to do, continue playing)
     */
    @Override
    public void windowCloseChosen()
    {
        // Nothing to do (continue playing)
    }

}
