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
 **/
package soc.message;

/**
 * This marker is for any {@link SOCMessage} type which an unauthenticated client can send.
 * (The type might also be sent by a client which has already authenticated.)
 *<P>
 * Other message types may be rejected by server if the client hasn't yet authenticated.
 * Instead of processing the message, server will reply with
 * {@link SOCStatusMessage}({@link SOCStatusMessage#SV_MUST_AUTH_FIRST SV_MUST_AUTH_FIRST}).
 *
 * @since 2.4.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public interface SOCMessageFromUnauthClient
{
    // empty marker interface
}
