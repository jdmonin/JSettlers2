/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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


/**
 * This is a list of constants for representing
 * types of resources in Settlers of Catan.
 */
public interface SOCResourceConstants
{
    /**
     * Warning: Don't mess with these constants, other pieces
     *          of code depend on these numbers staying like this.
     *          Clay is first, Wood is last, Unknown is after wood.
     */
    public static final int CLAY = 1;
    public static final int ORE = 2;
    public static final int SHEEP = 3;
    public static final int WHEAT = 4;
    public static final int WOOD = 5;
    public static final int UNKNOWN = 6;
    public static final int MIN = 1;
    public static final int MAXPLUSONE = 7;
}
