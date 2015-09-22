/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2010,2013-2015 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;

/**
 * This indicates that a {@link SOCMessage} type is always about a particular game
 * named in the message, or never about that game.
 *<P>
 * Check that {@link #getGame()} is not null before adding to a per-game message queue.
 *<P>
 * Most implementing types' constructors will always require a game; some abstract
 * subclasses such as {@link SOCMessageTemplateMi} may allow null, leaving the choice
 * to each of their own subclasses.  Non-abstract message types must always return a
 * game name from {@link #getGame()}, never null.
 *<P>
 * Template classes such as {@link SOCMessageTemplateMi} are convenient for quickly developing
 * a new message type, but they all implement {@code SOCMessageForGame}.  If the template classes
 * are used for non-game data from a server, the server will use {@link SOCMessage#GAME_NONE}
 * as the "game name".
 *
 * @since 1.1.11
 * @author Jeremy D Monin
 */
public interface SOCMessageForGame
{
    /**
     * Name of game this message is for.
     * Must not be {@code null} if message is in per-game structures or code.
     *<P>
     * At the server, the message treater dispatches incoming {@code SOCMessageForGame}s
     * based on their {@code getGame()}:
     *<UL>
     * <LI> {@code null}: Message is ignored
     * <LI> {@link SOCMessage#GAME_NONE}: Message is handled by {@code SOCServer} itself
     * <LI> Any other game name: Looks for a game with that name, to dispatch to
     *      the {@code GameHandler} for that game's type. If no game with that name is found,
     *      the message is ignored.
     *</UL>
     *
     * @return the name of the game, or (rarely) {@code null} if none.
     *
     * @see SOCMessage#GAME_NONE
     */
    public abstract String getGame();

    /**
     * Get the message type.  Implemented in {@link SOCMessage}.
     * @return the message type
     * @since 2.0.00
     */
    public abstract int getType();
}
