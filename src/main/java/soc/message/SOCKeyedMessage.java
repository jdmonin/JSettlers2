/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2014,2017 Jeremy D Monin <jeremy@nand.net>
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
 * This indicates that a {@link SOCMessage} type contains a keyed text field which needs
 * to be localized while sending to clients.  This is different from {@link SOCGameTextMsg}
 * because this message type also contains non-text data fields: See {@link SOCSVPTextMessage}
 * for an example.
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public interface SOCKeyedMessage
{
    /**
     * Get the message localization key, from {@link soc.util.SOCStringManager#get(String)},
     * to look up and send the text of as part of this message.
     * @return  The text key to be localized from the message's key field, or (rarely) {@code null}
     */
    public abstract String getKey();

    /**
     * Construct a localized copy of this message to be sent to clients.
     *<P>
     * Before v3.0.00 this method was {@code String toCmd(localizedText)}.
     *
     * @param localizedText  Text field contents localized by the server, from {@link #getKey()} and the
     *     client's locale, or {@code null} if {@code getKey() == null}
     * @return  A message formatted like {@link SOCMessage#toCmd()}
     */
    public abstract SOCMessage localize(final String localizedText);

}
