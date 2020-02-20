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

import soc.game.SOCGame;

/**
 * Data model for a game saved to/loaded from a file.
 *<P>
 * To save, use the {@link #SavedGameModel(SOCGame)} constructor.
 * To load, use {@link #SavedGameModel()}.
 * See those constructors' javadocs for usage details.
 *<P>
 * This standalone model is cleaner than trying to serialize/deserialize {@link SOCGame}, SOCBoard, etc.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.3.00
 */
public class SavedGameModel
{
    /**
     * Create an empty SavedGameModel to load a game file into.
     * Once loaded, state will temporarily be {@link SOCGame#LOADING}:
     * Call {@link #resumePlay(boolean)} to continue play.
     */
    public SavedGameModel()
    {
    }

    /**
     * Create a SavedGameModel to save as a game file.
     * @param ga  Game data to save
     */
    public SavedGameModel(final SOCGame ga)
    {
        this();

        // TODO implement
    }

    /**
     * Get the completely loaded game, or the game to be saved.
     * @return Game, or {@code null} if not loaded successfully
     */
    public SOCGame getGame()
    {
        return null;  // TODO implement
    }

    /**
     * Resume play of a loaded game.
     * @param ignoreConstraints  If true, don't check any {@link Constraint}s in the model
     * @return game ready to play, including current gameState
     * @throws IllegalStateException if a constraint is not met
     */
    public SOCGame resumePlay(final boolean ignoreConstraints)
        throws IllegalStateException
    {
        // TODO implement: maybe check constraints, load state from ga.oldState

        return null;
    }

    /**
     * A constraining condition requested before resuming the game.
     * For example, player 3 must be a "faster" built-in bot, or be a certain third-party bot class.
     * Constraints can be ignored when resuming.
     */
    public static class Constraint
    {
        // TBD
    }

}
