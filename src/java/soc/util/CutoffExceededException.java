/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.util;


/**
 * this exception means that the cutoff has been exceeded
 */
public class CutoffExceededException extends Exception
{
    /**
     * Creates a new CutoffExceededException object.
     */
    public CutoffExceededException()
    {
        super();
    }

    /**
     * Creates a new CutoffExceededException object.
     *
     * @param s DOCUMENT ME!
     */
    public CutoffExceededException(String s)
    {
        super(s);
    }
}
