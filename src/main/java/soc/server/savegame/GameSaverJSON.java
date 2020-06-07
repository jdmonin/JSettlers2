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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import soc.game.SOCGame;
import soc.server.SOCServer;

/**
 * Save a game and its board's current state to a JSON file.
 * Game state must be {@link SOCGame#ROLL_OR_CARD} or higher.
 * Uses {@link SavedGameModel}, including some custom field serializers
 * declared through its {@code @JsonAdapter} field annotations.
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
     *<P>
     * Assumes caller has checked that gson jar is on classpath
     * by calling {@code Class.forName("com.google.gson.Gson")} or similar.
     *
     * @param ga  Game to save; not null
     * @param saveDir  Existing directory into which to save the file
     * @param saveFilename  Filename to save as; recommended suffix is {@link #FILENAME_EXTENSION}
     * @param srv  Server, for game/player info lookups; not null
     * @throws IllegalArgumentException  if {@code saveDir} isn't a currently existing directory
     * @throws SavedGameModel.UnsupportedSGMOperationException  if game has an option or feature not yet supported
     *     by {@link SavedGameModel}; see {@link SavedGameModel#checkCanSave(SOCGame)} for details.
     * @throws IllegalStateException if game state &lt; {@link SOCGame#ROLL_OR_CARD}
     * @throws IOException  if a problem occurs while saving
     */
    public static void saveGame
        (final SOCGame ga, final File saveDir, final String saveFilename, final SOCServer srv)
        throws IllegalArgumentException, SavedGameModel.UnsupportedSGMOperationException,
            IllegalStateException, IOException
    {
        if (! saveDir.isDirectory())
            throw new IllegalArgumentException("Not found as directory: " + saveDir.getPath());

        final SavedGameModel sgm = new SavedGameModel(ga, srv);

        final Gson gson;
        try
        {
            final GsonBuilder gb = new GsonBuilder();
            SavedGameModel.initGsonRegisterAdapters(gb);
            gson = gb.setPrettyPrinting().create();
        }
        catch (Throwable th)
        {
            throw new IOException("failed to load Gson class: " + th, th);
        }

        try(OutputStreamWriter writer = new OutputStreamWriter
               (new FileOutputStream(new File(saveDir, saveFilename)), "UTF-8"))
        {
            gson.toJson(sgm, writer);
        }
    }

}
