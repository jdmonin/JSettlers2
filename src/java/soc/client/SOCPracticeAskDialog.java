/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2007-2010 Jeremy D Monin <jeremy@nand.net>
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
 * existing practice game, or start a new one.
 * The dialog is modal against {@link SOCPlayerClient}'s main frame.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
class SOCPracticeAskDialog extends AskDialog
{
    /**
     * Creates a new SOCPracticeAskDialog.
     *
     * @param cli      Player client interface
     * @param gamePI   Current game's player interface
     */
    public SOCPracticeAskDialog(GameAwtDisplay cli, SOCPlayerInterface gamePI)
    {
        super(cli, gamePI, "Practice game in progress",
            "A practice game is already being played.",
            "Show this game", "Create another", true, false);
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
        pcli.gameWithOptionsBeginSetup(true);
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
