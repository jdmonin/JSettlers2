/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2007-2010 Jeremy D Monin <jeremy@nand.net>
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
    public SOCPracticeAskDialog(SOCPlayerClient cli, SOCPlayerInterface gamePI)
    {
        super(cli, gamePI, "Practice game in progress",
            "A practice game is already being played.",
            "Show this game", "Create another", true, false);
    }

    /**
     * React to the Show button.
     */
    public void button1Chosen()
    {
        pi.show();    
    }

    /**
     * React to the Create button.
     */
    public void button2Chosen()
    {
        pcli.gameWithOptionsBeginSetup(true, false);
    }

    /**
     * React to the dialog window closed by user, or Esc pressed. (same as Show button)
     */
    public void windowCloseChosen()
    {
        button1Chosen();
    }

}
