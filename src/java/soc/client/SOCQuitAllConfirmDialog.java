/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2008,2010 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

import soc.client.SOCPlayerClient.GameAwtDisplay;


/**
 * This is the dialog to confirm when someone closes the client.
 * The quit action is to call client.putLeaveAll() and System.exit(0).
 * The dialog is modal against {@link SOCPlayerClient}'s main frame.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
class SOCQuitAllConfirmDialog extends AskDialog
{
    protected boolean hostedServerActive;

    /**
     * Creates and shows a new SOCQuitAllConfirmDialog.
     * "Continue" is default.
     *
     * @param cli      Player client interface
     * @param gamePIOrSelf   An active game's player interface, or the client's Frame
     *                 if we're hosting a local server but not actively playing
     * @throws IllegalArgumentException If cli or gameOrSelf is null
     */
    public static void createAndShow(GameAwtDisplay cli, Frame gamePIOrSelf)
        throws IllegalArgumentException
    {
        if ((cli == null) || (gamePIOrSelf == null))
            throw new IllegalArgumentException("no nulls");

        boolean hasAny = cli.getClient().getNet().anyHostedActiveGames();
        SOCQuitAllConfirmDialog qcd = new SOCQuitAllConfirmDialog(cli, gamePIOrSelf, hasAny);
        qcd.setVisible(true);
    }
    

    /**
     * Creates a new SOCQuitAllConfirmDialog.
     *
     * @param cli      Player client interface
     * @param gamePIOrSelf   An active game's player interface, or the client's Frame
     *                 if we're hosting a local server but not actively playing
     * @param hostedServerActive Is client hosting a local server with games active?
     *                 Call {@link SOCPlayerClient#anyHostedActiveGames()} to determine.
     */
    protected SOCQuitAllConfirmDialog(GameAwtDisplay cli, Frame gamePIOrSelf, boolean hostedServerActive)
    {
        super(cli, gamePIOrSelf,
            (hostedServerActive ? "Shut down game server?" : "Really quit all games?"),
            "One or more games are still active.",
            (hostedServerActive ? "Shut down server anyway" : "Quit all games"),
            (hostedServerActive ? "Continue serving" : "Continue playing"),
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
        pcli.getClient().getNet().putLeaveAll();
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
