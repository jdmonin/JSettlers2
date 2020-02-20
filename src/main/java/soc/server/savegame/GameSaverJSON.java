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
 * Save a game and its board's current state to a JSON file.
 * Uses {@link SavedGameModel}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @see GameLoaderJSON
 * @since 2.3.00
 */
public class GameSaverJSON
{
    /**
     * Standard suffix/extension for savegame files: {@code ".game.json"}
     */
    public static final String FILENAME_EXTENSION = ".game.json";

    /**
     * Save this game to a JSON file.
     *
     * @param ga  Game to save
     * @param saveDir  Existing directory into which to save the file
     * @param saveFilename  Filename to save as; recommended suffix is {@link #FILENAME_EXTENSION}
     * @throws IllegalArgumentException  if {@code saveDir} isn't a currently existing directory
     * @throws IOException  if a problem occurs while saving
     */
    public static void saveGame(final SOCGame ga, final File saveDir, final String saveFilename)
        throws IllegalArgumentException, IOException
    {
        if (! saveDir.isDirectory())
            throw new IllegalArgumentException("Not found as directory: " + saveDir.getPath());

        final SavedGameModel model = new SavedGameModel(ga);

        // TODO use GSON to save model to disk
        throw new IOException("TODO implement");
    }

}
