/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009 Jeremy Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.game;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * This exception indicates a game option too new for a client.
 * @see SOCGameOption#optionsMinimumVersion(Hashtable)
 *
 * @author Jeremy D. Monin <jeremy@nand.net>
 * @since 1.1.07
 */
public class SOCGameOptionVersionException extends IllegalArgumentException
{
    public final int cliVersion;
    public Vector problemOptionsTooNew;
    public SOCGameOptionVersionException(int cliVers, Vector optsValuesTooNew)
    {
        super("Client version vs game options");
        cliVersion = cliVers;
        problemOptionsTooNew = optsValuesTooNew;
    }
}
