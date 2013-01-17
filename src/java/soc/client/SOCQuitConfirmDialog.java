/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2007,2008,2010 Jeremy D Monin <jeremy@nand.net>
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

import soc.client.SOCPlayerClient.GameAwtDisplay;
import soc.game.SOCGame;


/**
 * This is the modal dialog to confirm when someone clicks the Quit Game button.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
class SOCQuitConfirmDialog extends AskDialog
{
    /**
     * Creates and shows a new SOCQuitConfirmDialog.
     * If the game is over, the "Quit" button is the default;
     * otherwise, Continue is default.
     *
     * @param cli      Player client interface
     * @param gamePI   Current game's player interface
     * @throws IllegalArgumentException If cli or gamePI is null
     */
    public static void createAndShow(GameAwtDisplay cli, SOCPlayerInterface gamePI)
        throws IllegalArgumentException
    {
        if ((cli == null) || (gamePI == null))
            throw new IllegalArgumentException("no nulls");
        SOCGame ga = gamePI.getGame();
        boolean gaOver = (ga.getGameState() >= SOCGame.OVER);

        SOCQuitConfirmDialog qcd = new SOCQuitConfirmDialog(cli, gamePI, gaOver);
        qcd.setVisible(true);
    }
    

    /**
     * Creates a new SOCQuitConfirmDialog.
     *
     * @param cli      Player client interface
     * @param gamePI   Current game's player interface
     * @param gameIsOver The game is over - "Quit" button should be default (if not over, Continue is default)
     */
    protected SOCQuitConfirmDialog(GameAwtDisplay cli, SOCPlayerInterface gamePI, boolean gameIsOver)
    {
        super(cli, gamePI, /*I*/"Really quit game "
                + gamePI.getGame().getName() + "?"/*18N*/,
            (gameIsOver
                ? /*I*/"Do you want to quit this finished game?"/*18N*/
                : /*I*/"Do you want to quit the game being played?"/*18N*/),
            /*I*/"Quit this game"/*18N*/,
            (gameIsOver
                ? /*I*/"Don't quit"/*18N*/
                : /*I*/"Continue playing"/*18N*/),
            ((gamePI.getGame().getGameState() != SOCGame.NEW)
                ? /*I*/"Reset board"/*18N*/
                : null),
            (gameIsOver ? 1 : 2));
    }

    /**
     * React to the Quit button. (call playerInterface.leaveGame)
     */
    @Override
    public void button1Chosen()
    {
        pi.leaveGame();
    }

    /**
     * React to the Continue button. (Nothing to do)
     */
    @Override
    public void button2Chosen()
    {
        // Nothing to do (continue playing)
    }

    /**
     * React to the Reset Board button. (call playerInterface.resetBoardRequest)
     */
    @Override
    public void button3Chosen()
    {
        pi.resetBoardRequest();
    }

    /**
     * React to the dialog window closed by user. (Nothing to do)
     */
    @Override
    public void windowCloseChosen()
    {
        // Nothing to do (continue playing)
    }

}
