/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2007-2010,2013,2016 Jeremy D Monin <jeremy@nand.net>
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

import soc.client.SOCPlayerClient.GameAwtDisplay;

/**
 * This is the dialog to ask players if they want to join an
 * existing practice game or start a new one.
 *<P>
 * The dialog is modal against {@link SOCPlayerClient}'s main frame.
 * Client should bring the existing practice game's {@link SOCPlayerInterface}
 * to the front for visibility, then show this dialog.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
@SuppressWarnings("serial")
class SOCPracticeAskDialog extends AskDialog
{

    /** i18n text strings; will use same locale as SOCPlayerClient's string manager.
     *  @since 2.0.00 */
    private static final soc.util.SOCStringManager strings = soc.util.SOCStringManager.getClientManager();

    /**
     * Creates a new SOCPracticeAskDialog.
     *
     * @param cli      Player client interface
     * @param gamePI   Current game's player interface
     */
    public SOCPracticeAskDialog(GameAwtDisplay cli, SOCPlayerInterface gamePI)
    {
        super(cli, gamePI, strings.get("dialog.practiceask.in.progress"),  // "Practice game in progress"
            strings.get("dialog.practiceask.already.being.played"),  // "A practice game is already being played."
            strings.get("dialog.practiceask.show.game"),  // "Show that game"
            strings.get("dialog.practiceask.create"),     // "Create another"
            true, false);
    }

    /**
     * React to the Show button.
     */
    @Override
    public void button1Chosen()
    {
        pi.setVisible(true);
    }

    /**
     * React to the Create button.
     */
    @Override
    public void button2Chosen()
    {
        pcli.gameWithOptionsBeginSetup(true, false);
    }

    /**
     * React to the dialog window closed by user, or Esc pressed. (same as Show button)
     */
    @Override
    public void windowCloseChosen()
    {
        button1Chosen();
    }

}
