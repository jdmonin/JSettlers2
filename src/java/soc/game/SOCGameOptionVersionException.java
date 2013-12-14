/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009,2013 Jeremy D Monin <jeremy@nand.net>
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
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/
package soc.game;

import java.util.Enumeration;
import java.util.Hashtable;  // for javadocs only
import java.util.Vector;

/**
 * This exception indicates game option(s) too new for a client.
 * @see SOCGameOption#optionsMinimumVersion(Hashtable)
 * @see SOCGameOption#optionsNewerThanVersion(int, boolean, boolean, Hashtable)
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.1.07
 */
public class SOCGameOptionVersionException extends IllegalArgumentException
{
    private static final long serialVersionUID = 1107L;

    /** Minimum client version required by game options */
    public final int gameOptsVersion;

    /** Requesting client's version */
    public final int cliVersion;

    /**
     * The {@link SOCGameOption}(s) which are too new,
     *     as returned by {@link SOCGameOption#optionsNewerThanVersion(int, boolean, boolean, Hashtable)}
     */
    public Vector<?> problemOptionsTooNew;

    /**
     * @param optVers Minimum client version required by game options
     * @param cliVers Requesting client's version
     * @param optsValuesTooNew The {@link SOCGameOption}(s) which are too new,
     *     as returned by {@link SOCGameOption#optionsNewerThanVersion(int, boolean, boolean, Hashtable)}
     */
    public SOCGameOptionVersionException(int optVers, int cliVers, Vector<?> optsValuesTooNew)
    {
        super("Client version vs game options");
        gameOptsVersion = optVers;
        cliVersion = cliVers;
        problemOptionsTooNew = optsValuesTooNew;
    }

    /**
     * Build the list of "problem options" as a string, separated by "," (SOCMessage.SEP2).
     * @return list of options (and values?) too new, or "" if none
     */
    public String problemOptionsList()
    {
        if (problemOptionsTooNew == null)
            return "";

        StringBuffer sb = new StringBuffer();
        boolean hadAny = false;
        for (Enumeration<?> e = problemOptionsTooNew.elements(); e.hasMoreElements(); )
        {
            Object opt = e.nextElement();
            String item;
            if (opt instanceof SOCGameOption)
                item = ((SOCGameOption) opt).optKey;
            else
                item = opt.toString();
            if (hadAny)
                sb.append(",");  // "," == SOCMessage.SEP2
            sb.append(item);
            hadAny = true;
        }
        return sb.toString();
    }

}
