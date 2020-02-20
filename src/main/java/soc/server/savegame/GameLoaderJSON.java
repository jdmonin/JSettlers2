/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.server.savegame;

import java.io.File;
import java.io.IOException;

import soc.game.SOCGame;

/**
 * Load a game and board's current state from a JSON file into a {@link SavedGameModel}.
 * Once loaded, game's {@link SOCGame#getGameState()} will temporarily have state {@link SOCGame#LOADING};
 * its actual gameState will be in game's {@code oldGameState} field.
 * Once the debug user has connected necessary bots or otherwise satisfied possible constraints,
 * must call {@link SavedGameModel#resumePlay(boolean)} to check constraints and resume game play.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @see GameSaverJSON
 * @since 2.3.00
 */
public class GameLoaderJSON
{
    /**
     * Load a game from a JSON file.
     *
     * @param loadFrom File to load from; filename should end with {@link GameSaverJSON#FILENAME_EXTENSION}
     * @return  loaded game model
     * @throws IOException  if a problem occurs while loading
     */
    public static SavedGameModel loadGame(File loadFrom)
        throws IOException
    {
        throw new IOException("TODO implement");
    }

}
