/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2008-2009,2014-2016 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;

// import java.util.NoSuchElementException;
// import java.util.StringTokenizer;


/**
 * Message containing multiple parameters, each of which may have sub-fields.  <BR>
 * Format: MESSAGETYPE sep param1 sep param2 sep param3 sep ...    <BR>
 * Example format of a param:  field1 SEP2 field2 SEP2 field3
 *<P>
 * MessageMultis are treated specially in {@link SOCMessage#toMsg(String)}.
 * Multiple {@link SOCMessage#sep_char} are allowed, separating each parameter.
 * This allows use of {@link SOCMessage#sep2_char} within the parameter to
 * separate its sub-fields.
 *<P>
 * The required static parseDataStr method is given a List of one or more Strings,
 * each of which is a parameter:
 *<br>
 * <tt> public static SOCMessageType parseDataStr({@literal List<String>} s) </tt>
 *<br>
 * If no parameters were seen, <tt>s</tt> will be null.
 *<P>
 * The section you add to {@link SOCMessage#toMsg(String)} will depend on whether
 * a message with exactly 1 parameter is valid: If so, {@code multiData} will be null;
 * pass {@code data} to your parseDataStr too.
 *
 *<H5>If your message never needs to handle exactly 1 parameter:</H5>
 *<pre>
 *     case GAMESWITHOPTIONS:
 *         return SOCGamesWithOptions.parseDataStr(multiData); </pre>
 *
 * <H5>If your message might be valid with 1 parameter:</H5>
 *<pre>
 *     case GAMESWITHOPTIONS:
 *         return SOCGamesWithOptions.parseDataStr(data, multiData); </pre>
 *<P>
 * Note that if, on the sending end of the network connection, you passed a
 * non-null gamename to the {@link SOCMessageTemplateMs} or {@link SOCMessageTemplateMi}
 * constructor, then on this end within the toMsg code,
 * multiData[0] is the gamename, and multiData[1] == param[0] from the sending end.
 * Your parseDataStr will need to separate out the gamename again, so it doesn't
 * become param[0] at this end.
 *
 * @see SOCMessageTemplateMi
 * @see SOCMessageTemplateMs
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
public abstract class SOCMessageMulti extends SOCMessage
{
    private static final long serialVersionUID = 1100L;  // last structural change v1.1.00

    // @see SOCMessageTemplateMi, SOCMessageTemplateMs
}
